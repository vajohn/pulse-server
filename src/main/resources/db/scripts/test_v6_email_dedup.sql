-- =====================================================================================
-- Integration test for V6 email identity de-duplication (PULSE-8).
--
-- Runs against a REAL Postgres that already has V6 applied (so it exercises the actual
-- schema, FK ON DELETE CASCADE rules, and the case-insensitive users_email_lower_key index).
--
-- It seeds a case-variant duplicate PAIR:
--   * canonical: has sf_user_id, holds SUPER_ADMIN, email "X-9001@ADSB.AE"
--   * orphan:    no sf_user_id, holds EMPLOYEE (a role the canonical lacks) + EMPLOYEE
--                permission, an audit_log row, and a manager link, email "x-9001@adsb.ae"
-- then replays the exact V6 merge body and ASSERTs:
--   - canonical kept, orphan gone
--   - orphan's EMPLOYEE role MERGED onto canonical (and SUPER_ADMIN preserved)
--   - orphan's permission + audit row + managed-report repointed to canonical
--   - surviving email is lower-cased
--   - a new case-variant insert is REJECTED by users_email_lower_key
--
-- Because V6 is already applied, the case-insensitive index would block seeding the
-- duplicate pair, so the test drops it, seeds + merges (the merge re-creates it just like
-- V6), then asserts. Wrapped in a transaction and ROLLED BACK — leaves the DB untouched.
-- Run: docker exec -i mahara_postgres psql -U postgres -d pulse -v ON_ERROR_STOP=1 -f - < test_v6_email_dedup.sql
-- (or pipe the file in). Any failed ASSERT aborts with a clear message.
-- =====================================================================================

BEGIN;

DO $$
DECLARE
    canonical_id UUID := '00000000-0000-0000-0000-0000000c0001';  -- has sf_user_id + SUPER_ADMIN
    orphan_id    UUID := '00000000-0000-0000-0000-0000000c0002';  -- orphan, EMPLOYEE
    report_id    UUID := '00000000-0000-0000-0000-0000000c0003';  -- managed by the orphan
    super_role   UUID;
    emp_role     UUID;
    emp_perm     UUID;
    dup_id       UUID;
    n_rows       INT;
    survivor_email TEXT;
    rejected     BOOLEAN := FALSE;
BEGIN
    -- Re-create the PRE-V6 state so the case-variant pair can be seeded: drop the
    -- case-insensitive index for the duration of the seed (the merge re-creates it,
    -- mirroring exactly what V6 does). The whole block is rolled back, so the live
    -- index is untouched.
    DROP INDEX IF EXISTS users_email_lower_key;

    -- ---- Roles / permission (reuse if the seed already created them) ----
    SELECT id INTO super_role FROM roles WHERE name = 'SUPER_ADMIN';
    IF super_role IS NULL THEN
        super_role := gen_random_uuid();
        INSERT INTO roles (id, name) VALUES (super_role, 'SUPER_ADMIN');
    END IF;
    SELECT id INTO emp_role FROM roles WHERE name = 'EMPLOYEE';
    IF emp_role IS NULL THEN
        emp_role := gen_random_uuid();
        INSERT INTO roles (id, name) VALUES (emp_role, 'EMPLOYEE');
    END IF;
    SELECT id INTO emp_perm FROM permissions WHERE name = 'SPARK_NOMINATE';
    IF emp_perm IS NULL THEN
        emp_perm := gen_random_uuid();
        INSERT INTO permissions (id, name) VALUES (emp_perm, 'SPARK_NOMINATE');
    END IF;

    -- ---- Seed the case-variant duplicate pair ----
    -- NOTE: emails differ only by case; this is exactly what the case-sensitive constraint allowed.
    INSERT INTO users (id, email, display_name, sf_user_id, active, created_at)
        VALUES (canonical_id, 'X-9001@ADSB.AE', 'Canonical Synced', '9001', TRUE, now() - interval '10 days');
    INSERT INTO users (id, email, display_name, sf_user_id, active, created_at)
        VALUES (orphan_id, 'x-9001@adsb.ae', 'Orphan Login', NULL, TRUE, now() - interval '1 day');
    INSERT INTO users (id, email, display_name, active, created_at)
        VALUES (report_id, 'report-9001@adsb.ae', 'Direct Report', TRUE, now());

    -- canonical holds SUPER_ADMIN; orphan holds EMPLOYEE (role the canonical lacks).
    INSERT INTO user_roles (user_id, role_id) VALUES (canonical_id, super_role);
    INSERT INTO user_roles (user_id, role_id) VALUES (orphan_id, emp_role);
    -- orphan also has a direct permission grant + an audit row + a managed report.
    INSERT INTO user_permissions (user_id, permission_id) VALUES (orphan_id, emp_perm);
    INSERT INTO audit_logs (id, user_id, action, entity_type, details, created_at)
        VALUES (gen_random_uuid(), orphan_id, 'LOGIN_X4AUTH', 'session', '{}'::jsonb, now());
    UPDATE users SET manager_id = orphan_id WHERE id = report_id;

    -- =================================================================================
    -- Replay the V6 merge body (verbatim logic) for this group.
    -- =================================================================================
    FOR dup_id IN
        SELECT id FROM users
        WHERE lower(trim(email)) = 'x-9001@adsb.ae' AND id <> canonical_id
    LOOP
        DELETE FROM user_roles d WHERE d.user_id = dup_id
            AND EXISTS (SELECT 1 FROM user_roles c WHERE c.user_id = canonical_id AND c.role_id = d.role_id);
        UPDATE user_roles SET user_id = canonical_id WHERE user_id = dup_id;

        DELETE FROM user_permissions d WHERE d.user_id = dup_id
            AND EXISTS (SELECT 1 FROM user_permissions c WHERE c.user_id = canonical_id AND c.permission_id = d.permission_id);
        UPDATE user_permissions SET user_id = canonical_id WHERE user_id = dup_id;

        -- audit_logs is immutable; V6 disables the triggers for the merge, so do the same here.
        ALTER TABLE audit_logs DISABLE TRIGGER audit_logs_immutable_update;
        UPDATE audit_logs SET user_id = canonical_id WHERE user_id = dup_id;
        ALTER TABLE audit_logs ENABLE TRIGGER audit_logs_immutable_update;

        UPDATE users      SET manager_id = canonical_id WHERE manager_id = dup_id;

        DELETE FROM users WHERE id = dup_id;
    END LOOP;

    UPDATE users SET email = lower(trim(email))
        WHERE id = canonical_id AND email <> lower(trim(email));

    -- Re-create the case-insensitive unique index exactly as V6 does, so the
    -- rejection assertion below exercises the real guard.
    CREATE UNIQUE INDEX IF NOT EXISTS users_email_lower_key ON users (lower(email));

    -- =================================================================================
    -- ASSERTIONS
    -- =================================================================================
    -- canonical kept
    SELECT count(*) INTO n_rows FROM users WHERE id = canonical_id;
    ASSERT n_rows = 1, 'canonical user must still exist';
    -- orphan gone
    SELECT count(*) INTO n_rows FROM users WHERE id = orphan_id;
    ASSERT n_rows = 0, 'orphan duplicate must be deleted';
    -- SUPER_ADMIN preserved AND EMPLOYEE merged onto canonical
    SELECT count(*) INTO n_rows FROM user_roles WHERE user_id = canonical_id AND role_id = super_role;
    ASSERT n_rows = 1, 'canonical must retain SUPER_ADMIN';
    SELECT count(*) INTO n_rows FROM user_roles WHERE user_id = canonical_id AND role_id = emp_role;
    ASSERT n_rows = 1, 'orphan EMPLOYEE role must be merged onto canonical';
    -- permission repointed
    SELECT count(*) INTO n_rows FROM user_permissions WHERE user_id = canonical_id AND permission_id = emp_perm;
    ASSERT n_rows = 1, 'orphan permission grant must be repointed to canonical';
    -- audit row repointed (not cascade-deleted)
    SELECT count(*) INTO n_rows FROM audit_logs WHERE user_id = canonical_id AND action = 'LOGIN_X4AUTH';
    ASSERT n_rows = 1, 'orphan audit row must survive, repointed to canonical';
    -- managed report repointed
    SELECT count(*) INTO n_rows FROM users WHERE id = report_id AND manager_id = canonical_id;
    ASSERT n_rows = 1, 'direct report must now point at the canonical manager';
    -- survivor email lower-cased
    SELECT email INTO survivor_email FROM users WHERE id = canonical_id;
    ASSERT survivor_email = 'x-9001@adsb.ae',
        format('survivor email must be lower-cased, was "%s"', survivor_email);

    -- a NEW case-variant insert must be rejected by users_email_lower_key
    BEGIN
        INSERT INTO users (id, email, display_name, active)
            VALUES (gen_random_uuid(), 'X-9001@ADSB.ae', 'Should Fail', TRUE);
    EXCEPTION WHEN unique_violation THEN
        rejected := TRUE;
    END;
    ASSERT rejected, 'case-variant email insert must be rejected by users_email_lower_key';

    RAISE NOTICE 'V6 dedup integration test: ALL ASSERTIONS PASSED';
END $$;

ROLLBACK;

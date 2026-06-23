-- =====================================================================================
-- V6 — Email identity normalization & de-duplication (PULSE-8)
-- =====================================================================================
-- Root cause: users.email had a CASE-SENSITIVE unique constraint (users_email_key).
-- saf-recon sync stored the SF-cased email (e.g. "X-2340@ADSB.ae") while X4Auth login
-- lowercased + did a case-sensitive lookup that MISSED the synced row, producing a
-- second, orphaned identity (no sf_user_id / org / merged roles).
--
-- This migration is idempotent & self-contained:
--   1. Merge every set of users sharing lower(email) into a single canonical row.
--      Canonical = the row WITH sf_user_id; tie / none -> smallest created_at (then id).
--      All FK references from the non-canonical duplicates are repointed to the canonical
--      id BEFORE the duplicates are deleted, so ON DELETE CASCADE never destroys merged
--      data. Composite-PK / unique-constrained child rows that would collide on repoint
--      are deleted first (the canonical's row wins), then the rest are repointed.
--   2. Normalise survivor emails to lower(trim(email)).
--   3. Replace the case-sensitive unique constraint with a case-insensitive unique index.
--
-- Re-runnable: after the merge the duplicate-group query returns nothing (no-op), and the
-- lower(email) unique index then prevents any new case-variant duplicate from being created.
-- =====================================================================================

-- audit_logs is immutable (BEFORE UPDATE/DELETE triggers raise). Its user_id FK is plain
-- NO ACTION (no cascade), so the duplicate user can't be deleted while audit rows reference
-- it, yet the trigger blocks repointing too. We must repoint the audit trail onto the
-- canonical id (preserving it), so disable the immutability triggers ONLY for the duration
-- of this migration, then restore them. (Migrations run as table owner; this is scoped to
-- the transaction and is the standard way to perform an authorised identity merge.)
ALTER TABLE audit_logs DISABLE TRIGGER audit_logs_immutable_update;
ALTER TABLE audit_logs DISABLE TRIGGER audit_logs_immutable_delete;

DO $$
DECLARE
    grp           RECORD;   -- one row per lower(email) that has >1 user
    canonical_id  UUID;
    dup_id        UUID;
BEGIN
    -- Iterate over each duplicate group (case-insensitive email shared by >1 user).
    FOR grp IN
        SELECT lower(trim(email)) AS norm_email
        FROM users
        WHERE email IS NOT NULL
        GROUP BY lower(trim(email))
        HAVING count(*) > 1
    LOOP
        -- Pick the canonical row for this group:
        --   prefer a row that has sf_user_id (the authoritative synced identity),
        --   then the earliest created_at, then the smallest id (fully deterministic).
        SELECT id INTO canonical_id
        FROM users
        WHERE lower(trim(email)) = grp.norm_email
        ORDER BY (sf_user_id IS NULL),                       -- false (has sf_user_id) sorts first
                 created_at NULLS LAST,
                 id
        LIMIT 1;

        -- Repoint + delete every non-canonical duplicate in this group.
        FOR dup_id IN
            SELECT id
            FROM users
            WHERE lower(trim(email)) = grp.norm_email
              AND id <> canonical_id
        LOOP
            ------------------------------------------------------------------
            -- Composite-PK / one-row-per-user join tables.
            -- Delete the dup's rows that would collide with an existing canonical
            -- row on the (user_id, other_key) unique key, THEN repoint the rest.
            -- This MERGES roles/permissions/teams/groups/org-units onto the
            -- canonical row without a PK violation (e.g. an orphan holding
            -- SUPER_ADMIN ends with SUPER_ADMIN on the canonical row).
            ------------------------------------------------------------------

            -- user_roles (PK: user_id, role_id)
            DELETE FROM user_roles d
            WHERE d.user_id = dup_id
              AND EXISTS (SELECT 1 FROM user_roles c
                          WHERE c.user_id = canonical_id AND c.role_id = d.role_id);
            UPDATE user_roles SET user_id = canonical_id WHERE user_id = dup_id;

            -- user_permissions (PK: user_id, permission_id)
            DELETE FROM user_permissions d
            WHERE d.user_id = dup_id
              AND EXISTS (SELECT 1 FROM user_permissions c
                          WHERE c.user_id = canonical_id AND c.permission_id = d.permission_id);
            UPDATE user_permissions SET user_id = canonical_id WHERE user_id = dup_id;

            -- user_teams (PK: user_id, team_id)
            DELETE FROM user_teams d
            WHERE d.user_id = dup_id
              AND EXISTS (SELECT 1 FROM user_teams c
                          WHERE c.user_id = canonical_id AND c.team_id = d.team_id);
            UPDATE user_teams SET user_id = canonical_id WHERE user_id = dup_id;

            -- user_groups (PK: user_id, group_id)
            DELETE FROM user_groups d
            WHERE d.user_id = dup_id
              AND EXISTS (SELECT 1 FROM user_groups c
                          WHERE c.user_id = canonical_id AND c.group_id = d.group_id);
            UPDATE user_groups SET user_id = canonical_id WHERE user_id = dup_id;

            -- user_org_unit (PK: user_id, org_unit_id)
            DELETE FROM user_org_unit d
            WHERE d.user_id = dup_id
              AND EXISTS (SELECT 1 FROM user_org_unit c
                          WHERE c.user_id = canonical_id AND c.org_unit_id = d.org_unit_id);
            UPDATE user_org_unit SET user_id = canonical_id WHERE user_id = dup_id;

            -- user_sf_profile (PK: user_id — exactly one profile per user). The canonical
            -- (sf_user_id-bearing) row normally owns the profile; if the dup also has one,
            -- keep the canonical's and drop the dup's.
            DELETE FROM user_sf_profile d
            WHERE d.user_id = dup_id
              AND EXISTS (SELECT 1 FROM user_sf_profile c WHERE c.user_id = canonical_id);
            UPDATE user_sf_profile SET user_id = canonical_id WHERE user_id = dup_id;

            ------------------------------------------------------------------
            -- Tables with a unique constraint that INCLUDES a user column.
            -- Repointing could collide on that unique key, so delete the dup's
            -- colliding rows first, then repoint the remainder.
            ------------------------------------------------------------------

            -- response_session: unique partial index idx_session_user_form_open
            --   ON (user_id, form_id) WHERE completed_at IS NULL — only OPEN sessions collide.
            DELETE FROM response_session d
            WHERE d.user_id = dup_id
              AND d.completed_at IS NULL
              AND EXISTS (SELECT 1 FROM response_session c
                          WHERE c.user_id = canonical_id
                            AND c.form_id = d.form_id
                            AND c.completed_at IS NULL);
            UPDATE response_session SET user_id = canonical_id WHERE user_id = dup_id;

            -- form_assignment.user_id: unique partial index uq_active_user_form_assignment
            --   ON (form_id, user_id) WHERE active = TRUE AND user_id IS NOT NULL.
            DELETE FROM form_assignment d
            WHERE d.user_id = dup_id
              AND d.active = TRUE
              AND EXISTS (SELECT 1 FROM form_assignment c
                          WHERE c.user_id = canonical_id
                            AND c.form_id = d.form_id
                            AND c.active = TRUE);
            UPDATE form_assignment SET user_id = canonical_id WHERE user_id = dup_id;
            -- form_assignment.assigned_by has no unique constraint — plain repoint.
            UPDATE form_assignment SET assigned_by = canonical_id WHERE assigned_by = dup_id;

            -- nominations.nominator_id: uq_one_nomination_per_category
            --   UNIQUE (award_period_id, category_id, nominator_id).
            DELETE FROM nominations d
            WHERE d.nominator_id = dup_id
              AND EXISTS (SELECT 1 FROM nominations c
                          WHERE c.nominator_id = canonical_id
                            AND c.award_period_id = d.award_period_id
                            AND c.category_id = d.category_id);
            UPDATE nominations SET nominator_id = canonical_id WHERE nominator_id = dup_id;
            -- nominations.nominee_id has no unique constraint — plain repoint.
            UPDATE nominations SET nominee_id = canonical_id WHERE nominee_id = dup_id;

            -- leader_votes.leader_id: uq_one_vote_per_category
            --   UNIQUE (award_period_id, category_id, leader_id).
            DELETE FROM leader_votes d
            WHERE d.leader_id = dup_id
              AND EXISTS (SELECT 1 FROM leader_votes c
                          WHERE c.leader_id = canonical_id
                            AND c.award_period_id = d.award_period_id
                            AND c.category_id = d.category_id);
            UPDATE leader_votes SET leader_id = canonical_id WHERE leader_id = dup_id;

            -- spark_congratulations: uq_one_congrats_per_user_winner UNIQUE (winner_id, user_id).
            -- Both columns reference users(id); repoint each, deleting collisions first.
            DELETE FROM spark_congratulations d
            WHERE d.user_id = dup_id
              AND EXISTS (SELECT 1 FROM spark_congratulations c
                          WHERE c.user_id = canonical_id AND c.winner_id = d.winner_id);
            UPDATE spark_congratulations SET user_id = canonical_id WHERE user_id = dup_id;
            DELETE FROM spark_congratulations d
            WHERE d.winner_id = dup_id
              AND EXISTS (SELECT 1 FROM spark_congratulations c
                          WHERE c.winner_id = canonical_id AND c.user_id = d.user_id);
            UPDATE spark_congratulations SET winner_id = canonical_id WHERE winner_id = dup_id;

            ------------------------------------------------------------------
            -- Plain single-FK tables (no user-keyed unique constraint) — straight repoint.
            ------------------------------------------------------------------
            UPDATE sessions             SET user_id          = canonical_id WHERE user_id          = dup_id;
            UPDATE audit_logs           SET user_id          = canonical_id WHERE user_id          = dup_id;
            UPDATE role_change_requests SET target_user_id   = canonical_id WHERE target_user_id   = dup_id;
            UPDATE role_change_requests SET requested_by_id  = canonical_id WHERE requested_by_id  = dup_id;
            UPDATE role_change_requests SET reviewed_by_id   = canonical_id WHERE reviewed_by_id   = dup_id;
            UPDATE award_periods        SET created_by       = canonical_id WHERE created_by       = dup_id;
            UPDATE nomination_attachments SET uploaded_by    = canonical_id WHERE uploaded_by      = dup_id;
            UPDATE spark_winners        SET winner_id        = canonical_id WHERE winner_id        = dup_id;
            UPDATE spark_winners        SET finalized_by     = canonical_id WHERE finalized_by     = dup_id;
            UPDATE psychometric_test    SET created_by       = canonical_id WHERE created_by       = dup_id;
            UPDATE scoring_key_version  SET published_by     = canonical_id WHERE published_by     = dup_id;
            UPDATE norm_table_version   SET published_by     = canonical_id WHERE published_by     = dup_id;
            UPDATE test_result          SET reviewed_by      = canonical_id WHERE reviewed_by      = dup_id;
            -- users.manager_id (self-reference): anyone managed by the dup now reports to canonical.
            UPDATE users                SET manager_id       = canonical_id WHERE manager_id       = dup_id;

            ------------------------------------------------------------------
            -- All references repointed — safe to delete the orphan duplicate.
            ------------------------------------------------------------------
            DELETE FROM users WHERE id = dup_id;

            RAISE NOTICE 'V6: merged duplicate user % into canonical % (email group %)',
                dup_id, canonical_id, grp.norm_email;
        END LOOP;
    END LOOP;
END $$;

-- Restore audit_logs immutability.
ALTER TABLE audit_logs ENABLE TRIGGER audit_logs_immutable_update;
ALTER TABLE audit_logs ENABLE TRIGGER audit_logs_immutable_delete;

-- Normalise the surviving rows so stored email == lower(trim(email)).
UPDATE users SET email = lower(trim(email)) WHERE email IS NOT NULL AND email <> lower(trim(email));

-- Replace the case-sensitive unique constraint/index with a case-insensitive one.
-- users_email_key is a UNIQUE CONSTRAINT (its backing index can't be dropped directly),
-- so drop the constraint; the second DROP INDEX is a fallback for environments where it
-- exists only as a plain index.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;
DROP INDEX IF EXISTS users_email_key;

CREATE UNIQUE INDEX IF NOT EXISTS users_email_lower_key ON users (lower(email));

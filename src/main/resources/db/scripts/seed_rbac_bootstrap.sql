-- =============================================================================
-- RBAC Bootstrap Seed — run MANUALLY after Flyway V4 migration
-- =============================================================================
--
-- Context:
--   Flyway V4 (V4__rbac_role_seed.sql) drops all non-EMPLOYEE roles and their
--   user_roles associations to establish a clean DB-backed RBAC baseline.
--   After V4 applies, only the EMPLOYEE role exists and no admin users have
--   any elevated role assignment. This script re-creates the two core
--   operational roles and grants them a sensible starting permission set.
--
-- How to run:
--   docker exec -i mahara_postgres psql -U postgres -d pulse \
--       < src/main/resources/db/scripts/seed_rbac_bootstrap.sql
--
-- After running this script, use POST /api/admin/roles/{id}/permissions
-- (with ROLE_ALL authority) to add or remove permissions as needed.
-- Then use PUT /api/admin/users/{userId}/roles to assign roles to users.
--
-- NOTE: This script is idempotent — it uses INSERT ... ON CONFLICT DO NOTHING.
-- =============================================================================

-- ── Step 1: Ensure core roles exist ─────────────────────────────────────────

INSERT INTO roles (id, name)
VALUES
    (gen_random_uuid(), 'HR_FULL_CRUD'),
    (gen_random_uuid(), 'MANAGER')
ON CONFLICT (name) DO NOTHING;

-- ── Step 2: Grant HR_FULL_CRUD its full permission set ───────────────────────
--
-- HR_FULL_CRUD receives:
--   All USR, ORG, SYNC, ROLE_READ, FORM, ASSESS, REPORT, SPARK permissions
--   plus SCOPE_ORG_WIDE for full org visibility.
--   Does NOT receive ROLE_CREATE/UPDATE/DELETE/ASSIGN_APPROVE or SYS_* by default
--   (those require ROLE_ALL which must be explicitly granted by a super-admin).

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'HR_FULL_CRUD'
  AND p.name IN (
      'USR_READ', 'USR_CREATE', 'USR_UPDATE', 'USR_ROLE_ASSIGN', 'USR_IMPORT',
      'ORG_READ', 'ORG_CREATE', 'ORG_UPDATE', 'ORG_MOVE_USER',
      'SYNC_STATUS',
      'ROLE_READ',
      'SCOPE_ORG_WIDE',
      'FORM_READ', 'FORM_CREATE', 'FORM_UPDATE', 'FORM_DELETE',
          'FORM_ASSIGN', 'FORM_PUBLISH', 'FORM_SESSION_READ',
      'ASSESS_READ', 'ASSESS_CREATE', 'ASSESS_UPDATE', 'ASSESS_DELETE',
          'ASSESS_ASSIGN', 'ASSESS_KEY_MANAGE', 'ASSESS_RESULT_READ',
          'ASSESS_COMPETENCY_MANAGE',
      'REPORT_VIEW', 'REPORT_EXPORT', 'REPORT_TEXT_VIEW', 'REPORT_ASSESS_VIEW',
      'SPARK_REVIEW', 'SPARK_MANAGE',
      'SYS_AUDIT_VIEW',
      'ROLE_ASSIGN_APPROVE'
  )
ON CONFLICT DO NOTHING;

-- ── Step 3: Grant MANAGER its permission set ─────────────────────────────────
--
-- MANAGER receives:
--   USR_READ (view directory), SCOPE_TEAM (subtree-only visibility),
--   FORM_READ, FORM_SESSION_READ (view forms/responses in their subtree),
--   ASSESS_RESULT_READ (view team psychometric results),
--   REPORT_VIEW (access team analytics dashboard),
--   SPARK_REVIEW, SPARK_NOMINATE, SPARK_VOTE (spark participation).

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'MANAGER'
  AND p.name IN (
      'USR_READ',
      'SCOPE_TEAM',
      'FORM_READ', 'FORM_SESSION_READ',
      'ASSESS_RESULT_READ',
      'REPORT_VIEW',
      'SPARK_NOMINATE', 'SPARK_VOTE', 'SPARK_REVIEW'
  )
ON CONFLICT DO NOTHING;

-- ── Step 4: Display the result ────────────────────────────────────────────────

SELECT r.name AS role, array_agg(p.name ORDER BY p.name) AS permissions
FROM roles r
LEFT JOIN role_permissions rp ON rp.role_id = r.id
LEFT JOIN permissions p ON p.id = rp.permission_id
WHERE r.name IN ('HR_FULL_CRUD', 'MANAGER', 'EMPLOYEE')
GROUP BY r.name
ORDER BY r.name;

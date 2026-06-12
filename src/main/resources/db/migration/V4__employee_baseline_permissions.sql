-- EMPLOYEE baseline permissions. The squashed V1 + V3 create the EMPLOYEE role with NO permissions,
-- so logged-in employees cannot use any feature. Grant the baseline every employee needs:
--   FORM_READ      — see/open assigned surveys
--   ASSESS_READ    — take assigned psychometric tests
--   SPARK_NOMINATE — submit Spark nominations
--   SPARK_VOTE     — cast Spark votes
--   ANNOUNCE_READ  — read broadcasts (permission name; BROADCAST_VIEWER is a role, not a permission)
-- Idempotent: insert only the (role,permission) pairs that don't already exist.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('FORM_READ', 'ASSESS_READ', 'SPARK_NOMINATE', 'SPARK_VOTE', 'ANNOUNCE_READ')
WHERE r.name = 'EMPLOYEE'
  AND NOT EXISTS (
        SELECT 1 FROM role_permissions rp
        WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

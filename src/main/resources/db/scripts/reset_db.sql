-- ============================================================
-- RESET: Drop all tables and Flyway history, then let Flyway
-- recreate everything on next bootRun.
--
-- Usage:  psql -U postgres -d pulse -f reset_db.sql
-- Then:   ./gradlew bootRun   (Flyway re-applies V1, V2, V3)
-- ============================================================

-- Disable FK checks during teardown
SET session_replication_role = 'replica';

DROP TABLE IF EXISTS answer_rating       CASCADE;
DROP TABLE IF EXISTS answer_choice       CASCADE;
DROP TABLE IF EXISTS answer_scale        CASCADE;
DROP TABLE IF EXISTS answer_text         CASCADE;
DROP TABLE IF EXISTS answer_submission   CASCADE;
DROP TABLE IF EXISTS response_session    CASCADE;
DROP TABLE IF EXISTS anon_identity       CASCADE;
DROP TABLE IF EXISTS questionnaire_assignment CASCADE;
DROP TABLE IF EXISTS candidate_answer    CASCADE;
DROP TABLE IF EXISTS question            CASCADE;
DROP TABLE IF EXISTS questionnaire       CASCADE;
DROP TABLE IF EXISTS audit_logs          CASCADE;
DROP TABLE IF EXISTS sessions            CASCADE;
DROP TABLE IF EXISTS user_org_unit       CASCADE;
DROP TABLE IF EXISTS user_groups         CASCADE;
DROP TABLE IF EXISTS user_teams          CASCADE;
DROP TABLE IF EXISTS user_permissions    CASCADE;
DROP TABLE IF EXISTS user_roles          CASCADE;
DROP TABLE IF EXISTS role_permissions    CASCADE;
DROP TABLE IF EXISTS users               CASCADE;
DROP TABLE IF EXISTS organizational_units CASCADE;
DROP TABLE IF EXISTS permissions         CASCADE;
DROP TABLE IF EXISTS roles               CASCADE;
DROP TABLE IF EXISTS teams               CASCADE;
DROP TABLE IF EXISTS groups              CASCADE;
DROP TABLE IF EXISTS titles              CASCADE;
DROP TABLE IF EXISTS flyway_schema_history CASCADE;

SET session_replication_role = 'origin';

-- Verify nothing remains
DO $$
BEGIN
  RAISE NOTICE 'All tables dropped. Run ./gradlew bootRun to recreate via Flyway.';
END $$;

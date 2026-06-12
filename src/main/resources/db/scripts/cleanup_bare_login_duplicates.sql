-- One-time cleanup of bare, login-provisioned duplicate users created before the
-- EMPLOYEE_NUMBER_STRICT matching fix. These users logged in via X4Auth but were never
-- linked to a saf-recon employee record (no sf_user_id, no org_unit, not Azure-sourced).
-- Under STRICT matching they re-resolve to the correct saf employee on next login, so the
-- bare record is obsolete.
--
-- Target set (used in every statement below):
--   sf_user_id  IS NULL   -- never matched/enriched by the saf sync
--   org_unit_id IS NULL   -- no org placement
--   azure_ad_id IS NULL   -- not an Azure/Entra-sourced account
--   last_login_at IS NOT NULL  -- was created by an interactive login (not a sync row)
--
-- FK notes (verified against the schema): most child tables that reference users use
-- ON DELETE CASCADE (user_roles, sessions, user_permissions, user_org_unit, user_groups,
-- user_teams, user_sf_profile) and clean up automatically. The exceptions are NO ACTION
-- (RESTRICT-like) and MUST be cleared first:
--   * audit_logs.user_id        — always has rows for a logged-in user (autoprovision + login)
--   * response_session.user_id  — has rows only if the user opened a survey/test session
--     (+ its answer_submission / answer_* children)
-- "Authored/created-by" columns (form_assignment.assigned_by, nominations.nominator_id,
-- award_periods.created_by, *_published_by, etc.) cannot have rows for a bare login user —
-- they have no permissions to create such records — so they are not handled here. If a future
-- target set is broader, re-check the FK list (pg_constraint where confrelid='users') first.
--
-- RUN ORDER:
--   1) Run the DRY-RUN SELECT, review the count + sample.
--   2) Only if correct, run the DELETE block inside the transaction.

-- ============================================================================
-- DRY RUN — review before deleting
-- ============================================================================
SELECT id, email, last_login_at
FROM users
WHERE sf_user_id IS NULL
  AND org_unit_id IS NULL
  AND azure_ad_id IS NULL
  AND last_login_at IS NOT NULL;

-- ============================================================================
-- DELETE — run only after reviewing the dry-run
-- ============================================================================
BEGIN;

-- 0) Typed answers + submissions for any survey/test sessions these users opened.
DELETE FROM answer_text   WHERE submission_id IN (SELECT asub.id FROM answer_submission asub JOIN response_session rs ON rs.id = asub.session_id WHERE rs.user_id IN (SELECT id FROM users WHERE sf_user_id IS NULL AND org_unit_id IS NULL AND azure_ad_id IS NULL AND last_login_at IS NOT NULL));
DELETE FROM answer_scale  WHERE submission_id IN (SELECT asub.id FROM answer_submission asub JOIN response_session rs ON rs.id = asub.session_id WHERE rs.user_id IN (SELECT id FROM users WHERE sf_user_id IS NULL AND org_unit_id IS NULL AND azure_ad_id IS NULL AND last_login_at IS NOT NULL));
DELETE FROM answer_choice WHERE submission_id IN (SELECT asub.id FROM answer_submission asub JOIN response_session rs ON rs.id = asub.session_id WHERE rs.user_id IN (SELECT id FROM users WHERE sf_user_id IS NULL AND org_unit_id IS NULL AND azure_ad_id IS NULL AND last_login_at IS NOT NULL));
DELETE FROM answer_rating WHERE submission_id IN (SELECT asub.id FROM answer_submission asub JOIN response_session rs ON rs.id = asub.session_id WHERE rs.user_id IN (SELECT id FROM users WHERE sf_user_id IS NULL AND org_unit_id IS NULL AND azure_ad_id IS NULL AND last_login_at IS NOT NULL));
DELETE FROM answer_adjective WHERE submission_id IN (SELECT asub.id FROM answer_submission asub JOIN response_session rs ON rs.id = asub.session_id WHERE rs.user_id IN (SELECT id FROM users WHERE sf_user_id IS NULL AND org_unit_id IS NULL AND azure_ad_id IS NULL AND last_login_at IS NOT NULL));

DELETE FROM answer_submission WHERE session_id IN (SELECT id FROM response_session WHERE user_id IN (SELECT id FROM users WHERE sf_user_id IS NULL AND org_unit_id IS NULL AND azure_ad_id IS NULL AND last_login_at IS NOT NULL));

-- 1) Sessions (survey/psychometric) opened by these users — NO ACTION FK.
DELETE FROM response_session WHERE user_id IN (SELECT id FROM users WHERE sf_user_id IS NULL AND org_unit_id IS NULL AND azure_ad_id IS NULL AND last_login_at IS NOT NULL);

-- 2) Audit log rows — NO ACTION FK, always present for a logged-in user.
DELETE FROM audit_logs WHERE user_id IN (SELECT id FROM users WHERE sf_user_id IS NULL AND org_unit_id IS NULL AND azure_ad_id IS NULL AND last_login_at IS NOT NULL);

-- 3) The bare users themselves. ON DELETE CASCADE removes user_roles, sessions (refresh
--    tokens), user_permissions, user_org_unit, user_groups, user_teams, user_sf_profile.
DELETE FROM users WHERE sf_user_id IS NULL AND org_unit_id IS NULL AND azure_ad_id IS NULL AND last_login_at IS NOT NULL;

COMMIT;

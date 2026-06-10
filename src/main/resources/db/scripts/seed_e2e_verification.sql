-- ============================================================================
-- seed_e2e_verification.sql
-- End-to-end endpoint-test fixtures for SURVEY + PSYCHOMETRIC features.
-- Question content drawn from br/Latest Versions/PTI Plus 2.0.csv (text-only items).
--
-- Candidate user: c0a8018d-9cd6-18f1-819c-d6e94c9e0c76 (real synced user).
-- Idempotent: re-running deletes prior seed rows (and their runtime children) first.
--
-- PSYCHOMETRIC scenario (validates the new parametric-norm scoring path):
--   1 ACTIVE PERSONALITY test, scale "Extraversion" (SUM), 3 Likert(1-4) SCALE items,
--   ACTIVE scoring key (FORWARD, weight 1), VALIDATED norm with mean=8, sd=2.
--   Answering all 3 items "4" -> raw = 12 -> z = (12-8)/2 = 2.0 -> STEN 10, %ile 97.72.
-- ============================================================================

-- ── Cleanup (FK-safe order) ─────────────────────────────────────────────────
-- Runtime children from prior runs
DELETE FROM scale_score      WHERE result_id IN (SELECT id FROM test_result WHERE test_id = 'eeee0001-0000-0000-0000-000000000002');
DELETE FROM competency_score WHERE result_id IN (SELECT id FROM test_result WHERE test_id = 'eeee0001-0000-0000-0000-000000000002');
DELETE FROM test_result      WHERE test_id = 'eeee0001-0000-0000-0000-000000000002';
DELETE FROM answer_scale  WHERE submission_id IN (SELECT s.id FROM answer_submission s JOIN response_session rs ON s.session_id = rs.id WHERE rs.form_id IN ('eeee0001-0000-0000-0000-000000000001','eeee0002-0000-0000-0000-000000000001'));
DELETE FROM answer_text   WHERE submission_id IN (SELECT s.id FROM answer_submission s JOIN response_session rs ON s.session_id = rs.id WHERE rs.form_id IN ('eeee0001-0000-0000-0000-000000000001','eeee0002-0000-0000-0000-000000000001'));
DELETE FROM answer_choice WHERE submission_id IN (SELECT s.id FROM answer_submission s JOIN response_session rs ON s.session_id = rs.id WHERE rs.form_id IN ('eeee0001-0000-0000-0000-000000000001','eeee0002-0000-0000-0000-000000000001'));
DELETE FROM answer_submission WHERE session_id IN (SELECT id FROM response_session WHERE form_id IN ('eeee0001-0000-0000-0000-000000000001','eeee0002-0000-0000-0000-000000000001'));
DELETE FROM response_session  WHERE form_id IN ('eeee0001-0000-0000-0000-000000000001','eeee0002-0000-0000-0000-000000000001');
-- Static seed rows
DELETE FROM norm_scale_param      WHERE norm_table_version_id = 'eeee0001-0000-0000-0000-000000000005';
DELETE FROM norm_table_version    WHERE id = 'eeee0001-0000-0000-0000-000000000005';
DELETE FROM scoring_key_item      WHERE scoring_key_id = 'eeee0001-0000-0000-0000-000000000004';
DELETE FROM scoring_key_version   WHERE id = 'eeee0001-0000-0000-0000-000000000004';
DELETE FROM psychometric_scale    WHERE test_id = 'eeee0001-0000-0000-0000-000000000002';
DELETE FROM psychometric_test     WHERE id = 'eeee0001-0000-0000-0000-000000000002';
DELETE FROM form_assignment       WHERE form_id IN ('eeee0001-0000-0000-0000-000000000001','eeee0002-0000-0000-0000-000000000001');
DELETE FROM question              WHERE form_id IN ('eeee0001-0000-0000-0000-000000000001','eeee0002-0000-0000-0000-000000000001');
DELETE FROM form                  WHERE id IN ('eeee0001-0000-0000-0000-000000000001','eeee0002-0000-0000-0000-000000000001');

-- ============================================================================
-- PSYCHOMETRIC
-- ============================================================================
INSERT INTO form (id, title, description, anon_window_minutes, type, created_at, updated_at) VALUES
  ('eeee0001-0000-0000-0000-000000000001', 'PTI Plus (E2E Seed)', 'E2E parametric-norm scoring fixture', 0, 'PSYCHOMETRIC', NOW(), NOW());

INSERT INTO psychometric_test (id, form_id, name, description, test_type, time_limit_secs, instructions, version, status, created_by, created_at, updated_at) VALUES
  ('eeee0001-0000-0000-0000-000000000002', 'eeee0001-0000-0000-0000-000000000001', 'PTI Plus (E2E Seed)', 'Personality, CTT scored', 'PERSONALITY', NULL, 'Answer honestly.', 1, 'ACTIVE', NULL, NOW(), NOW());

INSERT INTO psychometric_scale (id, test_id, parent_scale_id, name, description, score_method, display_order) VALUES
  ('eeee0001-0000-0000-0000-000000000003', 'eeee0001-0000-0000-0000-000000000002', NULL, 'Extraversion', 'Sociability / assertiveness', 'SUM', 1);

-- 3 Likert (1-4) SCALE items — text from PTI Plus 2.0.csv Q1-Q3
INSERT INTO question (id, form_id, body, question_type, effective_date, expiration_date, display_order, scale_min, scale_max, min_label, max_label, created_at, updated_at) VALUES
  ('eeee0001-0000-0000-0000-000000000011', 'eeee0001-0000-0000-0000-000000000001', 'I enjoy large social gatherings.', 'SCALE', NULL, NULL, 1, 1, 4, 'Strongly disagree', 'Strongly agree', NOW(), NOW()),
  ('eeee0001-0000-0000-0000-000000000012', 'eeee0001-0000-0000-0000-000000000001', 'Right now, I feel in control of my life.', 'SCALE', NULL, NULL, 2, 1, 4, 'Strongly disagree', 'Strongly agree', NOW(), NOW()),
  ('eeee0001-0000-0000-0000-000000000013', 'eeee0001-0000-0000-0000-000000000001', 'It''s easy for me to turn a goal into an action plan.', 'SCALE', NULL, NULL, 3, 1, 4, 'Strongly disagree', 'Strongly agree', NOW(), NOW());

INSERT INTO scoring_key_version (id, test_id, version, label, status, effective_from, created_at) VALUES
  ('eeee0001-0000-0000-0000-000000000004', 'eeee0001-0000-0000-0000-000000000002', 1, 'E2E Key v1', 'ACTIVE', NOW(), NOW());

INSERT INTO scoring_key_item (id, scoring_key_id, scale_id, question_id, direction, weight, correct_answer_id, partial_credit) VALUES
  ('eeee0001-0000-0000-0000-000000000041', 'eeee0001-0000-0000-0000-000000000004', 'eeee0001-0000-0000-0000-000000000003', 'eeee0001-0000-0000-0000-000000000011', 'FORWARD', 1, NULL, false),
  ('eeee0001-0000-0000-0000-000000000042', 'eeee0001-0000-0000-0000-000000000004', 'eeee0001-0000-0000-0000-000000000003', 'eeee0001-0000-0000-0000-000000000012', 'FORWARD', 1, NULL, false),
  ('eeee0001-0000-0000-0000-000000000043', 'eeee0001-0000-0000-0000-000000000004', 'eeee0001-0000-0000-0000-000000000003', 'eeee0001-0000-0000-0000-000000000013', 'FORWARD', 1, NULL, false);

-- VALIDATED parametric norm: mean=8, sd=2
INSERT INTO norm_table_version (id, test_id, version, label, sample_size, status, effective_from, created_at) VALUES
  ('eeee0001-0000-0000-0000-000000000005', 'eeee0001-0000-0000-0000-000000000002', 1, 'E2E Norm 2026-H1', 1000, 'VALIDATED', NOW(), NOW());

INSERT INTO norm_scale_param (id, norm_table_version_id, scale_id, mean, sd, sample_size) VALUES
  ('eeee0001-0000-0000-0000-000000000051', 'eeee0001-0000-0000-0000-000000000005', 'eeee0001-0000-0000-0000-000000000003', 8.0000, 2.0000, 1000);

-- Direct assignment to candidate
INSERT INTO form_assignment (id, form_id, org_unit_id, user_id, assigned_by, assigned_at, starts_at, expires_at, due_date, mandatory, active, include_children, allow_resubmission) VALUES
  ('eeee0001-0000-0000-0000-000000000006', 'eeee0001-0000-0000-0000-000000000001', NULL, 'c0a8018d-9cd6-18f1-819c-d6e94c9e0c76', 'c0a8018d-9cd6-18f1-819c-d6e94c9e0c76', NOW(), NULL, NULL, NULL, true, true, false, true);

-- CANDIDATE visibility policy: expose STEN profile / percentile / scale breakdown so the
-- candidate result endpoint surfaces the parametric scores (privacy default hides them).
DELETE FROM result_visibility_policy WHERE test_id = 'eeee0001-0000-0000-0000-000000000002';
INSERT INTO result_visibility_policy (id, test_id, audience, show_raw_score, show_sten_profile, show_percentile, show_competency_map, show_pass_fail_only, show_scale_breakdown) VALUES
  ('eeee0001-0000-0000-0000-000000000071', 'eeee0001-0000-0000-0000-000000000002', 'CANDIDATE', true, true, true, false, false, true);

-- ============================================================================
-- SURVEY
-- ============================================================================
INSERT INTO form (id, title, description, anon_window_minutes, type, created_at, updated_at) VALUES
  ('eeee0002-0000-0000-0000-000000000001', 'Engagement Pulse (E2E Seed)', 'E2E survey fixture', 0, 'SURVEY', NOW(), NOW());

INSERT INTO question (id, form_id, body, question_type, effective_date, expiration_date, display_order, scale_min, scale_max, min_label, max_label, created_at, updated_at) VALUES
  ('eeee0002-0000-0000-0000-000000000011', 'eeee0002-0000-0000-0000-000000000001', 'I feel valued at work.', 'SCALE', NULL, NULL, 1, 1, 5, 'Strongly disagree', 'Strongly agree', NOW(), NOW()),
  ('eeee0002-0000-0000-0000-000000000012', 'eeee0002-0000-0000-0000-000000000001', 'I would recommend this organization as a place to work.', 'SCALE', NULL, NULL, 2, 1, 5, 'Strongly disagree', 'Strongly agree', NOW(), NOW());

INSERT INTO form_assignment (id, form_id, org_unit_id, user_id, assigned_by, assigned_at, starts_at, expires_at, due_date, mandatory, active, include_children, allow_resubmission) VALUES
  ('eeee0002-0000-0000-0000-000000000006', 'eeee0002-0000-0000-0000-000000000001', NULL, 'c0a8018d-9cd6-18f1-819c-d6e94c9e0c76', 'c0a8018d-9cd6-18f1-819c-d6e94c9e0c76', NOW(), NULL, NULL, NULL, true, true, false, true);

-- Phase-3 micro-engagement E2E seed: PERSONALITY test with Immediate1 (IMMEDIATE, 1 item)
-- and Consolidated1 (CONSOLIDATED, 4 items), PARAMETRIC norms, active scoring key,
-- direct assignment to the candidate, CANDIDATE + HR_ADMIN visibility policies.
-- Idempotent-ish: deletes prior P3E2E rows first.

DO $$
DECLARE
  v_admin   UUID := '000016fb-6aba-4de1-a028-51cb3e923bee';
  v_cand    UUID := '00008711-ba68-498a-90a8-015058c059e6';
  v_form    UUID := 'a3e2e000-0000-4000-8000-000000000f01';
  v_test    UUID := 'a3e2e000-0000-4000-8000-0000000ec001';
  v_imm     UUID := 'a3e2e000-0000-4000-8000-00005ca1e001'; -- Immediate1 scale
  v_con     UUID := 'a3e2e000-0000-4000-8000-00005ca1e002'; -- Consolidated1 scale
  v_key     UUID := 'a3e2e000-0000-4000-8000-00000000e001';
  v_norm    UUID := 'a3e2e000-0000-4000-8000-00000000d001';
  v_qi      UUID := 'a3e2e000-0000-4000-8000-000000001001'; -- immediate question
  v_qc1     UUID := 'a3e2e000-0000-4000-8000-000000002001';
  v_qc2     UUID := 'a3e2e000-0000-4000-8000-000000002002';
  v_qc3     UUID := 'a3e2e000-0000-4000-8000-000000002003';
  v_qc4     UUID := 'a3e2e000-0000-4000-8000-000000002004';
BEGIN
  -- Cleanup any prior run
  DELETE FROM scale_progress      WHERE test_id = v_test;
  DELETE FROM user_item_exposure  WHERE test_id = v_test;
  DELETE FROM assessment_cadence  WHERE test_id = v_test;
  DELETE FROM scoring_key_item    WHERE scoring_key_id = v_key;
  DELETE FROM norm_scale_param    WHERE norm_table_version_id = v_norm;
  DELETE FROM scoring_key_version WHERE id = v_key;
  DELETE FROM norm_table_version  WHERE id = v_norm;
  DELETE FROM result_visibility_policy WHERE test_id = v_test;
  DELETE FROM form_assignment     WHERE form_id = v_form;
  DELETE FROM psychometric_scale  WHERE test_id = v_test;
  DELETE FROM question            WHERE form_id = v_form;
  DELETE FROM psychometric_test   WHERE id = v_test;
  DELETE FROM form                WHERE id = v_form;

  -- Form + test
  INSERT INTO form (id, title, description, type, created_at, updated_at)
    VALUES (v_form, 'P3 Micro E2E Form', 'Phase-3 micro-engagement e2e', 'PSYCHOMETRIC', now(), now());
  INSERT INTO psychometric_test (id, form_id, name, description, test_type, status, instructions, time_limit_secs, version, created_by, created_at, updated_at)
    VALUES (v_test, v_form, 'P3 Micro E2E', 'Immediate + Consolidated scales', 'PERSONALITY', 'ACTIVE', 'Answer honestly', 1800, 1, v_admin, now(), now());

  -- Scales
  INSERT INTO psychometric_scale (id, test_id, name, description, score_method, result_mode, display_order)
    VALUES (v_imm, v_test, 'Immediate1', 'IMMEDIATE single-item', 'MEAN', 'IMMEDIATE', 1);
  INSERT INTO psychometric_scale (id, test_id, name, description, score_method, result_mode, display_order)
    VALUES (v_con, v_test, 'Consolidated1', 'CONSOLIDATED four-item', 'MEAN', 'CONSOLIDATED', 2);

  -- Questions (SCALE 1..5)
  INSERT INTO question (id, form_id, body, question_type, scale_min, scale_max, min_label, max_label, display_order, created_at, updated_at)
    VALUES
    (v_qi,  v_form, 'Imm Q1',  'SCALE', 1, 5, 'Disagree', 'Agree', 1, now(), now()),
    (v_qc1, v_form, 'Con Q1',  'SCALE', 1, 5, 'Disagree', 'Agree', 2, now(), now()),
    (v_qc2, v_form, 'Con Q2',  'SCALE', 1, 5, 'Disagree', 'Agree', 3, now(), now()),
    (v_qc3, v_form, 'Con Q3',  'SCALE', 1, 5, 'Disagree', 'Agree', 4, now(), now()),
    (v_qc4, v_form, 'Con Q4',  'SCALE', 1, 5, 'Disagree', 'Agree', 5, now(), now());

  -- Active scoring key + items (LIKERT_VALUE FORWARD, weight 1)
  INSERT INTO scoring_key_version (id, test_id, version, status, label, effective_from, published_by, published_at, created_at)
    VALUES (v_key, v_test, 1, 'ACTIVE', 'P3E2E key', now(), v_admin, now(), now());
  INSERT INTO scoring_key_item (id, scoring_key_id, scale_id, question_id, weight, item_strategy, direction, partial_credit)
    VALUES
    (gen_random_uuid(), v_key, v_imm, v_qi,  1, 'LIKERT_VALUE', 'FORWARD', false),
    (gen_random_uuid(), v_key, v_con, v_qc1, 1, 'LIKERT_VALUE', 'FORWARD', false),
    (gen_random_uuid(), v_key, v_con, v_qc2, 1, 'LIKERT_VALUE', 'FORWARD', false),
    (gen_random_uuid(), v_key, v_con, v_qc3, 1, 'LIKERT_VALUE', 'FORWARD', false),
    (gen_random_uuid(), v_key, v_con, v_qc4, 1, 'LIKERT_VALUE', 'FORWARD', false);

  -- VALIDATED PARAMETRIC norm + per-scale params (mean/sd)
  INSERT INTO norm_table_version (id, test_id, version, norm_strategy, status, label, sample_size, effective_from, published_by, published_at, created_at)
    VALUES (v_norm, v_test, 1, 'PARAMETRIC', 'VALIDATED', 'P3E2E norm', 500, now(), v_admin, now(), now());
  INSERT INTO norm_scale_param (id, norm_table_version_id, scale_id, mean, sd, sample_size, t_factor, t_offset, t_clip_lo, t_clip_hi)
    VALUES
    (gen_random_uuid(), v_norm, v_imm, 3.0, 1.0, 500, 10, 50, 20, 80),
    (gen_random_uuid(), v_norm, v_con, 3.0, 1.0, 500, 10, 50, 20, 80);

  -- Direct assignment to candidate
  INSERT INTO form_assignment (id, form_id, user_id, assigned_by, assigned_at, mandatory, active, include_children, allow_resubmission)
    VALUES (gen_random_uuid(), v_form, v_cand, v_admin, now(), true, true, false, true);

  -- Visibility policies
  INSERT INTO result_visibility_policy (id, test_id, audience, show_sten_profile, show_scale_breakdown, show_raw_score, show_percentile, show_competency_map, show_pass_fail_only)
    VALUES
    (gen_random_uuid(), v_test, 'CANDIDATE', true, true, false, false, false, false),
    (gen_random_uuid(), v_test, 'HR_ADMIN',  true, true, false, false, false, false);

  RAISE NOTICE 'Seed done: test=% form=% imm=% con=% key=% norm=%', v_test, v_form, v_imm, v_con, v_key, v_norm;
END $$;

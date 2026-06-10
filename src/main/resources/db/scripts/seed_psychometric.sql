-- ============================================================
-- Seed: Psychometric test data for integration testing
-- Created: 2026-03-03
-- ============================================================

-- ── 1. COGNITIVE TEST — Verbal Reasoning ─────────────────────────────────────

INSERT INTO survey (id, title, description, type, anon_window_minutes, created_at, updated_at)
VALUES (
    'e1000000-0000-0000-0000-000000000001',
    'Verbal Reasoning Test',
    'Measures verbal comprehension and logical reasoning.',
    'PSYCHOMETRIC',
    60,
    NOW(), NOW()
);

INSERT INTO question (id, survey_id, body, question_type, display_order, created_at, updated_at)
VALUES
    ('e1000000-0000-0000-0001-000000000001', 'e1000000-0000-0000-0000-000000000001', 'Which word is the odd one out?', 'CHOICE', 1, NOW(), NOW()),
    ('e1000000-0000-0000-0001-000000000002', 'e1000000-0000-0000-0000-000000000001', 'Doctor is to Hospital as Teacher is to?', 'CHOICE', 2, NOW(), NOW()),
    ('e1000000-0000-0000-0001-000000000003', 'e1000000-0000-0000-0000-000000000001', 'Which sentence is grammatically correct?', 'CHOICE', 3, NOW(), NOW()),
    ('e1000000-0000-0000-0001-000000000004', 'e1000000-0000-0000-0000-000000000001', 'What is the synonym of "benevolent"?', 'CHOICE', 4, NOW(), NOW()),
    ('e1000000-0000-0000-0001-000000000005', 'e1000000-0000-0000-0000-000000000001', 'Which word best completes the sentence?', 'CHOICE', 5, NOW(), NOW());

-- Q1 answers (Carrot is correct — not a fruit)
INSERT INTO candidate_answer (id, question_id, label, display_order, is_correct) VALUES
    ('e1000000-0000-0000-0002-000000000101', 'e1000000-0000-0000-0001-000000000001', 'Apple', 1, FALSE),
    ('e1000000-0000-0000-0002-000000000102', 'e1000000-0000-0000-0001-000000000001', 'Banana', 2, FALSE),
    ('e1000000-0000-0000-0002-000000000103', 'e1000000-0000-0000-0001-000000000001', 'Carrot', 3, TRUE),
    ('e1000000-0000-0000-0002-000000000104', 'e1000000-0000-0000-0001-000000000001', 'Grape', 4, FALSE);
-- Q2 answers (School is correct)
INSERT INTO candidate_answer (id, question_id, label, display_order, is_correct) VALUES
    ('e1000000-0000-0000-0002-000000000201', 'e1000000-0000-0000-0001-000000000002', 'Library', 1, FALSE),
    ('e1000000-0000-0000-0002-000000000202', 'e1000000-0000-0000-0001-000000000002', 'School', 2, TRUE),
    ('e1000000-0000-0000-0002-000000000203', 'e1000000-0000-0000-0001-000000000002', 'Office', 3, FALSE),
    ('e1000000-0000-0000-0002-000000000204', 'e1000000-0000-0000-0001-000000000002', 'Market', 4, FALSE);
-- Q3 answers
INSERT INTO candidate_answer (id, question_id, label, display_order, is_correct) VALUES
    ('e1000000-0000-0000-0002-000000000301', 'e1000000-0000-0000-0001-000000000003', 'He go to school every day', 1, FALSE),
    ('e1000000-0000-0000-0002-000000000302', 'e1000000-0000-0000-0001-000000000003', 'He goes to school every day', 2, TRUE),
    ('e1000000-0000-0000-0002-000000000303', 'e1000000-0000-0000-0001-000000000003', 'He going to school every day', 3, FALSE),
    ('e1000000-0000-0000-0002-000000000304', 'e1000000-0000-0000-0001-000000000003', 'He gone to school every day', 4, FALSE);
-- Q4 answers
INSERT INTO candidate_answer (id, question_id, label, display_order, is_correct) VALUES
    ('e1000000-0000-0000-0002-000000000401', 'e1000000-0000-0000-0001-000000000004', 'Malevolent', 1, FALSE),
    ('e1000000-0000-0000-0002-000000000402', 'e1000000-0000-0000-0001-000000000004', 'Kind', 2, TRUE),
    ('e1000000-0000-0000-0002-000000000403', 'e1000000-0000-0000-0001-000000000004', 'Harsh', 3, FALSE),
    ('e1000000-0000-0000-0002-000000000404', 'e1000000-0000-0000-0001-000000000004', 'Indifferent', 4, FALSE);
-- Q5 answers
INSERT INTO candidate_answer (id, question_id, label, display_order, is_correct) VALUES
    ('e1000000-0000-0000-0002-000000000501', 'e1000000-0000-0000-0001-000000000005', 'reluctantly', 1, FALSE),
    ('e1000000-0000-0000-0002-000000000502', 'e1000000-0000-0000-0001-000000000005', 'eagerly', 2, TRUE),
    ('e1000000-0000-0000-0002-000000000503', 'e1000000-0000-0000-0001-000000000005', 'slowly', 3, FALSE),
    ('e1000000-0000-0000-0002-000000000504', 'e1000000-0000-0000-0001-000000000005', 'rarely', 4, FALSE);

INSERT INTO psychometric_test (id, survey_id, name, description, test_type, time_limit_secs, instructions, version, status, created_by, created_at, updated_at)
VALUES (
    'e1000000-0000-0000-0000-000000000002',
    'e1000000-0000-0000-0000-000000000001',
    'Verbal Reasoning – Cognitive',
    'Cognitive test measuring verbal reasoning ability.',
    'COGNITIVE', 1800,
    'You have 30 minutes. Choose the best answer.',
    1, 'ACTIVE',
    'bb000000-0000-0000-0000-000000000001',
    NOW(), NOW()
);

INSERT INTO psychometric_scale (id, test_id, parent_scale_id, name, description, score_method, display_order)
VALUES (
    'e1000000-0000-0000-0000-000000000003',
    'e1000000-0000-0000-0000-000000000002',
    NULL, 'Verbal Reasoning', 'Overall verbal reasoning score', 'SUM', 1
);

INSERT INTO result_visibility_policy (id, test_id, audience, show_raw_score, show_sten_profile, show_percentile, show_competency_map, show_pass_fail_only, show_scale_breakdown)
VALUES
    ('e1000000-0000-0000-0000-000000000011', 'e1000000-0000-0000-0000-000000000002', 'CANDIDATE', FALSE, FALSE, FALSE, FALSE, TRUE, FALSE),
    ('e1000000-0000-0000-0000-000000000012', 'e1000000-0000-0000-0000-000000000002', 'HR_ADMIN',  TRUE,  TRUE,  TRUE,  FALSE, FALSE, TRUE);

-- ── 2. PERSONALITY TEST — Big Five ───────────────────────────────────────────

INSERT INTO survey (id, title, description, type, anon_window_minutes, created_at, updated_at)
VALUES (
    'e2000000-0000-0000-0000-000000000001',
    'Personality Profile',
    'Openness and Conscientiousness assessment.',
    'PSYCHOMETRIC',
    60,
    NOW(), NOW()
);

INSERT INTO question (id, survey_id, body, question_type, display_order, created_at, updated_at)
VALUES
    ('e2000000-0000-0000-0001-000000000001', 'e2000000-0000-0000-0000-000000000001', 'I enjoy exploring new ideas.', 'SCALE', 1, NOW(), NOW()),
    ('e2000000-0000-0000-0001-000000000002', 'e2000000-0000-0000-0000-000000000001', 'I prefer routine over novelty.', 'SCALE', 2, NOW(), NOW()),
    ('e2000000-0000-0000-0001-000000000003', 'e2000000-0000-0000-0000-000000000001', 'I complete tasks on time.', 'SCALE', 3, NOW(), NOW()),
    ('e2000000-0000-0000-0001-000000000004', 'e2000000-0000-0000-0000-000000000001', 'I plan ahead carefully.', 'SCALE', 4, NOW(), NOW());

INSERT INTO psychometric_test (id, survey_id, name, description, test_type, time_limit_secs, instructions, version, status, created_by, created_at, updated_at)
VALUES (
    'e2000000-0000-0000-0000-000000000002',
    'e2000000-0000-0000-0000-000000000001',
    'Personality Profile – Big Five',
    'Personality assessment: Openness and Conscientiousness.',
    'PERSONALITY', NULL,
    'Rate each statement 1 (Strongly Disagree) to 5 (Strongly Agree).',
    1, 'ACTIVE',
    'bb000000-0000-0000-0000-000000000001',
    NOW(), NOW()
);

INSERT INTO psychometric_scale (id, test_id, parent_scale_id, name, description, score_method, display_order)
VALUES
    ('e2000000-0000-0000-0000-000000000003', 'e2000000-0000-0000-0000-000000000002', NULL, 'Openness',          'Openness to experience',     'MEAN', 1),
    ('e2000000-0000-0000-0000-000000000004', 'e2000000-0000-0000-0000-000000000002', NULL, 'Conscientiousness', 'Conscientiousness dimension', 'MEAN', 2);

INSERT INTO result_visibility_policy (id, test_id, audience, show_raw_score, show_sten_profile, show_percentile, show_competency_map, show_pass_fail_only, show_scale_breakdown)
VALUES
    ('e2000000-0000-0000-0000-000000000011', 'e2000000-0000-0000-0000-000000000002', 'CANDIDATE', FALSE, TRUE,  TRUE,  FALSE, FALSE, TRUE),
    ('e2000000-0000-0000-0000-000000000012', 'e2000000-0000-0000-0000-000000000002', 'HR_ADMIN',  TRUE,  TRUE,  TRUE,  FALSE, FALSE, TRUE);

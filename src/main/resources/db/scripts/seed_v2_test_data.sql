-- ============================================================
-- V2 Test Data: Assignment-based survey flow
--
-- Run AFTER:
--   1. ./gradlew bootRun  (creates schema via Flyway + EnumSyncService)
--   2. Log in via the app  (creates your user + org unit)
--   3. docker exec -i mahara_postgres psql -U postgres -d pulse < seed_v2_test_data.sql
--
-- Auto-detects the real logged-in user and their org unit.
-- Creates 7 survey assignments exercising all statuses:
--   1. Engagement Pulse     → assigned to org unit        (PENDING, mandatory, due in 2 weeks)
--   2. Onboarding Survey    → direct user assignment      (PENDING, optional, no due date)
--   3. Manager 360          → assigned to Katim w/ children (will become IN_PROGRESS once user starts)
--   4. Quarterly Retake     → direct user, allowResubmission (RETAKEABLE after completing)
--   5. Expired Past Survey  → assigned to org unit, expired  (OVERDUE — past due date, no session)
--   6. Anon Team Review     → direct user               (PENDING, anonymous, multi-subject RATING)
--   7. All Types Demo       → direct user                (PENDING, non-anon, every question type + rating variants)
-- ============================================================


-- ============================================================
-- 0. AUTO-DETECT real logged-in user
-- ============================================================

-- Fail fast if no real user has logged in yet
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM users WHERE azure_ad_id NOT LIKE 'aad-%') THEN
    RAISE EXCEPTION 'No real user found. Log in via the app first, then run this seed.';
  END IF;
END $$;

-- Store the real user's ID and org unit ID for use throughout the script
CREATE TEMP TABLE _seed_ctx AS
SELECT u.id AS user_id, u.org_unit_id AS org_unit_id
FROM users u
WHERE u.azure_ad_id NOT LIKE 'aad-%'
ORDER BY u.created_at
LIMIT 1;


-- ============================================================
-- 1. ORG STRUCTURE: Katim (group) → real org unit (child)
-- ============================================================

-- Katim is a top-level group (parent for the real org unit)
INSERT INTO organizational_units (id, parent_id, org_unit_name, org_unit_code, org_level, path, depth, active) VALUES
('aa000000-0000-0000-0000-000000000001', NULL,
 'Katim', 'KATIM', 'GROUP', '/KATIM', 0, TRUE)
ON CONFLICT (id) DO NOTHING;

-- Update the real org unit to sit under Katim
UPDATE organizational_units
SET parent_id = 'aa000000-0000-0000-0000-000000000001',
    path = '/KATIM/' || COALESCE(org_unit_code, id::text),
    depth = 1
WHERE id = (SELECT org_unit_id FROM _seed_ctx)
  AND parent_id IS NULL;


-- ============================================================
-- 2. TITLES & ROLES
-- ============================================================

INSERT INTO titles (id, name) VALUES
('cc000000-0000-0000-0000-000000000001', 'Manager'),
('cc000000-0000-0000-0000-000000000002', 'Senior Engineer')
ON CONFLICT (name) DO NOTHING;

INSERT INTO roles (id, name) VALUES
('cc100000-0000-0000-0000-000000000001', 'EMPLOYEE'),
('cc100000-0000-0000-0000-000000000002', 'MANAGER')
ON CONFLICT (name) DO NOTHING;

INSERT INTO permissions (id, name) VALUES
('cc200000-0000-0000-0000-000000000001', 'SURVEY_RESPOND'),
('cc200000-0000-0000-0000-000000000002', 'SURVEY_CREATE'),
('cc200000-0000-0000-0000-000000000003', 'ANALYTICS_VIEW')
ON CONFLICT (name) DO NOTHING;


-- ============================================================
-- 3. USERS
-- ============================================================

-- PM Manager (dummy user who assigns surveys)
INSERT INTO users (id, azure_ad_id, email, display_name, title_id, department, division, org_unit_id, active)
SELECT 'dd000000-0000-0000-0000-000000000001'::uuid,
       'aad-mgr-pm-001', 'pm.manager@edge.ae', 'PM Manager',
       t.id, 'Product Management', 'Katim',
       (SELECT org_unit_id FROM _seed_ctx), TRUE
FROM titles t WHERE t.name = 'Manager'
ON CONFLICT (id) DO NOTHING;

-- Assign roles
INSERT INTO user_roles (user_id, role_id)
SELECT (SELECT user_id FROM _seed_ctx), r.id FROM roles r WHERE r.name = 'EMPLOYEE'
ON CONFLICT DO NOTHING;
INSERT INTO user_roles (user_id, role_id)
SELECT 'dd000000-0000-0000-0000-000000000001'::uuid, r.id FROM roles r WHERE r.name = 'MANAGER'
ON CONFLICT DO NOTHING;

-- PM Manager is leader of the org unit
INSERT INTO user_org_unit (user_id, org_unit_id, is_leader)
SELECT 'dd000000-0000-0000-0000-000000000001', (SELECT org_unit_id FROM _seed_ctx), TRUE
ON CONFLICT DO NOTHING;

-- Test user reports to PM Manager
UPDATE users SET manager_id = 'dd000000-0000-0000-0000-000000000001'
WHERE id = (SELECT user_id FROM _seed_ctx);


-- ============================================================
-- 4. QUESTIONNAIRES
-- ============================================================

-- Q1: Employee Engagement Pulse (all 4 question types, anonymous-capable)
INSERT INTO questionnaire (id, title, description, anon_window_minutes, created_at) VALUES
('ee000000-0000-0000-0000-000000000001',
 'Q1 2026 Engagement Pulse',
 'Quarterly engagement survey covering motivation, belonging, wellbeing, and growth. Uses all question types: scale, text, choice, and rating.',
 60, '2026-02-01 09:00:00')
ON CONFLICT (id) DO NOTHING;

INSERT INTO question (id, questionnaire_id, body, question_type, display_order) VALUES
('ee100000-0000-0000-0000-000000000001', 'ee000000-0000-0000-0000-000000000001',
 'I feel motivated to go beyond what is expected of me.', 'SCALE', 1),
('ee100000-0000-0000-0000-000000000002', 'ee000000-0000-0000-0000-000000000001',
 'I feel a strong sense of belonging within my team.', 'SCALE', 2),
('ee100000-0000-0000-0000-000000000003', 'ee000000-0000-0000-0000-000000000001',
 'My workload allows me to maintain a healthy work-life balance.', 'SCALE', 3),
('ee100000-0000-0000-0000-000000000004', 'ee000000-0000-0000-0000-000000000001',
 'What is one thing we could improve?', 'TEXT', 4),
('ee100000-0000-0000-0000-000000000005', 'ee000000-0000-0000-0000-000000000001',
 'Which area needs the most improvement?', 'CHOICE', 5),
('ee100000-0000-0000-0000-000000000006', 'ee000000-0000-0000-0000-000000000001',
 'Rate the following aspects of your experience.', 'RATING', 6)
ON CONFLICT (id) DO NOTHING;

-- Add subject labels to the RATING question
UPDATE question SET subject_labels = '["Communication", "Technical Skills", "Leadership", "Teamwork"]'
WHERE id = 'ee100000-0000-0000-0000-000000000006';

INSERT INTO candidate_answer (id, question_id, label, display_order) VALUES
('ee200000-0000-0000-0000-000000000001', 'ee100000-0000-0000-0000-000000000005', 'Communication & Transparency', 1),
('ee200000-0000-0000-0000-000000000002', 'ee100000-0000-0000-0000-000000000005', 'Career Development', 2),
('ee200000-0000-0000-0000-000000000003', 'ee100000-0000-0000-0000-000000000005', 'Work-Life Balance', 3),
('ee200000-0000-0000-0000-000000000004', 'ee100000-0000-0000-0000-000000000005', 'Leadership', 4)
ON CONFLICT (id) DO NOTHING;


-- Q2: Onboarding Experience (short, non-anonymous)
INSERT INTO questionnaire (id, title, description, anon_window_minutes, created_at) VALUES
('ee000000-0000-0000-0000-000000000002',
 'New Hire Onboarding Feedback',
 'Post-onboarding survey for new employees. Non-anonymous to enable follow-up.',
 0, '2026-02-10 09:00:00')
ON CONFLICT (id) DO NOTHING;

INSERT INTO question (id, questionnaire_id, body, question_type, display_order) VALUES
('ee100000-0000-0000-0000-000000000011', 'ee000000-0000-0000-0000-000000000002',
 'My onboarding prepared me well for my role.', 'SCALE', 1),
('ee100000-0000-0000-0000-000000000012', 'ee000000-0000-0000-0000-000000000002',
 'I felt welcomed during my first weeks.', 'SCALE', 2),
('ee100000-0000-0000-0000-000000000013', 'ee000000-0000-0000-0000-000000000002',
 'What could we improve about onboarding?', 'TEXT', 3)
ON CONFLICT (id) DO NOTHING;


-- Q3: Manager Effectiveness 360 (anonymous-capable, assigned to Katim group with children)
INSERT INTO questionnaire (id, title, description, anon_window_minutes, created_at) VALUES
('ee000000-0000-0000-0000-000000000003',
 'Manager Effectiveness 360',
 'Anonymous upward feedback on manager effectiveness. Rate communication, support, and development.',
 90, '2026-02-05 09:00:00')
ON CONFLICT (id) DO NOTHING;

INSERT INTO question (id, questionnaire_id, body, question_type, display_order) VALUES
('ee100000-0000-0000-0000-000000000021', 'ee000000-0000-0000-0000-000000000003',
 'My manager communicates expectations clearly.', 'SCALE', 1),
('ee100000-0000-0000-0000-000000000022', 'ee000000-0000-0000-0000-000000000003',
 'My manager provides constructive feedback.', 'SCALE', 2),
('ee100000-0000-0000-0000-000000000023', 'ee000000-0000-0000-0000-000000000003',
 'My manager supports my professional development.', 'SCALE', 3),
('ee100000-0000-0000-0000-000000000024', 'ee000000-0000-0000-0000-000000000003',
 'I feel comfortable raising concerns with my manager.', 'SCALE', 4),
('ee100000-0000-0000-0000-000000000025', 'ee000000-0000-0000-0000-000000000003',
 'What is one thing your manager could do differently?', 'TEXT', 5)
ON CONFLICT (id) DO NOTHING;


-- Q4: Quick Retakeable Check-in (short, allows resubmission)
INSERT INTO questionnaire (id, title, description, anon_window_minutes, created_at) VALUES
('ee000000-0000-0000-0000-000000000004',
 'Weekly Wellbeing Check-in',
 'Quick weekly pulse check. You can retake this survey each week to track how you are doing.',
 0, '2026-02-15 09:00:00')
ON CONFLICT (id) DO NOTHING;

INSERT INTO question (id, questionnaire_id, body, question_type, display_order, subject_labels) VALUES
('ee100000-0000-0000-0000-000000000031', 'ee000000-0000-0000-0000-000000000004',
 'How would you rate your overall wellbeing this week?', 'SCALE', 1, NULL),
('ee100000-0000-0000-0000-000000000032', 'ee000000-0000-0000-0000-000000000004',
 'What is your biggest challenge right now?', 'CHOICE', 2, NULL),
('ee100000-0000-0000-0000-000000000033', 'ee000000-0000-0000-0000-000000000004',
 'Anything else you want to share?', 'TEXT', 3, NULL),
('ee100000-0000-0000-0000-000000000034', 'ee000000-0000-0000-0000-000000000004',
 'Rate the following aspects of your work-life balance this week.', 'RATING', 4,
 '["Workload", "Flexibility", "Team Support", "Stress Level"]'),
('ee100000-0000-0000-0000-000000000035', 'ee000000-0000-0000-0000-000000000004',
 'Rate your overall energy level this week.', 'RATING', 5,
 '["Energy Level"]')
ON CONFLICT (id) DO NOTHING;

INSERT INTO candidate_answer (id, question_id, label, display_order) VALUES
('ee200000-0000-0000-0000-000000000011', 'ee100000-0000-0000-0000-000000000032', 'Workload', 1),
('ee200000-0000-0000-0000-000000000012', 'ee100000-0000-0000-0000-000000000032', 'Team dynamics', 2),
('ee200000-0000-0000-0000-000000000013', 'ee100000-0000-0000-0000-000000000032', 'Unclear priorities', 3),
('ee200000-0000-0000-0000-000000000014', 'ee100000-0000-0000-0000-000000000032', 'Nothing major', 4)
ON CONFLICT (id) DO NOTHING;


-- Q5: Expired past survey (to test OVERDUE status)
INSERT INTO questionnaire (id, title, description, anon_window_minutes, created_at) VALUES
('ee000000-0000-0000-0000-000000000005',
 'January Culture Assessment',
 'Monthly culture check-in for January. This survey has passed its due date.',
 60, '2026-01-05 09:00:00')
ON CONFLICT (id) DO NOTHING;

INSERT INTO question (id, questionnaire_id, body, question_type, display_order) VALUES
('ee100000-0000-0000-0000-000000000041', 'ee000000-0000-0000-0000-000000000005',
 'I feel our team culture has improved this month.', 'SCALE', 1),
('ee100000-0000-0000-0000-000000000042', 'ee000000-0000-0000-0000-000000000005',
 'Our team celebrates wins regularly.', 'SCALE', 2)
ON CONFLICT (id) DO NOTHING;


-- Q6: Anonymous Multi-Rating Review (tests multi-subject ratings + incognito theme)
INSERT INTO questionnaire (id, title, description, anon_window_minutes, created_at) VALUES
('ee000000-0000-0000-0000-000000000006',
 'Anonymous Team Performance Review',
 'Anonymous peer review covering multiple competency areas. Each rating question has its own set of subjects. This survey triggers the incognito/anonymous theme.',
 60, '2026-02-20 09:00:00')
ON CONFLICT (id) DO NOTHING;

INSERT INTO question (id, questionnaire_id, body, question_type, display_order, subject_labels) VALUES
('ee100000-0000-0000-0000-000000000051', 'ee000000-0000-0000-0000-000000000006',
 'Rate each team member on their collaboration and communication.', 'RATING', 1,
 '["Ahmad Al-Mansoori", "Fatima Al-Hashimi", "Omar Al-Kaabi", "Noura Al-Suwaidi"]'),
('ee100000-0000-0000-0000-000000000052', 'ee000000-0000-0000-0000-000000000006',
 'Rate each department on responsiveness and support.', 'RATING', 2,
 '["Engineering", "Design", "Product Management", "QA"]'),
('ee100000-0000-0000-0000-000000000053', 'ee000000-0000-0000-0000-000000000006',
 'Rate the following leadership competencies of your direct manager.', 'RATING', 3,
 '["Strategic Thinking", "Decision Making", "Empathy", "Accountability", "Mentorship"]'),
('ee100000-0000-0000-0000-000000000054', 'ee000000-0000-0000-0000-000000000006',
 'Any additional feedback you would like to share?', 'TEXT', 4, NULL),
('ee100000-0000-0000-0000-000000000055', 'ee000000-0000-0000-0000-000000000006',
 'Overall, how would you rate the team dynamic this quarter?', 'SCALE', 5, NULL)
ON CONFLICT (id) DO NOTHING;


-- Q7: All Question Types Demo (non-anonymous, exercises every answer type + rating variants)
INSERT INTO questionnaire (id, title, description, anon_window_minutes, created_at) VALUES
('ee000000-0000-0000-0000-000000000007',
 'All Question Types Demo',
 'Comprehensive survey showcasing every question type. Includes scale, text, choice, multi-subject rating, and single-subject rating questions.',
 0, '2026-02-22 09:00:00')
ON CONFLICT (id) DO NOTHING;

INSERT INTO question (id, questionnaire_id, body, question_type, display_order, subject_labels) VALUES
('ee100000-0000-0000-0000-000000000061', 'ee000000-0000-0000-0000-000000000007',
 'How satisfied are you with the tools and resources provided for your work?', 'SCALE', 1, NULL),
('ee100000-0000-0000-0000-000000000062', 'ee000000-0000-0000-0000-000000000007',
 'Describe one process that could be improved in your day-to-day workflow.', 'TEXT', 2, NULL),
('ee100000-0000-0000-0000-000000000063', 'ee000000-0000-0000-0000-000000000007',
 'Which communication channel do you find most effective?', 'CHOICE', 3, NULL),
('ee100000-0000-0000-0000-000000000064', 'ee000000-0000-0000-0000-000000000007',
 'Rate each cross-functional team on how well they collaborate with your team.', 'RATING', 4,
 '["Engineering", "Design", "Product", "QA", "Operations"]'),
('ee100000-0000-0000-0000-000000000065', 'ee000000-0000-0000-0000-000000000007',
 'Rate your overall onboarding experience.', 'RATING', 5,
 '["Onboarding Experience"]'),
('ee100000-0000-0000-0000-000000000066', 'ee000000-0000-0000-0000-000000000007',
 'I have a clear understanding of my career growth path here.', 'SCALE', 6, NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO candidate_answer (id, question_id, label, display_order) VALUES
('ee200000-0000-0000-0000-000000000021', 'ee100000-0000-0000-0000-000000000063', 'Microsoft Teams', 1),
('ee200000-0000-0000-0000-000000000022', 'ee100000-0000-0000-0000-000000000063', 'Email', 2),
('ee200000-0000-0000-0000-000000000023', 'ee100000-0000-0000-0000-000000000063', 'In-person meetings', 3),
('ee200000-0000-0000-0000-000000000024', 'ee100000-0000-0000-0000-000000000063', 'Slack / Chat', 4)
ON CONFLICT (id) DO NOTHING;


-- ============================================================
-- 5. ASSIGNMENTS  (all assigned by PM Manager)
-- ============================================================

-- Assignment 1: Engagement Pulse → org unit
--   PENDING: mandatory, due in 2 weeks, no session yet
INSERT INTO questionnaire_assignment
  (id, questionnaire_id, org_unit_id, user_id, assigned_by, starts_at, expires_at, due_date, mandatory, include_children, allow_resubmission)
SELECT 'ff000000-0000-0000-0000-000000000001',
 'ee000000-0000-0000-0000-000000000001',
 org_unit_id, NULL,
 'dd000000-0000-0000-0000-000000000001',
 '2026-02-20 00:00:00', '2026-03-20 23:59:59', '2026-03-10 23:59:59',
 TRUE, TRUE, FALSE
FROM _seed_ctx;

-- Assignment 2: Onboarding Feedback → direct to test user
--   PENDING: optional, no due date, no expiry
INSERT INTO questionnaire_assignment
  (id, questionnaire_id, org_unit_id, user_id, assigned_by, starts_at, expires_at, due_date, mandatory, include_children, allow_resubmission)
SELECT 'ff000000-0000-0000-0000-000000000002',
 'ee000000-0000-0000-0000-000000000002',
 NULL, user_id,
 'dd000000-0000-0000-0000-000000000001',
 NULL, NULL, NULL,
 FALSE, FALSE, FALSE
FROM _seed_ctx;

-- Assignment 3: Manager 360 → Katim group (includeChildren=true, so child org-unit users see it)
--   PENDING: mandatory, due in 3 weeks
INSERT INTO questionnaire_assignment
  (id, questionnaire_id, org_unit_id, user_id, assigned_by, starts_at, expires_at, due_date, mandatory, include_children, allow_resubmission)
VALUES
('ff000000-0000-0000-0000-000000000003',
 'ee000000-0000-0000-0000-000000000003',
 'aa000000-0000-0000-0000-000000000001', NULL,
 'dd000000-0000-0000-0000-000000000001',
 '2026-02-20 00:00:00', '2026-04-01 23:59:59', '2026-03-15 23:59:59',
 TRUE, TRUE, FALSE);

-- Assignment 4: Weekly Wellbeing → direct to test user, allowResubmission=true
--   Will become RETAKEABLE after the user completes it once
INSERT INTO questionnaire_assignment
  (id, questionnaire_id, org_unit_id, user_id, assigned_by, starts_at, expires_at, due_date, mandatory, include_children, allow_resubmission)
SELECT 'ff000000-0000-0000-0000-000000000004',
 'ee000000-0000-0000-0000-000000000004',
 NULL, user_id,
 'dd000000-0000-0000-0000-000000000001',
 NULL, NULL, NULL,
 FALSE, FALSE, TRUE
FROM _seed_ctx;

-- Assignment 5: January Culture Assessment → org unit, past due
--   OVERDUE: due date is in the past
INSERT INTO questionnaire_assignment
  (id, questionnaire_id, org_unit_id, user_id, assigned_by, starts_at, expires_at, due_date, mandatory, include_children, allow_resubmission)
SELECT 'ff000000-0000-0000-0000-000000000005',
 'ee000000-0000-0000-0000-000000000005',
 org_unit_id, NULL,
 'dd000000-0000-0000-0000-000000000001',
 '2026-01-05 00:00:00', NULL, '2026-01-31 23:59:59',
 TRUE, TRUE, FALSE
FROM _seed_ctx;

-- Assignment 6: Anonymous Team Performance Review → direct to test user
--   PENDING: anonymous (anonWindowMinutes=60), multi-subject RATING questions + incognito theme
INSERT INTO questionnaire_assignment
  (id, questionnaire_id, org_unit_id, user_id, assigned_by, starts_at, expires_at, due_date, mandatory, include_children, allow_resubmission)
SELECT 'ff000000-0000-0000-0000-000000000006',
 'ee000000-0000-0000-0000-000000000006',
 NULL, user_id,
 'dd000000-0000-0000-0000-000000000001',
 '2026-02-20 00:00:00', '2026-04-20 23:59:59', '2026-04-01 23:59:59',
 TRUE, FALSE, FALSE
FROM _seed_ctx;

-- Assignment 7: All Question Types Demo → direct to test user
--   PENDING: non-anonymous, exercises every question type including both rating variants
INSERT INTO questionnaire_assignment
  (id, questionnaire_id, org_unit_id, user_id, assigned_by, starts_at, expires_at, due_date, mandatory, include_children, allow_resubmission)
SELECT 'ff000000-0000-0000-0000-000000000007',
 'ee000000-0000-0000-0000-000000000007',
 NULL, user_id,
 'dd000000-0000-0000-0000-000000000001',
 '2026-02-22 00:00:00', '2026-04-30 23:59:59', '2026-04-15 23:59:59',
 FALSE, FALSE, FALSE
FROM _seed_ctx;

-- Clean up
DROP TABLE _seed_ctx;


-- ============================================================
-- Expected results when GET /api/assignments/my for test user:
--
-- 1. "Q1 2026 Engagement Pulse"           → PENDING    (mandatory, due 2026-03-10)
-- 2. "New Hire Onboarding Feedback"       → PENDING    (optional, no due date)
-- 3. "Manager Effectiveness 360"          → PENDING    (mandatory, due 2026-03-15, via Katim parent)
-- 4. "Weekly Wellbeing Check-in"          → PENDING    (optional, allowResubmission=true,
--                                                       SCALE + CHOICE + TEXT + multi-subject RATING + single RATING)
-- 5. "January Culture Assessment"         → OVERDUE    (mandatory, due date 2026-01-31 is past)
-- 6. "Anonymous Team Performance Review"  → PENDING    (mandatory, due 2026-04-01, anonymous → incognito theme,
--                                                       3 multi-subject RATING questions + text + scale)
-- 7. "All Question Types Demo"           → PENDING    (optional, due 2026-04-15, non-anonymous,
--                                                       SCALE + TEXT + CHOICE + multi-subject RATING + single RATING)
-- ============================================================

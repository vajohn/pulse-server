-- ============================================================
-- Seed: Dummy survey data to test all survey features
-- (Moved from V2 migration — run manually for test environments)
-- Covers: org hierarchy, users, all 4 question types,
--         identified & anonymous sessions, answer versioning,
--         completed & in-progress sessions
-- ============================================================

-- ============================================================
-- 1. ORGANIZATIONAL STRUCTURE (group → cluster → entity → org_unit → team)
-- ============================================================

INSERT INTO organizational_units (id, parent_id, org_unit_name, org_unit_code, org_level, path, depth, active) VALUES
('a0000000-0000-0000-0000-000000000001', NULL, 'Edge Group', 'EDGE', 'GROUP', '/EDGE', 0, TRUE),
('a0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000001', 'Operations Cluster', 'OPS', 'CLUSTER', '/EDGE/OPS', 1, TRUE),
('a0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000001', 'Technology Cluster', 'TECH', 'CLUSTER', '/EDGE/TECH', 1, TRUE),
('a0000000-0000-0000-0000-000000000004', 'a0000000-0000-0000-0000-000000000002', 'Logistics Entity', 'OPS-LOG', 'ENTITY', '/EDGE/OPS/OPS-LOG', 2, TRUE),
('a0000000-0000-0000-0000-000000000005', 'a0000000-0000-0000-0000-000000000003', 'Engineering Entity', 'TECH-ENG', 'ENTITY', '/EDGE/TECH/TECH-ENG', 2, TRUE),
('a0000000-0000-0000-0000-000000000006', 'a0000000-0000-0000-0000-000000000004', 'Supply Chain', 'OPS-LOG-SC', 'ORG_UNIT', '/EDGE/OPS/OPS-LOG/OPS-LOG-SC', 3, TRUE),
('a0000000-0000-0000-0000-000000000007', 'a0000000-0000-0000-0000-000000000005', 'Platform Development', 'TECH-ENG-PD', 'ORG_UNIT', '/EDGE/TECH/TECH-ENG/TECH-ENG-PD', 3, TRUE),
('a0000000-0000-0000-0000-000000000008', 'a0000000-0000-0000-0000-000000000006', 'Procurement Team', 'OPS-LOG-SC-PROC', 'TEAM', '/EDGE/OPS/OPS-LOG/OPS-LOG-SC/OPS-LOG-SC-PROC', 4, TRUE),
('a0000000-0000-0000-0000-000000000009', 'a0000000-0000-0000-0000-000000000007', 'Backend Team', 'TECH-ENG-PD-BE', 'TEAM', '/EDGE/TECH/TECH-ENG/TECH-ENG-PD/TECH-ENG-PD-BE', 4, TRUE),
('a0000000-0000-0000-0000-00000000000a', 'a0000000-0000-0000-0000-000000000007', 'Mobile Team', 'TECH-ENG-PD-MOB', 'TEAM', '/EDGE/TECH/TECH-ENG/TECH-ENG-PD/TECH-ENG-PD-MOB', 4, TRUE)
ON CONFLICT (id) DO NOTHING;


-- ============================================================
-- 2. TITLES, ROLES, PERMISSIONS (skip if already exist)
-- ============================================================

INSERT INTO titles (id, name) VALUES
('b0000000-0000-0000-0000-000000000001', 'Director'),
('b0000000-0000-0000-0000-000000000002', 'Senior Manager'),
('b0000000-0000-0000-0000-000000000003', 'Manager'),
('b0000000-0000-0000-0000-000000000004', 'Team Lead'),
('b0000000-0000-0000-0000-000000000005', 'Senior Engineer'),
('b0000000-0000-0000-0000-000000000006', 'Engineer'),
('b0000000-0000-0000-0000-000000000007', 'Analyst')
ON CONFLICT (name) DO NOTHING;

INSERT INTO roles (id, name) VALUES
('c0000000-0000-0000-0000-000000000001', 'EMPLOYEE'),
('c0000000-0000-0000-0000-000000000002', 'MANAGER'),
('c0000000-0000-0000-0000-000000000003', 'HR_FULL_CRUD')
ON CONFLICT (name) DO NOTHING;

-- Role-permission mappings — use post-V16 permission names (V4 migration seeds all permissions).
-- EMPLOYEE: Spark features + AI
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'EMPLOYEE' AND p.name IN ('SPARK_NOMINATE', 'SPARK_VOTE', 'AI_USE')
ON CONFLICT DO NOTHING;

-- MANAGER: EMPLOYEE permissions + team scoping + assessment results
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'MANAGER'
  AND p.name IN ('SPARK_NOMINATE', 'SPARK_VOTE', 'AI_USE',
                 'SCOPE_TEAM', 'ASSESS_RESULT_READ', 'REPORT_ASSESS_VIEW')
ON CONFLICT DO NOTHING;


-- ============================================================
-- 3. USERS (across different org units and roles)
-- ============================================================

-- HR Admin
INSERT INTO users (id, azure_ad_id, email, display_name, title_id, department, division, org_unit_id, manager_id, active)
SELECT 'd0000000-0000-0000-0000-000000000001'::uuid, 'aad-hr-admin-001', 'fatima.ahmed@edge.ae', 'Fatima Ahmed',
       t.id, 'Human Resources', 'Operations',
       'a0000000-0000-0000-0000-000000000004'::uuid, NULL, TRUE
FROM titles t WHERE t.name = 'Senior Manager'
ON CONFLICT (azure_ad_id) DO NOTHING;

-- Managers
INSERT INTO users (id, azure_ad_id, email, display_name, title_id, department, division, org_unit_id, manager_id, active)
SELECT 'd0000000-0000-0000-0000-000000000002'::uuid, 'aad-mgr-ops-001', 'khalid.omar@edge.ae', 'Khalid Omar',
       t.id, 'Supply Chain', 'Operations',
       'a0000000-0000-0000-0000-000000000006'::uuid, NULL, TRUE
FROM titles t WHERE t.name = 'Manager'
ON CONFLICT (azure_ad_id) DO NOTHING;

INSERT INTO users (id, azure_ad_id, email, display_name, title_id, department, division, org_unit_id, manager_id, active)
SELECT 'd0000000-0000-0000-0000-000000000003'::uuid, 'aad-mgr-tech-001', 'sara.hassan@edge.ae', 'Sara Hassan',
       t.id, 'Platform Development', 'Technology',
       'a0000000-0000-0000-0000-000000000007'::uuid, NULL, TRUE
FROM titles t WHERE t.name = 'Manager'
ON CONFLICT (azure_ad_id) DO NOTHING;

-- Team leads
INSERT INTO users (id, azure_ad_id, email, display_name, title_id, department, division, org_unit_id, manager_id, active)
SELECT 'd0000000-0000-0000-0000-000000000004'::uuid, 'aad-lead-proc-001', 'ahmed.ali@edge.ae', 'Ahmed Ali',
       t.id, 'Supply Chain', 'Operations',
       'a0000000-0000-0000-0000-000000000008'::uuid, 'd0000000-0000-0000-0000-000000000002'::uuid, TRUE
FROM titles t WHERE t.name = 'Team Lead'
ON CONFLICT (azure_ad_id) DO NOTHING;

INSERT INTO users (id, azure_ad_id, email, display_name, title_id, department, division, org_unit_id, manager_id, active)
SELECT 'd0000000-0000-0000-0000-000000000005'::uuid, 'aad-lead-be-001', 'noura.salem@edge.ae', 'Noura Salem',
       t.id, 'Platform Development', 'Technology',
       'a0000000-0000-0000-0000-000000000009'::uuid, 'd0000000-0000-0000-0000-000000000003'::uuid, TRUE
FROM titles t WHERE t.name = 'Team Lead'
ON CONFLICT (azure_ad_id) DO NOTHING;

-- Engineers / employees
INSERT INTO users (id, azure_ad_id, email, display_name, title_id, department, division, org_unit_id, manager_id, active)
SELECT 'd0000000-0000-0000-0000-000000000006'::uuid, 'aad-eng-001', 'omar.rashid@edge.ae', 'Omar Rashid',
       t.id, 'Platform Development', 'Technology',
       'a0000000-0000-0000-0000-000000000009'::uuid, 'd0000000-0000-0000-0000-000000000005'::uuid, TRUE
FROM titles t WHERE t.name = 'Senior Engineer'
ON CONFLICT (azure_ad_id) DO NOTHING;

INSERT INTO users (id, azure_ad_id, email, display_name, title_id, department, division, org_unit_id, manager_id, active)
SELECT 'd0000000-0000-0000-0000-000000000007'::uuid, 'aad-eng-002', 'layla.nasser@edge.ae', 'Layla Nasser',
       t.id, 'Platform Development', 'Technology',
       'a0000000-0000-0000-0000-000000000009'::uuid, 'd0000000-0000-0000-0000-000000000005'::uuid, TRUE
FROM titles t WHERE t.name = 'Engineer'
ON CONFLICT (azure_ad_id) DO NOTHING;

INSERT INTO users (id, azure_ad_id, email, display_name, title_id, department, division, org_unit_id, manager_id, active)
SELECT 'd0000000-0000-0000-0000-000000000008'::uuid, 'aad-eng-003', 'youssef.karam@edge.ae', 'Youssef Karam',
       t.id, 'Platform Development', 'Technology',
       'a0000000-0000-0000-0000-00000000000a'::uuid, 'd0000000-0000-0000-0000-000000000003'::uuid, TRUE
FROM titles t WHERE t.name = 'Engineer'
ON CONFLICT (azure_ad_id) DO NOTHING;

INSERT INTO users (id, azure_ad_id, email, display_name, title_id, department, division, org_unit_id, manager_id, active)
SELECT 'd0000000-0000-0000-0000-000000000009'::uuid, 'aad-analyst-001', 'huda.mahmoud@edge.ae', 'Huda Mahmoud',
       t.id, 'Supply Chain', 'Operations',
       'a0000000-0000-0000-0000-000000000008'::uuid, 'd0000000-0000-0000-0000-000000000004'::uuid, TRUE
FROM titles t WHERE t.name = 'Analyst'
ON CONFLICT (azure_ad_id) DO NOTHING;

INSERT INTO users (id, azure_ad_id, email, display_name, title_id, department, division, org_unit_id, manager_id, active)
SELECT 'd0000000-0000-0000-0000-00000000000a'::uuid, 'aad-analyst-002', 'tariq.farouk@edge.ae', 'Tariq Farouk',
       t.id, 'Supply Chain', 'Operations',
       'a0000000-0000-0000-0000-000000000008'::uuid, 'd0000000-0000-0000-0000-000000000004'::uuid, TRUE
FROM titles t WHERE t.name = 'Analyst'
ON CONFLICT (azure_ad_id) DO NOTHING;

-- Assign roles (look up role IDs by name)
INSERT INTO user_roles (user_id, role_id)
SELECT 'd0000000-0000-0000-0000-000000000001'::uuid, r.id FROM roles r WHERE r.name = 'HR_FULL_CRUD'
ON CONFLICT DO NOTHING;
INSERT INTO user_roles (user_id, role_id)
SELECT 'd0000000-0000-0000-0000-000000000002'::uuid, r.id FROM roles r WHERE r.name = 'MANAGER'
ON CONFLICT DO NOTHING;
INSERT INTO user_roles (user_id, role_id)
SELECT 'd0000000-0000-0000-0000-000000000003'::uuid, r.id FROM roles r WHERE r.name = 'MANAGER'
ON CONFLICT DO NOTHING;
INSERT INTO user_roles (user_id, role_id)
SELECT 'd0000000-0000-0000-0000-000000000004'::uuid, r.id FROM roles r WHERE r.name = 'MANAGER'
ON CONFLICT DO NOTHING;
INSERT INTO user_roles (user_id, role_id)
SELECT 'd0000000-0000-0000-0000-000000000005'::uuid, r.id FROM roles r WHERE r.name = 'MANAGER'
ON CONFLICT DO NOTHING;
INSERT INTO user_roles (user_id, role_id)
SELECT 'd0000000-0000-0000-0000-000000000006'::uuid, r.id FROM roles r WHERE r.name = 'EMPLOYEE'
ON CONFLICT DO NOTHING;
INSERT INTO user_roles (user_id, role_id)
SELECT 'd0000000-0000-0000-0000-000000000007'::uuid, r.id FROM roles r WHERE r.name = 'EMPLOYEE'
ON CONFLICT DO NOTHING;
INSERT INTO user_roles (user_id, role_id)
SELECT 'd0000000-0000-0000-0000-000000000008'::uuid, r.id FROM roles r WHERE r.name = 'EMPLOYEE'
ON CONFLICT DO NOTHING;
INSERT INTO user_roles (user_id, role_id)
SELECT 'd0000000-0000-0000-0000-000000000009'::uuid, r.id FROM roles r WHERE r.name = 'EMPLOYEE'
ON CONFLICT DO NOTHING;
INSERT INTO user_roles (user_id, role_id)
SELECT 'd0000000-0000-0000-0000-00000000000a'::uuid, r.id FROM roles r WHERE r.name = 'EMPLOYEE'
ON CONFLICT DO NOTHING;

-- Leadership assignments
INSERT INTO user_org_unit (user_id, org_unit_id, is_leader, assigned_at) VALUES
('d0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000006', TRUE, '2025-01-15'),
('d0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000007', TRUE, '2025-01-15'),
('d0000000-0000-0000-0000-000000000004', 'a0000000-0000-0000-0000-000000000008', TRUE, '2025-03-01'),
('d0000000-0000-0000-0000-000000000005', 'a0000000-0000-0000-0000-000000000009', TRUE, '2025-03-01')
ON CONFLICT DO NOTHING;


-- ============================================================
-- 4. FORM 1: Employee Engagement Pulse (all 4 types)
-- ============================================================

INSERT INTO form (id, title, description, anon_window_minutes, type, created_at) VALUES
('f0000000-0000-0000-0000-000000000001',
 'Q1 2026 Employee Engagement Pulse',
 'Quarterly pulse survey measuring engagement across sustainable engagement, belonging, wellbeing, empowerment, and growth. Combines Likert scales, open text feedback, choice selections, and multi-subject ratings.',
 60, 'SURVEY',
 '2026-01-15 09:00:00');

-- SCALE questions (Q1-Q8)
INSERT INTO question (id, form_id, body, question_type, display_order, created_at) VALUES
('f1000000-0000-0000-0000-000000000001', 'f0000000-0000-0000-0000-000000000001',
 'I feel motivated to go beyond what is expected of me in my role.', 'SCALE', 1, '2026-01-15 09:00:00'),
('f1000000-0000-0000-0000-000000000002', 'f0000000-0000-0000-0000-000000000001',
 'I feel a strong sense of belonging within my team.', 'SCALE', 2, '2026-01-15 09:00:00'),
('f1000000-0000-0000-0000-000000000003', 'f0000000-0000-0000-0000-000000000001',
 'My workload allows me to maintain a healthy work-life balance.', 'SCALE', 3, '2026-01-15 09:00:00'),
('f1000000-0000-0000-0000-000000000004', 'f0000000-0000-0000-0000-000000000001',
 'I have the autonomy to make decisions about how I do my work.', 'SCALE', 4, '2026-01-15 09:00:00'),
('f1000000-0000-0000-0000-000000000005', 'f0000000-0000-0000-0000-000000000001',
 'I have adequate opportunities for professional development and growth.', 'SCALE', 5, '2026-01-15 09:00:00'),
('f1000000-0000-0000-0000-000000000006', 'f0000000-0000-0000-0000-000000000001',
 'My direct manager inspires me to do my best work.', 'SCALE', 6, '2026-01-15 09:00:00'),
('f1000000-0000-0000-0000-000000000007', 'f0000000-0000-0000-0000-000000000001',
 'I have the tools and resources I need to perform my job effectively.', 'SCALE', 7, '2026-01-15 09:00:00'),
('f1000000-0000-0000-0000-000000000008', 'f0000000-0000-0000-0000-000000000001',
 'On a scale of 1-10, how likely are you to recommend this organization as a great place to work?', 'SCALE', 8, '2026-01-15 09:00:00');

-- TEXT question (Q9)
INSERT INTO question (id, form_id, body, question_type, display_order, created_at) VALUES
('f1000000-0000-0000-0000-000000000009', 'f0000000-0000-0000-0000-000000000001',
 'What is the one thing we could change to make this a better place to work?', 'TEXT', 9, '2026-01-15 09:00:00');

-- CHOICE question (Q10)
INSERT INTO question (id, form_id, body, question_type, display_order, created_at) VALUES
('f1000000-0000-0000-0000-00000000000a', 'f0000000-0000-0000-0000-000000000001',
 'Which area do you feel needs the most improvement in our organization?', 'CHOICE', 10, '2026-01-15 09:00:00');

INSERT INTO candidate_answer (id, question_id, label, display_order) VALUES
('f2000000-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-00000000000a', 'Communication & Transparency', 1),
('f2000000-0000-0000-0000-000000000002', 'f1000000-0000-0000-0000-00000000000a', 'Career Development', 2),
('f2000000-0000-0000-0000-000000000003', 'f1000000-0000-0000-0000-00000000000a', 'Compensation & Benefits', 3),
('f2000000-0000-0000-0000-000000000004', 'f1000000-0000-0000-0000-00000000000a', 'Work-Life Balance', 4),
('f2000000-0000-0000-0000-000000000005', 'f1000000-0000-0000-0000-00000000000a', 'Leadership & Management', 5),
('f2000000-0000-0000-0000-000000000006', 'f1000000-0000-0000-0000-00000000000a', 'Team Collaboration', 6);

-- MULTI_RATING question (Q11) — fixed from RATING
INSERT INTO question (id, form_id, body, question_type, display_order, subject_labels, created_at) VALUES
('f1000000-0000-0000-0000-00000000000b', 'f0000000-0000-0000-0000-000000000001',
 'Please rate the following aspects of your experience at the organization.', 'MULTI_RATING', 11,
 '["IT Support","Office Facilities","Learning & Development","Internal Communications"]',
 '2026-01-15 09:00:00');

-- CHOICE question (Q12)
INSERT INTO question (id, form_id, body, question_type, display_order, created_at) VALUES
('f1000000-0000-0000-0000-00000000000c', 'f0000000-0000-0000-0000-000000000001',
 'How often would you prefer to receive formal feedback from your manager?', 'CHOICE', 12, '2026-01-15 09:00:00');

INSERT INTO candidate_answer (id, question_id, label, display_order) VALUES
('f2000000-0000-0000-0000-000000000007', 'f1000000-0000-0000-0000-00000000000c', 'Weekly', 1),
('f2000000-0000-0000-0000-000000000008', 'f1000000-0000-0000-0000-00000000000c', 'Bi-weekly', 2),
('f2000000-0000-0000-0000-000000000009', 'f1000000-0000-0000-0000-00000000000c', 'Monthly', 3),
('f2000000-0000-0000-0000-00000000000a', 'f1000000-0000-0000-0000-00000000000c', 'Quarterly', 4);


-- ============================================================
-- 5. FORM 2: Onboarding Experience (shorter, non-anonymous)
-- ============================================================

INSERT INTO form (id, title, description, anon_window_minutes, type, created_at) VALUES
('f0000000-0000-0000-0000-000000000002',
 'New Hire Onboarding Experience',
 'Post-onboarding survey for employees who joined in the last 90 days. Non-anonymous to enable follow-up support.',
 0, 'SURVEY',
 '2026-02-01 09:00:00');

INSERT INTO question (id, form_id, body, question_type, display_order, created_at) VALUES
('f1000000-0000-0000-0000-000000000101', 'f0000000-0000-0000-0000-000000000002',
 'My onboarding experience prepared me well for my role.', 'SCALE', 1, '2026-02-01 09:00:00'),
('f1000000-0000-0000-0000-000000000102', 'f0000000-0000-0000-0000-000000000002',
 'I felt welcomed and supported during my first weeks.', 'SCALE', 2, '2026-02-01 09:00:00'),
('f1000000-0000-0000-0000-000000000103', 'f0000000-0000-0000-0000-000000000002',
 'The training materials and resources provided were helpful.', 'SCALE', 3, '2026-02-01 09:00:00'),
('f1000000-0000-0000-0000-000000000104', 'f0000000-0000-0000-0000-000000000002',
 'What could we improve about the onboarding process?', 'TEXT', 4, '2026-02-01 09:00:00');

-- MULTI_RATING question (Q5) — fixed from RATING, with subject_labels
INSERT INTO question (id, form_id, body, question_type, display_order, subject_labels, created_at) VALUES
('f1000000-0000-0000-0000-000000000105', 'f0000000-0000-0000-0000-000000000002',
 'Rate your experience with each onboarding component.', 'MULTI_RATING', 5,
 '["Orientation Day","IT Setup & Access","Buddy Program","Role Clarity","Team Integration"]',
 '2026-02-01 09:00:00');


-- ============================================================
-- 6. FORM 3: Manager Effectiveness (with expired question)
-- ============================================================

INSERT INTO form (id, title, description, anon_window_minutes, type, created_at) VALUES
('f0000000-0000-0000-0000-000000000003',
 'Manager Effectiveness 360',
 'Anonymous upward feedback on manager effectiveness. Includes an expired question to test filtering.',
 90, 'SURVEY',
 '2025-11-01 09:00:00');

INSERT INTO question (id, form_id, body, question_type, display_order, effective_date, expiration_date, created_at) VALUES
('f1000000-0000-0000-0000-000000000201', 'f0000000-0000-0000-0000-000000000003',
 'My manager communicates expectations clearly.', 'SCALE', 1, '2025-11-01 09:00:00', NULL, '2025-11-01 09:00:00'),
('f1000000-0000-0000-0000-000000000202', 'f0000000-0000-0000-0000-000000000003',
 'My manager provides constructive feedback that helps me improve.', 'SCALE', 2, '2025-11-01 09:00:00', NULL, '2025-11-01 09:00:00'),
('f1000000-0000-0000-0000-000000000203', 'f0000000-0000-0000-0000-000000000003',
 'My manager supports my professional development.', 'SCALE', 3, '2025-11-01 09:00:00', NULL, '2025-11-01 09:00:00'),
-- EXPIRED question — should be filtered out by findActiveBySurveyId
('f1000000-0000-0000-0000-000000000204', 'f0000000-0000-0000-0000-000000000003',
 '[DEPRECATED] My manager is approachable. (Replaced by Q5)', 'SCALE', 4, '2025-11-01 09:00:00', '2026-01-01 00:00:00', '2025-11-01 09:00:00'),
-- Replacement question with later effective date
('f1000000-0000-0000-0000-000000000205', 'f0000000-0000-0000-0000-000000000003',
 'I feel comfortable raising concerns or ideas with my manager.', 'SCALE', 5, '2026-01-01 00:00:00', NULL, '2025-12-20 09:00:00'),
('f1000000-0000-0000-0000-000000000206', 'f0000000-0000-0000-0000-000000000003',
 'What is one thing your manager could do differently?', 'TEXT', 6, '2025-11-01 09:00:00', NULL, '2025-11-01 09:00:00');


-- ============================================================
-- 7. RESPONSE SESSIONS & ANSWERS — Form 1 (Engagement Pulse)
-- ============================================================

-- 7a. IDENTIFIED session — Omar (completed)
INSERT INTO response_session (id, form_id, user_id, is_anonymous, started_at, completed_at) VALUES
('f3000000-0000-0000-0000-000000000001', 'f0000000-0000-0000-0000-000000000001',
 'd0000000-0000-0000-0000-000000000006', FALSE, '2026-02-10 10:15:00', '2026-02-10 10:32:00');

-- Omar's SCALE answers (Q1-Q8)
INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
('f4000000-0000-0000-0000-000000000001', 'f3000000-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-000000000001', 'SCALE', 1, TRUE, '2026-02-10 10:16:00'),
('f4000000-0000-0000-0000-000000000002', 'f3000000-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-000000000002', 'SCALE', 1, TRUE, '2026-02-10 10:17:00'),
('f4000000-0000-0000-0000-000000000003', 'f3000000-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-000000000003', 'SCALE', 1, TRUE, '2026-02-10 10:18:00'),
('f4000000-0000-0000-0000-000000000004', 'f3000000-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-000000000004', 'SCALE', 1, TRUE, '2026-02-10 10:19:00'),
('f4000000-0000-0000-0000-000000000005', 'f3000000-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-000000000005', 'SCALE', 1, TRUE, '2026-02-10 10:20:00'),
('f4000000-0000-0000-0000-000000000006', 'f3000000-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-000000000006', 'SCALE', 1, TRUE, '2026-02-10 10:21:00'),
('f4000000-0000-0000-0000-000000000007', 'f3000000-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-000000000007', 'SCALE', 1, TRUE, '2026-02-10 10:22:00'),
('f4000000-0000-0000-0000-000000000008', 'f3000000-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-000000000008', 'SCALE', 1, TRUE, '2026-02-10 10:23:00');

INSERT INTO answer_scale (id, submission_id, value, min_value, max_value) VALUES
('f5000000-0000-0000-0000-000000000001', 'f4000000-0000-0000-0000-000000000001', 4, 1, 5),
('f5000000-0000-0000-0000-000000000002', 'f4000000-0000-0000-0000-000000000002', 5, 1, 5),
('f5000000-0000-0000-0000-000000000003', 'f4000000-0000-0000-0000-000000000003', 3, 1, 5),
('f5000000-0000-0000-0000-000000000004', 'f4000000-0000-0000-0000-000000000004', 4, 1, 5),
('f5000000-0000-0000-0000-000000000005', 'f4000000-0000-0000-0000-000000000005', 3, 1, 5),
('f5000000-0000-0000-0000-000000000006', 'f4000000-0000-0000-0000-000000000006', 5, 1, 5),
('f5000000-0000-0000-0000-000000000007', 'f4000000-0000-0000-0000-000000000007', 4, 1, 5),
('f5000000-0000-0000-0000-000000000008', 'f4000000-0000-0000-0000-000000000008', 8, 1, 10);

-- Omar's TEXT answer (Q9)
INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
('f4000000-0000-0000-0000-000000000009', 'f3000000-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-000000000009', 'TEXT', 1, TRUE, '2026-02-10 10:25:00');
INSERT INTO answer_text (id, submission_id, value) VALUES
('f5100000-0000-0000-0000-000000000001', 'f4000000-0000-0000-0000-000000000009',
 'More cross-team collaboration sessions would help break down silos between engineering and operations. Currently we only interact during formal reviews.');

-- Omar's CHOICE answer (Q10 — Career Development)
INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
('f4000000-0000-0000-0000-00000000000a', 'f3000000-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-00000000000a', 'CHOICE', 1, TRUE, '2026-02-10 10:27:00');
INSERT INTO answer_choice (id, submission_id, candidate_answer_id) VALUES
('f5200000-0000-0000-0000-000000000001', 'f4000000-0000-0000-0000-00000000000a', 'f2000000-0000-0000-0000-000000000002');

-- Omar's RATING answer (Q11 — 4 subjects)
INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
('f4000000-0000-0000-0000-00000000000b', 'f3000000-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-00000000000b', 'RATING', 1, TRUE, '2026-02-10 10:29:00');
INSERT INTO answer_rating (id, submission_id, subject_label, stars, max_stars) VALUES
('f5300000-0000-0000-0000-000000000001', 'f4000000-0000-0000-0000-00000000000b', 'IT Support', 4, 5),
('f5300000-0000-0000-0000-000000000002', 'f4000000-0000-0000-0000-00000000000b', 'Office Facilities', 3, 5),
('f5300000-0000-0000-0000-000000000003', 'f4000000-0000-0000-0000-00000000000b', 'Learning & Development', 4, 5),
('f5300000-0000-0000-0000-000000000004', 'f4000000-0000-0000-0000-00000000000b', 'Internal Communications', 2, 5);

-- Omar's CHOICE answer (Q12 — Bi-weekly)
INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
('f4000000-0000-0000-0000-00000000000c', 'f3000000-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-00000000000c', 'CHOICE', 1, TRUE, '2026-02-10 10:31:00');
INSERT INTO answer_choice (id, submission_id, candidate_answer_id) VALUES
('f5200000-0000-0000-0000-000000000002', 'f4000000-0000-0000-0000-00000000000c', 'f2000000-0000-0000-0000-000000000008');


-- 7b. IDENTIFIED session — Layla (completed, with ANSWER VERSIONING on Q1)
INSERT INTO response_session (id, form_id, user_id, is_anonymous, started_at, completed_at) VALUES
('f3000000-0000-0000-0000-000000000002', 'f0000000-0000-0000-0000-000000000001',
 'd0000000-0000-0000-0000-000000000007', FALSE, '2026-02-11 14:00:00', '2026-02-11 14:25:00');

-- Layla Q1: version 1 (superseded, score=2)
INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
('f4000000-0000-0000-0000-000000000101', 'f3000000-0000-0000-0000-000000000002', 'f1000000-0000-0000-0000-000000000001', 'SCALE', 1, FALSE, '2026-02-11 14:02:00');
INSERT INTO answer_scale (id, submission_id, value, min_value, max_value) VALUES
('f5000000-0000-0000-0000-000000000101', 'f4000000-0000-0000-0000-000000000101', 2, 1, 5);

-- Layla Q1: version 2 (current, score=4)
INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
('f4000000-0000-0000-0000-000000000102', 'f3000000-0000-0000-0000-000000000002', 'f1000000-0000-0000-0000-000000000001', 'SCALE', 2, TRUE, '2026-02-11 14:05:00');
INSERT INTO answer_scale (id, submission_id, value, min_value, max_value) VALUES
('f5000000-0000-0000-0000-000000000102', 'f4000000-0000-0000-0000-000000000102', 4, 1, 5);

-- Layla remaining SCALE answers (Q2-Q8)
INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
('f4000000-0000-0000-0000-000000000103', 'f3000000-0000-0000-0000-000000000002', 'f1000000-0000-0000-0000-000000000002', 'SCALE', 1, TRUE, '2026-02-11 14:06:00'),
('f4000000-0000-0000-0000-000000000104', 'f3000000-0000-0000-0000-000000000002', 'f1000000-0000-0000-0000-000000000003', 'SCALE', 1, TRUE, '2026-02-11 14:07:00'),
('f4000000-0000-0000-0000-000000000105', 'f3000000-0000-0000-0000-000000000002', 'f1000000-0000-0000-0000-000000000004', 'SCALE', 1, TRUE, '2026-02-11 14:08:00'),
('f4000000-0000-0000-0000-000000000106', 'f3000000-0000-0000-0000-000000000002', 'f1000000-0000-0000-0000-000000000005', 'SCALE', 1, TRUE, '2026-02-11 14:09:00'),
('f4000000-0000-0000-0000-000000000107', 'f3000000-0000-0000-0000-000000000002', 'f1000000-0000-0000-0000-000000000006', 'SCALE', 1, TRUE, '2026-02-11 14:10:00'),
('f4000000-0000-0000-0000-000000000108', 'f3000000-0000-0000-0000-000000000002', 'f1000000-0000-0000-0000-000000000007', 'SCALE', 1, TRUE, '2026-02-11 14:11:00'),
('f4000000-0000-0000-0000-000000000109', 'f3000000-0000-0000-0000-000000000002', 'f1000000-0000-0000-0000-000000000008', 'SCALE', 1, TRUE, '2026-02-11 14:12:00');

INSERT INTO answer_scale (id, submission_id, value, min_value, max_value) VALUES
('f5000000-0000-0000-0000-000000000103', 'f4000000-0000-0000-0000-000000000103', 4, 1, 5),
('f5000000-0000-0000-0000-000000000104', 'f4000000-0000-0000-0000-000000000104', 2, 1, 5),
('f5000000-0000-0000-0000-000000000105', 'f4000000-0000-0000-0000-000000000105', 5, 1, 5),
('f5000000-0000-0000-0000-000000000106', 'f4000000-0000-0000-0000-000000000106', 4, 1, 5),
('f5000000-0000-0000-0000-000000000107', 'f4000000-0000-0000-0000-000000000107', 3, 1, 5),
('f5000000-0000-0000-0000-000000000108', 'f4000000-0000-0000-0000-000000000108', 4, 1, 5),
('f5000000-0000-0000-0000-000000000109', 'f4000000-0000-0000-0000-000000000109', 7, 1, 10);

-- Layla TEXT (Q9)
INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
('f4000000-0000-0000-0000-00000000010a', 'f3000000-0000-0000-0000-000000000002', 'f1000000-0000-0000-0000-000000000009', 'TEXT', 1, TRUE, '2026-02-11 14:14:00');
INSERT INTO answer_text (id, submission_id, value) VALUES
('f5100000-0000-0000-0000-000000000002', 'f4000000-0000-0000-0000-00000000010a',
 'Better documentation for internal systems. I spent my first two weeks trying to find how things work with no central knowledge base.');

-- Layla CHOICE (Q10 — Communication & Transparency)
INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
('f4000000-0000-0000-0000-00000000010b', 'f3000000-0000-0000-0000-000000000002', 'f1000000-0000-0000-0000-00000000000a', 'CHOICE', 1, TRUE, '2026-02-11 14:16:00');
INSERT INTO answer_choice (id, submission_id, candidate_answer_id) VALUES
('f5200000-0000-0000-0000-000000000003', 'f4000000-0000-0000-0000-00000000010b', 'f2000000-0000-0000-0000-000000000001');

-- Layla RATING (Q11)
INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
('f4000000-0000-0000-0000-00000000010c', 'f3000000-0000-0000-0000-000000000002', 'f1000000-0000-0000-0000-00000000000b', 'RATING', 1, TRUE, '2026-02-11 14:18:00');
INSERT INTO answer_rating (id, submission_id, subject_label, stars, max_stars) VALUES
('f5300000-0000-0000-0000-000000000005', 'f4000000-0000-0000-0000-00000000010c', 'IT Support', 5, 5),
('f5300000-0000-0000-0000-000000000006', 'f4000000-0000-0000-0000-00000000010c', 'Office Facilities', 4, 5),
('f5300000-0000-0000-0000-000000000007', 'f4000000-0000-0000-0000-00000000010c', 'Learning & Development', 2, 5),
('f5300000-0000-0000-0000-000000000008', 'f4000000-0000-0000-0000-00000000010c', 'Internal Communications', 1, 5);

-- Layla CHOICE (Q12 — Monthly)
INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
('f4000000-0000-0000-0000-00000000010d', 'f3000000-0000-0000-0000-000000000002', 'f1000000-0000-0000-0000-00000000000c', 'CHOICE', 1, TRUE, '2026-02-11 14:20:00');
INSERT INTO answer_choice (id, submission_id, candidate_answer_id) VALUES
('f5200000-0000-0000-0000-000000000004', 'f4000000-0000-0000-0000-00000000010d', 'f2000000-0000-0000-0000-000000000009');


-- 7c. IDENTIFIED session — Huda (IN PROGRESS — partial answers)
INSERT INTO response_session (id, form_id, user_id, is_anonymous, started_at, completed_at) VALUES
('f3000000-0000-0000-0000-000000000003', 'f0000000-0000-0000-0000-000000000001',
 'd0000000-0000-0000-0000-000000000009', FALSE, '2026-02-12 09:30:00', NULL);

INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
('f4000000-0000-0000-0000-000000000201', 'f3000000-0000-0000-0000-000000000003', 'f1000000-0000-0000-0000-000000000001', 'SCALE', 1, TRUE, '2026-02-12 09:31:00'),
('f4000000-0000-0000-0000-000000000202', 'f3000000-0000-0000-0000-000000000003', 'f1000000-0000-0000-0000-000000000002', 'SCALE', 1, TRUE, '2026-02-12 09:32:00'),
('f4000000-0000-0000-0000-000000000203', 'f3000000-0000-0000-0000-000000000003', 'f1000000-0000-0000-0000-000000000003', 'SCALE', 1, TRUE, '2026-02-12 09:33:00');

INSERT INTO answer_scale (id, submission_id, value, min_value, max_value) VALUES
('f5000000-0000-0000-0000-000000000201', 'f4000000-0000-0000-0000-000000000201', 3, 1, 5),
('f5000000-0000-0000-0000-000000000202', 'f4000000-0000-0000-0000-000000000202', 4, 1, 5),
('f5000000-0000-0000-0000-000000000203', 'f4000000-0000-0000-0000-000000000203', 2, 1, 5);


-- ============================================================
-- 8. ANONYMOUS SESSIONS — Form 3 (Manager Effectiveness)
-- ============================================================

INSERT INTO anon_identity (id, org_unit_id, form_id, token, window_start, window_end, sequence_in_window) VALUES
('f6000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000009', 'f0000000-0000-0000-0000-000000000003',
 'anon-token-be-001', '2026-02-15 09:00:00', '2026-02-15 10:30:00', 1),
('f6000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000009', 'f0000000-0000-0000-0000-000000000003',
 'anon-token-be-002', '2026-02-15 09:00:00', '2026-02-15 10:30:00', 2);

-- Anonymous session 1 (completed)
INSERT INTO response_session (id, form_id, anon_identity_id, is_anonymous, started_at, completed_at) VALUES
('f3000000-0000-0000-0000-000000000004', 'f0000000-0000-0000-0000-000000000003',
 'f6000000-0000-0000-0000-000000000001', TRUE, '2026-02-15 09:05:00', '2026-02-15 09:18:00');

INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
('f4000000-0000-0000-0000-000000000301', 'f3000000-0000-0000-0000-000000000004', 'f1000000-0000-0000-0000-000000000201', 'SCALE', 1, TRUE, '2026-02-15 09:07:00'),
('f4000000-0000-0000-0000-000000000302', 'f3000000-0000-0000-0000-000000000004', 'f1000000-0000-0000-0000-000000000202', 'SCALE', 1, TRUE, '2026-02-15 09:09:00'),
('f4000000-0000-0000-0000-000000000303', 'f3000000-0000-0000-0000-000000000004', 'f1000000-0000-0000-0000-000000000203', 'SCALE', 1, TRUE, '2026-02-15 09:11:00'),
('f4000000-0000-0000-0000-000000000304', 'f3000000-0000-0000-0000-000000000004', 'f1000000-0000-0000-0000-000000000205', 'SCALE', 1, TRUE, '2026-02-15 09:13:00'),
('f4000000-0000-0000-0000-000000000305', 'f3000000-0000-0000-0000-000000000004', 'f1000000-0000-0000-0000-000000000206', 'TEXT', 1, TRUE, '2026-02-15 09:16:00');

INSERT INTO answer_scale (id, submission_id, value, min_value, max_value) VALUES
('f5000000-0000-0000-0000-000000000301', 'f4000000-0000-0000-0000-000000000301', 5, 1, 5),
('f5000000-0000-0000-0000-000000000302', 'f4000000-0000-0000-0000-000000000302', 4, 1, 5),
('f5000000-0000-0000-0000-000000000303', 'f4000000-0000-0000-0000-000000000303', 5, 1, 5),
('f5000000-0000-0000-0000-000000000304', 'f4000000-0000-0000-0000-000000000304', 4, 1, 5);

INSERT INTO answer_text (id, submission_id, value) VALUES
('f5100000-0000-0000-0000-000000000003', 'f4000000-0000-0000-0000-000000000305',
 'Noura is great at technical mentorship but could improve on sharing broader team priorities. Sometimes we learn about direction changes from other teams first.');

-- Anonymous session 2 (completed)
INSERT INTO response_session (id, form_id, anon_identity_id, is_anonymous, started_at, completed_at) VALUES
('f3000000-0000-0000-0000-000000000005', 'f0000000-0000-0000-0000-000000000003',
 'f6000000-0000-0000-0000-000000000002', TRUE, '2026-02-15 09:20:00', '2026-02-15 09:35:00');

INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
('f4000000-0000-0000-0000-000000000401', 'f3000000-0000-0000-0000-000000000005', 'f1000000-0000-0000-0000-000000000201', 'SCALE', 1, TRUE, '2026-02-15 09:22:00'),
('f4000000-0000-0000-0000-000000000402', 'f3000000-0000-0000-0000-000000000005', 'f1000000-0000-0000-0000-000000000202', 'SCALE', 1, TRUE, '2026-02-15 09:24:00'),
('f4000000-0000-0000-0000-000000000403', 'f3000000-0000-0000-0000-000000000005', 'f1000000-0000-0000-0000-000000000203', 'SCALE', 1, TRUE, '2026-02-15 09:26:00'),
('f4000000-0000-0000-0000-000000000404', 'f3000000-0000-0000-0000-000000000005', 'f1000000-0000-0000-0000-000000000205', 'SCALE', 1, TRUE, '2026-02-15 09:28:00'),
('f4000000-0000-0000-0000-000000000405', 'f3000000-0000-0000-0000-000000000005', 'f1000000-0000-0000-0000-000000000206', 'TEXT', 1, TRUE, '2026-02-15 09:32:00');

INSERT INTO answer_scale (id, submission_id, value, min_value, max_value) VALUES
('f5000000-0000-0000-0000-000000000401', 'f4000000-0000-0000-0000-000000000401', 3, 1, 5),
('f5000000-0000-0000-0000-000000000402', 'f4000000-0000-0000-0000-000000000402', 2, 1, 5),
('f5000000-0000-0000-0000-000000000403', 'f4000000-0000-0000-0000-000000000403', 3, 1, 5),
('f5000000-0000-0000-0000-000000000404', 'f4000000-0000-0000-0000-000000000404', 4, 1, 5);

INSERT INTO answer_text (id, submission_id, value) VALUES
('f5100000-0000-0000-0000-000000000004', 'f4000000-0000-0000-0000-000000000405',
 'More regular one-on-ones would help. Currently they get cancelled when things are busy, which is exactly when I need them most.');


-- ============================================================
-- 9. ONBOARDING FORM — Form 2 (non-anonymous, Youssef)
-- ============================================================

INSERT INTO response_session (id, form_id, user_id, is_anonymous, started_at, completed_at) VALUES
('f3000000-0000-0000-0000-000000000006', 'f0000000-0000-0000-0000-000000000002',
 'd0000000-0000-0000-0000-000000000008', FALSE, '2026-02-18 11:00:00', '2026-02-18 11:12:00');

INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
('f4000000-0000-0000-0000-000000000501', 'f3000000-0000-0000-0000-000000000006', 'f1000000-0000-0000-0000-000000000101', 'SCALE', 1, TRUE, '2026-02-18 11:02:00'),
('f4000000-0000-0000-0000-000000000502', 'f3000000-0000-0000-0000-000000000006', 'f1000000-0000-0000-0000-000000000102', 'SCALE', 1, TRUE, '2026-02-18 11:03:00'),
('f4000000-0000-0000-0000-000000000503', 'f3000000-0000-0000-0000-000000000006', 'f1000000-0000-0000-0000-000000000103', 'SCALE', 1, TRUE, '2026-02-18 11:04:00'),
('f4000000-0000-0000-0000-000000000504', 'f3000000-0000-0000-0000-000000000006', 'f1000000-0000-0000-0000-000000000104', 'TEXT', 1, TRUE, '2026-02-18 11:07:00'),
('f4000000-0000-0000-0000-000000000505', 'f3000000-0000-0000-0000-000000000006', 'f1000000-0000-0000-0000-000000000105', 'RATING', 1, TRUE, '2026-02-18 11:10:00');

INSERT INTO answer_scale (id, submission_id, value, min_value, max_value) VALUES
('f5000000-0000-0000-0000-000000000501', 'f4000000-0000-0000-0000-000000000501', 3, 1, 5),
('f5000000-0000-0000-0000-000000000502', 'f4000000-0000-0000-0000-000000000502', 5, 1, 5),
('f5000000-0000-0000-0000-000000000503', 'f4000000-0000-0000-0000-000000000503', 2, 1, 5);

INSERT INTO answer_text (id, submission_id, value) VALUES
('f5100000-0000-0000-0000-000000000005', 'f4000000-0000-0000-0000-000000000504',
 'The buddy system was helpful but my assigned buddy was in a different office. Having a local buddy would have made the first week much smoother.');

INSERT INTO answer_rating (id, submission_id, subject_label, stars, max_stars) VALUES
('f5300000-0000-0000-0000-000000000009', 'f4000000-0000-0000-0000-000000000505', 'Orientation Day', 4, 5),
('f5300000-0000-0000-0000-00000000000a', 'f4000000-0000-0000-0000-000000000505', 'IT Setup & Access', 2, 5),
('f5300000-0000-0000-0000-00000000000b', 'f4000000-0000-0000-0000-000000000505', 'Buddy Program', 3, 5),
('f5300000-0000-0000-0000-00000000000c', 'f4000000-0000-0000-0000-000000000505', 'Role Clarity', 4, 5),
('f5300000-0000-0000-0000-00000000000d', 'f4000000-0000-0000-0000-000000000505', 'Team Integration', 5, 5);

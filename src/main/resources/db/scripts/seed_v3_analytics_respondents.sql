-- ============================================================
-- Seed: Analytics respondents for "test survey"
-- Adds an org hierarchy, manager/employee users, and 20+
-- completed response sessions (mix of identified & anonymous)
-- so that analytics charts and role-scoped report are testable.
--
-- Requires: seed_v1_dummy_data NOT applied (this DB uses the
--           real V1 migration schema with one live "test survey").
--
-- Safe to re-run: all inserts use ON CONFLICT DO NOTHING.
-- ============================================================

-- ============================================================
-- 0. IDs referenced throughout this script
-- ============================================================
-- Survey:       c0a8018d-9c9e-14a8-819c-9e358ee70015
-- Q TEXT:       c0a8018d-9c9e-14a8-819c-9e35bb240017
-- Q SCALE:      c0a8018d-9c9e-14a8-819c-9e35de070019
-- Q CHOICE:     c0a8018d-9c9e-14a8-819c-9e367b21001b
--   choice opt1 "label one":  c0a8018d-9c9e-14a8-819c-9e367b22001c
--   choice opt2 "🏷️ two":     c0a8018d-9c9e-14a8-819c-9e367b22001d
-- Q RATING:     c0a8018d-9c9e-14a8-819c-9e37074e001f  (no labels)
-- Q MULTI_RATING: c0a8018d-9c9e-14a8-819c-9e3787c70021
--   subjects: "first rating", "second rating"
-- Existing org: c0a8018d-9c9e-14a8-819c-9e35253a0012  (Unassigned /UNASSIGNED)
-- Existing user: c0a8018d-9c9e-14a8-819c-9e35253a0013

-- ============================================================
-- 1. ORG HIERARCHY  (inclusive UUID-based paths)
-- ============================================================
-- The `path` column stores the full path including this unit's
-- segment, so LIKE '/aa001%' matches the unit itself and all
-- descendants. Format: /<parent-path>/<this-id>

INSERT INTO organizational_units
  (id, parent_id, org_unit_name, org_unit_code, org_level, path, depth, active)
VALUES
  -- Root group
  ('aa000000-0000-0000-0000-000000000001',
   NULL, 'Edge Group', 'EDGE-GRP', 'GROUP',
   '/aa000000-0000-0000-0000-000000000001', 0, TRUE),

  -- Tech cluster (child of group)
  ('aa000000-0000-0000-0000-000000000002',
   'aa000000-0000-0000-0000-000000000001',
   'Technology Cluster', 'TECH', 'CLUSTER',
   '/aa000000-0000-0000-0000-000000000001/aa000000-0000-0000-0000-000000000002',
   1, TRUE),

  -- Ops cluster (child of group)
  ('aa000000-0000-0000-0000-000000000003',
   'aa000000-0000-0000-0000-000000000001',
   'Operations Cluster', 'OPS', 'CLUSTER',
   '/aa000000-0000-0000-0000-000000000001/aa000000-0000-0000-0000-000000000003',
   1, TRUE),

  -- Backend Team (child of Tech)
  ('aa000000-0000-0000-0000-000000000004',
   'aa000000-0000-0000-0000-000000000002',
   'Backend Team', 'TECH-BE', 'TEAM',
   '/aa000000-0000-0000-0000-000000000001/aa000000-0000-0000-0000-000000000002/aa000000-0000-0000-0000-000000000004',
   2, TRUE),

  -- Supply Chain Team (child of Ops)
  ('aa000000-0000-0000-0000-000000000005',
   'aa000000-0000-0000-0000-000000000003',
   'Supply Chain Team', 'OPS-SC', 'TEAM',
   '/aa000000-0000-0000-0000-000000000001/aa000000-0000-0000-0000-000000000003/aa000000-0000-0000-0000-000000000005',
   2, TRUE)
ON CONFLICT (id) DO NOTHING;


-- ============================================================
-- 2. ROLES (should exist — just make sure REPORT_VIEW is wired)
-- ============================================================
-- Roles already in DB: EMPLOYEE, MANAGER, HR_USER_MANAGE,
--   HR_SURVEY_MANAGE, HR_REPORT_VIEW, HR_FULL_CRUD
-- Permissions include REPORT_VIEW already wired to MANAGER and
-- HR_FULL_CRUD roles.


-- ============================================================
-- 3. USERS
-- ============================================================
-- HR (Edge Group)
INSERT INTO users
  (id, azure_ad_id, email, display_name, org_unit_id, active)
VALUES
  ('bb000000-0000-0000-0000-000000000001',
   'aad-seed-hr-001', 'hr.admin@edge.ae', 'Fatima Ahmed',
   'aa000000-0000-0000-0000-000000000001', TRUE)
ON CONFLICT (azure_ad_id) DO NOTHING;

-- Manager Tech Cluster
INSERT INTO users
  (id, azure_ad_id, email, display_name, org_unit_id, active)
VALUES
  ('bb000000-0000-0000-0000-000000000002',
   'aad-seed-mgr-tech', 'sara.tech@edge.ae', 'Sara Hassan',
   'aa000000-0000-0000-0000-000000000002', TRUE)
ON CONFLICT (azure_ad_id) DO NOTHING;

-- Manager Ops Cluster
INSERT INTO users
  (id, azure_ad_id, email, display_name, org_unit_id, active)
VALUES
  ('bb000000-0000-0000-0000-000000000003',
   'aad-seed-mgr-ops', 'khalid.ops@edge.ae', 'Khalid Omar',
   'aa000000-0000-0000-0000-000000000003', TRUE)
ON CONFLICT (azure_ad_id) DO NOTHING;

-- Backend Team engineers (5 users in TECH-BE)
INSERT INTO users
  (id, azure_ad_id, email, display_name, org_unit_id, manager_id, active)
VALUES
  ('bb000000-0000-0000-0000-000000000011', 'aad-seed-be-001', 'omar.be@edge.ae',   'Omar Rashid',   'aa000000-0000-0000-0000-000000000004', 'bb000000-0000-0000-0000-000000000002', TRUE),
  ('bb000000-0000-0000-0000-000000000012', 'aad-seed-be-002', 'layla.be@edge.ae',  'Layla Nasser',  'aa000000-0000-0000-0000-000000000004', 'bb000000-0000-0000-0000-000000000002', TRUE),
  ('bb000000-0000-0000-0000-000000000013', 'aad-seed-be-003', 'noura.be@edge.ae',  'Noura Salem',   'aa000000-0000-0000-0000-000000000004', 'bb000000-0000-0000-0000-000000000002', TRUE),
  ('bb000000-0000-0000-0000-000000000014', 'aad-seed-be-004', 'ahmed.be@edge.ae',  'Ahmed Ali',     'aa000000-0000-0000-0000-000000000004', 'bb000000-0000-0000-0000-000000000002', TRUE),
  ('bb000000-0000-0000-0000-000000000015', 'aad-seed-be-005', 'youssef.be@edge.ae','Youssef Karam', 'aa000000-0000-0000-0000-000000000004', 'bb000000-0000-0000-0000-000000000002', TRUE)
ON CONFLICT (azure_ad_id) DO NOTHING;

-- Supply Chain analysts (5 users in OPS-SC)
INSERT INTO users
  (id, azure_ad_id, email, display_name, org_unit_id, manager_id, active)
VALUES
  ('bb000000-0000-0000-0000-000000000021', 'aad-seed-sc-001', 'huda.sc@edge.ae',   'Huda Mahmoud', 'aa000000-0000-0000-0000-000000000005', 'bb000000-0000-0000-0000-000000000003', TRUE),
  ('bb000000-0000-0000-0000-000000000022', 'aad-seed-sc-002', 'tariq.sc@edge.ae',  'Tariq Farouk', 'aa000000-0000-0000-0000-000000000005', 'bb000000-0000-0000-0000-000000000003', TRUE),
  ('bb000000-0000-0000-0000-000000000023', 'aad-seed-sc-003', 'mona.sc@edge.ae',   'Mona Jabir',   'aa000000-0000-0000-0000-000000000005', 'bb000000-0000-0000-0000-000000000003', TRUE),
  ('bb000000-0000-0000-0000-000000000024', 'aad-seed-sc-004', 'rania.sc@edge.ae',  'Rania Darwish','aa000000-0000-0000-0000-000000000005', 'bb000000-0000-0000-0000-000000000003', TRUE),
  ('bb000000-0000-0000-0000-000000000025', 'aad-seed-sc-005', 'hassan.sc@edge.ae', 'Hassan Nour',  'aa000000-0000-0000-0000-000000000005', 'bb000000-0000-0000-0000-000000000003', TRUE)
ON CONFLICT (azure_ad_id) DO NOTHING;

-- Assign roles
INSERT INTO user_roles (user_id, role_id)
SELECT 'bb000000-0000-0000-0000-000000000001', r.id FROM roles r WHERE r.name = 'HR_FULL_CRUD'
ON CONFLICT DO NOTHING;
INSERT INTO user_roles (user_id, role_id)
SELECT 'bb000000-0000-0000-0000-000000000002', r.id FROM roles r WHERE r.name = 'MANAGER'
ON CONFLICT DO NOTHING;
INSERT INTO user_roles (user_id, role_id)
SELECT 'bb000000-0000-0000-0000-000000000003', r.id FROM roles r WHERE r.name = 'MANAGER'
ON CONFLICT DO NOTHING;
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.azure_ad_id LIKE 'aad-seed-be-%' AND r.name = 'EMPLOYEE'
ON CONFLICT DO NOTHING;
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.azure_ad_id LIKE 'aad-seed-sc-%' AND r.name = 'EMPLOYEE'
ON CONFLICT DO NOTHING;


-- ============================================================
-- 4. SURVEY ASSIGNMENT for the org hierarchy
-- ============================================================
INSERT INTO survey_assignment
  (id, survey_id, org_unit_id, assigned_by, mandatory, active, include_children)
VALUES
  ('cc000000-0000-0000-0000-000000000001',
   'c0a8018d-9c9e-14a8-819c-9e358ee70015',
   'aa000000-0000-0000-0000-000000000001',
   'bb000000-0000-0000-0000-000000000001',
   FALSE, TRUE, TRUE)
ON CONFLICT DO NOTHING;


-- ============================================================
-- 5. IDENTIFIED COMPLETED SESSIONS
--    10 users (5 Backend, 5 Supply Chain) all completing the survey
-- ============================================================

-- Helper: insert a full completed session for one identified user
-- Pattern: session → scale answer → text answer → choice answer
--          → rating (single) → multi_rating (2 subjects)

-- ── Session BB011 (Omar, Backend) ───────────────────────────
INSERT INTO response_session (id, survey_id, user_id, is_anonymous, started_at, completed_at)
VALUES ('cc000000-0000-0000-0000-000000001001',
        'c0a8018d-9c9e-14a8-819c-9e358ee70015',
        'bb000000-0000-0000-0000-000000000011', FALSE,
        '2026-02-20 09:00:00', '2026-02-20 09:15:00')
ON CONFLICT DO NOTHING;

INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
  ('cc000000-0000-0000-0001-000000001001', 'cc000000-0000-0000-0000-000000001001', 'c0a8018d-9c9e-14a8-819c-9e35de070019', 'SCALE', 1, TRUE, '2026-02-20 09:01:00'),
  ('cc000000-0000-0000-0002-000000001001', 'cc000000-0000-0000-0000-000000001001', 'c0a8018d-9c9e-14a8-819c-9e35bb240017', 'TEXT',  1, TRUE, '2026-02-20 09:03:00'),
  ('cc000000-0000-0000-0003-000000001001', 'cc000000-0000-0000-0000-000000001001', 'c0a8018d-9c9e-14a8-819c-9e367b21001b', 'CHOICE',1, TRUE, '2026-02-20 09:05:00'),
  ('cc000000-0000-0000-0004-000000001001', 'cc000000-0000-0000-0000-000000001001', 'c0a8018d-9c9e-14a8-819c-9e37074e001f', 'RATING',1, TRUE, '2026-02-20 09:07:00'),
  ('cc000000-0000-0000-0005-000000001001', 'cc000000-0000-0000-0000-000000001001', 'c0a8018d-9c9e-14a8-819c-9e3787c70021', 'RATING',1, TRUE, '2026-02-20 09:09:00')
ON CONFLICT DO NOTHING;
INSERT INTO answer_scale (id, submission_id, value, min_value, max_value)
  VALUES ('cc000000-0000-0000-aaaa-000000001001', 'cc000000-0000-0000-0001-000000001001', 4, 1, 5) ON CONFLICT DO NOTHING;
INSERT INTO answer_text (id, submission_id, value)
  VALUES ('cc000000-0000-0000-bbbb-000000001001', 'cc000000-0000-0000-0002-000000001001', 'Better cross-team collaboration.') ON CONFLICT DO NOTHING;
INSERT INTO answer_choice (id, submission_id, candidate_answer_id)
  VALUES ('cc000000-0000-0000-cccc-000000001001', 'cc000000-0000-0000-0003-000000001001', 'c0a8018d-9c9e-14a8-819c-9e367b22001c') ON CONFLICT DO NOTHING;
INSERT INTO answer_rating (id, submission_id, subject_label, stars, max_stars) VALUES
  ('cc000000-0000-0000-dddd-000000001001', 'cc000000-0000-0000-0004-000000001001', 'Overall', 4, 5) ON CONFLICT DO NOTHING;
INSERT INTO answer_rating (id, submission_id, subject_label, stars, max_stars) VALUES
  ('cc000000-0000-0000-eeee-000000001001', 'cc000000-0000-0000-0005-000000001001', 'first rating',  4, 5),
  ('cc000000-0000-0000-ffff-000000001001', 'cc000000-0000-0000-0005-000000001001', 'second rating', 3, 5) ON CONFLICT DO NOTHING;

-- ── Session BB012 (Layla, Backend) ──────────────────────────
INSERT INTO response_session (id, survey_id, user_id, is_anonymous, started_at, completed_at)
VALUES ('cc000000-0000-0000-0000-000000001002',
        'c0a8018d-9c9e-14a8-819c-9e358ee70015',
        'bb000000-0000-0000-0000-000000000012', FALSE,
        '2026-02-20 10:00:00', '2026-02-20 10:18:00')
ON CONFLICT DO NOTHING;
INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
  ('cc000000-0000-0000-0001-000000001002', 'cc000000-0000-0000-0000-000000001002', 'c0a8018d-9c9e-14a8-819c-9e35de070019', 'SCALE', 1, TRUE, '2026-02-20 10:01:00'),
  ('cc000000-0000-0000-0002-000000001002', 'cc000000-0000-0000-0000-000000001002', 'c0a8018d-9c9e-14a8-819c-9e35bb240017', 'TEXT',  1, TRUE, '2026-02-20 10:03:00'),
  ('cc000000-0000-0000-0003-000000001002', 'cc000000-0000-0000-0000-000000001002', 'c0a8018d-9c9e-14a8-819c-9e367b21001b', 'CHOICE',1, TRUE, '2026-02-20 10:05:00'),
  ('cc000000-0000-0000-0004-000000001002', 'cc000000-0000-0000-0000-000000001002', 'c0a8018d-9c9e-14a8-819c-9e37074e001f', 'RATING',1, TRUE, '2026-02-20 10:07:00'),
  ('cc000000-0000-0000-0005-000000001002', 'cc000000-0000-0000-0000-000000001002', 'c0a8018d-9c9e-14a8-819c-9e3787c70021', 'RATING',1, TRUE, '2026-02-20 10:09:00')
ON CONFLICT DO NOTHING;
INSERT INTO answer_scale (id, submission_id, value, min_value, max_value)
  VALUES ('cc000000-0000-0000-aaaa-000000001002', 'cc000000-0000-0000-0001-000000001002', 2, 1, 5) ON CONFLICT DO NOTHING;
INSERT INTO answer_text (id, submission_id, value)
  VALUES ('cc000000-0000-0000-bbbb-000000001002', 'cc000000-0000-0000-0002-000000001002', 'More documentation for internal systems.') ON CONFLICT DO NOTHING;
INSERT INTO answer_choice (id, submission_id, candidate_answer_id)
  VALUES ('cc000000-0000-0000-cccc-000000001002', 'cc000000-0000-0000-0003-000000001002', 'c0a8018d-9c9e-14a8-819c-9e367b22001d') ON CONFLICT DO NOTHING;
INSERT INTO answer_rating (id, submission_id, subject_label, stars, max_stars)
  VALUES ('cc000000-0000-0000-dddd-000000001002', 'cc000000-0000-0000-0004-000000001002', 'Overall', 2, 5) ON CONFLICT DO NOTHING;
INSERT INTO answer_rating (id, submission_id, subject_label, stars, max_stars) VALUES
  ('cc000000-0000-0000-eeee-000000001002', 'cc000000-0000-0000-0005-000000001002', 'first rating',  3, 5),
  ('cc000000-0000-0000-ffff-000000001002', 'cc000000-0000-0000-0005-000000001002', 'second rating', 4, 5) ON CONFLICT DO NOTHING;

-- ── Session BB013 (Noura, Backend) ──────────────────────────
INSERT INTO response_session (id, survey_id, user_id, is_anonymous, started_at, completed_at)
VALUES ('cc000000-0000-0000-0000-000000001003',
        'c0a8018d-9c9e-14a8-819c-9e358ee70015',
        'bb000000-0000-0000-0000-000000000013', FALSE,
        '2026-02-21 09:00:00', '2026-02-21 09:20:00')
ON CONFLICT DO NOTHING;
INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
  ('cc000000-0000-0000-0001-000000001003', 'cc000000-0000-0000-0000-000000001003', 'c0a8018d-9c9e-14a8-819c-9e35de070019', 'SCALE', 1, TRUE, '2026-02-21 09:01:00'),
  ('cc000000-0000-0000-0002-000000001003', 'cc000000-0000-0000-0000-000000001003', 'c0a8018d-9c9e-14a8-819c-9e35bb240017', 'TEXT',  1, TRUE, '2026-02-21 09:04:00'),
  ('cc000000-0000-0000-0003-000000001003', 'cc000000-0000-0000-0000-000000001003', 'c0a8018d-9c9e-14a8-819c-9e367b21001b', 'CHOICE',1, TRUE, '2026-02-21 09:06:00'),
  ('cc000000-0000-0000-0004-000000001003', 'cc000000-0000-0000-0000-000000001003', 'c0a8018d-9c9e-14a8-819c-9e37074e001f', 'RATING',1, TRUE, '2026-02-21 09:08:00'),
  ('cc000000-0000-0000-0005-000000001003', 'cc000000-0000-0000-0000-000000001003', 'c0a8018d-9c9e-14a8-819c-9e3787c70021', 'RATING',1, TRUE, '2026-02-21 09:10:00')
ON CONFLICT DO NOTHING;
INSERT INTO answer_scale (id, submission_id, value, min_value, max_value)
  VALUES ('cc000000-0000-0000-aaaa-000000001003', 'cc000000-0000-0000-0001-000000001003', 5, 1, 5) ON CONFLICT DO NOTHING;
INSERT INTO answer_text (id, submission_id, value)
  VALUES ('cc000000-0000-0000-bbbb-000000001003', 'cc000000-0000-0000-0002-000000001003', 'Flexible working hours would improve morale.') ON CONFLICT DO NOTHING;
INSERT INTO answer_choice (id, submission_id, candidate_answer_id)
  VALUES ('cc000000-0000-0000-cccc-000000001003', 'cc000000-0000-0000-0003-000000001003', 'c0a8018d-9c9e-14a8-819c-9e367b22001c') ON CONFLICT DO NOTHING;
INSERT INTO answer_rating (id, submission_id, subject_label, stars, max_stars)
  VALUES ('cc000000-0000-0000-dddd-000000001003', 'cc000000-0000-0000-0004-000000001003', 'Overall', 5, 5) ON CONFLICT DO NOTHING;
INSERT INTO answer_rating (id, submission_id, subject_label, stars, max_stars) VALUES
  ('cc000000-0000-0000-eeee-000000001003', 'cc000000-0000-0000-0005-000000001003', 'first rating',  5, 5),
  ('cc000000-0000-0000-ffff-000000001003', 'cc000000-0000-0000-0005-000000001003', 'second rating', 4, 5) ON CONFLICT DO NOTHING;

-- ── Session BB014 (Ahmed, Backend) ──────────────────────────
INSERT INTO response_session (id, survey_id, user_id, is_anonymous, started_at, completed_at)
VALUES ('cc000000-0000-0000-0000-000000001004',
        'c0a8018d-9c9e-14a8-819c-9e358ee70015',
        'bb000000-0000-0000-0000-000000000014', FALSE,
        '2026-02-21 11:00:00', '2026-02-21 11:22:00')
ON CONFLICT DO NOTHING;
INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
  ('cc000000-0000-0000-0001-000000001004', 'cc000000-0000-0000-0000-000000001004', 'c0a8018d-9c9e-14a8-819c-9e35de070019', 'SCALE', 1, TRUE, '2026-02-21 11:01:00'),
  ('cc000000-0000-0000-0002-000000001004', 'cc000000-0000-0000-0000-000000001004', 'c0a8018d-9c9e-14a8-819c-9e35bb240017', 'TEXT',  1, TRUE, '2026-02-21 11:04:00'),
  ('cc000000-0000-0000-0003-000000001004', 'cc000000-0000-0000-0000-000000001004', 'c0a8018d-9c9e-14a8-819c-9e367b21001b', 'CHOICE',1, TRUE, '2026-02-21 11:06:00'),
  ('cc000000-0000-0000-0004-000000001004', 'cc000000-0000-0000-0000-000000001004', 'c0a8018d-9c9e-14a8-819c-9e37074e001f', 'RATING',1, TRUE, '2026-02-21 11:08:00'),
  ('cc000000-0000-0000-0005-000000001004', 'cc000000-0000-0000-0000-000000001004', 'c0a8018d-9c9e-14a8-819c-9e3787c70021', 'RATING',1, TRUE, '2026-02-21 11:10:00')
ON CONFLICT DO NOTHING;
INSERT INTO answer_scale (id, submission_id, value, min_value, max_value)
  VALUES ('cc000000-0000-0000-aaaa-000000001004', 'cc000000-0000-0000-0001-000000001004', 3, 1, 5) ON CONFLICT DO NOTHING;
INSERT INTO answer_text (id, submission_id, value)
  VALUES ('cc000000-0000-0000-bbbb-000000001004', 'cc000000-0000-0000-0002-000000001004', 'Clearer promotion criteria.') ON CONFLICT DO NOTHING;
INSERT INTO answer_choice (id, submission_id, candidate_answer_id)
  VALUES ('cc000000-0000-0000-cccc-000000001004', 'cc000000-0000-0000-0003-000000001004', 'c0a8018d-9c9e-14a8-819c-9e367b22001c') ON CONFLICT DO NOTHING;
INSERT INTO answer_rating (id, submission_id, subject_label, stars, max_stars)
  VALUES ('cc000000-0000-0000-dddd-000000001004', 'cc000000-0000-0000-0004-000000001004', 'Overall', 3, 5) ON CONFLICT DO NOTHING;
INSERT INTO answer_rating (id, submission_id, subject_label, stars, max_stars) VALUES
  ('cc000000-0000-0000-eeee-000000001004', 'cc000000-0000-0000-0005-000000001004', 'first rating',  2, 5),
  ('cc000000-0000-0000-ffff-000000001004', 'cc000000-0000-0000-0005-000000001004', 'second rating', 3, 5) ON CONFLICT DO NOTHING;

-- ── Session BB015 (Youssef, Backend) ────────────────────────
INSERT INTO response_session (id, survey_id, user_id, is_anonymous, started_at, completed_at)
VALUES ('cc000000-0000-0000-0000-000000001005',
        'c0a8018d-9c9e-14a8-819c-9e358ee70015',
        'bb000000-0000-0000-0000-000000000015', FALSE,
        '2026-02-22 08:00:00', '2026-02-22 08:17:00')
ON CONFLICT DO NOTHING;
INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
  ('cc000000-0000-0000-0001-000000001005', 'cc000000-0000-0000-0000-000000001005', 'c0a8018d-9c9e-14a8-819c-9e35de070019', 'SCALE', 1, TRUE, '2026-02-22 08:01:00'),
  ('cc000000-0000-0000-0002-000000001005', 'cc000000-0000-0000-0000-000000001005', 'c0a8018d-9c9e-14a8-819c-9e35bb240017', 'TEXT',  1, TRUE, '2026-02-22 08:04:00'),
  ('cc000000-0000-0000-0003-000000001005', 'cc000000-0000-0000-0000-000000001005', 'c0a8018d-9c9e-14a8-819c-9e367b21001b', 'CHOICE',1, TRUE, '2026-02-22 08:06:00'),
  ('cc000000-0000-0000-0004-000000001005', 'cc000000-0000-0000-0000-000000001005', 'c0a8018d-9c9e-14a8-819c-9e37074e001f', 'RATING',1, TRUE, '2026-02-22 08:08:00'),
  ('cc000000-0000-0000-0005-000000001005', 'cc000000-0000-0000-0000-000000001005', 'c0a8018d-9c9e-14a8-819c-9e3787c70021', 'RATING',1, TRUE, '2026-02-22 08:10:00')
ON CONFLICT DO NOTHING;
INSERT INTO answer_scale (id, submission_id, value, min_value, max_value)
  VALUES ('cc000000-0000-0000-aaaa-000000001005', 'cc000000-0000-0000-0001-000000001005', 4, 1, 5) ON CONFLICT DO NOTHING;
INSERT INTO answer_text (id, submission_id, value)
  VALUES ('cc000000-0000-0000-bbbb-000000001005', 'cc000000-0000-0000-0002-000000001005', 'More regular team socials.') ON CONFLICT DO NOTHING;
INSERT INTO answer_choice (id, submission_id, candidate_answer_id)
  VALUES ('cc000000-0000-0000-cccc-000000001005', 'cc000000-0000-0000-0003-000000001005', 'c0a8018d-9c9e-14a8-819c-9e367b22001d') ON CONFLICT DO NOTHING;
INSERT INTO answer_rating (id, submission_id, subject_label, stars, max_stars)
  VALUES ('cc000000-0000-0000-dddd-000000001005', 'cc000000-0000-0000-0004-000000001005', 'Overall', 4, 5) ON CONFLICT DO NOTHING;
INSERT INTO answer_rating (id, submission_id, subject_label, stars, max_stars) VALUES
  ('cc000000-0000-0000-eeee-000000001005', 'cc000000-0000-0000-0005-000000001005', 'first rating',  4, 5),
  ('cc000000-0000-0000-ffff-000000001005', 'cc000000-0000-0000-0005-000000001005', 'second rating', 5, 5) ON CONFLICT DO NOTHING;

-- ── Supply Chain sessions (BB021-BB025) ─────────────────────
-- SC users: scale values 3,4,2,5,3 | choice alternates label_one/two
INSERT INTO response_session (id, survey_id, user_id, is_anonymous, started_at, completed_at)
VALUES
  ('cc000000-0000-0000-0000-000000002001','c0a8018d-9c9e-14a8-819c-9e358ee70015','bb000000-0000-0000-0000-000000000021',FALSE,'2026-02-22 09:00:00','2026-02-22 09:18:00'),
  ('cc000000-0000-0000-0000-000000002002','c0a8018d-9c9e-14a8-819c-9e358ee70015','bb000000-0000-0000-0000-000000000022',FALSE,'2026-02-22 10:00:00','2026-02-22 10:19:00'),
  ('cc000000-0000-0000-0000-000000002003','c0a8018d-9c9e-14a8-819c-9e358ee70015','bb000000-0000-0000-0000-000000000023',FALSE,'2026-02-22 11:00:00','2026-02-22 11:21:00'),
  ('cc000000-0000-0000-0000-000000002004','c0a8018d-9c9e-14a8-819c-9e358ee70015','bb000000-0000-0000-0000-000000000024',FALSE,'2026-02-22 13:00:00','2026-02-22 13:16:00'),
  ('cc000000-0000-0000-0000-000000002005','c0a8018d-9c9e-14a8-819c-9e358ee70015','bb000000-0000-0000-0000-000000000025',FALSE,'2026-02-22 14:00:00','2026-02-22 14:20:00')
ON CONFLICT DO NOTHING;

-- Scale answers for SC
INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
  ('cc000000-0000-0000-0001-000000002001','cc000000-0000-0000-0000-000000002001','c0a8018d-9c9e-14a8-819c-9e35de070019','SCALE',1,TRUE,'2026-02-22 09:01:00'),
  ('cc000000-0000-0000-0001-000000002002','cc000000-0000-0000-0000-000000002002','c0a8018d-9c9e-14a8-819c-9e35de070019','SCALE',1,TRUE,'2026-02-22 10:01:00'),
  ('cc000000-0000-0000-0001-000000002003','cc000000-0000-0000-0000-000000002003','c0a8018d-9c9e-14a8-819c-9e35de070019','SCALE',1,TRUE,'2026-02-22 11:01:00'),
  ('cc000000-0000-0000-0001-000000002004','cc000000-0000-0000-0000-000000002004','c0a8018d-9c9e-14a8-819c-9e35de070019','SCALE',1,TRUE,'2026-02-22 13:01:00'),
  ('cc000000-0000-0000-0001-000000002005','cc000000-0000-0000-0000-000000002005','c0a8018d-9c9e-14a8-819c-9e35de070019','SCALE',1,TRUE,'2026-02-22 14:01:00')
ON CONFLICT DO NOTHING;
INSERT INTO answer_scale (id, submission_id, value, min_value, max_value) VALUES
  ('cc000000-0000-0000-aaaa-000000002001','cc000000-0000-0000-0001-000000002001',3,1,5),
  ('cc000000-0000-0000-aaaa-000000002002','cc000000-0000-0000-0001-000000002002',4,1,5),
  ('cc000000-0000-0000-aaaa-000000002003','cc000000-0000-0000-0001-000000002003',2,1,5),
  ('cc000000-0000-0000-aaaa-000000002004','cc000000-0000-0000-0001-000000002004',5,1,5),
  ('cc000000-0000-0000-aaaa-000000002005','cc000000-0000-0000-0001-000000002005',3,1,5)
ON CONFLICT DO NOTHING;

-- Text answers for SC
INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
  ('cc000000-0000-0000-0002-000000002001','cc000000-0000-0000-0000-000000002001','c0a8018d-9c9e-14a8-819c-9e35bb240017','TEXT',1,TRUE,'2026-02-22 09:03:00'),
  ('cc000000-0000-0000-0002-000000002002','cc000000-0000-0000-0000-000000002002','c0a8018d-9c9e-14a8-819c-9e35bb240017','TEXT',1,TRUE,'2026-02-22 10:03:00'),
  ('cc000000-0000-0000-0002-000000002003','cc000000-0000-0000-0000-000000002003','c0a8018d-9c9e-14a8-819c-9e35bb240017','TEXT',1,TRUE,'2026-02-22 11:03:00'),
  ('cc000000-0000-0000-0002-000000002004','cc000000-0000-0000-0000-000000002004','c0a8018d-9c9e-14a8-819c-9e35bb240017','TEXT',1,TRUE,'2026-02-22 13:03:00'),
  ('cc000000-0000-0000-0002-000000002005','cc000000-0000-0000-0000-000000002005','c0a8018d-9c9e-14a8-819c-9e35bb240017','TEXT',1,TRUE,'2026-02-22 14:03:00')
ON CONFLICT DO NOTHING;
INSERT INTO answer_text (id, submission_id, value) VALUES
  ('cc000000-0000-0000-bbbb-000000002001','cc000000-0000-0000-0002-000000002001','Streamline approval workflows.'),
  ('cc000000-0000-0000-bbbb-000000002002','cc000000-0000-0000-0002-000000002002','Better supplier management tools.'),
  ('cc000000-0000-0000-bbbb-000000002003','cc000000-0000-0000-0002-000000002003','More transparency in procurement.'),
  ('cc000000-0000-0000-bbbb-000000002004','cc000000-0000-0000-0002-000000002004','Enhanced reporting dashboards.'),
  ('cc000000-0000-0000-bbbb-000000002005','cc000000-0000-0000-0002-000000002005','Cross-functional training sessions.')
ON CONFLICT DO NOTHING;

-- Choice answers for SC
INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
  ('cc000000-0000-0000-0003-000000002001','cc000000-0000-0000-0000-000000002001','c0a8018d-9c9e-14a8-819c-9e367b21001b','CHOICE',1,TRUE,'2026-02-22 09:05:00'),
  ('cc000000-0000-0000-0003-000000002002','cc000000-0000-0000-0000-000000002002','c0a8018d-9c9e-14a8-819c-9e367b21001b','CHOICE',1,TRUE,'2026-02-22 10:05:00'),
  ('cc000000-0000-0000-0003-000000002003','cc000000-0000-0000-0000-000000002003','c0a8018d-9c9e-14a8-819c-9e367b21001b','CHOICE',1,TRUE,'2026-02-22 11:05:00'),
  ('cc000000-0000-0000-0003-000000002004','cc000000-0000-0000-0000-000000002004','c0a8018d-9c9e-14a8-819c-9e367b21001b','CHOICE',1,TRUE,'2026-02-22 13:05:00'),
  ('cc000000-0000-0000-0003-000000002005','cc000000-0000-0000-0000-000000002005','c0a8018d-9c9e-14a8-819c-9e367b21001b','CHOICE',1,TRUE,'2026-02-22 14:05:00')
ON CONFLICT DO NOTHING;
INSERT INTO answer_choice (id, submission_id, candidate_answer_id) VALUES
  ('cc000000-0000-0000-cccc-000000002001','cc000000-0000-0000-0003-000000002001','c0a8018d-9c9e-14a8-819c-9e367b22001d'),
  ('cc000000-0000-0000-cccc-000000002002','cc000000-0000-0000-0003-000000002002','c0a8018d-9c9e-14a8-819c-9e367b22001c'),
  ('cc000000-0000-0000-cccc-000000002003','cc000000-0000-0000-0003-000000002003','c0a8018d-9c9e-14a8-819c-9e367b22001d'),
  ('cc000000-0000-0000-cccc-000000002004','cc000000-0000-0000-0003-000000002004','c0a8018d-9c9e-14a8-819c-9e367b22001c'),
  ('cc000000-0000-0000-cccc-000000002005','cc000000-0000-0000-0003-000000002005','c0a8018d-9c9e-14a8-819c-9e367b22001d')
ON CONFLICT DO NOTHING;

-- Rating (single subject) for SC
INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
  ('cc000000-0000-0000-0004-000000002001','cc000000-0000-0000-0000-000000002001','c0a8018d-9c9e-14a8-819c-9e37074e001f','RATING',1,TRUE,'2026-02-22 09:07:00'),
  ('cc000000-0000-0000-0004-000000002002','cc000000-0000-0000-0000-000000002002','c0a8018d-9c9e-14a8-819c-9e37074e001f','RATING',1,TRUE,'2026-02-22 10:07:00'),
  ('cc000000-0000-0000-0004-000000002003','cc000000-0000-0000-0000-000000002003','c0a8018d-9c9e-14a8-819c-9e37074e001f','RATING',1,TRUE,'2026-02-22 11:07:00'),
  ('cc000000-0000-0000-0004-000000002004','cc000000-0000-0000-0000-000000002004','c0a8018d-9c9e-14a8-819c-9e37074e001f','RATING',1,TRUE,'2026-02-22 13:07:00'),
  ('cc000000-0000-0000-0004-000000002005','cc000000-0000-0000-0000-000000002005','c0a8018d-9c9e-14a8-819c-9e37074e001f','RATING',1,TRUE,'2026-02-22 14:07:00')
ON CONFLICT DO NOTHING;
INSERT INTO answer_rating (id, submission_id, subject_label, stars, max_stars) VALUES
  ('cc000000-0000-0000-dddd-000000002001','cc000000-0000-0000-0004-000000002001','Overall',3,5),
  ('cc000000-0000-0000-dddd-000000002002','cc000000-0000-0000-0004-000000002002','Overall',4,5),
  ('cc000000-0000-0000-dddd-000000002003','cc000000-0000-0000-0004-000000002003','Overall',2,5),
  ('cc000000-0000-0000-dddd-000000002004','cc000000-0000-0000-0004-000000002004','Overall',5,5),
  ('cc000000-0000-0000-dddd-000000002005','cc000000-0000-0000-0004-000000002005','Overall',3,5)
ON CONFLICT DO NOTHING;

-- Multi-rating for SC
INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
  ('cc000000-0000-0000-0005-000000002001','cc000000-0000-0000-0000-000000002001','c0a8018d-9c9e-14a8-819c-9e3787c70021','RATING',1,TRUE,'2026-02-22 09:09:00'),
  ('cc000000-0000-0000-0005-000000002002','cc000000-0000-0000-0000-000000002002','c0a8018d-9c9e-14a8-819c-9e3787c70021','RATING',1,TRUE,'2026-02-22 10:09:00'),
  ('cc000000-0000-0000-0005-000000002003','cc000000-0000-0000-0000-000000002003','c0a8018d-9c9e-14a8-819c-9e3787c70021','RATING',1,TRUE,'2026-02-22 11:09:00'),
  ('cc000000-0000-0000-0005-000000002004','cc000000-0000-0000-0000-000000002004','c0a8018d-9c9e-14a8-819c-9e3787c70021','RATING',1,TRUE,'2026-02-22 13:09:00'),
  ('cc000000-0000-0000-0005-000000002005','cc000000-0000-0000-0000-000000002005','c0a8018d-9c9e-14a8-819c-9e3787c70021','RATING',1,TRUE,'2026-02-22 14:09:00')
ON CONFLICT DO NOTHING;
INSERT INTO answer_rating (id, submission_id, subject_label, stars, max_stars) VALUES
  ('cc000000-0000-0000-eeee-000000002001','cc000000-0000-0000-0005-000000002001','first rating',3,5),
  ('cc000000-0000-0000-ffff-000000002001','cc000000-0000-0000-0005-000000002001','second rating',2,5),
  ('cc000000-0000-0000-eeee-000000002002','cc000000-0000-0000-0005-000000002002','first rating',4,5),
  ('cc000000-0000-0000-ffff-000000002002','cc000000-0000-0000-0005-000000002002','second rating',5,5),
  ('cc000000-0000-0000-eeee-000000002003','cc000000-0000-0000-0005-000000002003','first rating',2,5),
  ('cc000000-0000-0000-ffff-000000002003','cc000000-0000-0000-0005-000000002003','second rating',3,5),
  ('cc000000-0000-0000-eeee-000000002004','cc000000-0000-0000-0005-000000002004','first rating',5,5),
  ('cc000000-0000-0000-ffff-000000002004','cc000000-0000-0000-0005-000000002004','second rating',4,5),
  ('cc000000-0000-0000-eeee-000000002005','cc000000-0000-0000-0005-000000002005','first rating',3,5),
  ('cc000000-0000-0000-ffff-000000002005','cc000000-0000-0000-0005-000000002005','second rating',2,5)
ON CONFLICT DO NOTHING;


-- ============================================================
-- 6. ANONYMOUS COMPLETED SESSIONS (8 sessions)
--    Linked to the Edge Group org unit via anon_identity
-- ============================================================

INSERT INTO anon_identity
  (id, org_unit_id, survey_id, token, window_start, window_end, sequence_in_window)
VALUES
  ('dd000000-0000-0000-0000-000000000001','aa000000-0000-0000-0000-000000000001','c0a8018d-9c9e-14a8-819c-9e358ee70015','anon-seed-001','2026-02-23 09:00:00','2026-02-23 10:30:00',1),
  ('dd000000-0000-0000-0000-000000000002','aa000000-0000-0000-0000-000000000001','c0a8018d-9c9e-14a8-819c-9e358ee70015','anon-seed-002','2026-02-23 09:00:00','2026-02-23 10:30:00',2),
  ('dd000000-0000-0000-0000-000000000003','aa000000-0000-0000-0000-000000000001','c0a8018d-9c9e-14a8-819c-9e358ee70015','anon-seed-003','2026-02-23 09:00:00','2026-02-23 10:30:00',3),
  ('dd000000-0000-0000-0000-000000000004','aa000000-0000-0000-0000-000000000002','c0a8018d-9c9e-14a8-819c-9e358ee70015','anon-seed-004','2026-02-23 11:00:00','2026-02-23 12:30:00',1),
  ('dd000000-0000-0000-0000-000000000005','aa000000-0000-0000-0000-000000000002','c0a8018d-9c9e-14a8-819c-9e358ee70015','anon-seed-005','2026-02-23 11:00:00','2026-02-23 12:30:00',2),
  ('dd000000-0000-0000-0000-000000000006','aa000000-0000-0000-0000-000000000003','c0a8018d-9c9e-14a8-819c-9e358ee70015','anon-seed-006','2026-02-23 13:00:00','2026-02-23 14:30:00',1),
  ('dd000000-0000-0000-0000-000000000007','aa000000-0000-0000-0000-000000000003','c0a8018d-9c9e-14a8-819c-9e358ee70015','anon-seed-007','2026-02-23 13:00:00','2026-02-23 14:30:00',2),
  ('dd000000-0000-0000-0000-000000000008','aa000000-0000-0000-0000-000000000004','c0a8018d-9c9e-14a8-819c-9e358ee70015','anon-seed-008','2026-02-24 09:00:00','2026-02-24 10:30:00',1)
ON CONFLICT DO NOTHING;

INSERT INTO response_session (id, survey_id, anon_identity_id, is_anonymous, started_at, completed_at)
VALUES
  ('cc000000-0000-0000-0000-000000003001','c0a8018d-9c9e-14a8-819c-9e358ee70015','dd000000-0000-0000-0000-000000000001',TRUE,'2026-02-23 09:05:00','2026-02-23 09:22:00'),
  ('cc000000-0000-0000-0000-000000003002','c0a8018d-9c9e-14a8-819c-9e358ee70015','dd000000-0000-0000-0000-000000000002',TRUE,'2026-02-23 09:30:00','2026-02-23 09:48:00'),
  ('cc000000-0000-0000-0000-000000003003','c0a8018d-9c9e-14a8-819c-9e358ee70015','dd000000-0000-0000-0000-000000000003',TRUE,'2026-02-23 10:00:00','2026-02-23 10:18:00'),
  ('cc000000-0000-0000-0000-000000003004','c0a8018d-9c9e-14a8-819c-9e358ee70015','dd000000-0000-0000-0000-000000000004',TRUE,'2026-02-23 11:05:00','2026-02-23 11:23:00'),
  ('cc000000-0000-0000-0000-000000003005','c0a8018d-9c9e-14a8-819c-9e358ee70015','dd000000-0000-0000-0000-000000000005',TRUE,'2026-02-23 11:35:00','2026-02-23 11:52:00'),
  ('cc000000-0000-0000-0000-000000003006','c0a8018d-9c9e-14a8-819c-9e358ee70015','dd000000-0000-0000-0000-000000000006',TRUE,'2026-02-23 13:05:00','2026-02-23 13:21:00'),
  ('cc000000-0000-0000-0000-000000003007','c0a8018d-9c9e-14a8-819c-9e358ee70015','dd000000-0000-0000-0000-000000000007',TRUE,'2026-02-23 13:40:00','2026-02-23 13:57:00'),
  ('cc000000-0000-0000-0000-000000003008','c0a8018d-9c9e-14a8-819c-9e358ee70015','dd000000-0000-0000-0000-000000000008',TRUE,'2026-02-24 09:05:00','2026-02-24 09:22:00')
ON CONFLICT DO NOTHING;

-- Scale answers for anonymous sessions (values: 5,4,3,5,4,3,2,4)
INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
  ('cc000000-0000-0000-0001-000000003001','cc000000-0000-0000-0000-000000003001','c0a8018d-9c9e-14a8-819c-9e35de070019','SCALE',1,TRUE,'2026-02-23 09:06:00'),
  ('cc000000-0000-0000-0001-000000003002','cc000000-0000-0000-0000-000000003002','c0a8018d-9c9e-14a8-819c-9e35de070019','SCALE',1,TRUE,'2026-02-23 09:31:00'),
  ('cc000000-0000-0000-0001-000000003003','cc000000-0000-0000-0000-000000003003','c0a8018d-9c9e-14a8-819c-9e35de070019','SCALE',1,TRUE,'2026-02-23 10:01:00'),
  ('cc000000-0000-0000-0001-000000003004','cc000000-0000-0000-0000-000000003004','c0a8018d-9c9e-14a8-819c-9e35de070019','SCALE',1,TRUE,'2026-02-23 11:06:00'),
  ('cc000000-0000-0000-0001-000000003005','cc000000-0000-0000-0000-000000003005','c0a8018d-9c9e-14a8-819c-9e35de070019','SCALE',1,TRUE,'2026-02-23 11:36:00'),
  ('cc000000-0000-0000-0001-000000003006','cc000000-0000-0000-0000-000000003006','c0a8018d-9c9e-14a8-819c-9e35de070019','SCALE',1,TRUE,'2026-02-23 13:06:00'),
  ('cc000000-0000-0000-0001-000000003007','cc000000-0000-0000-0000-000000003007','c0a8018d-9c9e-14a8-819c-9e35de070019','SCALE',1,TRUE,'2026-02-23 13:41:00'),
  ('cc000000-0000-0000-0001-000000003008','cc000000-0000-0000-0000-000000003008','c0a8018d-9c9e-14a8-819c-9e35de070019','SCALE',1,TRUE,'2026-02-24 09:06:00')
ON CONFLICT DO NOTHING;
INSERT INTO answer_scale (id, submission_id, value, min_value, max_value) VALUES
  ('cc000000-0000-0000-aaaa-000000003001','cc000000-0000-0000-0001-000000003001',5,1,5),
  ('cc000000-0000-0000-aaaa-000000003002','cc000000-0000-0000-0001-000000003002',4,1,5),
  ('cc000000-0000-0000-aaaa-000000003003','cc000000-0000-0000-0001-000000003003',3,1,5),
  ('cc000000-0000-0000-aaaa-000000003004','cc000000-0000-0000-0001-000000003004',5,1,5),
  ('cc000000-0000-0000-aaaa-000000003005','cc000000-0000-0000-0001-000000003005',4,1,5),
  ('cc000000-0000-0000-aaaa-000000003006','cc000000-0000-0000-0001-000000003006',3,1,5),
  ('cc000000-0000-0000-aaaa-000000003007','cc000000-0000-0000-0001-000000003007',2,1,5),
  ('cc000000-0000-0000-aaaa-000000003008','cc000000-0000-0000-0001-000000003008',4,1,5)
ON CONFLICT DO NOTHING;


-- ============================================================
-- 7. IN-PROGRESS SESSIONS (3 — no completed_at)
-- ============================================================
INSERT INTO response_session (id, survey_id, user_id, is_anonymous, started_at, completed_at)
VALUES
  ('cc000000-0000-0000-0000-000000004001','c0a8018d-9c9e-14a8-819c-9e358ee70015','bb000000-0000-0000-0000-000000000002',FALSE,'2026-02-27 10:00:00',NULL),
  ('cc000000-0000-0000-0000-000000004002','c0a8018d-9c9e-14a8-819c-9e358ee70015','bb000000-0000-0000-0000-000000000003',FALSE,'2026-02-27 11:00:00',NULL),
  ('cc000000-0000-0000-0000-000000004003','c0a8018d-9c9e-14a8-819c-9e358ee70015','bb000000-0000-0000-0000-000000000011',FALSE,'2026-02-27 12:00:00',NULL)
ON CONFLICT DO NOTHING;

-- Partial answers for in-progress sessions
INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at) VALUES
  ('cc000000-0000-0000-0001-000000004001','cc000000-0000-0000-0000-000000004001','c0a8018d-9c9e-14a8-819c-9e35de070019','SCALE',1,TRUE,'2026-02-27 10:01:00'),
  ('cc000000-0000-0000-0001-000000004002','cc000000-0000-0000-0000-000000004002','c0a8018d-9c9e-14a8-819c-9e35de070019','SCALE',1,TRUE,'2026-02-27 11:01:00'),
  ('cc000000-0000-0000-0001-000000004003','cc000000-0000-0000-0000-000000004003','c0a8018d-9c9e-14a8-819c-9e35de070019','SCALE',1,TRUE,'2026-02-27 12:01:00')
ON CONFLICT DO NOTHING;
INSERT INTO answer_scale (id, submission_id, value, min_value, max_value) VALUES
  ('cc000000-0000-0000-aaaa-000000004001','cc000000-0000-0000-0001-000000004001',4,1,5),
  ('cc000000-0000-0000-aaaa-000000004002','cc000000-0000-0000-0001-000000004002',3,1,5),
  ('cc000000-0000-0000-aaaa-000000004003','cc000000-0000-0000-0001-000000004003',5,1,5)
ON CONFLICT DO NOTHING;


-- ============================================================
-- Summary after this seed:
--   Completed sessions: 10 identified (BE+SC) + 8 anonymous + 2 pre-existing = 20
--   Identified sessions: 11  Anonymous sessions: 8  In-progress: 3+existing
--   Scale scores (identified): 4,2,5,3,4 | 3,4,2,5,3  (avg ≈ 3.5)
--   Scale scores (anon):       5,4,3,5,4,3,2,4         (avg ≈ 3.75)
--   Choice "label one": BE=3, SC=2 (total 5)
--   Choice "🏷️ two":   BE=2, SC=3 (total 5)
--   Backend pathPrefix: /aa000000-.../aa000000-...-002
--   Backend sessions: 5 identified in TECH-BE
--   Ops sessions: 5 identified in OPS-SC
-- ============================================================

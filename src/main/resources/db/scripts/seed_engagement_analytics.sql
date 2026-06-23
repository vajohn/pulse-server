-- ============================================================================
-- seed_engagement_analytics.sql  (PULSE-WEB-4)
--
-- Seeds completed response sessions + current SCALE and RATING/MULTI_RATING
-- answers so the org-wide engagement endpoint
-- (GET /api/admin/analytics/engagement) returns a real, non-masked, COMPOSITE
-- result (>= 5 distinct respondents) for manual curl verification.
--
-- C-1 composite: the engagement score combines BOTH answer sources, each
-- normalized to a common 1..5 scale:
--   * SCALE answers   (answer_scale)  — value in [min_value,max_value]  → 1..5
--   * RATING answers  (answer_rating) — stars in [1,max_stars]          → 1..5
--     (MULTI_RATING rows are averaged per submission first, then normalized)
--
-- M-5: FORM TYPE. RATING is sourced from FormType.SURVEY forms. This script
-- therefore seeds onto the SURVEY form below (NOT a PSYCHOMETRIC form). The
-- SCALE source today also includes psychometric SCALE answers in production;
-- here we deliberately seed SCALE answers onto the SAME SURVEY form so the
-- manual check exercises the composite without depending on psychometric data.
--
-- Form used: eeee0002-0000-0000-0000-000000000001
--            title "Engagement Pulse (E2E Seed)", type = SURVEY
--            (has 2 existing SCALE questions; this script ADDS one RATING
--             question, id cccc0002-..., for the RATING source.)
-- Scope used: org unit path
--   /EDGE_GROUP_ROOT/SF_G_10000/SF_E_1000/SF_OU_10001182
--
-- Idempotent: re-running first deletes everything it previously inserted,
-- matched by the deterministic id prefixes 'dddddddd-' (answers/sessions/subs)
-- and 'cccc0002-' (the seeded RATING question), then re-inserts.
--
-- Two windows are seeded so the trend metric is exercised:
--   * current period  : completed_at = now() - 2 days   (higher scores)
--   * previous period : completed_at = now() - 40 days   (lower scores → UP)
--
-- Usage:
--   docker exec -i mahara_postgres psql -U postgres -d pulse \
--     < src/main/resources/db/scripts/seed_engagement_analytics.sql
-- ============================================================================

BEGIN;

-- Clean up prior runs of THIS seed only (deterministic id prefixes).
DELETE FROM answer_scale       WHERE id::text LIKE 'dddddddd-%';
DELETE FROM answer_rating      WHERE id::text LIKE 'dddddddd-%';
DELETE FROM answer_submission  WHERE id::text LIKE 'dddddddd-%';
DELETE FROM response_session   WHERE id::text LIKE 'dddddddd-%';
DELETE FROM question           WHERE id::text LIKE 'cccc0002-%';

-- Add a RATING question to the SURVEY form so the RATING source has data.
-- 5-star scale → normalization is the identity (stars 1..5 ≡ 1..5).
INSERT INTO question (id, form_id, body, question_type, display_order, created_at, updated_at, subject_labels)
VALUES (
    'cccc0002-0000-0000-0000-000000000001',
    'eeee0002-0000-0000-0000-000000000001',
    'Rate your overall experience',
    'RATING', 3, now(), now(), NULL
);

WITH
target_users AS (
    SELECT u.id AS user_id, row_number() OVER (ORDER BY u.id) AS rn
    FROM users u
    JOIN organizational_units ou ON ou.id = u.org_unit_id
    WHERE u.active = true
      AND ou.path = '/EDGE_GROUP_ROOT/SF_G_10000/SF_E_1000/SF_OU_10001182'
    LIMIT 6
),
scale_qs AS (
    SELECT id AS question_id, row_number() OVER (ORDER BY display_order) AS qn
    FROM question
    WHERE form_id = 'eeee0002-0000-0000-0000-000000000001'
      AND question_type = 'SCALE'
),
-- one session per (user, window)
sessions AS (
    SELECT
        ('dddddddd-0000-0000-' || lpad(tu.rn::text, 4, '0') || '-' ||
         lpad((w.win_idx)::text, 12, '0'))::uuid AS session_id,
        tu.user_id,
        w.completed_at,
        tu.rn, w.win_idx, w.base_score
    FROM target_users tu
    CROSS JOIN (VALUES
        (1, now() - interval '2 days',  4),   -- current period, higher avg
        (2, now() - interval '40 days', 3)    -- previous period, lower avg
    ) AS w(win_idx, completed_at, base_score)
),
ins_session AS (
    INSERT INTO response_session (id, form_id, user_id, is_anonymous, started_at, completed_at)
    SELECT session_id, 'eeee0002-0000-0000-0000-000000000001', user_id, false,
           completed_at - interval '5 minutes', completed_at
    FROM sessions
    RETURNING id
),
-- SCALE submissions (one per scale question per session)
scale_submissions AS (
    SELECT
        ('dddddddd-1111-0000-' || lpad(s.rn::text, 4, '0') || '-' ||
         lpad((s.win_idx * 10 + sq.qn)::text, 12, '0'))::uuid AS submission_id,
        s.session_id, sq.question_id,
        LEAST(5, GREATEST(1, s.base_score + ((s.rn + sq.qn) % 2))) AS score
    FROM sessions s
    CROSS JOIN scale_qs sq
),
ins_scale_submission AS (
    INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at)
    SELECT submission_id, session_id, question_id, 'SCALE', 1, true, now()
    FROM scale_submissions
    RETURNING id
),
ins_scale AS (
    INSERT INTO answer_scale (id, submission_id, value, min_value, max_value)
    SELECT
        ('dddddddd-2222-0000-0000-' || lpad(row_number() OVER (ORDER BY submission_id)::text, 12, '0'))::uuid,
        submission_id, score, 1, 5
    FROM scale_submissions
    RETURNING id
),
-- RATING submissions (one per session on the seeded RATING question)
rating_submissions AS (
    SELECT
        ('dddddddd-3333-0000-' || lpad(s.rn::text, 4, '0') || '-' ||
         lpad((s.win_idx)::text, 12, '0'))::uuid AS submission_id,
        s.session_id,
        LEAST(5, GREATEST(1, s.base_score + (s.rn % 2))) AS stars
    FROM sessions s
),
ins_rating_submission AS (
    INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at)
    SELECT submission_id, session_id, 'cccc0002-0000-0000-0000-000000000001', 'RATING', 1, true, now()
    FROM rating_submissions
    RETURNING id
)
INSERT INTO answer_rating (id, submission_id, subject_label, stars, max_stars)
SELECT
    ('dddddddd-4444-0000-0000-' || lpad(row_number() OVER (ORDER BY submission_id)::text, 12, '0'))::uuid,
    submission_id, 'overall', stars, 5
FROM rating_submissions;

COMMIT;

-- Refresh analytics MVs (the engagement endpoint uses live queries, but other
-- analytics endpoints read these; refreshing keeps the DB consistent for testing).
REFRESH MATERIALIZED VIEW mv_analytics_summary;
REFRESH MATERIALIZED VIEW mv_question_scale_distribution;

-- Sanity output
SELECT 'seeded sessions' AS what, count(*) AS n FROM response_session WHERE id::text LIKE 'dddddddd-%'
UNION ALL
SELECT 'seeded scale answers',  count(*) FROM answer_scale  WHERE id::text LIKE 'dddddddd-%'
UNION ALL
SELECT 'seeded rating answers', count(*) FROM answer_rating WHERE id::text LIKE 'dddddddd-%';

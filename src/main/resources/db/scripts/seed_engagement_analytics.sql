-- ============================================================================
-- seed_engagement_analytics.sql  (PULSE-WEB-4)
--
-- Seeds completed survey response sessions + current SCALE answers so the
-- org-wide engagement endpoint (GET /api/admin/analytics/engagement) returns a
-- real, non-masked result (>= 5 respondents) for manual curl verification.
--
-- Scope used: org unit SF_OU_10001182
--   path = /EDGE_GROUP_ROOT/SF_G_10000/SF_E_1000/SF_OU_10001182
-- Form used: eeee0001-0000-0000-0000-000000000001 (3 SCALE questions).
--
-- Idempotent: re-running deletes the rows it previously inserted (matched by the
-- deterministic id prefix 'dddddddd-') before re-inserting.
--
-- Seeds two windows for the trend metric:
--   * current period  : completed_at = now()-2 days  (avg ~4.x)
--   * previous period : completed_at = now()-40 days (avg ~3.x, lower → trend UP)
--
-- Usage:
--   docker exec -i mahara_postgres psql -U postgres -d pulse \
--     < src/main/resources/db/scripts/seed_engagement_analytics.sql
-- ============================================================================

BEGIN;

-- Clean up prior runs of THIS seed only (deterministic id prefix).
DELETE FROM answer_scale       WHERE id::text LIKE 'dddddddd-%';
DELETE FROM answer_submission  WHERE id::text LIKE 'dddddddd-%';
DELETE FROM response_session   WHERE id::text LIKE 'dddddddd-%';

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
    WHERE form_id = 'eeee0001-0000-0000-0000-000000000001'
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
    SELECT session_id, 'eeee0001-0000-0000-0000-000000000001', user_id, false,
           completed_at - interval '5 minutes', completed_at
    FROM sessions
    RETURNING id
),
submissions AS (
    SELECT
        ('dddddddd-1111-0000-' || lpad(s.rn::text, 4, '0') || '-' ||
         lpad((s.win_idx * 10 + sq.qn)::text, 12, '0'))::uuid AS submission_id,
        s.session_id, sq.question_id,
        -- vary the score a little per user/question but keep window averages distinct
        LEAST(5, GREATEST(1, s.base_score + ((s.rn + sq.qn) % 2))) AS score
    FROM sessions s
    CROSS JOIN scale_qs sq
),
ins_submission AS (
    INSERT INTO answer_submission (id, session_id, question_id, answer_type, version, is_current, submitted_at)
    SELECT submission_id, session_id, question_id, 'SCALE', 1, true, now()
    FROM submissions
    RETURNING id
)
INSERT INTO answer_scale (id, submission_id, value, min_value, max_value)
SELECT
    ('dddddddd-2222-0000-0000-' || lpad(row_number() OVER (ORDER BY submission_id)::text, 12, '0'))::uuid,
    submission_id, score, 1, 5
FROM submissions;

COMMIT;

-- Refresh analytics MVs (the engagement endpoint uses live queries, but other
-- analytics endpoints read these; refreshing keeps the DB consistent for testing).
REFRESH MATERIALIZED VIEW mv_analytics_summary;
REFRESH MATERIALIZED VIEW mv_question_scale_distribution;

-- Sanity output
SELECT 'seeded sessions' AS what, count(*) FROM response_session WHERE id::text LIKE 'dddddddd-%'
UNION ALL
SELECT 'seeded scale answers', count(*) FROM answer_scale WHERE id::text LIKE 'dddddddd-%';

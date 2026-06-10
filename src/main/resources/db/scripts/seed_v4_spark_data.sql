-- ─────────────────────────────────────────────────────────────────
-- Spark Rewards Test Seed Data  (Phase 1 of spark-test-plan.md)
-- Safe to re-run: uses INSERT ... ON CONFLICT DO NOTHING
-- ─────────────────────────────────────────────────────────────────

-- ── Period constants ─────────────────────────────────────────────
-- Period 1 (existing):  c0a8018d-9ca6-179d-819c-a64b0c790004  NOMINATION_OPEN
-- Period 2 (new seed):  a0000000-0000-0000-0000-000000000001  ANNOUNCED

-- ── User UUIDs (from dev DB) ─────────────────────────────────────
-- hr.admin        bb000000-0000-0000-0000-000000000001
-- sara.tech       bb000000-0000-0000-0000-000000000002  (MANAGER / SPARK_VOTE)
-- khalid.ops      bb000000-0000-0000-0000-000000000003  (MANAGER / SPARK_VOTE)
-- omar.be         bb000000-0000-0000-0000-000000000011  (EMPLOYEE / SPARK_NOMINATE)
-- layla.be        bb000000-0000-0000-0000-000000000012  (EMPLOYEE / SPARK_NOMINATE)
-- noura.be        bb000000-0000-0000-0000-000000000013  (EMPLOYEE / SPARK_NOMINATE)
-- youssef.be      bb000000-0000-0000-0000-000000000015  (EMPLOYEE)
-- ahmed.be        bb000000-0000-0000-0000-000000000014  (EMPLOYEE)

-- ─────────────────────────────────────────────────────────────────
-- 1. Award period in ANNOUNCED status (for winner display tests)
-- ─────────────────────────────────────────────────────────────────
INSERT INTO award_periods (
    id, name, nomination_start, nomination_end,
    voting_start, voting_end, status,
    eligible_entities, award_amount, created_at, created_by
)
VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'Q3 2025 Awards',
    '2025-07-01 00:00:00',
    '2025-07-31 23:59:59',
    '2025-08-01 00:00:00',
    '2025-08-15 23:59:59',
    'ANNOUNCED',
    '{}',
    500,
    now(),
    'bb000000-0000-0000-0000-000000000001'
)
ON CONFLICT (id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────
-- 2. Nominations for NOMINATION_OPEN period (Period 1)
--    Each nominator can nominate once per category per period.
--    IDs use fixed UUIDs to allow idempotent re-seeding.
-- ─────────────────────────────────────────────────────────────────

-- khalid.ops nominates omar.be for PROACTIVE
INSERT INTO nominations (
    id, award_period_id, category_id, nominator_id, nominee_id,
    justification, status, submitted_at
)
VALUES (
    'b1000000-0000-0000-0000-000000000001',
    'c0a8018d-9ca6-179d-819c-a64b0c790004',
    'PROACTIVE',
    'bb000000-0000-0000-0000-000000000003',
    'bb000000-0000-0000-0000-000000000011',
    'Omar consistently takes initiative, identifies issues before they escalate, and delivers solutions without being asked.',
    'SUBMITTED',
    now()
)
ON CONFLICT (id) DO NOTHING;

-- sara.tech nominates layla.be for CANDO
INSERT INTO nominations (
    id, award_period_id, category_id, nominator_id, nominee_id,
    justification, status, submitted_at
)
VALUES (
    'b1000000-0000-0000-0000-000000000002',
    'c0a8018d-9ca6-179d-819c-a64b0c790004',
    'CANDO',
    'bb000000-0000-0000-0000-000000000002',
    'bb000000-0000-0000-0000-000000000012',
    'Layla never says no to a challenge. She took on three projects simultaneously and delivered all on time.',
    'SUBMITTED',
    now()
)
ON CONFLICT (id) DO NOTHING;

-- khalid.ops nominates noura.be for COLLABORATIVE
INSERT INTO nominations (
    id, award_period_id, category_id, nominator_id, nominee_id,
    justification, status, submitted_at
)
VALUES (
    'b1000000-0000-0000-0000-000000000003',
    'c0a8018d-9ca6-179d-819c-a64b0c790004',
    'COLLABORATIVE',
    'bb000000-0000-0000-0000-000000000003',
    'bb000000-0000-0000-0000-000000000013',
    'Noura bridges teams seamlessly. She set up cross-functional workshops that reduced integration time by 40%.',
    'SUBMITTED',
    now()
)
ON CONFLICT (id) DO NOTHING;

-- sara.tech nominates ahmed.be for ACTIONDRIVER
INSERT INTO nominations (
    id, award_period_id, category_id, nominator_id, nominee_id,
    justification, status, submitted_at
)
VALUES (
    'b1000000-0000-0000-0000-000000000004',
    'c0a8018d-9ca6-179d-819c-a64b0c790004',
    'ACTIONDRIVER',
    'bb000000-0000-0000-0000-000000000002',
    'bb000000-0000-0000-0000-000000000014',
    'Ahmed drives results every sprint. He sets clear targets, tracks KPIs, and holds himself and the team accountable.',
    'SUBMITTED',
    now()
)
ON CONFLICT (id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────
-- 3. Nominations for ANNOUNCED period (Period 2) — status WINNER / NOT_SELECTED
-- ─────────────────────────────────────────────────────────────────

-- Nomination: youssef.be for PROACTIVE (WINNER)
INSERT INTO nominations (
    id, award_period_id, category_id, nominator_id, nominee_id,
    justification, status, submitted_at
)
VALUES (
    'b2000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000001',
    'PROACTIVE',
    'bb000000-0000-0000-0000-000000000002',
    'bb000000-0000-0000-0000-000000000015',
    'Youssef consistently anticipates risks and resolves them ahead of time.',
    'WINNER',
    '2025-07-10 10:00:00'
)
ON CONFLICT (id) DO NOTHING;

-- Nomination: layla.be for PROACTIVE (NOT_SELECTED)
INSERT INTO nominations (
    id, award_period_id, category_id, nominator_id, nominee_id,
    justification, status, submitted_at
)
VALUES (
    'b2000000-0000-0000-0000-000000000002',
    'a0000000-0000-0000-0000-000000000001',
    'PROACTIVE',
    'bb000000-0000-0000-0000-000000000003',
    'bb000000-0000-0000-0000-000000000012',
    'Layla demonstrates strong proactive behavior in her team.',
    'NOT_SELECTED',
    '2025-07-12 10:00:00'
)
ON CONFLICT (id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────
-- 4. Leader votes for ANNOUNCED period
--    LeaderVote.nominee_id → nominations.id  (NOT users.id)
-- ─────────────────────────────────────────────────────────────────

-- sara.tech voted for youssef.be (via nomination b2000000-...-0001) in PROACTIVE
INSERT INTO leader_votes (
    id, award_period_id, category_id, leader_id, nominee_id,
    endorsement_comment, voted_at, updated_at
)
VALUES (
    'c1000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000001',
    'PROACTIVE',
    'bb000000-0000-0000-0000-000000000002',
    'b2000000-0000-0000-0000-000000000001',
    'Youssef is the epitome of proactivity on our team.',
    '2025-08-05 14:00:00',
    '2025-08-05 14:00:00'
)
ON CONFLICT (id) DO NOTHING;

-- khalid.ops voted for youssef.be (via nomination b2000000-...-0001) in PROACTIVE
INSERT INTO leader_votes (
    id, award_period_id, category_id, leader_id, nominee_id,
    endorsement_comment, voted_at, updated_at
)
VALUES (
    'c1000000-0000-0000-0000-000000000002',
    'a0000000-0000-0000-0000-000000000001',
    'PROACTIVE',
    'bb000000-0000-0000-0000-000000000003',
    'b2000000-0000-0000-0000-000000000001',
    'Agreed. Youssef sets the standard for everyone.',
    '2025-08-06 09:00:00',
    '2025-08-06 09:00:00'
)
ON CONFLICT (id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────
-- 5. SparkWinner for ANNOUNCED period
-- ─────────────────────────────────────────────────────────────────

INSERT INTO spark_winners (
    id, award_period_id, category_id, winner_id,
    vote_count, hr_justification, finalized_by, finalized_at, announced_at, award_points
)
VALUES (
    'd1000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000001',
    'PROACTIVE',
    'bb000000-0000-0000-0000-000000000015',
    2,
    'Unanimous choice by all leaders this quarter.',
    'bb000000-0000-0000-0000-000000000001',
    '2025-08-20 12:00:00',
    '2025-08-21 09:00:00',
    500
)
ON CONFLICT (id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────
-- 6. SparkCongratulation on the winner
-- ─────────────────────────────────────────────────────────────────

INSERT INTO spark_congratulations (
    id, winner_id, award_period_id, user_id,
    reaction, message, created_at
)
VALUES (
    'e1000000-0000-0000-0000-000000000001',
    'd1000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000001',
    'bb000000-0000-0000-0000-000000000011',
    '🎉',
    'Well deserved, Youssef! Keep it up!',
    '2025-08-21 10:00:00'
)
ON CONFLICT (id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────
-- Verify row counts
-- ─────────────────────────────────────────────────────────────────
SELECT 'award_periods'       AS "table", COUNT(*) FROM award_periods
UNION ALL
SELECT 'nominations'         AS "table", COUNT(*) FROM nominations
UNION ALL
SELECT 'leader_votes'        AS "table", COUNT(*) FROM leader_votes
UNION ALL
SELECT 'spark_winners'       AS "table", COUNT(*) FROM spark_winners
UNION ALL
SELECT 'spark_congratulations' AS "table", COUNT(*) FROM spark_congratulations;

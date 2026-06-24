-- V7: Scoring-engine deltas — decimal STEN, T-scores, parameterized standardization,
-- composite definitions, norm/item strategy selectors, result-state + validity.

-- 1. Decimal STEN (was INT) + persist T-score
-- mv_psychometric_scale_stats references scale_score.sten_score; drop before ALTER, recreate after.
DROP MATERIALIZED VIEW IF EXISTS mv_psychometric_scale_stats;
ALTER TABLE scale_score  ALTER COLUMN sten_score TYPE NUMERIC(4,2);
ALTER TABLE scale_score  ADD COLUMN  t_score    NUMERIC(6,2);
-- norm_entry has a CHECK constraint referencing sten_score; drop it first, re-add after type change.
ALTER TABLE norm_entry   DROP CONSTRAINT chk_sten;
ALTER TABLE norm_entry   ALTER COLUMN sten_score TYPE NUMERIC(4,2);
ALTER TABLE norm_entry   ADD  CONSTRAINT chk_sten CHECK (sten_score IS NULL OR (sten_score >= 1 AND sten_score <= 10));
-- Recreate the materialized view with decimal sten_score (integer bucket equality works on NUMERIC).
CREATE MATERIALIZED VIEW mv_psychometric_scale_stats AS
  SELECT tr.test_id,
         ss.scale_id,
         count(ss.id) AS result_count,
         round(avg(ss.raw_score), 3) AS avg_raw_score,
         round(avg(ss.sten_score), 2) AS avg_sten,
         round(avg(ss.percentile), 2) AS avg_percentile,
         round(COALESCE(stddev(ss.raw_score), 0::numeric), 3) AS stddev_raw_score,
         sum(CASE WHEN ss.sten_score >= 1 AND ss.sten_score < 2  THEN 1 ELSE 0 END) AS sten_1_count,
         sum(CASE WHEN ss.sten_score >= 2 AND ss.sten_score < 3  THEN 1 ELSE 0 END) AS sten_2_count,
         sum(CASE WHEN ss.sten_score >= 3 AND ss.sten_score < 4  THEN 1 ELSE 0 END) AS sten_3_count,
         sum(CASE WHEN ss.sten_score >= 4 AND ss.sten_score < 5  THEN 1 ELSE 0 END) AS sten_4_count,
         sum(CASE WHEN ss.sten_score >= 5 AND ss.sten_score < 6  THEN 1 ELSE 0 END) AS sten_5_count,
         sum(CASE WHEN ss.sten_score >= 6 AND ss.sten_score < 7  THEN 1 ELSE 0 END) AS sten_6_count,
         sum(CASE WHEN ss.sten_score >= 7 AND ss.sten_score < 8  THEN 1 ELSE 0 END) AS sten_7_count,
         sum(CASE WHEN ss.sten_score >= 8 AND ss.sten_score < 9  THEN 1 ELSE 0 END) AS sten_8_count,
         sum(CASE WHEN ss.sten_score >= 9 AND ss.sten_score < 10 THEN 1 ELSE 0 END) AS sten_9_count,
         sum(CASE WHEN ss.sten_score = 10                        THEN 1 ELSE 0 END) AS sten_10_count
    FROM scale_score ss
    JOIN test_result tr ON tr.id = ss.result_id
   WHERE tr.status::text = ANY (ARRAY['SCORED'::character varying, 'REVIEWED'::character varying]::text[])
   GROUP BY tr.test_id, ss.scale_id;
CREATE UNIQUE INDEX mv_psychometric_scale_stats_test_id_scale_id_idx
    ON mv_psychometric_scale_stats (test_id, scale_id);

-- 2. Parameterized T-score standardization per scale norm (defaults = personality scale)
ALTER TABLE norm_scale_param ADD COLUMN t_factor  NUMERIC(6,3) NOT NULL DEFAULT 10;
ALTER TABLE norm_scale_param ADD COLUMN t_offset  NUMERIC(6,3) NOT NULL DEFAULT 50;
ALTER TABLE norm_scale_param ADD COLUMN t_clip_lo NUMERIC(6,2) NOT NULL DEFAULT 10;
ALTER TABLE norm_scale_param ADD COLUMN t_clip_hi NUMERIC(6,2) NOT NULL DEFAULT 120;

-- 3. Composite definition on scales (NULL composite_method = leaf scored from items)
ALTER TABLE psychometric_scale ADD COLUMN composite_method VARCHAR(40);
ALTER TABLE psychometric_scale ADD COLUMN composite_basis  VARCHAR(8);

-- 4. Norm strategy selector
ALTER TABLE norm_table_version ADD COLUMN norm_strategy VARCHAR(24) NOT NULL DEFAULT 'PARAMETRIC';

-- 5. Item strategy selector (NULL = derive from question_type at load)
ALTER TABLE scoring_key_item ADD COLUMN item_strategy VARCHAR(24);

-- 6. Result state + validity status (computed in 1A; surfaced/gated in later plans)
ALTER TABLE test_result ADD COLUMN result_state    VARCHAR(20) NOT NULL DEFAULT 'FINAL';
ALTER TABLE test_result ADD COLUMN validity_status VARCHAR(20);

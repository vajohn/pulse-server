-- V11: longitudinal capability profiles + talent analytics (§12).
-- capability_score_history is APPEND-ONLY (one row per scored leaf scale per result);
-- capability_profile_current is a one-row-per-(user,scale) projection of the latest value + trend.
-- A scale-level `restricted` flag (sourced from the import scoring-sheet) lets analytics exclude
-- CWB + validity scales (§6.3) by data, never by name-matching.

-- 1. restricted flag on scales: CWB / validity scales are interpreter-only and NEVER aggregated
--    or trended (D3/§6.3). Default false = ordinary, surfaceable scale.
ALTER TABLE psychometric_scale ADD COLUMN restricted BOOLEAN NOT NULL DEFAULT false;

-- 2. capability_score_history — immutable append log. Every FINAL, VALID, non-restricted leaf
--    scale score is appended with its full norm provenance. Never UPDATEd (D5/§6.5).
CREATE TABLE capability_score_history (
    id                     UUID PRIMARY KEY,
    user_id                UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    scale_id               UUID NOT NULL REFERENCES psychometric_scale(id) ON DELETE CASCADE,
    test_id                UUID NOT NULL REFERENCES psychometric_test(id) ON DELETE CASCADE,
    result_id              UUID NOT NULL REFERENCES test_result(id) ON DELETE CASCADE,
    z_score                NUMERIC(6,3),
    t_score                NUMERIC(6,2),
    sten_score             NUMERIC(4,2),
    percentile             NUMERIC(5,2),
    norm_table_version_id  UUID REFERENCES norm_table_version(id),
    scored_at              TIMESTAMP NOT NULL,
    created_at             TIMESTAMP NOT NULL DEFAULT now(),
    -- one history row per scale per result (idempotent re-scoring guard).
    CONSTRAINT uq_history_scale_result UNIQUE (scale_id, result_id)
);
CREATE INDEX idx_history_user_scale ON capability_score_history(user_id, scale_id);
CREATE INDEX idx_history_scale_scored ON capability_score_history(scale_id, scored_at);

-- 3. capability_profile_current — latest value + trend per (user, scale) (D1).
--    sten_delta = latest.sten - prev.sten (NULL when no prior administration).
--    norm_changed = latest norm version differs from the previous one (D5 flag).
CREATE TABLE capability_profile_current (
    user_id                UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    scale_id               UUID NOT NULL REFERENCES psychometric_scale(id) ON DELETE CASCADE,
    test_id                UUID NOT NULL REFERENCES psychometric_test(id) ON DELETE CASCADE,
    latest_result_id       UUID NOT NULL REFERENCES test_result(id) ON DELETE CASCADE,
    z_score                NUMERIC(6,3),
    t_score                NUMERIC(6,2),
    sten_score             NUMERIC(4,2),
    percentile             NUMERIC(5,2),
    norm_table_version_id  UUID REFERENCES norm_table_version(id),
    scored_at              TIMESTAMP NOT NULL,
    prev_sten_score        NUMERIC(4,2),
    prev_scored_at         TIMESTAMP,
    prev_norm_version_id   UUID REFERENCES norm_table_version(id),
    sten_delta             NUMERIC(5,2),
    norm_changed           BOOLEAN NOT NULL DEFAULT false,
    n_administrations      INT NOT NULL DEFAULT 1,
    updated_at             TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, scale_id)
);
CREATE INDEX idx_profile_current_scale ON capability_profile_current(scale_id);
CREATE INDEX idx_profile_current_test ON capability_profile_current(test_id);

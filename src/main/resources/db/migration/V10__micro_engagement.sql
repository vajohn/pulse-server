-- V10: micro-engagement — admin cadence, per-user item exposure, per-scale accrual progress,
-- and a result_mode selector on scales (IMMEDIATE vs CONSOLIDATED). §11 of the design spec.

-- 1. result_mode on scales: NULL/IMMEDIATE = score when items complete in a session;
--    CONSOLIDATED = accrue across micro-sessions, score only when full item set is answered.
ALTER TABLE psychometric_scale ADD COLUMN result_mode VARCHAR(16) NOT NULL DEFAULT 'IMMEDIATE';

-- 2. assessment_cadence: admin-configured delivery rhythm per test + population (D2).
CREATE TABLE assessment_cadence (
    id                   UUID PRIMARY KEY,
    test_id              UUID NOT NULL REFERENCES psychometric_test(id) ON DELETE CASCADE,
    cadence              VARCHAR(16) NOT NULL,                 -- WEEKLY | MONTHLY
    max_items_per_admin  INT NOT NULL DEFAULT 12,              -- per micro-set (1..15)
    org_unit_id          UUID REFERENCES organizational_units(id),  -- NULL = whole org
    include_children     BOOLEAN NOT NULL DEFAULT true,
    starts_at            TIMESTAMP,                            -- NULL = open-ended start
    ends_at              TIMESTAMP,                            -- NULL = open-ended end
    active               BOOLEAN NOT NULL DEFAULT true,
    created_by           UUID REFERENCES users(id),
    created_at           TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_cadence_max_items CHECK (max_items_per_admin BETWEEN 1 AND 15)
);
CREATE INDEX idx_cadence_test_active ON assessment_cadence(test_id) WHERE active = true;

-- 3. user_item_exposure: which questions a user has already seen (avoids repeats, tracks coverage).
CREATE TABLE user_item_exposure (
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    question_id  UUID NOT NULL REFERENCES question(id) ON DELETE CASCADE,
    test_id      UUID NOT NULL REFERENCES psychometric_test(id) ON DELETE CASCADE,
    first_seen   TIMESTAMP NOT NULL DEFAULT now(),
    answered_at  TIMESTAMP,                                    -- NULL until answered in a completed session
    PRIMARY KEY (user_id, question_id)
);
CREATE INDEX idx_exposure_user_test ON user_item_exposure(user_id, test_id);

-- 4. scale_progress: per (user, scale, window) accrual that drives consolidation (D3) + norm pin (D4).
CREATE TABLE scale_progress (
    id                     UUID PRIMARY KEY,
    user_id                UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    scale_id               UUID NOT NULL REFERENCES psychometric_scale(id) ON DELETE CASCADE,
    test_id                UUID NOT NULL REFERENCES psychometric_test(id) ON DELETE CASCADE,
    window_id              UUID NOT NULL,                      -- groups one consolidation window
    norm_table_version_id  UUID REFERENCES norm_table_version(id),  -- frozen at window open (D4)
    items_required         INT NOT NULL,                       -- full item set for this scale
    items_collected        INT NOT NULL DEFAULT 0,
    state                  VARCHAR(16) NOT NULL DEFAULT 'COLLECTING',
    opened_at              TIMESTAMP NOT NULL DEFAULT now(),
    consolidated_at        TIMESTAMP,
    CONSTRAINT uq_scale_progress_window UNIQUE (user_id, scale_id, window_id)
);
CREATE INDEX idx_scale_progress_user_test ON scale_progress(user_id, test_id);

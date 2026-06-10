-- ============================================================
-- V1: Pulse — Fully Consolidated Schema
-- Canonical state after V1–V21 (flattened 2026-03-09)
--
-- DDL only (no throwaway ALTER/UPDATE/DELETE migration logic).
-- Roles and permissions are seeded here; spark_categories are
-- fixed EDGE DNA reference data.
-- ============================================================

-- ============================================================
-- Reference / Infrastructure tables
-- ============================================================

CREATE TABLE titles (
    id   UUID PRIMARY KEY,
    name VARCHAR(128) UNIQUE NOT NULL
);

CREATE TABLE roles (
    id   UUID PRIMARY KEY,
    name VARCHAR(128) UNIQUE NOT NULL
);

CREATE TABLE permissions (
    id          UUID PRIMARY KEY,
    name        VARCHAR(128) UNIQUE NOT NULL,
    description VARCHAR(256)
);

CREATE TABLE teams (
    id   UUID PRIMARY KEY,
    name VARCHAR(128) UNIQUE NOT NULL
);

CREATE TABLE groups (
    id   UUID PRIMARY KEY,
    name VARCHAR(128) UNIQUE NOT NULL
);

-- ============================================================
-- Organizational structure (hierarchical via path)
-- ============================================================

CREATE TABLE organizational_units (
    id             UUID PRIMARY KEY,
    parent_id      UUID REFERENCES organizational_units(id),
    org_unit_name  VARCHAR(256) NOT NULL,
    org_unit_code  VARCHAR(64) UNIQUE,
    org_level      VARCHAR(32) NOT NULL,
    path           TEXT NOT NULL DEFAULT '',
    depth          INT NOT NULL DEFAULT 0,
    entra_group_id VARCHAR(128),
    active         BOOLEAN NOT NULL DEFAULT TRUE,
    metadata       JSONB,
    -- SF sync columns (V5, V6, V7)
    sf_external_code VARCHAR(128),            -- NOT UNIQUE: same code can appear at multiple levels
    sync_source      VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    company_code     VARCHAR(128),             -- maps to SF custom01 (legal entity)
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_org_units_parent       ON organizational_units(parent_id);
CREATE INDEX idx_org_units_level        ON organizational_units(org_level);
CREATE INDEX idx_org_units_path         ON organizational_units(path);
CREATE INDEX idx_org_units_active       ON organizational_units(active);
CREATE INDEX idx_org_units_sf_code      ON organizational_units(sf_external_code) WHERE sf_external_code IS NOT NULL;
CREATE INDEX idx_org_units_sync_source  ON organizational_units(sync_source);
CREATE INDEX idx_org_units_company_code ON organizational_units(company_code);

-- ============================================================
-- Users and mappings
-- ============================================================

CREATE TABLE users (
    id                  UUID PRIMARY KEY,
    azure_ad_id         VARCHAR(128) UNIQUE,   -- nullable: SF-only users have no Azure AD ID
    email               VARCHAR(256) UNIQUE NOT NULL,
    display_name        VARCHAR(256),
    title_id            UUID REFERENCES titles(id),
    -- anonymous_pulse_id removed (V17)
    department          VARCHAR(256),
    division            VARCHAR(256),
    cost_center         VARCHAR(128),
    employee_id         VARCHAR(128),
    org_unit_id         UUID REFERENCES organizational_units(id),
    manager_id          UUID REFERENCES users(id),
    entra_last_synced_at TIMESTAMP,
    last_login_at       TIMESTAMP,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    -- SF sync columns (V5, V6)
    sf_user_id          VARCHAR(64) UNIQUE,
    company_code        VARCHAR(128),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_org_unit     ON users(org_unit_id);
CREATE INDEX idx_users_manager      ON users(manager_id);
CREATE INDEX idx_users_active       ON users(active);
CREATE INDEX idx_users_email        ON users(email);
CREATE INDEX idx_users_sf_user_id   ON users(sf_user_id);
CREATE INDEX idx_users_company_code ON users(company_code);

CREATE TABLE user_roles (
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE user_permissions (
    user_id       UUID REFERENCES users(id) ON DELETE CASCADE,
    permission_id UUID REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, permission_id)
);

CREATE TABLE user_teams (
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    team_id UUID REFERENCES teams(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, team_id)
);

CREATE TABLE user_groups (
    user_id  UUID REFERENCES users(id) ON DELETE CASCADE,
    group_id UUID REFERENCES groups(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, group_id)
);

CREATE TABLE role_permissions (
    role_id       UUID REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- Leadership assignment junction (is_leader marks org unit leaders for Spark voting)
CREATE TABLE user_org_unit (
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    org_unit_id UUID NOT NULL REFERENCES organizational_units(id) ON DELETE CASCADE,
    is_leader   BOOLEAN NOT NULL DEFAULT FALSE,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, org_unit_id)
);

-- ============================================================
-- SF Sync State (V6)
-- ============================================================

CREATE TABLE sf_sync_state (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    sync_type           VARCHAR(16)  NOT NULL,   -- FULL or DELTA
    started_at          TIMESTAMP    NOT NULL,
    completed_at        TIMESTAMP,
    status              VARCHAR(16)  NOT NULL DEFAULT 'RUNNING',  -- RUNNING, SUCCESS, FAILED
    users_processed     INT          NOT NULL DEFAULT 0,
    users_created       INT          NOT NULL DEFAULT 0,
    users_updated       INT          NOT NULL DEFAULT 0,
    users_deactivated   INT          NOT NULL DEFAULT 0,
    org_units_processed INT          NOT NULL DEFAULT 0,
    org_units_created   INT          NOT NULL DEFAULT 0,
    org_units_updated   INT          NOT NULL DEFAULT 0,
    error_count         INT          NOT NULL DEFAULT 0,
    last_delta_token    VARCHAR(1024)
);

CREATE INDEX idx_sf_sync_state_started ON sf_sync_state(started_at DESC);

-- ============================================================
-- User SF Profile (V8) — 1:1 extension table for SF metadata
-- Keeps hot users table lean for auth and session queries.
-- ============================================================

CREATE TABLE user_sf_profile (
    user_id          UUID        PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    sf_synced_at     TIMESTAMP,
    sf_hire_date     DATE,
    sf_employee_type VARCHAR(64),
    sf_function      VARCHAR(128)
);

-- ============================================================
-- Form (Survey) Module
-- type discriminator: SURVEY | PSYCHOMETRIC
-- ============================================================

CREATE TABLE form (
    id                  UUID PRIMARY KEY,
    title               VARCHAR(512) NOT NULL,
    description         TEXT,
    anon_window_minutes INT NOT NULL DEFAULT 60,
    type                VARCHAR(32) NOT NULL,  -- SURVEY | PSYCHOMETRIC
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_form_type ON form(type);

-- Questions (SCALE | TEXT | CHOICE_SINGLE | CHOICE_MULTIPLE | ADJECTIVE_CHECKLIST | FORCED_CHOICE | RATING | MULTI_RATING)
-- scale_min/max/min_label/max_label: populated for psychometric SCALE questions
-- forced_choice_pairs: JSONB pairs for FORCED_CHOICE questions (V18)
CREATE TABLE question (
    id                UUID PRIMARY KEY,
    form_id           UUID NOT NULL REFERENCES form(id) ON DELETE CASCADE,
    body              TEXT NOT NULL,
    question_type     VARCHAR(32) NOT NULL,
    effective_date    TIMESTAMP,
    expiration_date   TIMESTAMP,
    display_order     INT NOT NULL DEFAULT 0,
    subject_labels    JSONB,
    scale_min         INT,
    scale_max         INT,
    min_label         VARCHAR(255),
    max_label         VARCHAR(255),
    -- AR translation columns (nullable — fall back to EN when NULL)
    body_ar             TEXT,
    min_label_ar        VARCHAR(255),
    max_label_ar        VARCHAR(255),
    subject_labels_ar   JSONB,
    forced_choice_pairs JSONB,  -- V18: [{a, scaleA, b, scaleB, aAr, bAr}] for FORCED_CHOICE
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_scale_range CHECK (
        question_type != 'SCALE'
        OR (
            scale_min IS NOT NULL
            AND scale_max IS NOT NULL
            AND scale_max - scale_min >= 2
            AND scale_max - scale_min <= 9
        )
    )
);

CREATE INDEX idx_question_form      ON question(form_id);
CREATE INDEX idx_question_effective ON question(effective_date, expiration_date);

-- Multiple-choice answer options
CREATE TABLE candidate_answer (
    id            UUID PRIMARY KEY,
    question_id   UUID NOT NULL REFERENCES question(id) ON DELETE CASCADE,
    label         VARCHAR(512) NOT NULL,
    label_ar      VARCHAR(512),            -- AR translation (nullable)
    display_order INT NOT NULL DEFAULT 0,
    is_correct    BOOLEAN  -- NULL = personality; TRUE = keyed answer; never sent to candidate
);

CREATE INDEX idx_candidate_answer_question ON candidate_answer(question_id);

-- ============================================================
-- Anonymous Identity (windowed daily tokens)
-- ============================================================

CREATE TABLE anon_identity (
    id                  UUID PRIMARY KEY,
    org_unit_id         UUID NOT NULL REFERENCES organizational_units(id),
    form_id             UUID NOT NULL REFERENCES form(id),
    token               VARCHAR(512) NOT NULL UNIQUE,
    window_start        TIMESTAMP NOT NULL,
    window_end          TIMESTAMP NOT NULL,
    sequence_in_window  INT NOT NULL DEFAULT 1,
    CONSTRAINT uq_anon_identity_form_org_token UNIQUE (form_id, org_unit_id, token)
);

CREATE INDEX idx_anon_identity_lookup ON anon_identity(form_id, org_unit_id, window_start);

-- ============================================================
-- Response Session
-- server_start_epoch / time_limit_secs: psychometric timer
-- item_sequence: randomised question order for psychometric tests
-- focus_loss_count: browser tab-away events during psychometric session (V13)
-- ============================================================

CREATE TABLE response_session (
    id                  UUID PRIMARY KEY,
    form_id             UUID NOT NULL REFERENCES form(id),
    user_id             UUID REFERENCES users(id),
    anon_identity_id    UUID REFERENCES anon_identity(id) ON DELETE SET NULL,
    is_anonymous        BOOLEAN NOT NULL DEFAULT FALSE,
    started_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at        TIMESTAMP,
    server_start_epoch  BIGINT,
    time_limit_secs     INT,
    item_sequence       JSONB,
    focus_loss_count    INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT chk_session_identity CHECK (user_id IS NOT NULL OR anon_identity_id IS NOT NULL)
);

CREATE INDEX idx_response_session_form     ON response_session(form_id);
CREATE INDEX idx_response_session_user     ON response_session(user_id);
CREATE INDEX idx_response_session_anon     ON response_session(anon_identity_id);
CREATE INDEX idx_response_session_user_form ON response_session(user_id, form_id);

-- BUG-002 fix (V2): prevent duplicate open sessions for same user+form
CREATE UNIQUE INDEX idx_session_user_form_open
    ON response_session(user_id, form_id)
    WHERE completed_at IS NULL;

-- ============================================================
-- Answer Submissions (versioned)
-- ============================================================

CREATE TABLE answer_submission (
    id           UUID PRIMARY KEY,
    session_id   UUID NOT NULL REFERENCES response_session(id) ON DELETE CASCADE,
    question_id  UUID NOT NULL REFERENCES question(id),
    answer_type  VARCHAR(32) NOT NULL,
    version      INT NOT NULL DEFAULT 1,
    is_current   BOOLEAN NOT NULL DEFAULT TRUE,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    comment      TEXT
);

CREATE INDEX idx_answer_submission_session  ON answer_submission(session_id);
CREATE INDEX idx_answer_submission_question ON answer_submission(question_id);
CREATE UNIQUE INDEX idx_answer_submission_current
    ON answer_submission(session_id, question_id) WHERE is_current = TRUE;

-- Answer payloads (one table per question type)
CREATE TABLE answer_text (
    id            UUID PRIMARY KEY,
    submission_id UUID NOT NULL UNIQUE REFERENCES answer_submission(id) ON DELETE CASCADE,
    value         TEXT NOT NULL
);

CREATE INDEX idx_answer_text_submission ON answer_text(submission_id);

CREATE TABLE answer_scale (
    id            UUID PRIMARY KEY,
    submission_id UUID NOT NULL UNIQUE REFERENCES answer_submission(id) ON DELETE CASCADE,
    value         INT NOT NULL,
    min_value     INT NOT NULL DEFAULT 1,
    max_value     INT NOT NULL DEFAULT 5
);

CREATE INDEX idx_answer_scale_submission ON answer_scale(submission_id);

CREATE TABLE answer_choice (
    id                  UUID PRIMARY KEY,
    submission_id       UUID NOT NULL UNIQUE REFERENCES answer_submission(id) ON DELETE CASCADE,
    candidate_answer_id UUID NOT NULL REFERENCES candidate_answer(id)
);

CREATE INDEX idx_answer_choice_submission ON answer_choice(submission_id);

CREATE TABLE answer_rating (
    id            UUID PRIMARY KEY,
    submission_id UUID NOT NULL REFERENCES answer_submission(id) ON DELETE CASCADE,
    subject_label VARCHAR(256) NOT NULL,
    stars         INT NOT NULL,
    max_stars     INT NOT NULL DEFAULT 5
);

CREATE INDEX idx_answer_rating_submission ON answer_rating(submission_id);

-- Adjective checklist answers (V18)
CREATE TABLE answer_adjective (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id UUID NOT NULL REFERENCES answer_submission(id) ON DELETE CASCADE,
    selected      JSONB NOT NULL DEFAULT '[]'
);

CREATE INDEX idx_answer_adjective_submission ON answer_adjective(submission_id);

-- ============================================================
-- Form Assignments
-- ============================================================

CREATE TABLE form_assignment (
    id               UUID PRIMARY KEY,
    form_id          UUID NOT NULL REFERENCES form(id),
    org_unit_id      UUID REFERENCES organizational_units(id),
    user_id          UUID REFERENCES users(id),
    assigned_by      UUID NOT NULL REFERENCES users(id),
    assigned_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    starts_at        TIMESTAMP,
    expires_at       TIMESTAMP,
    due_date         TIMESTAMP,
    mandatory        BOOLEAN NOT NULL DEFAULT FALSE,
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    include_children BOOLEAN NOT NULL DEFAULT TRUE,
    allow_resubmission BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_assignment_target CHECK (
        (org_unit_id IS NOT NULL AND user_id IS NULL) OR
        (org_unit_id IS NULL AND user_id IS NOT NULL)
    ),
    CONSTRAINT chk_window_order CHECK (
        starts_at IS NULL OR expires_at IS NULL OR starts_at < expires_at
    )
);

CREATE UNIQUE INDEX uq_active_org_form_assignment
    ON form_assignment(form_id, org_unit_id)
    WHERE active = TRUE AND org_unit_id IS NOT NULL;

CREATE UNIQUE INDEX uq_active_user_form_assignment
    ON form_assignment(form_id, user_id)
    WHERE active = TRUE AND user_id IS NOT NULL;

CREATE INDEX idx_form_assignment_form     ON form_assignment(form_id)     WHERE active = TRUE;
CREATE INDEX idx_form_assignment_user     ON form_assignment(user_id)     WHERE active = TRUE;
CREATE INDEX idx_form_assignment_org_unit ON form_assignment(org_unit_id) WHERE active = TRUE;
CREATE INDEX idx_form_assignment_expires  ON form_assignment(expires_at)  WHERE active = TRUE AND expires_at IS NOT NULL;

-- ============================================================
-- Auth Sessions (refresh token management)
-- ============================================================

CREATE TABLE sessions (
    id                 UUID PRIMARY KEY,
    user_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token_hash VARCHAR(128) NOT NULL,
    device_info        VARCHAR(512),
    ip_address         VARCHAR(45),
    expires_at         TIMESTAMP NOT NULL,
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    revoked_at         TIMESTAMP
);

CREATE INDEX idx_sessions_user         ON sessions(user_id);
CREATE INDEX idx_sessions_refresh_hash ON sessions(refresh_token_hash);
CREATE INDEX idx_sessions_expires      ON sessions(expires_at);

-- ============================================================
-- Audit Logs (append-only, tamper-proof via triggers)
-- details column is JSONB — always use auditService.buildDetail()
-- ============================================================

CREATE TABLE audit_logs (
    id          UUID PRIMARY KEY,
    user_id     UUID REFERENCES users(id),
    action      VARCHAR(128) NOT NULL,
    entity_type VARCHAR(128),
    entity_id   UUID,
    details     JSONB,
    ip_address  VARCHAR(45),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_user         ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_action       ON audit_logs(action);
CREATE INDEX idx_audit_logs_created      ON audit_logs(created_at);
CREATE INDEX idx_audit_logs_user_created ON audit_logs(user_id, created_at DESC);

CREATE OR REPLACE FUNCTION prevent_audit_log_modification()
    RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs are immutable — UPDATE and DELETE are not permitted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_logs_immutable_update
    BEFORE UPDATE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_log_modification();

CREATE TRIGGER audit_logs_immutable_delete
    BEFORE DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_log_modification();

-- ============================================================
-- Role Change Requests
-- ============================================================

CREATE TABLE role_change_requests (
    id              UUID PRIMARY KEY,
    target_user_id  UUID NOT NULL REFERENCES users(id),
    requested_by_id UUID NOT NULL REFERENCES users(id),
    role_name       VARCHAR(128) NOT NULL,
    action          VARCHAR(16) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reviewed_by_id  UUID REFERENCES users(id),
    review_comment  TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    reviewed_at     TIMESTAMP
);

CREATE INDEX idx_role_change_status       ON role_change_requests(status);
CREATE INDEX idx_role_change_target       ON role_change_requests(target_user_id);
CREATE INDEX idx_role_change_requested_by ON role_change_requests(requested_by_id);

-- ============================================================
-- Spark Rewards Module
-- ============================================================

CREATE TABLE award_periods (
    id               UUID PRIMARY KEY,
    name             VARCHAR(256) NOT NULL,
    nomination_start TIMESTAMP NOT NULL,
    nomination_end   TIMESTAMP NOT NULL,
    voting_start     TIMESTAMP NOT NULL,
    voting_end       TIMESTAMP NOT NULL,
    status           VARCHAR(32) NOT NULL DEFAULT 'UPCOMING',
    eligible_entities TEXT,
    award_amount     NUMERIC(10, 2),
    created_by       UUID REFERENCES users(id),
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Spark Categories — fixed EDGE DNA reference data
CREATE TABLE spark_categories (
    id            VARCHAR(32) PRIMARY KEY,
    name          VARCHAR(128) NOT NULL,
    description   TEXT,
    icon          VARCHAR(64),
    display_order INT NOT NULL DEFAULT 0,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO spark_categories (id, name, description, icon, display_order, is_active) VALUES
    ('PROACTIVE',     'Pro-active Champion',   'Thinks about the future for our organisation and focuses effectively on key priorities',                        'rocket_launch', 1, TRUE),
    ('CANDO',         'Can-Do Champion',        'Willing to take ownership and the initiative to solve problems with efficiency and agility',                     'thumb_up',      2, TRUE),
    ('COLLABORATIVE', 'Collaborative Champion', 'Works with integrity, is a strong team player and can inspire & develop others',                                'groups',        3, TRUE),
    ('ACTIONDRIVER',  'Action Driver Champion', 'Acts with pace and resilience, has a sense of urgency to drive and deliver change across the organisation',     'bolt',          4, TRUE);

CREATE TABLE nominations (
    id              UUID PRIMARY KEY,
    award_period_id UUID NOT NULL REFERENCES award_periods(id),
    category_id     VARCHAR(32) NOT NULL REFERENCES spark_categories(id),
    nominator_id    UUID NOT NULL REFERENCES users(id),
    nominee_id      UUID NOT NULL REFERENCES users(id),
    justification   TEXT NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
    submitted_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_one_nomination_per_category UNIQUE (award_period_id, category_id, nominator_id)
);

CREATE INDEX idx_nominations_period    ON nominations(award_period_id);
CREATE INDEX idx_nominations_nominee   ON nominations(nominee_id);
CREATE INDEX idx_nominations_nominator ON nominations(nominator_id);
CREATE INDEX idx_nominations_category  ON nominations(category_id);

CREATE TABLE nomination_attachments (
    id            UUID PRIMARY KEY,
    nomination_id UUID NOT NULL REFERENCES nominations(id) ON DELETE CASCADE,
    file_name     VARCHAR(512) NOT NULL,
    file_type     VARCHAR(64) NOT NULL,
    file_size     BIGINT NOT NULL,
    storage_url   TEXT,
    uploaded_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    uploaded_by   UUID NOT NULL REFERENCES users(id)
);

CREATE INDEX idx_attachments_nomination ON nomination_attachments(nomination_id);

CREATE TABLE leader_votes (
    id                  UUID PRIMARY KEY,
    award_period_id     UUID NOT NULL REFERENCES award_periods(id),
    category_id         VARCHAR(32) NOT NULL REFERENCES spark_categories(id),
    leader_id           UUID NOT NULL REFERENCES users(id),
    nominee_id          UUID NOT NULL REFERENCES nominations(id),
    endorsement_comment TEXT,
    voted_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_one_vote_per_category UNIQUE (award_period_id, category_id, leader_id)
);

CREATE INDEX idx_leader_votes_period          ON leader_votes(award_period_id);
CREATE INDEX idx_leader_votes_leader          ON leader_votes(leader_id);
CREATE INDEX idx_leader_votes_category        ON leader_votes(category_id);
CREATE INDEX idx_leader_votes_period_category ON leader_votes(award_period_id, category_id);

CREATE TABLE spark_winners (
    id               UUID PRIMARY KEY,
    award_period_id  UUID NOT NULL REFERENCES award_periods(id),
    category_id      VARCHAR(32) NOT NULL REFERENCES spark_categories(id),
    winner_id        UUID NOT NULL REFERENCES users(id),
    vote_count       INT NOT NULL DEFAULT 0,
    hr_justification TEXT,
    finalized_by     UUID REFERENCES users(id),
    finalized_at     TIMESTAMP,
    announced_at     TIMESTAMP,
    award_points     NUMERIC(10, 2),
    CONSTRAINT uq_one_winner_per_category UNIQUE (award_period_id, category_id)
);

CREATE INDEX idx_winners_period ON spark_winners(award_period_id);

CREATE TABLE spark_congratulations (
    id              UUID PRIMARY KEY,
    winner_id       UUID NOT NULL REFERENCES spark_winners(id) ON DELETE CASCADE,
    award_period_id UUID NOT NULL REFERENCES award_periods(id),
    user_id         UUID NOT NULL REFERENCES users(id),
    reaction        VARCHAR(32),
    message         TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_one_congrats_per_user_winner UNIQUE (winner_id, user_id)
);

CREATE INDEX idx_congratulations_winner ON spark_congratulations(winner_id);

-- ============================================================
-- Psychometric Test Layer
-- ============================================================

CREATE TABLE psychometric_test (
    id              UUID PRIMARY KEY,
    form_id         UUID NOT NULL UNIQUE REFERENCES form(id),
    name            VARCHAR(512) NOT NULL,
    description     TEXT,
    test_type       VARCHAR(16) NOT NULL,   -- COGNITIVE | PERSONALITY | COMPETENCY
    time_limit_secs INT,                    -- NULL = untimed
    instructions    TEXT,
    version         INT NOT NULL DEFAULT 1,
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_test_type   CHECK (test_type IN ('COGNITIVE','PERSONALITY','COMPETENCY')),
    CONSTRAINT chk_test_status CHECK (status    IN ('DRAFT','ACTIVE','RETIRED'))
);

-- Psychometric scales (self-referencing hierarchy)
CREATE TABLE psychometric_scale (
    id              UUID PRIMARY KEY,
    test_id         UUID NOT NULL REFERENCES psychometric_test(id) ON DELETE CASCADE,
    parent_scale_id UUID REFERENCES psychometric_scale(id),
    name            VARCHAR(256) NOT NULL,
    description     TEXT,
    score_method    VARCHAR(4) NOT NULL DEFAULT 'SUM',  -- SUM | MEAN
    display_order   INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_score_method CHECK (score_method IN ('SUM','MEAN'))
);

CREATE INDEX idx_scale_test   ON psychometric_scale(test_id);
CREATE INDEX idx_scale_parent ON psychometric_scale(parent_scale_id);

-- ============================================================
-- Scoring Key Versioning
-- ============================================================

CREATE TABLE scoring_key_version (
    id              UUID PRIMARY KEY,
    test_id         UUID NOT NULL REFERENCES psychometric_test(id),
    version         INT NOT NULL,
    label           VARCHAR(256),
    status          VARCHAR(12) NOT NULL DEFAULT 'STAGING',
    cronbach_alpha  NUMERIC(5, 3),  -- internal reliability measure, populated by batch job
    effective_from  TIMESTAMP,
    effective_until TIMESTAMP,
    published_by    UUID REFERENCES users(id),
    published_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (test_id, version),
    CONSTRAINT chk_key_status CHECK (status IN ('STAGING','ACTIVE','DEPRECATED'))
);

CREATE INDEX idx_scoring_key_test ON scoring_key_version(test_id, status);

-- V20: exactly one ACTIVE scoring key per test at any moment
CREATE UNIQUE INDEX idx_uq_active_scoring_key_per_test
    ON scoring_key_version (test_id)
    WHERE status = 'ACTIVE';

-- Maps each question to a scale within a scoring key version
-- direction: FORWARD (score as-is) | REVERSE (11 - sten) for reverse-keyed items
-- partial_credit (V18): true enables per-option credit for CHOICE_MULTIPLE
CREATE TABLE scoring_key_item (
    id                UUID PRIMARY KEY,
    scoring_key_id    UUID NOT NULL REFERENCES scoring_key_version(id) ON DELETE CASCADE,
    scale_id          UUID NOT NULL REFERENCES psychometric_scale(id),
    question_id       UUID NOT NULL REFERENCES question(id),
    direction         VARCHAR(8) NOT NULL DEFAULT 'FORWARD',
    weight            NUMERIC(6,3) NOT NULL DEFAULT 1.0,
    correct_answer_id UUID REFERENCES candidate_answer(id),  -- NULL for personality items
    partial_credit    BOOLEAN NOT NULL DEFAULT false,
    UNIQUE (scoring_key_id, question_id, scale_id),
    CONSTRAINT chk_direction CHECK (direction IN ('FORWARD','REVERSE'))
);

CREATE INDEX idx_ski_key      ON scoring_key_item(scoring_key_id);
CREATE INDEX idx_ski_question ON scoring_key_item(question_id);

-- Multiple correct answers for CHOICE_MULTIPLE items (V18)
CREATE TABLE scoring_key_correct_answers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scoring_key_item_id UUID NOT NULL REFERENCES scoring_key_item(id) ON DELETE CASCADE,
    candidate_answer_id UUID NOT NULL REFERENCES candidate_answer(id),
    UNIQUE (scoring_key_item_id, candidate_answer_id)
);

CREATE INDEX idx_skca_item ON scoring_key_correct_answers(scoring_key_item_id);

-- ============================================================
-- Norm Table Versioning
-- ============================================================

CREATE TABLE norm_table_version (
    id              UUID PRIMARY KEY,
    test_id         UUID NOT NULL REFERENCES psychometric_test(id),
    version         INT NOT NULL,
    label           VARCHAR(512) NOT NULL,  -- e.g. "UAE Military Officers 2026"
    sample_size     INT,
    status          VARCHAR(12) NOT NULL DEFAULT 'PROVISIONAL',
    effective_from  TIMESTAMP,
    effective_until TIMESTAMP,
    published_by    UUID REFERENCES users(id),
    published_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (test_id, version),
    CONSTRAINT chk_norm_status CHECK (status IN ('PROVISIONAL','VALIDATED','DEPRECATED'))
);

-- V20: exactly one VALIDATED norm table per test at any moment
CREATE UNIQUE INDEX idx_uq_validated_norm_table_per_test
    ON norm_table_version (test_id)
    WHERE status = 'VALIDATED';

-- Raw score bucket → normalised score lookup
CREATE TABLE norm_entry (
    id            UUID PRIMARY KEY,
    norm_table_id UUID NOT NULL REFERENCES norm_table_version(id) ON DELETE CASCADE,
    scale_id      UUID NOT NULL REFERENCES psychometric_scale(id),
    raw_score_min NUMERIC NOT NULL,
    raw_score_max NUMERIC NOT NULL,
    percentile    NUMERIC(5,2),
    sten_score    INT,
    z_score       NUMERIC(6,3),
    CONSTRAINT chk_raw_range CHECK (raw_score_min <= raw_score_max),
    CONSTRAINT chk_sten      CHECK (sten_score IS NULL OR (sten_score BETWEEN 1 AND 10))
);

CREATE INDEX idx_norm_lookup ON norm_entry(norm_table_id, scale_id, raw_score_min, raw_score_max);

-- ============================================================
-- Test Results
-- ============================================================

CREATE TABLE test_result (
    id                     UUID PRIMARY KEY,
    test_id                UUID NOT NULL REFERENCES psychometric_test(id),
    session_id             UUID NOT NULL UNIQUE REFERENCES response_session(id),
    scoring_key_version_id UUID REFERENCES scoring_key_version(id),
    norm_table_version_id  UUID REFERENCES norm_table_version(id),
    status                 VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    scored_at              TIMESTAMP,
    reviewed_by            UUID REFERENCES users(id),
    reviewed_at            TIMESTAMP,
    review_notes           TEXT,
    focus_loss_count       INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_result_status CHECK (status IN ('PENDING','SCORED','REVIEWED','FLAGGED'))
);

CREATE INDEX idx_test_result_test    ON test_result(test_id);
CREATE INDEX idx_test_result_session ON test_result(session_id);
CREATE INDEX idx_test_result_status  ON test_result(status);

-- One row per scale per result
CREATE TABLE scale_score (
    id             UUID PRIMARY KEY,
    result_id      UUID NOT NULL REFERENCES test_result(id) ON DELETE CASCADE,
    scale_id       UUID NOT NULL REFERENCES psychometric_scale(id),
    raw_score      NUMERIC NOT NULL,
    z_score        NUMERIC(6,3),
    sten_score     INT,
    percentile     NUMERIC(5,2),
    items_answered INT NOT NULL,
    items_total    INT NOT NULL,
    UNIQUE (result_id, scale_id)
);

CREATE INDEX idx_scale_score_result ON scale_score(result_id);

-- ============================================================
-- Result Visibility Policies
-- ============================================================

CREATE TABLE result_visibility_policy (
    id                   UUID PRIMARY KEY,
    test_id              UUID NOT NULL REFERENCES psychometric_test(id),
    audience             VARCHAR(16) NOT NULL,
    show_raw_score       BOOLEAN NOT NULL DEFAULT FALSE,
    show_sten_profile    BOOLEAN NOT NULL DEFAULT FALSE,
    show_percentile      BOOLEAN NOT NULL DEFAULT FALSE,
    show_competency_map  BOOLEAN NOT NULL DEFAULT FALSE,
    show_pass_fail_only  BOOLEAN NOT NULL DEFAULT TRUE,
    show_scale_breakdown BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (test_id, audience),
    CONSTRAINT chk_audience CHECK (audience IN ('CANDIDATE','MANAGER','HR_ADMIN','PSYCHOLOGIST'))
);

-- ============================================================
-- Competency Framework
-- ============================================================

CREATE TABLE competency (
    id            UUID PRIMARY KEY,
    name          VARCHAR(256) NOT NULL,
    description   TEXT,
    org_context   VARCHAR(512),
    display_order INT NOT NULL DEFAULT 0
);

CREATE TABLE competency_scale_weight (
    competency_id UUID NOT NULL REFERENCES competency(id) ON DELETE CASCADE,
    scale_id      UUID NOT NULL REFERENCES psychometric_scale(id) ON DELETE CASCADE,
    weight        NUMERIC(5,3) NOT NULL DEFAULT 1.0,
    direction     VARCHAR(8) NOT NULL DEFAULT 'FORWARD',
    PRIMARY KEY (competency_id, scale_id),
    CONSTRAINT chk_comp_direction CHECK (direction IN ('FORWARD','REVERSE'))
);

CREATE TABLE competency_score (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    result_id     UUID NOT NULL REFERENCES test_result(id) ON DELETE CASCADE,
    competency_id UUID NOT NULL REFERENCES competency(id),
    score         NUMERIC(5, 3) NOT NULL CHECK (score >= 0 AND score <= 10),
    CONSTRAINT uq_competency_score_result_competency UNIQUE (result_id, competency_id)
);

CREATE INDEX idx_competency_score_result ON competency_score(result_id);

-- ============================================================
-- Permission & Role Seed Data (V10 thin-role taxonomy + V19)
-- All inserts are idempotent (ON CONFLICT DO NOTHING).
-- ============================================================

-- Ensure all permissions exist
INSERT INTO permissions (id, name, description)
SELECT gen_random_uuid(), p.name, p.name
FROM (VALUES
  ('FORM_READ'), ('FORM_ALL'), ('FORM_CREATE'), ('FORM_UPDATE'), ('FORM_DELETE'),
  ('FORM_ASSIGN'), ('FORM_PUBLISH'), ('FORM_SESSION_READ'),
  ('ASSESS_READ'), ('ASSESS_ALL'), ('ASSESS_CREATE'), ('ASSESS_UPDATE'), ('ASSESS_DELETE'),
  ('ASSESS_ASSIGN'), ('ASSESS_KEY_MANAGE'), ('ASSESS_RESULT_READ'), ('ASSESS_COMPETENCY_MANAGE'),
  ('SPARK_NOMINATE'), ('SPARK_VOTE'), ('SPARK_REVIEW'), ('SPARK_MANAGE'), ('SPARK_ALL'),
  ('ANNOUNCE_READ'), ('ANNOUNCE_ALL'),
  ('REPORT_VIEW'), ('REPORT_EXPORT'), ('REPORT_TEXT_VIEW'), ('REPORT_ASSESS_VIEW'), ('REPORT_ALL'),
  ('SCOPE_TEAM'), ('SCOPE_ENTITY'), ('SCOPE_ORG_WIDE'),
  ('USR_READ'), ('USR_UPDATE'), ('USR_ALL'), ('USR_CREATE'), ('USR_DELETE'),
  ('USR_ROLE_ASSIGN'), ('USR_IMPORT'),
  ('ORG_READ'), ('ORG_ALL'),
  ('SYNC_ALL'),
  ('ROLE_ALL'),
  ('AI_USE'), ('SYS_AUDIT_VIEW'), ('SYS_APPROVE')
) AS p(name)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = p.name);

-- ── Tier 1 — Participation roles ─────────────────────────────────────────────

INSERT INTO roles (id, name) VALUES (gen_random_uuid(), 'SURVEY_RESPONDENT') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'SURVEY_RESPONDENT' AND p.name IN ('FORM_READ', 'SPARK_NOMINATE')
ON CONFLICT DO NOTHING;

INSERT INTO roles (id, name) VALUES (gen_random_uuid(), 'ASSESSMENT_CANDIDATE') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ASSESSMENT_CANDIDATE' AND p.name IN ('ASSESS_READ', 'ASSESS_RESULT_READ')
ON CONFLICT DO NOTHING;

INSERT INTO roles (id, name) VALUES (gen_random_uuid(), 'PEER_NOMINATOR') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'PEER_NOMINATOR' AND p.name = 'SPARK_NOMINATE'
ON CONFLICT DO NOTHING;

INSERT INTO roles (id, name) VALUES (gen_random_uuid(), 'BROADCAST_VIEWER') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'BROADCAST_VIEWER' AND p.name = 'ANNOUNCE_READ'
ON CONFLICT DO NOTHING;

INSERT INTO roles (id, name) VALUES (gen_random_uuid(), 'SPARK_VOTER') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'SPARK_VOTER' AND p.name IN ('SPARK_VOTE', 'SPARK_REVIEW')
ON CONFLICT DO NOTHING;

-- ── Tier 2 — Elevated access roles ────────────────────────────────────────────

INSERT INTO roles (id, name) VALUES (gen_random_uuid(), 'SCOPE_TEAM_LEAD') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'SCOPE_TEAM_LEAD' AND p.name IN ('SCOPE_TEAM', 'USR_READ', 'REPORT_VIEW')
ON CONFLICT DO NOTHING;

INSERT INTO roles (id, name) VALUES (gen_random_uuid(), 'SCOPE_ENTITY_LEAD') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'SCOPE_ENTITY_LEAD' AND p.name IN ('SCOPE_ENTITY', 'USR_READ', 'REPORT_VIEW')
ON CONFLICT DO NOTHING;

INSERT INTO roles (id, name) VALUES (gen_random_uuid(), 'SURVEY_RESULT_VIEWER') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'SURVEY_RESULT_VIEWER' AND p.name IN ('REPORT_VIEW', 'REPORT_EXPORT')
ON CONFLICT DO NOTHING;

INSERT INTO roles (id, name) VALUES (gen_random_uuid(), 'ASSESSMENT_RESULT_VIEWER') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ASSESSMENT_RESULT_VIEWER' AND p.name IN ('REPORT_ASSESS_VIEW', 'ASSESS_READ')
ON CONFLICT DO NOTHING;

-- ── Tier 3 — HR capability roles ─────────────────────────────────────────────

INSERT INTO roles (id, name) VALUES (gen_random_uuid(), 'FORM_AUTHOR') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'FORM_AUTHOR' AND p.name IN ('SCOPE_ORG_WIDE', 'FORM_ALL', 'REPORT_VIEW')
ON CONFLICT DO NOTHING;

INSERT INTO roles (id, name) VALUES (gen_random_uuid(), 'FORM_ASSIGNER') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'FORM_ASSIGNER' AND p.name IN ('FORM_READ', 'FORM_ASSIGN', 'USR_READ')
ON CONFLICT DO NOTHING;

INSERT INTO roles (id, name) VALUES (gen_random_uuid(), 'SURVEY_ANALYST') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'SURVEY_ANALYST' AND p.name IN ('REPORT_VIEW', 'REPORT_EXPORT')
ON CONFLICT DO NOTHING;

INSERT INTO roles (id, name) VALUES (gen_random_uuid(), 'SURVEY_TEXT_ANALYST') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'SURVEY_TEXT_ANALYST' AND p.name = 'REPORT_TEXT_VIEW'
ON CONFLICT DO NOTHING;

-- ASSESSMENT_ADMIN: ASSESS_ALL + FORM_READ/UPDATE/ASSIGN (V19) + SCOPE_ORG_WIDE + REPORT_ASSESS_VIEW
INSERT INTO roles (id, name) VALUES (gen_random_uuid(), 'ASSESSMENT_ADMIN') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ASSESSMENT_ADMIN'
  AND p.name IN (
    'SCOPE_ORG_WIDE', 'ASSESS_ALL', 'REPORT_ASSESS_VIEW',
    'FORM_READ', 'FORM_UPDATE', 'FORM_ASSIGN'  -- V19
  )
ON CONFLICT DO NOTHING;

INSERT INTO roles (id, name) VALUES (gen_random_uuid(), 'DIRECTORY_ADMIN') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'DIRECTORY_ADMIN' AND p.name IN ('SCOPE_ORG_WIDE', 'USR_ALL', 'ORG_ALL', 'SYNC_ALL')
ON CONFLICT DO NOTHING;

INSERT INTO roles (id, name) VALUES (gen_random_uuid(), 'SPARK_ADMIN') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'SPARK_ADMIN' AND p.name IN ('SCOPE_ORG_WIDE', 'SPARK_ALL')
ON CONFLICT DO NOTHING;

INSERT INTO roles (id, name) VALUES (gen_random_uuid(), 'BROADCAST_AUTHOR') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'BROADCAST_AUTHOR' AND p.name IN ('SCOPE_ORG_WIDE', 'ANNOUNCE_ALL')
ON CONFLICT DO NOTHING;

-- ── Tier 4 — Bootstrap role ───────────────────────────────────────────────────

INSERT INTO roles (id, name) VALUES (gen_random_uuid(), 'ROLE_ADMINISTRATOR') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ROLE_ADMINISTRATOR' AND p.name = 'ROLE_ALL'
ON CONFLICT DO NOTHING;

-- ============================================================
-- Materialized Views (pre-computed aggregates — must be LAST)
-- All referenced tables must exist before this section.
-- ============================================================

CREATE MATERIALIZED VIEW mv_analytics_summary AS
SELECT
    ou.path                          AS org_path,
    ou.org_unit_name,
    f.id                             AS form_id,
    f.title                          AS form_title,
    COUNT(DISTINCT rs.id)            AS respondent_count,
    AVG(sa.value::double precision)  AS avg_score,
    COUNT(sa.id)                     AS answer_count
FROM answer_scale sa
JOIN answer_submission asub ON asub.id  = sa.submission_id
JOIN response_session  rs   ON rs.id   = asub.session_id
JOIN question          q    ON q.id    = asub.question_id
JOIN form              f    ON f.id    = q.form_id
JOIN users             u    ON u.id    = rs.user_id
JOIN organizational_units ou ON ou.id = u.org_unit_id
WHERE rs.completed_at IS NOT NULL
  AND asub.is_current = true
GROUP BY ou.path, ou.org_unit_name, f.id, f.title;

CREATE UNIQUE INDEX ON mv_analytics_summary (org_path, form_id);

CREATE MATERIALIZED VIEW mv_form_session_counts AS
SELECT
    rs.form_id,
    rs.user_id,
    COUNT(*) FILTER (WHERE rs.completed_at IS NOT NULL) AS completed_count,
    COUNT(*) FILTER (WHERE rs.completed_at IS NULL)     AS in_progress_count
FROM response_session rs
WHERE rs.user_id IS NOT NULL
GROUP BY rs.form_id, rs.user_id;

CREATE UNIQUE INDEX ON mv_form_session_counts (form_id, user_id);

CREATE MATERIALIZED VIEW mv_question_scale_distribution AS
SELECT
    asub.question_id,
    sa.value::integer AS score,
    COUNT(sa.id)      AS score_count
FROM answer_scale sa
JOIN answer_submission asub ON asub.id = sa.submission_id
JOIN response_session  rs   ON rs.id   = asub.session_id
WHERE asub.is_current = true
  AND rs.completed_at IS NOT NULL
GROUP BY asub.question_id, sa.value;

CREATE UNIQUE INDEX ON mv_question_scale_distribution (question_id, score);

CREATE MATERIALIZED VIEW mv_psychometric_scale_stats AS
SELECT
    tr.test_id,
    ss.scale_id,
    COUNT(ss.id)                                         AS result_count,
    ROUND(AVG(ss.raw_score)::NUMERIC, 3)                 AS avg_raw_score,
    ROUND(AVG(ss.sten_score)::NUMERIC, 2)                AS avg_sten,
    ROUND(AVG(ss.percentile)::NUMERIC, 2)                AS avg_percentile,
    ROUND(COALESCE(STDDEV(ss.raw_score), 0)::NUMERIC, 3) AS stddev_raw_score,
    SUM(CASE WHEN ss.sten_score = 1  THEN 1 ELSE 0 END)  AS sten_1_count,
    SUM(CASE WHEN ss.sten_score = 2  THEN 1 ELSE 0 END)  AS sten_2_count,
    SUM(CASE WHEN ss.sten_score = 3  THEN 1 ELSE 0 END)  AS sten_3_count,
    SUM(CASE WHEN ss.sten_score = 4  THEN 1 ELSE 0 END)  AS sten_4_count,
    SUM(CASE WHEN ss.sten_score = 5  THEN 1 ELSE 0 END)  AS sten_5_count,
    SUM(CASE WHEN ss.sten_score = 6  THEN 1 ELSE 0 END)  AS sten_6_count,
    SUM(CASE WHEN ss.sten_score = 7  THEN 1 ELSE 0 END)  AS sten_7_count,
    SUM(CASE WHEN ss.sten_score = 8  THEN 1 ELSE 0 END)  AS sten_8_count,
    SUM(CASE WHEN ss.sten_score = 9  THEN 1 ELSE 0 END)  AS sten_9_count,
    SUM(CASE WHEN ss.sten_score = 10 THEN 1 ELSE 0 END)  AS sten_10_count
FROM scale_score ss
JOIN test_result tr ON tr.id = ss.result_id
WHERE tr.status IN ('SCORED', 'REVIEWED')
GROUP BY tr.test_id, ss.scale_id;

CREATE UNIQUE INDEX ON mv_psychometric_scale_stats (test_id, scale_id);

CREATE MATERIALIZED VIEW mv_psychometric_test_summary AS
SELECT
    tr.test_id,
    COUNT(*)                                                AS total_results,
    SUM(CASE WHEN tr.status = 'PENDING'  THEN 1 ELSE 0 END) AS pending_count,
    SUM(CASE WHEN tr.status = 'SCORED'   THEN 1 ELSE 0 END) AS scored_count,
    SUM(CASE WHEN tr.status = 'REVIEWED' THEN 1 ELSE 0 END) AS reviewed_count,
    SUM(CASE WHEN tr.status = 'FLAGGED'  THEN 1 ELSE 0 END) AS flagged_count,
    MAX(tr.scored_at)                                        AS last_scored_at,
    ROUND(AVG(tr.focus_loss_count)::NUMERIC, 2)              AS avg_focus_loss_count
FROM test_result tr
GROUP BY tr.test_id;

CREATE UNIQUE INDEX ON mv_psychometric_test_summary (test_id);

-- MV: per-(question, option) choice answer counts for global survey report analytics.
-- Eliminates per-question GROUP BY queries in AnalyticsService.buildChoiceReport().
-- Refreshed on the same 5-minute schedule as the other analytics MVs.
CREATE MATERIALIZED VIEW mv_question_choice_distribution AS
SELECT
    asub.question_id,
    ac.candidate_answer_id,
    ca.label    AS option_label,
    COUNT(ac.id) AS choice_count
FROM answer_choice ac
JOIN answer_submission asub ON asub.id = ac.submission_id
JOIN candidate_answer  ca   ON ca.id  = ac.candidate_answer_id
JOIN response_session  rs   ON rs.id  = asub.session_id
WHERE asub.is_current = true
  AND rs.completed_at IS NOT NULL
GROUP BY asub.question_id, ac.candidate_answer_id, ca.label;

CREATE UNIQUE INDEX ON mv_question_choice_distribution (question_id, candidate_answer_id);

-- MV: per-(question, subject) rating averages for global survey report analytics.
-- Eliminates per-question AVG + subject GROUP BY queries in AnalyticsService.buildRatingReport().
-- total_response_count stores the question-level distinct submission count for threshold checks.
CREATE MATERIALIZED VIEW mv_question_rating_stats AS
WITH subject_agg AS (
    SELECT
        asub.question_id,
        ar.subject_label,
        COUNT(DISTINCT asub.id)          AS subject_response_count,
        AVG(ar.stars::double precision)  AS avg_stars
    FROM answer_rating ar
    JOIN answer_submission asub ON asub.id = ar.submission_id
    JOIN response_session  rs   ON rs.id  = asub.session_id
    WHERE asub.is_current = true
      AND rs.completed_at IS NOT NULL
    GROUP BY asub.question_id, ar.subject_label
),
question_totals AS (
    SELECT
        asub.question_id,
        COUNT(DISTINCT asub.id) AS total_response_count
    FROM answer_rating ar
    JOIN answer_submission asub ON asub.id = ar.submission_id
    JOIN response_session  rs   ON rs.id  = asub.session_id
    WHERE asub.is_current = true
      AND rs.completed_at IS NOT NULL
    GROUP BY asub.question_id
)
SELECT
    s.question_id,
    s.subject_label,
    s.subject_response_count,
    s.avg_stars,
    t.total_response_count
FROM subject_agg s
JOIN question_totals t ON t.question_id = s.question_id;

CREATE UNIQUE INDEX ON mv_question_rating_stats (question_id, subject_label);

-- MV: per-(form, exact org_path) session counts for assignment breakdown analytics.
-- Eliminates per-assignment COUNT queries in AnalyticsService.buildAssignmentBreakdown().
-- Subtree counts are computed in-memory by matching org_path startsWith the target prefix.
CREATE MATERIALIZED VIEW mv_form_org_session_counts AS
SELECT
    rs.form_id,
    ou.path                                                      AS org_path,
    COUNT(*) FILTER (WHERE rs.completed_at IS NOT NULL)           AS completed_count,
    COUNT(*) FILTER (WHERE rs.completed_at IS NULL)               AS in_progress_count
FROM response_session rs
JOIN users             u  ON u.id  = rs.user_id
JOIN organizational_units ou ON ou.id = u.org_unit_id
WHERE rs.user_id IS NOT NULL
GROUP BY rs.form_id, ou.path;

CREATE UNIQUE INDEX ON mv_form_org_session_counts (form_id, org_path);


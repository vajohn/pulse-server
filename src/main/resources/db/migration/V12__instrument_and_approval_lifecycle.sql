-- V12: instrument reference + dual-control approval lifecycle for psychometric tests.
--
-- Consolidated from the former V12 (psychometric_instrument) and V13 (test_approval_lifecycle)
-- into ONE migration so dev/k2 applies a single step on top of V11 rather than two
-- (k2 pulse_dev sits at V11; neither prior file had been deployed there).
--
-- Ordering note: test_approval_request is CREATEd *before* its CHECK constraint exists
-- (the constraint is now inlined in the CREATE TABLE). The former standalone V13 added the
-- chk_approval_status constraint via ALTER TABLE *before* the table was created — a latent
-- ordering bug that only survived locally because the table pre-existed from an earlier
-- e2e iteration. A clean apply (V11 → here) would have failed; that is fixed here.

-- ─── 1. Instrument reference (dedup-safe) ────────────────────────────────────────────────
-- An instrument (Big Five, ATP, PTI Plus, the CA suite…) is a specific test OF a TestType.
-- canonical_name (UNIQUE) makes duplicates structurally impossible; the app computes it via
-- InstrumentNormalizer.canonical(displayName). instrument_id on psychometric_test is nullable
-- (a test need not name an instrument). No scoring-engine impact.

CREATE TABLE psychometric_instrument (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name   TEXT NOT NULL,
    canonical_name TEXT NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT psychometric_instrument_canonical_name_key UNIQUE (canonical_name)
);

ALTER TABLE psychometric_test
    ADD COLUMN instrument_id UUID NULL
        REFERENCES psychometric_instrument (id) ON DELETE SET NULL;

CREATE INDEX idx_psychometric_test_instrument ON psychometric_test (instrument_id);

-- Idempotent seed of the known in-scope instruments.
-- INVARIANT: canonical_name = InstrumentNormalizer.canonical(display_name)
--            i.e. display_name lowercased, all non-alphanumeric chars stripped.
-- Display names carry NO parenthetical qualifiers (version codes, form suffixes,
-- "in development" etc.) — the instrument's identity is its name alone.
-- Example: display "Big Five" → canonical 'bigfive' (not 'bigfivein development').
-- ON CONFLICT (canonical_name) DO NOTHING keeps re-runs / repeated boots safe.
INSERT INTO psychometric_instrument (display_name, canonical_name) VALUES
    ('PTI Plus',                     'ptiplus'),
    ('Adaptive Traits Profiler',     'adaptivetraitsprofiler'),
    ('Vocational Interests Profiler','vocationalinterestsprofiler'),
    ('Logical Reasoning',            'logicalreasoning'),
    ('Verbal Reasoning',             'verbalreasoning'),
    ('Numerical Reasoning',          'numericalreasoning'),
    ('Attention to Detail',          'attentiontodetail'),
    ('English Reading Assessment',   'englishreadingassessment'),
    ('English Listening Assessment', 'englishlisteningassessment'),
    ('Big Five',                     'bigfive')
ON CONFLICT (canonical_name) DO NOTHING;

-- ─── 2. Dual-control approval lifecycle ──────────────────────────────────────────────────
-- TestStatus.PENDING_APPROVAL and PermissionName.ASSESS_APPROVE are enum values,
-- synced into varchar columns / the permissions table on boot.

-- Lineage pointer: a revised test (new DRAFT version) supersedes the prior version.
ALTER TABLE psychometric_test
    ADD COLUMN supersedes_id UUID NULL
        REFERENCES psychometric_test (id);

-- Widen chk_test_status so PENDING_APPROVAL is allowed.
-- The constraint was originally defined in V1 as ('DRAFT','ACTIVE','RETIRED'); any status the
-- Java TestStatus enum carries must be listed here.
ALTER TABLE psychometric_test
    DROP CONSTRAINT IF EXISTS chk_test_status;
ALTER TABLE psychometric_test
    ADD CONSTRAINT chk_test_status
        CHECK (status IN ('DRAFT','PENDING_APPROVAL','ACTIVE','RETIRED'));

-- One approval request per (test, version); chk_approval_status matches the TestApprovalStatus enum.
CREATE TABLE test_approval_request (
    id                  UUID PRIMARY KEY,
    test_id             UUID NOT NULL REFERENCES psychometric_test (id),
    test_version        INT  NOT NULL,
    submitted_by        UUID NOT NULL REFERENCES users (id),
    submitted_at        TIMESTAMP NOT NULL DEFAULT now(),
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    reviewed_by         UUID NULL REFERENCES users (id),
    reviewed_at         TIMESTAMP NULL,
    approval_reference  TEXT NULL,
    review_comment      TEXT NULL,
    CONSTRAINT chk_approval_status CHECK (status IN ('PENDING','APPROVED','REJECTED'))
);

CREATE INDEX idx_test_approval_status ON test_approval_request (status);
CREATE INDEX idx_test_approval_test   ON test_approval_request (test_id);

-- At most one PENDING request per (test, version) — enforces the "one open request" rule.
CREATE UNIQUE INDEX idx_test_approval_open
    ON test_approval_request (test_id, test_version)
    WHERE status = 'PENDING';

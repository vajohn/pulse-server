-- V12: dedup-safe instrument reference for psychometric tests.
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

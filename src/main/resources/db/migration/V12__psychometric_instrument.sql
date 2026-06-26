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

-- Idempotent seed of the known in-scope instruments (canonical computed to match
-- InstrumentNormalizer: lowercase, non-alphanumeric runs → single space, trimmed).
-- ON CONFLICT (canonical_name) DO NOTHING keeps re-runs / repeated boots safe.
INSERT INTO psychometric_instrument (display_name, canonical_name) VALUES
    ('PTI Plus',                           'pti plus'),
    ('Adaptive Traits Profiler (ATP)',      'adaptive traits profiler atp'),
    ('Vocational Interests Profiler (VIP)', 'vocational interests profiler vip'),
    ('Logical Reasoning (CA.b)',            'logical reasoning ca b'),
    ('Verbal Reasoning (CA.a)',             'verbal reasoning ca a'),
    ('Numerical Reasoning (CA.a)',          'numerical reasoning ca a'),
    ('Attention to Detail (CA.a)',          'attention to detail ca a'),
    ('English Reading Assessment',          'english reading assessment'),
    ('English Listening Assessment',        'english listening assessment'),
    ('Big Five (in development)',           'big five')
ON CONFLICT (canonical_name) DO NOTHING;

-- V9: question image assets. Interim: bytes in Postgres (data); future: external URL (storage_uri).
CREATE TABLE psychometric_asset (
    id            UUID PRIMARY KEY,
    sha256        VARCHAR(64) NOT NULL UNIQUE,      -- content-addressed: dedup + integrity
    content_type  VARCHAR(64) NOT NULL,
    byte_size     INT NOT NULL,
    locale        VARCHAR(8),                       -- 'en' | 'ar' | NULL (shared)
    data          BYTEA,                            -- interim store (now)
    storage_uri   VARCHAR(1024),                    -- future external store
    original_name VARCHAR(255),
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_asset_payload CHECK (data IS NOT NULL OR storage_uri IS NOT NULL)
);

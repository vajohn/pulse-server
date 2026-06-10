-- V2: Parametric-normal norms — (mean, SD) per scale per norm version.
-- Supersedes the norm_entry lookup table for all NEW imports. norm_entry is left
-- in place (no new writes) until any legacy lookup-based norms are migrated, then dropped.

CREATE TABLE norm_scale_param (
    id                    UUID PRIMARY KEY,
    norm_table_version_id UUID NOT NULL REFERENCES norm_table_version(id) ON DELETE CASCADE,
    scale_id              UUID NOT NULL REFERENCES psychometric_scale(id),
    mean                  NUMERIC(10,4) NOT NULL,
    sd                    NUMERIC(10,4) NOT NULL,
    sample_size           INT,
    CONSTRAINT chk_norm_scale_param_sd_positive CHECK (sd > 0),
    UNIQUE (norm_table_version_id, scale_id)
);

CREATE INDEX idx_norm_scale_param_lookup
    ON norm_scale_param (norm_table_version_id, scale_id);

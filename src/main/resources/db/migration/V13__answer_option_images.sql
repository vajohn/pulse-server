-- V13: optional per-locale image for an answer option (candidate_answer).
-- An option may carry an image instead of, or in addition to, its text label
-- (e.g. "which figure comes next?"). Per-locale to mirror body (EN vs AR /_ARA variants).
-- ON DELETE SET NULL keeps option rows valid if an asset is ever pruned.
ALTER TABLE candidate_answer
    ADD COLUMN image_asset_id    UUID NULL REFERENCES psychometric_asset (id) ON DELETE SET NULL,
    ADD COLUMN image_asset_id_ar UUID NULL REFERENCES psychometric_asset (id) ON DELETE SET NULL;

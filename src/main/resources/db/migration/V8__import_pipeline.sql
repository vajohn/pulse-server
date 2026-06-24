-- V8: import-pipeline support — composite rounding precision + per-option scale tag (VIP tally).
ALTER TABLE psychometric_scale ADD COLUMN composite_rounding_scale INT;        -- NULL = default 1 dp
ALTER TABLE candidate_answer   ADD COLUMN tag_scale_id UUID REFERENCES psychometric_scale(id);  -- OPTION_TAGGED_TALLY (VIP)
CREATE INDEX idx_candidate_answer_tag_scale ON candidate_answer(tag_scale_id);

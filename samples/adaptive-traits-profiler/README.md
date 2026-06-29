# Adaptive Traits Profiler (ATP) — sample package

- **Type:** PERSONALITY
- **Picture-based:** no
- **Questions:** 169  ·  **Scales:** 11  ·  **Keyed:** no (personality)

Binary forced-choice personality instrument — each item is a 2-option choice between two statements
("which is most like you"), 11 trait scales. Imported as `CHOICE_SINGLE` questions and scored by the
`BINARY_FORCED_CHOICE` strategy from the chosen option's ordinal.

**Files:** `questions.csv`, `answer_key.csv`, `scoring_sheet.csv`, `sample_responses.csv`, `expected_scores.csv`

**Import:** Assessments → Import assessment → type **Personality**, attach the CSVs. Step-by-step with screenshots: see [`../README.md`](../README.md).

**Derive answers:** `sample_responses.csv` holds demo candidate answers; `expected_scores.csv` the STEN scores they derive to.

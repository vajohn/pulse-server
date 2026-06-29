package com.edge.pulse.data.enums;

import java.util.List;
import java.util.Set;

/**
 * Capability profile for each psychometric test type.
 *
 * <p>The UI, validation layer, and admin service read this enum rather than
 * branching on {@code if (testType == COGNITIVE)} checks. Adding a future type
 * (e.g. SJT, EI — see {@code ai/docs/adding-a-new-instrument-or-type.md}) means
 * one new {@link TestType} constant plus one new constant here — no other
 * structural changes to routing, the hub, or the scoring engine.
 *
 * <p>Each constant carries self-documenting metadata ({@code displayLabel},
 * {@code description}, {@code measures}, {@code exampleInstruments}) so the
 * dashboard explains the taxonomy from a single source of truth (the
 * {@code GET /api/admin/psychometric/test-types} catalog) instead of hardcoded copy.
 */
public enum TestTypeCapabilities {

    COGNITIVE(
            "Cognitive ability",
            "Reasoning and problem-solving ability (right/wrong, timed).",
            Measures.MAXIMAL,
            List.of("Logical Reasoning", "Verbal Reasoning", "Numerical Reasoning", "Attention to Detail"),
            true,
            true,
            Set.of(QuestionType.CHOICE_SINGLE, QuestionType.CHOICE_MULTIPLE)
    ),
    PERSONALITY(
            "Personality",
            "Typical behaviour, traits & interests (self-report; no right/wrong).",
            Measures.TYPICAL,
            List.of("Big Five", "Adaptive Traits Profiler", "PTI Plus", "Vocational Interests (VIP)"),
            false,
            false,
            // CHOICE_SINGLE is permitted so binary forced-choice instruments (e.g. the Adaptive
            // Traits Profiler) can be imported: each item is a 2-option choice between two
            // statements, scored by the BINARY_FORCED_CHOICE strategy from the selected option's
            // ordinal. CHOICE_SINGLE carries no "correct answer" by itself (keying lives in the
            // scoring key), so it stays consistent with a no-right/wrong personality test.
            Set.of(QuestionType.SCALE, QuestionType.ADJECTIVE_CHECKLIST, QuestionType.FORCED_CHOICE,
                    QuestionType.CHOICE_SINGLE)
    ),
    /**
     * Competency scores are <em>derived</em> from other tests' scales (a weighted mean of
     * trait/personality scale STENs, reverse-aware), per {@code Competency_scoring_psych.ipynb}.
     * A competency test therefore has NO items of its own — {@link #allowedQuestionTypes} is empty,
     * which makes {@code addQuestion} reject items with a clear message.
     */
    COMPETENCY(
            "Competency",
            "Job-relevant competencies derived from trait/personality scales.",
            Measures.DERIVED,
            List.of("Competency framework"),
            false,
            false,
            Set.of()
    );

    public final String displayLabel;
    public final String description;
    public final Measures measures;
    public final List<String> exampleInstruments;
    public final boolean timeLimitRequired;
    public final boolean timeLimitVisible;
    public final Set<QuestionType> allowedQuestionTypes;

    TestTypeCapabilities(String displayLabel,
                         String description,
                         Measures measures,
                         List<String> exampleInstruments,
                         boolean timeLimitRequired,
                         boolean timeLimitVisible,
                         Set<QuestionType> allowedQuestionTypes) {
        this.displayLabel = displayLabel;
        this.description = description;
        this.measures = measures;
        this.exampleInstruments = exampleInstruments;
        this.timeLimitRequired = timeLimitRequired;
        this.timeLimitVisible = timeLimitVisible;
        this.allowedQuestionTypes = allowedQuestionTypes;
    }

    /**
     * Returns the capability profile for the given test type.
     *
     * @throws IllegalArgumentException if {@code type} has no capability profile
     *                                  (should not happen as long as TestType and
     *                                  TestTypeCapabilities stay in sync — guarded by a test)
     */
    public static TestTypeCapabilities of(TestType type) {
        return valueOf(type.name());
    }
}

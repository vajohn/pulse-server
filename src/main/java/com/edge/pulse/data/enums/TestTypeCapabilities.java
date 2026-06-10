package com.edge.pulse.data.enums;

import java.util.Set;

/**
 * Capability profile for each psychometric test type.
 *
 * <p>The UI, validation layer, and admin service read this enum rather than
 * branching on {@code if (testType == COGNITIVE)} checks. Adding a future type
 * (SJT, EI, Aptitude, etc.) means one new enum constant here — no other
 * structural changes to routing, the hub, or the scoring engine.
 *
 * <p>COMPETENCY, SJT, EI, Aptitude, and other types are deliberately out of
 * scope. Each requires a dedicated design sprint to define question/answer
 * formats before any code is written.
 */
public enum TestTypeCapabilities {

    COGNITIVE(
            true,
            true,
            Set.of(QuestionType.CHOICE_SINGLE, QuestionType.CHOICE_MULTIPLE)
    ),
    PERSONALITY(
            false,
            false,
            Set.of(QuestionType.SCALE, QuestionType.ADJECTIVE_CHECKLIST, QuestionType.FORCED_CHOICE)
    );
    // COMPETENCY, SJT, EI, Aptitude, etc. are out of scope for this build.
    // Each is added here when its question/answer formats have been designed.
    // No other file changes needed.

    public final boolean timeLimitRequired;
    public final boolean timeLimitVisible;
    public final Set<QuestionType> allowedQuestionTypes;

    TestTypeCapabilities(boolean timeLimitRequired,
                         boolean timeLimitVisible,
                         Set<QuestionType> allowedQuestionTypes) {
        this.timeLimitRequired = timeLimitRequired;
        this.timeLimitVisible = timeLimitVisible;
        this.allowedQuestionTypes = allowedQuestionTypes;
    }

    /**
     * Returns the capability profile for the given test type.
     *
     * @throws IllegalArgumentException if {@code type} has no capability profile
     *                                  (should not happen as long as TestType and
     *                                  TestTypeCapabilities stay in sync)
     */
    public static TestTypeCapabilities of(TestType type) {
        return valueOf(type.name());
    }
}

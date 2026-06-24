package com.edge.pulse.services.psychometric.scoring.model;

import java.util.List;
import java.util.UUID;

/** A candidate's answer to one question, normalized for strategies. */
public record ItemResponse(
        UUID questionId,
        Integer scaleValue,          // LIKERT_VALUE / BINARY_FORCED_CHOICE (the numeric value 1..n)
        Integer scaleMin,
        Integer scaleMax,
        List<UUID> selectedAnswerIds,// CHOICE_* / forced-choice option ids
        UUID selectedTagScaleId,     // OPTION_TAGGED_TALLY: scale the selected option maps to
        Integer adjectiveCount       // ADJECTIVE_COUNT
) {
    public boolean answered() {
        return scaleValue != null
            || (selectedAnswerIds != null && !selectedAnswerIds.isEmpty())
            || selectedTagScaleId != null
            || adjectiveCount != null;
    }
}

package com.edge.pulse.services.psychometric.scoring.model;

import com.edge.pulse.data.enums.ItemStrategyType;
import com.edge.pulse.data.enums.ScoreDirection;
import java.util.List;
import java.util.UUID;

/** One scoring-key item flattened for the calculator. */
public record ItemConfig(
        UUID questionId,
        UUID scaleId,
        ItemStrategyType strategy,
        ScoreDirection direction,
        double weight,
        UUID correctAnswerId,        // ANSWER_KEY_SINGLE
        List<UUID> correctAnswerIds, // ANSWER_KEY_MULTIPLE
        boolean partialCredit,
        UUID tagScaleId              // OPTION_TAGGED_TALLY: scale the chosen option maps to (resolved per response)
) {}

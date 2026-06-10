package com.edge.pulse.data.dto.psychometric;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One scoring key item returned by GET /tests/{testId}/scoring-key.
 * Flutter reads this to pre-populate the B1 Scoring Key tab.
 */
public record ScoringKeyItemDto(
        UUID questionId,
        String questionBody,
        String questionType,
        UUID scaleId,
        String scaleName,
        String direction,
        BigDecimal weight,
        UUID correctAnswerId,
        String correctAnswerLabel,
        boolean partialCredit
) {}

package com.edge.pulse.data.dto.psychometric;

import java.math.BigDecimal;
import java.util.UUID;

public record ScaleScoreDto(
        UUID scaleId,
        String scaleName,
        /** NULL when the visibility policy hides raw scores. */
        BigDecimal rawScore,
        /** NULL when the visibility policy hides the sten profile. */
        BigDecimal stenScore,
        /** NULL until T-score has been computed; hidden when sten profile is hidden. */
        BigDecimal tScore,
        /** NULL when the visibility policy hides percentile. */
        BigDecimal percentile,
        /** NULL when the visibility policy hides the sten profile (z-score is part of sten profile). */
        BigDecimal zScore,
        int itemsAnswered,
        int itemsTotal
) {}

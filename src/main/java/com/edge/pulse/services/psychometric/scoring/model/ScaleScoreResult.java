package com.edge.pulse.services.psychometric.scoring.model;

import java.math.BigDecimal;
import java.util.UUID;

public record ScaleScoreResult(
        UUID scaleId,
        BigDecimal rawScore,
        BigDecimal zScore,     // null if not parametric
        BigDecimal stenScore,  // null if not normed
        BigDecimal tScore,     // null if not normed/parametric
        BigDecimal percentile, // null if not normed
        int itemsAnswered,
        int itemsTotal
) {}

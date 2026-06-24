package com.edge.pulse.data.dto.psychometric;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One sten-band entry returned by GET /tests/{testId}/norm-table.
 * Flutter reads this to pre-populate the B2 Norm Table tab.
 */
public record NormEntryDto(
        UUID scaleId,
        String scaleName,
        BigDecimal stenScore,
        BigDecimal rawScoreMin,
        BigDecimal rawScoreMax,
        BigDecimal percentile,
        BigDecimal zScore
) {}

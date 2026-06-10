package com.edge.pulse.data.dto.psychometric;

import java.math.BigDecimal;
import java.util.UUID;

/** Competency score as seen by candidates/managers via the visibility policy. */
public record CompetencyScoreDto(
        UUID competencyId,
        String name,
        /** Normalized 0.000–10.000. */
        BigDecimal score
) {}

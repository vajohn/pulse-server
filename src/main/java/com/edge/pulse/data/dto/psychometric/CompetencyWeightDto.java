package com.edge.pulse.data.dto.psychometric;

import java.math.BigDecimal;
import java.util.UUID;

/** Admin view of a single (competency, scale) weight mapping. */
public record CompetencyWeightDto(
        UUID competencyId,
        UUID scaleId,
        String scaleName,
        BigDecimal weight,
        String direction
) {}

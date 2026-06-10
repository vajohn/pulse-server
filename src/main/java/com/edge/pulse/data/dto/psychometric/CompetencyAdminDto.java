package com.edge.pulse.data.dto.psychometric;

import java.util.List;
import java.util.UUID;

/** Admin representation of a competency with its current scale weights. */
public record CompetencyAdminDto(
        UUID id,
        String name,
        String description,
        String orgContext,
        int displayOrder,
        List<CompetencyWeightDto> weights
) {}

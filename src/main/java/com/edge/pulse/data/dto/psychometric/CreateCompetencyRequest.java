package com.edge.pulse.data.dto.psychometric;

import jakarta.validation.constraints.NotBlank;

public record CreateCompetencyRequest(
        @NotBlank String name,
        String description,
        String orgContext,
        int displayOrder
) {}

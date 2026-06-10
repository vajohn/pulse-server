package com.edge.pulse.data.dto.psychometric;

/** All fields nullable — only non-null values are applied (partial update). */
public record UpdateCompetencyRequest(
        String name,
        String description,
        String orgContext,
        Integer displayOrder
) {}

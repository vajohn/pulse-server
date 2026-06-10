package com.edge.pulse.data.dto.psychometric;

/**
 * Request body for {@code PUT /api/admin/psychometric/tests/{testId}}.
 *
 * <p>All fields are nullable for partial update — only non-null values are applied.
 */
public record UpdatePsychometricTestRequest(
        String name,
        String description,
        String instructions,
        Integer timeLimitSecs
) {}

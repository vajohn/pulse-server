package com.edge.pulse.data.dto.psychometric;

import java.util.UUID;

/**
 * Request body for {@code PUT /api/admin/psychometric/tests/{testId}/scales/{scaleId}}.
 *
 * <p>All fields are nullable for partial update.
 */
public record UpdateScaleRequest(
        String name,
        String description,
        String scoreMethod,
        UUID parentScaleId,
        Integer displayOrder
) {}

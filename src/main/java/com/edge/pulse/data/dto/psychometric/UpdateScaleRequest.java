package com.edge.pulse.data.dto.psychometric;

import com.edge.pulse.data.enums.CompositeBasis;
import com.edge.pulse.data.enums.CompositeMethod;

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
        Integer displayOrder,
        /** NULL = no change to composite method. */
        CompositeMethod compositeMethod,
        /** NULL = no change to composite basis. */
        CompositeBasis compositeBasis,
        /** NULL = no change to composite rounding scale. */
        Integer compositeRoundingScale
) {}

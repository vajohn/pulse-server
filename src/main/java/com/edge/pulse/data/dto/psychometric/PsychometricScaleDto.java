package com.edge.pulse.data.dto.psychometric;

import com.edge.pulse.data.enums.CompositeBasis;
import com.edge.pulse.data.enums.CompositeMethod;

import java.util.UUID;

/**
 * Admin read DTO for a psychometric scale.
 */
public record PsychometricScaleDto(
        UUID scaleId,
        UUID testId,
        /** NULL = root scale (no parent). */
        UUID parentScaleId,
        String name,
        String description,
        /** ScoreMethod.name() — e.g. "SUM", "MEAN". */
        String scoreMethod,
        int displayOrder,
        /** NULL = leaf scale (not a composite). */
        CompositeMethod compositeMethod,
        /** Which score type child scales contribute when this is a composite. NULL for leaf scales. */
        CompositeBasis compositeBasis,
        /** Decimal places for composite rollup rounding. NULL = default 1 dp. */
        Integer compositeRoundingScale
) {}

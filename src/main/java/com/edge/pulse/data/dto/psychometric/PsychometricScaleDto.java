package com.edge.pulse.data.dto.psychometric;

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
        int displayOrder
) {}

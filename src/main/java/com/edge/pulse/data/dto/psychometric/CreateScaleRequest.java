package com.edge.pulse.data.dto.psychometric;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for {@code POST /api/admin/psychometric/tests/{testId}/scales}.
 *
 * {@code scoreMethod} must be a valid {@link com.edge.pulse.data.enums.ScoreMethod}
 * name (e.g. {@code "SUM"}, {@code "MEAN"}).  Using {@code String} keeps the DTO
 * decoupled from the enum and consistent with {@link UpdateScaleRequest}.
 */
public record CreateScaleRequest(
        @NotBlank String name,
        String description,
        @NotNull String scoreMethod,
        /** NULL = root scale. Non-null = subscale of the given parent. */
        UUID parentScaleId,
        int displayOrder
) {}

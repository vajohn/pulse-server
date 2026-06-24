package com.edge.pulse.data.dto.psychometric;

import com.edge.pulse.data.enums.CompositeBasis;
import com.edge.pulse.data.enums.CompositeMethod;
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
        int displayOrder,
        /** NULL = leaf scale (not a composite). */
        CompositeMethod compositeMethod,
        /** Which score type child scales contribute when this is a composite. NULL for leaf scales. */
        CompositeBasis compositeBasis,
        /** Decimal places for composite rollup rounding. NULL = default 1 dp. */
        Integer compositeRoundingScale
) {}

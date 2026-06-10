package com.edge.pulse.data.dto.psychometric;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpsertCompetencyWeightRequest(
        @NotNull BigDecimal weight,
        /** Must be "FORWARD" or "REVERSE". */
        @NotNull String direction
) {}

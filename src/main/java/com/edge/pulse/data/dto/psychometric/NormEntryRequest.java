package com.edge.pulse.data.dto.psychometric;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One sten-band entry in a UI-driven norm table PUT payload.
 */
public record NormEntryRequest(
        @NotNull UUID scaleId,
        @Min(1) @Max(10) int stenScore,
        @NotNull BigDecimal rawScoreMin,
        @NotNull BigDecimal rawScoreMax,
        /** 0–100; optional. */
        BigDecimal percentile,
        /** Optional z-score (e.g. -2.00 to +2.00). */
        BigDecimal zScore
) {}

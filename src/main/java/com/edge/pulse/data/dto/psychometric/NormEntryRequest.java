package com.edge.pulse.data.dto.psychometric;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One sten-band entry in a UI-driven norm table PUT payload.
 */
public record NormEntryRequest(
        @NotNull UUID scaleId,
        @DecimalMin("1") @DecimalMax("10") BigDecimal stenScore,
        @NotNull BigDecimal rawScoreMin,
        @NotNull BigDecimal rawScoreMax,
        /** 0–100; optional. */
        BigDecimal percentile,
        /** Optional z-score (e.g. -2.00 to +2.00). */
        BigDecimal zScore
) {}

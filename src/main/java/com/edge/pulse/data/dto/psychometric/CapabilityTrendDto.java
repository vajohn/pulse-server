package com.edge.pulse.data.dto.psychometric;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Per-employee longitudinal trend for one test (§12, D1/D5). One series per non-restricted leaf
 * scale, oldest→newest. {@code normBoundaryCrossed} is true when any adjacent pair of points used
 * different norm versions — the UI must flag and NOT compare silently across it (D5). No SEM band
 * is emitted (D6 — the ScaleScore DTO has no SEM field; deferred).
 */
public record CapabilityTrendDto(
        UUID userId,
        UUID testId,
        List<ScaleTrend> scales) {

    public record ScaleTrend(
            UUID scaleId,
            String scaleName,
            int nAdministrations,
            boolean normBoundaryCrossed,
            List<TrendPoint> points) {}

    public record TrendPoint(
            @JsonSerialize(using = ToStringSerializer.class) BigDecimal sten,
            @JsonSerialize(using = ToStringSerializer.class) BigDecimal tScore,
            UUID normTableVersionId,
            LocalDateTime scoredAt) {}
}

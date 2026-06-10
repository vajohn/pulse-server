package com.edge.pulse.data.dto.spark;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateAwardPeriodRequest(
        @NotBlank String name,
        @NotNull LocalDateTime nominationStart,
        @NotNull LocalDateTime nominationEnd,
        @NotNull LocalDateTime votingStart,
        @NotNull LocalDateTime votingEnd,
        String eligibleEntities,
        BigDecimal awardAmount
) {}

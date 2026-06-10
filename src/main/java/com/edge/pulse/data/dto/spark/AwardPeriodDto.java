package com.edge.pulse.data.dto.spark;

import com.edge.pulse.data.enums.AwardPeriodStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AwardPeriodDto(
        UUID id,
        String name,
        LocalDateTime nominationStart,
        LocalDateTime nominationEnd,
        LocalDateTime votingStart,
        LocalDateTime votingEnd,
        AwardPeriodStatus status,
        String eligibleEntities,
        BigDecimal awardAmount,
        LocalDateTime createdAt
) {}

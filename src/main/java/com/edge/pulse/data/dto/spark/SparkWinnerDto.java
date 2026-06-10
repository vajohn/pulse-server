package com.edge.pulse.data.dto.spark;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record SparkWinnerDto(
        UUID id,
        UUID awardPeriodId,
        String awardPeriodName,
        SparkCategoryDto category,
        NomineeInfoDto winner,
        int voteCount,
        LocalDateTime announcedAt,
        @JsonSerialize(using = ToStringSerializer.class) BigDecimal awardPoints,
        long congratulationCount
) {}

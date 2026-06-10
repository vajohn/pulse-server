package com.edge.pulse.data.dto.spark;

import java.time.LocalDateTime;
import java.util.UUID;

public record LeaderVoteDto(
        UUID id,
        UUID awardPeriodId,
        SparkCategoryDto category,
        NomineeInfoDto nominee,
        String endorsementComment,
        LocalDateTime votedAt
) {}

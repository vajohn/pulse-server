package com.edge.pulse.data.dto.spark;

import java.util.List;

public record SparkHomeDto(
        AwardPeriodDto currentPeriod,
        int badgeCount,
        List<NominationDto> myNominations,
        List<SparkWinnerDto> recentWinners,
        // For entity leaders: pending vote count per category
        int pendingVoteCount
) {}

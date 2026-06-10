package com.edge.pulse.data.dto;

import java.util.List;
import java.util.Map;

/**
 * Team/dashboard analytics summary.
 * Matches the Flutter {@code AnalyticsSummary} model shape exactly.
 * {@code thresholdMet = false} when fewer than MIN_RESPONDENTS have completed
 * any surveys — the Flutter UI shows a privacy shield in that case.
 */
public record AnalyticsSummaryDto(
        int totalRespondents,
        double overallAverageScore,
        Map<String, Double> averageByCategory,
        Map<String, Integer> respondentsByCategory,
        List<OrgUnitScoreDto> orgUnitScores,
        boolean thresholdMet,
        int anonymousRespondents,
        int identifiedRespondents
) {

    /** Per-org-unit aggregate row shown in the HR dashboard leaderboard. */
    public record OrgUnitScoreDto(
            String orgUnitName,
            double averageScore,
            int respondentCount
    ) {}
}

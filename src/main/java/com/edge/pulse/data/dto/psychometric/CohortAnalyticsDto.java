package com.edge.pulse.data.dto.psychometric;

import java.util.List;
import java.util.UUID;

/**
 * Org-scoped cohort capability analytics (§12, D2). When the resolved cohort has fewer than
 * MIN_RESPONDENTS distinct subjects the result is masked: {@code masked=true} and every aggregate
 * is null/empty (mirrors EngagementSummaryDto.masked). Restricted scales (CWB/validity, D3) and
 * INVALID results (D4) never appear. Per-scale rows below MIN_RESPONDENTS are also suppressed.
 */
public record CohortAnalyticsDto(
        UUID testId,
        boolean masked,
        int subjectCount,            // distinct subjects in the resolved scope (0 when masked)
        List<ScaleCohortStat> scales // empty when masked
) {
    /** Per-leaf-scale distribution over the cohort's latest STEN values (band-first). */
    public record ScaleCohortStat(
            UUID scaleId,
            String scaleName,
            int resultCount,         // distinct subjects with a latest sten for this scale
            Double meanSten,
            long[] stenHistogram     // 10 buckets: index 0 = sten-1 .. index 9 = sten-10
    ) {}

    public static CohortAnalyticsDto masked(UUID testId) {
        return new CohortAnalyticsDto(testId, true, 0, List.of());
    }
}

package com.edge.pulse.data.dto.psychometric;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Analytics summary for a psychometric test, read from the pre-computed
 * materialized views {@code mv_psychometric_test_summary} and
 * {@code mv_psychometric_scale_stats}.
 *
 * <p>Scale rows where {@code resultCount < MIN_RESPONDENTS} (5) are excluded
 * to protect individual privacy.
 */
public record PsychometricTestAnalyticsDto(
        UUID testId,
        long totalResults,
        long pendingCount,
        long scoredCount,
        long reviewedCount,
        long flaggedCount,
        LocalDateTime lastScoredAt,
        Double avgFocusLossCount,
        List<ScaleAnalyticsDto> scaleStats) {

    /**
     * Per-scale aggregate statistics and sten-score histogram.
     *
     * <p>{@code stenHistogram} is a 10-element array: index 0 = sten-1 count,
     * index 9 = sten-10 count.
     */
    public record ScaleAnalyticsDto(
            UUID scaleId,
            long resultCount,
            Double avgRawScore,
            Double avgSten,
            Double avgPercentile,
            Double stddevRawScore,
            long[] stenHistogram
    ) {}
}

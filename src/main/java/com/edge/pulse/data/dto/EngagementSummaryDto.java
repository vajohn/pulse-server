package com.edge.pulse.data.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

/**
 * Org-wide engagement analytics summary (PULSE-WEB-4).
 *
 * <p>Consumed by the HR dashboard (WEB-5) and the org-scope switcher (WEB-6).
 *
 * <p><b>Privacy:</b> when the resolved scope has fewer than
 * {@link com.edge.pulse.services.AnalyticsConstants#MIN_RESPONDENTS} distinct
 * completed respondents, the server returns {@code masked = true} and ALL
 * aggregate fields are {@code null}/empty — the underlying numbers are never
 * computed or serialized. This suppression is enforced server-side and is
 * mandatory (privacy-first defense context).
 *
 * <p><b>Numeric encoding:</b> scores use primitive {@code double} → serialized
 * as JSON numbers, matching the existing {@link AnalyticsSummaryDto}. WEB-5/6
 * should parse defensively with {@code double.parse(x.toString())}.
 *
 * <p><b>"Category":</b> the schema has no question-level category dimension, so
 * {@code categoryScores} is grouped by <em>form</em> (each form/survey is treated
 * as a category). See the task report for the data-model note.
 *
 * <p><b>TODO(engagement-source) — TEMPORARY composite (PULSE-WEB-4 / C-1):</b> every
 * score here ({@code overallScore}, {@code categoryScores}, {@code scoreDistribution},
 * {@code trend}) is the normalized-to-1..5 combination of SURVEY {@code RATING}/
 * {@code MULTI_RATING} answers AND {@code SCALE} answers. SCALE today still includes
 * <em>psychometric</em> Likert items. This is an explicit, temporary product decision
 * ("do both for now, revisit later"); revisit to split pure-engagement (survey) from
 * psychometric. {@code respondents} is the distinct contributor count across BOTH
 * sources; {@code participationRate} uses it over the active-user denominator.
 */
public record EngagementSummaryDto(

        // ── scope echo (so the client can confirm what was resolved) ──────────
        String scopeLevel,          // GROUP|CLUSTER|ENTITY|ORG_UNIT|TEAM, or "GLOBAL"
        UUID nodeId,                // resolved org unit id, or null for global
        String nodeName,            // resolved org unit name, or null for global
        boolean includeChildren,
        int periodDays,             // length of the engagement window in days

        // ── privacy gate ─────────────────────────────────────────────────────
        boolean masked,             // true => all aggregates below are suppressed

        // ── aggregates (null/empty when masked) ──────────────────────────────
        Integer respondents,        // distinct completed respondents in scope+window
        Integer eligibleUsers,      // active users in scope (denominator)
        Double participationRate,   // respondents / eligibleUsers * 100
        Double overallScore,        // mean scale score across scope+window
        List<CategoryScore> categoryScores,   // per-form mean scores
        List<ScoreBucket> scoreDistribution,  // histogram of scale values
        Trend trend,                // current vs previous period

        // ── eNPS (NOT SUPPORTED by the current data model — always null) ──────
        // The schema has no eNPS-style 0-10 recommendation question/category.
        // Flagged in the task report; populate once a data-model addition exists.
        // M-1: NON_NULL so a perpetual "enps":null doesn't mislead WEB-5 into
        // thinking the field is computed-but-zero — it is simply absent until a
        // data-model addition exists.
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Double enps
) {

    /** Per-form (treated as category) mean score row. */
    public record CategoryScore(
            String category,       // form title
            double meanScore,
            int respondents
    ) {}

    /** One bucket of the scale-value histogram. */
    public record ScoreBucket(
            int score,
            long count
    ) {}

    /**
     * Trend of the overall score vs the immediately-preceding window of the
     * same length. {@code previousScore}/{@code delta} are null when the
     * previous window had no qualifying respondents.
     */
    public record Trend(
            Double currentScore,
            Double previousScore,
            Double delta,          // current - previous
            String direction       // UP | DOWN | FLAT | NO_PRIOR_DATA
    ) {}

    /** Builds a fully-masked result that leaks no aggregate data. */
    public static EngagementSummaryDto masked(String scopeLevel, UUID nodeId, String nodeName,
                                              boolean includeChildren, int periodDays) {
        return new EngagementSummaryDto(
                scopeLevel, nodeId, nodeName, includeChildren, periodDays,
                true,
                null, null, null, null, null, null, null,
                null);
    }
}

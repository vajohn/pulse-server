package com.edge.pulse.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Periodically refreshes the three survey/form analytics materialized views:
 * <ul>
 *   <li>{@code mv_analytics_summary} — per-(org_path, form) scale score averages</li>
 *   <li>{@code mv_form_session_counts} — per-(form, user) completed/in-progress counts</li>
 *   <li>{@code mv_question_scale_distribution} — per-(question, score) answer counts</li>
 * </ul>
 *
 * <p>The refresh uses {@code CONCURRENTLY} so that read queries are never
 * blocked during the refresh window.  Each view requires a unique index
 * (created in the corresponding Flyway migration) for concurrent refresh.
 *
 * <p>{@code REFRESH MATERIALIZED VIEW CONCURRENTLY} cannot run inside a
 * transaction block, so this method intentionally has no {@code @Transactional}
 * annotation.  The {@code JdbcTemplate.execute()} call uses autocommit mode.
 *
 * <p>Refresh interval is controlled by {@code pulse.analytics.view-refresh-ms}
 * (default 5 minutes).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsRefreshJob {

    private static final Set<String> ALLOWED_VIEWS = Set.of(
            "mv_analytics_summary",
            "mv_form_session_counts",
            "mv_question_scale_distribution",
            "mv_question_choice_distribution",
            "mv_question_rating_stats",
            "mv_form_org_session_counts"
    );

    private final JdbcTemplate jdbc;

    @Scheduled(fixedDelayString = "${pulse.analytics.view-refresh-ms:300000}")
    public void refreshAnalyticsViews() {
        refreshView("mv_analytics_summary");
        refreshView("mv_form_session_counts");
        refreshView("mv_question_scale_distribution");
        refreshView("mv_question_choice_distribution");
        refreshView("mv_question_rating_stats");
        refreshView("mv_form_org_session_counts");
    }

    private void refreshView(String viewName) {
        if (!ALLOWED_VIEWS.contains(viewName)) {
            log.error("AnalyticsRefresh: refused to refresh unknown view '{}'", viewName);
            return;
        }
        try {
            log.info("AnalyticsRefresh: refreshing {}", viewName);
            jdbc.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY " + viewName);
            log.info("AnalyticsRefresh: {} refreshed", viewName);
        } catch (Exception e) {
            log.error("AnalyticsRefresh: failed to refresh {} — {}", viewName, e.getMessage(), e);
        }
    }
}

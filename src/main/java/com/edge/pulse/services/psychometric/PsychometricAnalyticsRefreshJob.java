package com.edge.pulse.services.psychometric;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Periodically refreshes the two psychometric materialized views:
 * <ul>
 *   <li>{@code mv_psychometric_scale_stats} — per-(test, scale) sten histogram and stats</li>
 *   <li>{@code mv_psychometric_test_summary} — per-test result status counts</li>
 * </ul>
 *
 * <p>{@code REFRESH MATERIALIZED VIEW CONCURRENTLY} cannot run inside a transaction block,
 * so this method intentionally has no {@code @Transactional} annotation.
 *
 * <p>Refresh interval: {@code pulse.psychometric.view-refresh-ms} (default 10 minutes).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PsychometricAnalyticsRefreshJob {

    private static final Set<String> ALLOWED_VIEWS = Set.of(
            "mv_psychometric_scale_stats",
            "mv_psychometric_test_summary"
    );

    private final JdbcTemplate jdbc;

    @Scheduled(fixedDelayString = "${pulse.psychometric.view-refresh-ms:600000}")
    public void refreshPsychometricViews() {
        refreshView("mv_psychometric_scale_stats");
        refreshView("mv_psychometric_test_summary");
    }

    private void refreshView(String viewName) {
        if (!ALLOWED_VIEWS.contains(viewName)) {
            log.error("PsychometricRefresh: refused to refresh unknown view '{}'", viewName);
            return;
        }
        try {
            log.info("PsychometricRefresh: refreshing {}", viewName);
            jdbc.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY " + viewName);
            log.info("PsychometricRefresh: {} refreshed", viewName);
        } catch (Exception e) {
            log.error("PsychometricRefresh: failed to refresh {} — {}", viewName, e.getMessage(), e);
        }
    }
}

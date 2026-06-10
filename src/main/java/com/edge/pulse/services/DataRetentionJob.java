package com.edge.pulse.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Enforces data-retention rules on a daily cron schedule (default 02:00 UTC).
 *
 * <ul>
 *   <li>Survey responses ({@code response_session} + cascaded child tables) — deleted after 2 years</li>
 *   <li>Audit logs ({@code audit_logs}) — deleted after 7 years</li>
 *   <li>Expired anonymous identity windows ({@code anon_identity}) — deleted; ephemeral Redis pulse IDs expire automatically via TTL</li>
 * </ul>
 *
 * <p>Uses {@code JdbcTemplate} bulk SQL with autocommit per-statement — no {@code @Transactional}
 * needed, matching the established pattern of {@link AnalyticsRefreshJob}.
 */
@Component
@Slf4j
public class DataRetentionJob {

    private final JdbcTemplate jdbc;
    private final int surveyResponseYears;
    private final int auditLogYears;

    public DataRetentionJob(
            JdbcTemplate jdbc,
            @Value("${pulse.retention.survey-response-years:2}") int surveyResponseYears,
            @Value("${pulse.retention.audit-log-years:7}") int auditLogYears) {
        this.jdbc = jdbc;
        this.surveyResponseYears = surveyResponseYears;
        this.auditLogYears = auditLogYears;
    }

    @Scheduled(cron = "${pulse.retention.cron:0 0 2 * * *}")
    public void runRetention() {
        pruneSurveyResponses();
        pruneAuditLogs();
        pruneExpiredAnonTokens();
    }

    // package-private for unit testing
    void pruneSurveyResponses() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusYears(surveyResponseYears);
        int n = jdbc.update("DELETE FROM response_session WHERE started_at < ?", cutoff);
        log.info("DataRetention: pruned {} response_session rows (cutoff={})", n, cutoff);
        // answer_submission + answer_scale/choice/text/rating cascade via DB ON DELETE CASCADE
    }

    void pruneAuditLogs() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusYears(auditLogYears);
        int n = jdbc.update("DELETE FROM audit_logs WHERE created_at < ?", cutoff);
        log.info("DataRetention: pruned {} audit_logs rows (cutoff={})", n, cutoff);
    }

    void pruneExpiredAnonTokens() {
        int deleted = jdbc.update("DELETE FROM anon_identity WHERE window_end < ?",
                LocalDateTime.now(ZoneOffset.UTC));
        // Ephemeral pulse IDs in Redis expire automatically via their 24-hour TTL —
        // no DB rotation needed.
        log.info("DataRetention: deleted {} expired anon_identity rows", deleted);
    }
}

package com.edge.pulse.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DataRetentionJobTest {

    @Mock
    JdbcTemplate jdbc;

    DataRetentionJob job;

    @BeforeEach
    void setUp() {
        job = new DataRetentionJob(jdbc, 2, 7);
        // JdbcTemplate.update() returns 0 by default — no stubs needed
    }

    @Test
    void pruneSurveyResponses_deletesWithTwoYearCutoff() {
        job.pruneSurveyResponses();
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(jdbc).update(contains("response_session"), cutoff.capture());
        assertThat(cutoff.getValue()).isBefore(LocalDateTime.now().minusYears(1));
        assertThat(cutoff.getValue()).isAfter(LocalDateTime.now().minusYears(3));
    }

    @Test
    void pruneAuditLogs_deletesWithSevenYearCutoff() {
        job.pruneAuditLogs();
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(jdbc).update(contains("audit_logs"), cutoff.capture());
        assertThat(cutoff.getValue()).isBefore(LocalDateTime.now().minusYears(6));
        assertThat(cutoff.getValue()).isAfter(LocalDateTime.now().minusYears(8));
    }

    @Test
    void pruneExpiredAnonTokens_deletesExpiredWindows() {
        job.pruneExpiredAnonTokens();
        verify(jdbc).update(contains("anon_identity"), any(LocalDateTime.class));
    }

    @Test
    void runRetention_invokesAllThreeSteps() {
        job.runRetention();
        // response_session, audit_logs, anon_identity each take a LocalDateTime param
        verify(jdbc, times(3)).update(anyString(), any(LocalDateTime.class));
    }
}

package com.edge.pulse.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AnalyticsRefreshJobTest {

    @Mock
    private JdbcTemplate jdbc;

    @InjectMocks
    private AnalyticsRefreshJob job;

    @Test
    void refreshAnalyticsViews_executesAllThreeRefreshStatements() {
        job.refreshAnalyticsViews();

        verify(jdbc).execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_analytics_summary");
        verify(jdbc).execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_form_session_counts");
        verify(jdbc).execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_question_scale_distribution");
    }
}

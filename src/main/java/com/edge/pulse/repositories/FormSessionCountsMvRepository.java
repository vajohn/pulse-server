package com.edge.pulse.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Native-SQL repository for the {@code mv_form_session_counts} materialized view.
 *
 * <p>Used by {@code AnalyticsService.buildAssignmentBreakdown()} to replace two per-assignment
 * COUNT queries with a single indexed MV lookup. When the view has no row for the
 * given (formId, userId) pair (user has never opened the form), the method returns
 * {@code null} and the caller must fall back to live COUNT queries that will return zeros.
 */
@Repository
@RequiredArgsConstructor
public class FormSessionCountsMvRepository {

    private final JdbcTemplate jdbc;

    /**
     * Returns {@code long[]{completedCount, inProgressCount}} for the given (formId, userId),
     * or {@code null} if the MV has no row (user has never started the form).
     */
    @Nullable
    public long[] findCounts(UUID formId, UUID userId) {
        String sql = "SELECT completed_count, in_progress_count " +
                     "FROM mv_form_session_counts " +
                     "WHERE form_id = ? AND user_id = ?";
        List<long[]> rows = jdbc.query(sql,
                (rs, i) -> new long[]{rs.getLong("completed_count"), rs.getLong("in_progress_count")},
                formId, userId);
        return rows.isEmpty() ? null : rows.getFirst();
    }
}

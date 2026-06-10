package com.edge.pulse.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Native-SQL repository for the {@code mv_form_org_session_counts} materialized view.
 *
 * <p>Used by {@code AnalyticsService.buildAssignmentBreakdown()} to replace two per-assignment
 * COUNT queries on {@code response_session} with a single batch MV read per form.
 *
 * <p>The MV stores one row per (form_id, exact org_path) — i.e., the leaf org-unit level
 * only. Subtree aggregations (when {@code includeChildren=true}) are computed in-memory
 * by matching paths with {@code String.startsWith(pathPrefix)}.
 */
@Repository
@RequiredArgsConstructor
public class FormOrgSessionCountsMvRepository {

    private final JdbcTemplate jdbc;

    /**
     * Loads all (org_path → [completedCount, inProgressCount]) rows for the given form
     * in a single query. Returns an empty map when the form has no session data yet.
     *
     * <p>Each map value is {@code long[2]}: index 0 = completedCount, index 1 = inProgressCount.
     */
    public Map<String, long[]> findCountsByFormId(UUID formId) {
        String sql = "SELECT org_path, completed_count, in_progress_count " +
                     "FROM mv_form_org_session_counts " +
                     "WHERE form_id = ?";
        Map<String, long[]> result = new LinkedHashMap<>();
        jdbc.query(sql, rs -> {
            result.put(
                rs.getString("org_path"),
                new long[]{rs.getLong("completed_count"), rs.getLong("in_progress_count")}
            );
        }, formId);
        return result;
    }
}

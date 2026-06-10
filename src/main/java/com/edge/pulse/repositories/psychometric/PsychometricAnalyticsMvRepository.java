package com.edge.pulse.repositories.psychometric;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Native-SQL repository that reads from the two psychometric materialized views:
 * <ul>
 *   <li>{@code mv_psychometric_scale_stats} — per-(test, scale) aggregate stats and sten histogram</li>
 *   <li>{@code mv_psychometric_test_summary} — per-test result status counts</li>
 * </ul>
 *
 * <p>Analytics must be backed by these pre-computed views — never inline aggregations.
 * Rows where {@code result_count < minN} are excluded to protect individual privacy.
 */
@Repository
@RequiredArgsConstructor
public class PsychometricAnalyticsMvRepository {

    private final JdbcTemplate jdbc;

    /**
     * Returns scale stats for a given test from the MV.
     * Each row: [scale_id (UUID), result_count (Long), avg_raw_score (Double), avg_sten (Double),
     *            avg_percentile (Double), stddev_raw_score (Double),
     *            sten_1..sten_10 counts (Long × 10)]
     *
     * @param testId UUID of the psychometric test
     * @param minN   minimum result count to include a row (privacy threshold)
     */
    public List<Object[]> findScaleStatsByTest(UUID testId, int minN) {
        String sql = """
            SELECT scale_id, result_count, avg_raw_score, avg_sten, avg_percentile, stddev_raw_score,
                   sten_1_count, sten_2_count, sten_3_count, sten_4_count, sten_5_count,
                   sten_6_count, sten_7_count, sten_8_count, sten_9_count, sten_10_count
            FROM mv_psychometric_scale_stats
            WHERE test_id = ?
              AND result_count >= ?
            ORDER BY scale_id
            """;
        return jdbc.query(sql, (rs, i) -> new Object[]{
                rs.getObject("scale_id", java.util.UUID.class),
                rs.getLong("result_count"),
                rs.getObject("avg_raw_score"),
                rs.getObject("avg_sten"),
                rs.getObject("avg_percentile"),
                rs.getObject("stddev_raw_score"),
                rs.getLong("sten_1_count"), rs.getLong("sten_2_count"), rs.getLong("sten_3_count"),
                rs.getLong("sten_4_count"), rs.getLong("sten_5_count"), rs.getLong("sten_6_count"),
                rs.getLong("sten_7_count"), rs.getLong("sten_8_count"), rs.getLong("sten_9_count"),
                rs.getLong("sten_10_count")
        }, testId, minN);
    }

    /**
     * Returns the test-level result status summary from the MV.
     * Returns a single Object[]: [total, pending, scored, reviewed, flagged, lastScoredAt, avgFocusLoss]
     */
    public Object[] findTestSummary(UUID testId) {
        String sql = """
            SELECT total_results, pending_count, scored_count, reviewed_count, flagged_count,
                   last_scored_at, avg_focus_loss_count
            FROM mv_psychometric_test_summary
            WHERE test_id = ?
            """;
        List<Object[]> rows = jdbc.query(sql, (rs, i) -> new Object[]{
                rs.getLong("total_results"),
                rs.getLong("pending_count"),
                rs.getLong("scored_count"),
                rs.getLong("reviewed_count"),
                rs.getLong("flagged_count"),
                rs.getTimestamp("last_scored_at"),
                rs.getObject("avg_focus_loss_count")
        }, testId);
        return rows.isEmpty() ? null : rows.getFirst();
    }
}

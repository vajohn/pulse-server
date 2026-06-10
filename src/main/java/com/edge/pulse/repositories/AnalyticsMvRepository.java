package com.edge.pulse.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Native-SQL repository that reads from the {@code mv_analytics_summary}
 * materialized view.  The view stores pre-aggregated
 * (org_path, survey) rows so that dashboard queries avoid a full
 * table scan + GROUP BY on answer_scale / response_session.
 *
 * <p>All three query methods accept an optional {@code pathFilter}
 * path prefix (e.g. {@code "/EDGE/OPS"}) or {@code null} for
 * global (all org units). Queries match the prefix exactly OR any
 * descendant path (prefix + "/...") to avoid boundary ambiguity.
 *
 * <p>Because the view stores {@code avg_score} and {@code answer_count}
 * per row, re-aggregation across rows uses a weighted mean:
 * {@code SUM(avg_score * answer_count) / NULLIF(SUM(answer_count), 0)}.
 * This produces the same result as {@code AVG(sa.value)} over the
 * original rows.
 */
@Repository
@RequiredArgsConstructor
public class AnalyticsMvRepository {

    private final JdbcTemplate jdbc;

    /**
     * Weighted average scale score across the view, optionally scoped
     * by org-unit path prefix.
     *
     * @param pathFilter path prefix such as {@code "/EDGE/OPS"}, or
     *                   {@code null} for global
     * @return weighted average, or empty if the view has no rows
     */
    public Optional<Double> findGlobalAverage(@Nullable String pathFilter) {
        String sql = "SELECT SUM(avg_score * answer_count) / NULLIF(SUM(answer_count), 0) " +
                     "FROM mv_analytics_summary" +
                     (pathFilter != null ? " WHERE (org_path = ? OR org_path LIKE CONCAT(?, '/%'))" : "");
        Double result = pathFilter != null
                ? jdbc.queryForObject(sql, Double.class, pathFilter, pathFilter)
                : jdbc.queryForObject(sql, Double.class);
        return Optional.ofNullable(result);
    }

    /**
     * Per-survey weighted average and respondent count from the view.
     *
     * @param minN       minimum respondent count for a survey to be included
     * @param pathFilter path prefix or {@code null} for global
     * @return list of {@code Object[3]}: surveyTitle (String), avgScore (Double),
     *         respondentCount (Long)
     */
    public List<Object[]> findSurveyAverages(int minN, @Nullable String pathFilter) {
        String sql = "SELECT form_title, " +
                     "SUM(avg_score * answer_count) / NULLIF(SUM(answer_count), 0), " +
                     "SUM(respondent_count) " +
                     "FROM mv_analytics_summary " +
                     (pathFilter != null ? "WHERE (org_path = ? OR org_path LIKE CONCAT(?, '/%')) " : "") +
                     "GROUP BY form_id, form_title " +
                     "HAVING SUM(respondent_count) >= ?";
        if (pathFilter != null) {
            return jdbc.query(sql,
                    (rs, i) -> new Object[]{rs.getString(1), rs.getDouble(2), rs.getLong(3)},
                    pathFilter, pathFilter, minN);
        }
        return jdbc.query(sql,
                (rs, i) -> new Object[]{rs.getString(1), rs.getDouble(2), rs.getLong(3)},
                minN);
    }

    /**
     * Per-org-unit weighted average and respondent count from the view,
     * ordered by average score descending.
     *
     * @param minN       minimum respondent count for an org unit to be included
     * @param pathFilter path prefix or {@code null} for global
     * @return list of {@code Object[3]}: orgUnitName (String), avgScore (Double),
     *         respondentCount (Long)
     */
    public List<Object[]> findOrgUnitScores(int minN, @Nullable String pathFilter) {
        String sql = "SELECT org_unit_name, " +
                     "SUM(avg_score * answer_count) / NULLIF(SUM(answer_count), 0), " +
                     "SUM(respondent_count) " +
                     "FROM mv_analytics_summary " +
                     (pathFilter != null ? "WHERE (org_path = ? OR org_path LIKE CONCAT(?, '/%')) " : "") +
                     "GROUP BY org_path, org_unit_name " +
                     "HAVING SUM(respondent_count) >= ? " +
                     "ORDER BY 2 DESC";
        if (pathFilter != null) {
            return jdbc.query(sql,
                    (rs, i) -> new Object[]{rs.getString(1), rs.getDouble(2), rs.getLong(3)},
                    pathFilter, pathFilter, minN);
        }
        return jdbc.query(sql,
                (rs, i) -> new Object[]{rs.getString(1), rs.getDouble(2), rs.getLong(3)},
                minN);
    }
}

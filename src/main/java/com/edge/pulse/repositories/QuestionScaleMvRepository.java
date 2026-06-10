package com.edge.pulse.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Native-SQL repository for the {@code mv_question_scale_distribution} materialized view.
 *
 * <p>Eliminates the per-question AVG and distribution GROUP BY queries in
 * {@code AnalyticsService.buildScaleReport()} for the global (no org-unit path filter)
 * HR dashboard view. From the returned distribution rows the caller computes:
 * <pre>
 *   responseCount = SUM(score_count)
 *   avg           = SUM(score * score_count) / SUM(score_count)
 * </pre>
 *
 * <p>Path-filtered (manager / scoped HR) views still use live JPQL queries because
 * a static MV cannot accommodate arbitrary org-unit path prefixes.
 */
@Repository
@RequiredArgsConstructor
public class QuestionScaleMvRepository {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    /**
     * Returns distribution rows for the given question from the MV,
     * ordered by score ascending.
     *
     * <p>Each row: {@code Object[2]} — score (Integer), scoreCount (Long).
     * Returns an empty list if the question has no recorded answers.
     */
    public List<Object[]> findDistributionByQuestionId(UUID questionId) {
        String sql = "SELECT score, score_count " +
                     "FROM mv_question_scale_distribution " +
                     "WHERE question_id = ? " +
                     "ORDER BY score ASC";
        return jdbc.query(sql,
                (rs, i) -> new Object[]{rs.getInt("score"), rs.getLong("score_count")},
                questionId);
    }

    /**
     * Batch-loads scale distributions for multiple questions in a single query.
     * Returns a map keyed by question ID; each value is a list of
     * {@code Object[2]} rows: score (Integer), scoreCount (Long), ordered by score ascending.
     * Questions with no recorded answers are absent from the map.
     */
    public Map<UUID, List<Object[]>> findDistributionsByQuestionIds(Collection<UUID> questionIds) {
        if (questionIds.isEmpty()) return Map.of();
        String sql = "SELECT question_id, score, score_count " +
                     "FROM mv_question_scale_distribution " +
                     "WHERE question_id IN (:ids) " +
                     "ORDER BY question_id, score ASC";
        Map<UUID, List<Object[]>> result = new LinkedHashMap<>();
        namedJdbc.query(sql, Map.of("ids", questionIds), rs -> {
            UUID qid = rs.getObject("question_id", UUID.class);
            result.computeIfAbsent(qid, k -> new ArrayList<>())
                  .add(new Object[]{rs.getInt("score"), rs.getLong("score_count")});
        });
        return result;
    }
}

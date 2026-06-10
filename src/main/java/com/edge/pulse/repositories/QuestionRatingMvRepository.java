package com.edge.pulse.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Native-SQL repository for the {@code mv_question_rating_stats} materialized view.
 *
 * <p>Used by {@code AnalyticsService.buildRatingReport()} to replace per-question
 * COUNT + AVG + subject GROUP BY queries with a single batch read when no org-unit
 * path scope is applied.
 *
 * <p>Path-filtered (manager / scoped HR) views still use live JPQL queries because
 * a static MV cannot accommodate arbitrary org-unit path prefixes.
 */
@Repository
@RequiredArgsConstructor
public class QuestionRatingMvRepository {

    private final NamedParameterJdbcTemplate namedJdbc;

    /**
     * Batch-loads rating stats for multiple questions in a single query.
     * Returns a map keyed by question ID; each value is a list of
     * {@code Object[3]} rows: subjectLabel (String), avgStars (Double),
     * totalResponseCount (Long — same value on all rows for a given question),
     * ordered by subject label ascending.
     * Questions with no recorded answers are absent from the map.
     */
    public Map<UUID, List<Object[]>> findStatsByQuestionIds(Collection<UUID> questionIds) {
        if (questionIds.isEmpty()) return Map.of();
        String sql = "SELECT question_id, subject_label, avg_stars, total_response_count " +
                     "FROM mv_question_rating_stats " +
                     "WHERE question_id IN (:ids) " +
                     "ORDER BY question_id, subject_label ASC";
        Map<UUID, List<Object[]>> result = new LinkedHashMap<>();
        namedJdbc.query(sql, Map.of("ids", questionIds), rs -> {
            UUID qid = rs.getObject("question_id", UUID.class);
            result.computeIfAbsent(qid, k -> new ArrayList<>())
                  .add(new Object[]{
                      rs.getString("subject_label"),
                      rs.getDouble("avg_stars"),
                      rs.getLong("total_response_count")
                  });
        });
        return result;
    }
}

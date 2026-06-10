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
 * Native-SQL repository for the {@code mv_question_choice_distribution} materialized view.
 *
 * <p>Used by {@code AnalyticsService.buildChoiceReport()} to replace per-question
 * COUNT + GROUP BY queries with a single batch read when no org-unit path scope is applied.
 *
 * <p>Path-filtered (manager / scoped HR) views still use live JPQL queries because
 * a static MV cannot accommodate arbitrary org-unit path prefixes.
 */
@Repository
@RequiredArgsConstructor
public class QuestionChoiceMvRepository {

    private final NamedParameterJdbcTemplate namedJdbc;

    /**
     * Batch-loads choice distributions for multiple questions in a single query.
     * Returns a map keyed by question ID; each value is a list of
     * {@code Object[2]} rows: optionLabel (String), choiceCount (Long),
     * ordered by choice count descending.
     * Questions with no recorded answers are absent from the map.
     */
    public Map<UUID, List<Object[]>> findDistributionsByQuestionIds(Collection<UUID> questionIds) {
        if (questionIds.isEmpty()) return Map.of();
        String sql = "SELECT question_id, option_label, choice_count " +
                     "FROM mv_question_choice_distribution " +
                     "WHERE question_id IN (:ids) " +
                     "ORDER BY question_id, choice_count DESC";
        Map<UUID, List<Object[]>> result = new LinkedHashMap<>();
        namedJdbc.query(sql, Map.of("ids", questionIds), rs -> {
            UUID qid = rs.getObject("question_id", UUID.class);
            result.computeIfAbsent(qid, k -> new ArrayList<>())
                  .add(new Object[]{rs.getString("option_label"), rs.getLong("choice_count")});
        });
        return result;
    }
}

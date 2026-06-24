package com.edge.pulse.repositories.answer;

import com.edge.pulse.data.models.AnswerScale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnswerScaleRepository extends JpaRepository<AnswerScale, UUID> {

    Optional<AnswerScale> findBySubmissionId(UUID submissionId);

    List<AnswerScale> findBySubmissionIdIn(Collection<UUID> submissionIds);

    @Query("SELECT COUNT(a) FROM AnswerScale a " +
           "WHERE a.submission.question.id = :qid " +
           "AND a.submission.isCurrent = true " +
           "AND a.submission.session.completedAt IS NOT NULL")
    long countByQuestionId(@Param("qid") UUID questionId);

    @Query("SELECT AVG(CAST(a.value AS double)) FROM AnswerScale a " +
           "WHERE a.submission.question.id = :qid " +
           "AND a.submission.isCurrent = true " +
           "AND a.submission.session.completedAt IS NOT NULL")
    Optional<Double> findAverageByQuestionId(@Param("qid") UUID questionId);

    /** Returns rows of [score (int), count (long)] ordered by score ascending. */
    @Query("SELECT a.value, COUNT(a) FROM AnswerScale a " +
           "WHERE a.submission.question.id = :qid " +
           "AND a.submission.isCurrent = true " +
           "AND a.submission.session.completedAt IS NOT NULL " +
           "GROUP BY a.value ORDER BY a.value ASC")
    List<Object[]> findDistributionByQuestionId(@Param("qid") UUID questionId);

    @Query("SELECT AVG(CAST(a.value AS double)) FROM AnswerScale a " +
           "WHERE a.submission.isCurrent = true " +
           "AND a.submission.session.completedAt IS NOT NULL")
    Optional<Double> findGlobalAverage();

    @Query("SELECT COUNT(DISTINCT a.submission.session.id) FROM AnswerScale a " +
           "WHERE a.submission.isCurrent = true " +
           "AND a.submission.session.completedAt IS NOT NULL")
    long countDistinctCompletedSessions();

    /**
     * Returns rows of [orgUnitName (String), avgScore (Double), respondentCount (Long)]
     * for org units with >= minRespondents completed sessions.
     */
    @Query("SELECT rs.user.orgUnit.orgUnitName, AVG(CAST(a.value AS double)), COUNT(DISTINCT rs.id) " +
           "FROM AnswerScale a " +
           "JOIN a.submission sub " +
           "JOIN sub.session rs " +
           "WHERE rs.completedAt IS NOT NULL " +
           "AND rs.user IS NOT NULL " +
           "AND rs.user.orgUnit IS NOT NULL " +
           "AND a.submission.isCurrent = true " +
           "GROUP BY rs.user.orgUnit.id, rs.user.orgUnit.orgUnitName " +
           "HAVING COUNT(DISTINCT rs.id) >= :minN " +
           "ORDER BY AVG(CAST(a.value AS double)) DESC")
    List<Object[]> findOrgUnitScores(@Param("minN") int minRespondents);

    /**
     * Returns rows of [surveyTitle (String), avgScore (Double), respondentCount (Long)]
     * grouped by survey for the team analytics dashboard.
     * Only includes surveys with >= minRespondents completed sessions.
     */
    @Query("SELECT sub.question.form.title, AVG(CAST(a.value AS double)), COUNT(DISTINCT rs.id) " +
           "FROM AnswerScale a " +
           "JOIN a.submission sub " +
           "JOIN sub.session rs " +
           "WHERE rs.completedAt IS NOT NULL " +
           "AND a.submission.isCurrent = true " +
           "GROUP BY sub.question.form.id, sub.question.form.title " +
           "HAVING COUNT(DISTINCT rs.id) >= :minN")
    List<Object[]> findSurveyAverages(@Param("minN") int minRespondents);

    @Query("SELECT COUNT(a) FROM AnswerScale a " +
           "WHERE a.submission.question.id = :qid " +
           "AND a.submission.isCurrent = true " +
           "AND a.submission.session.completedAt IS NOT NULL " +
           "AND (a.submission.session.user.orgUnit.path = :pathPrefix " +
           "     OR a.submission.session.user.orgUnit.path LIKE CONCAT(:pathPrefix, '/%'))")
    long countByQuestionIdAndPath(@Param("qid") UUID questionId, @Param("pathPrefix") String pathPrefix);

    @Query("SELECT AVG(CAST(a.value AS double)) FROM AnswerScale a " +
           "WHERE a.submission.question.id = :qid " +
           "AND a.submission.isCurrent = true " +
           "AND a.submission.session.completedAt IS NOT NULL " +
           "AND (a.submission.session.user.orgUnit.path = :pathPrefix " +
           "     OR a.submission.session.user.orgUnit.path LIKE CONCAT(:pathPrefix, '/%'))")
    Optional<Double> findAverageByQuestionIdAndPath(@Param("qid") UUID questionId, @Param("pathPrefix") String pathPrefix);

    @Query("SELECT a.value, COUNT(a) FROM AnswerScale a " +
           "WHERE a.submission.question.id = :qid " +
           "AND a.submission.isCurrent = true " +
           "AND a.submission.session.completedAt IS NOT NULL " +
           "AND (a.submission.session.user.orgUnit.path = :pathPrefix " +
           "     OR a.submission.session.user.orgUnit.path LIKE CONCAT(:pathPrefix, '/%')) " +
           "GROUP BY a.value ORDER BY a.value ASC")
    List<Object[]> findDistributionByQuestionIdAndPath(@Param("qid") UUID questionId, @Param("pathPrefix") String pathPrefix);

    /**
     * Returns all current AnswerScale rows for a session.
     * Used by the scoring engine to evaluate personality (SCALE/Likert) items — avoids N+1.
     */
    @Query("""
        SELECT ans FROM AnswerScale ans
        JOIN FETCH ans.submission sub
        JOIN FETCH sub.question
        WHERE sub.session.id = :sessionId
          AND sub.isCurrent = true
    """)
    List<AnswerScale> findCurrentBySessionId(@Param("sessionId") UUID sessionId);

    /**
     * CONSOLIDATED accrual (Phase 3, Fix B): the user's CURRENT scale answers for a given set of
     * question ids across ALL their completed sessions on a form, since the consolidation window
     * opened. Used to score a consolidated scale from the FULL accrued answer set (not just the
     * releasing session). One row per (question) — current submissions only.
     */
    @Query("""
        SELECT ans FROM AnswerScale ans
        JOIN FETCH ans.submission sub
        JOIN FETCH sub.question q
        JOIN sub.session rs
        WHERE rs.user.id = :userId
          AND q.form.id = :formId
          AND sub.isCurrent = true
          AND rs.completedAt IS NOT NULL
          AND rs.completedAt >= :since
          AND q.id IN :questionIds
    """)
    List<AnswerScale> findCurrentForUserFormQuestionsSince(@Param("userId") UUID userId,
                                                           @Param("formId") UUID formId,
                                                           @Param("questionIds") Collection<UUID> questionIds,
                                                           @Param("since") LocalDateTime since);

    // -----------------------------------------------------------------------
    // Dashboard analytics: optional path-scope and date-range filtering.
    // Pass pathFilter as "<prefix>" (no trailing %) or null; pass since as a LocalDateTime.
    // -----------------------------------------------------------------------

    // Pass pathFilter as "<prefix>" or null (null skips the path filter).
    // Pass since as a lower-bound date; use LocalDateTime.of(2000,1,1,0,0) to include all records.
    // Queries match the org unit itself (exact) OR any descendant (prefix + "/...").
    @Query("SELECT AVG(CAST(a.value AS double)) FROM AnswerScale a " +
           "JOIN a.submission sub JOIN sub.session rs " +
           "WHERE a.submission.isCurrent = true " +
           "AND rs.completedAt IS NOT NULL " +
           "AND (:pathFilter IS NULL OR rs.user.orgUnit.path = :pathFilter " +
           "     OR rs.user.orgUnit.path LIKE CONCAT(:pathFilter, '/%')) " +
           "AND rs.completedAt >= :since")
    Optional<Double> findGlobalAverageFiltered(@Param("pathFilter") String pathFilter,
                                               @Param("since") LocalDateTime since);

    @Query("SELECT sub.question.form.title, AVG(CAST(a.value AS double)), COUNT(DISTINCT rs.id) " +
           "FROM AnswerScale a " +
           "JOIN a.submission sub " +
           "JOIN sub.session rs " +
           "WHERE rs.completedAt IS NOT NULL " +
           "AND a.submission.isCurrent = true " +
           "AND (:pathFilter IS NULL OR rs.user.orgUnit.path = :pathFilter " +
           "     OR rs.user.orgUnit.path LIKE CONCAT(:pathFilter, '/%')) " +
           "AND rs.completedAt >= :since " +
           "GROUP BY sub.question.form.id, sub.question.form.title " +
           "HAVING COUNT(DISTINCT rs.id) >= :minN")
    List<Object[]> findSurveyAveragesFiltered(@Param("minN") int minRespondents,
                                              @Param("pathFilter") String pathFilter,
                                              @Param("since") LocalDateTime since);

    @Query("SELECT rs.user.orgUnit.orgUnitName, AVG(CAST(a.value AS double)), COUNT(DISTINCT rs.id) " +
           "FROM AnswerScale a " +
           "JOIN a.submission sub " +
           "JOIN sub.session rs " +
           "WHERE rs.completedAt IS NOT NULL " +
           "AND rs.user IS NOT NULL " +
           "AND rs.user.orgUnit IS NOT NULL " +
           "AND a.submission.isCurrent = true " +
           "AND (:pathFilter IS NULL OR rs.user.orgUnit.path = :pathFilter " +
           "     OR rs.user.orgUnit.path LIKE CONCAT(:pathFilter, '/%')) " +
           "AND rs.completedAt >= :since " +
           "GROUP BY rs.user.orgUnit.id, rs.user.orgUnit.orgUnitName " +
           "HAVING COUNT(DISTINCT rs.id) >= :minN " +
           "ORDER BY AVG(CAST(a.value AS double)) DESC")
    List<Object[]> findOrgUnitScoresFiltered(@Param("minN") int minRespondents,
                                             @Param("pathFilter") String pathFilter,
                                             @Param("since") LocalDateTime since);

    // -----------------------------------------------------------------------
    // Org-wide engagement analytics (PULSE-WEB-4).
    // exactOnly = true restricts to the org unit itself (path = :pathFilter);
    // exactOnly = false (the default subtree behaviour) also matches descendants.
    // pathFilter null = global. since lower-bounds completedAt (use the epoch
    // sentinel LocalDateTime.of(2000,1,1,0,0) to include all records).
    // -----------------------------------------------------------------------

    /** Overall scale-answer average across the scope+window. */
    @Query("SELECT AVG(CAST(a.value AS double)) FROM AnswerScale a " +
           "JOIN a.submission sub JOIN sub.session rs " +
           "WHERE a.submission.isCurrent = true " +
           "AND rs.completedAt IS NOT NULL " +
           "AND rs.user IS NOT NULL AND rs.user.orgUnit IS NOT NULL " +
           "AND (:pathFilter IS NULL " +
           "     OR rs.user.orgUnit.path = :pathFilter " +
           "     OR (:exactOnly = false AND rs.user.orgUnit.path LIKE CONCAT(:pathFilter, '/%'))) " +
           "AND rs.completedAt >= :since")
    Optional<Double> findScopedOverallAverage(@Param("pathFilter") String pathFilter,
                                              @Param("exactOnly") boolean exactOnly,
                                              @Param("since") LocalDateTime since);

    /** Distinct completed respondent count across the scope+window (scale answers only). */
    @Query("SELECT COUNT(DISTINCT rs.id) FROM AnswerScale a " +
           "JOIN a.submission sub JOIN sub.session rs " +
           "WHERE a.submission.isCurrent = true " +
           "AND rs.completedAt IS NOT NULL " +
           "AND rs.user IS NOT NULL AND rs.user.orgUnit IS NOT NULL " +
           "AND (:pathFilter IS NULL " +
           "     OR rs.user.orgUnit.path = :pathFilter " +
           "     OR (:exactOnly = false AND rs.user.orgUnit.path LIKE CONCAT(:pathFilter, '/%'))) " +
           "AND rs.completedAt >= :since")
    long countScopedRespondents(@Param("pathFilter") String pathFilter,
                                @Param("exactOnly") boolean exactOnly,
                                @Param("since") LocalDateTime since);

    /** Per-form [formTitle, avgScore, respondentCount] within scope+window. */
    @Query("SELECT sub.question.form.title, AVG(CAST(a.value AS double)), COUNT(DISTINCT rs.id) " +
           "FROM AnswerScale a JOIN a.submission sub JOIN sub.session rs " +
           "WHERE a.submission.isCurrent = true " +
           "AND rs.completedAt IS NOT NULL " +
           "AND rs.user IS NOT NULL AND rs.user.orgUnit IS NOT NULL " +
           "AND (:pathFilter IS NULL " +
           "     OR rs.user.orgUnit.path = :pathFilter " +
           "     OR (:exactOnly = false AND rs.user.orgUnit.path LIKE CONCAT(:pathFilter, '/%'))) " +
           "AND rs.completedAt >= :since " +
           "GROUP BY sub.question.form.id, sub.question.form.title " +
           "ORDER BY sub.question.form.title ASC")
    List<Object[]> findScopedFormAverages(@Param("pathFilter") String pathFilter,
                                          @Param("exactOnly") boolean exactOnly,
                                          @Param("since") LocalDateTime since);

    /** Score histogram [scoreValue, count] across scope+window, ascending. */
    @Query("SELECT a.value, COUNT(a) FROM AnswerScale a " +
           "JOIN a.submission sub JOIN sub.session rs " +
           "WHERE a.submission.isCurrent = true " +
           "AND rs.completedAt IS NOT NULL " +
           "AND rs.user IS NOT NULL AND rs.user.orgUnit IS NOT NULL " +
           "AND (:pathFilter IS NULL " +
           "     OR rs.user.orgUnit.path = :pathFilter " +
           "     OR (:exactOnly = false AND rs.user.orgUnit.path LIKE CONCAT(:pathFilter, '/%'))) " +
           "AND rs.completedAt >= :since " +
           "GROUP BY a.value ORDER BY a.value ASC")
    List<Object[]> findScopedScoreDistribution(@Param("pathFilter") String pathFilter,
                                               @Param("exactOnly") boolean exactOnly,
                                               @Param("since") LocalDateTime since);

    /** Overall average over an explicit [since, until) window — used for trend deltas. */
    @Query("SELECT AVG(CAST(a.value AS double)) FROM AnswerScale a " +
           "JOIN a.submission sub JOIN sub.session rs " +
           "WHERE a.submission.isCurrent = true " +
           "AND rs.completedAt IS NOT NULL " +
           "AND rs.user IS NOT NULL AND rs.user.orgUnit IS NOT NULL " +
           "AND (:pathFilter IS NULL " +
           "     OR rs.user.orgUnit.path = :pathFilter " +
           "     OR (:exactOnly = false AND rs.user.orgUnit.path LIKE CONCAT(:pathFilter, '/%'))) " +
           "AND rs.completedAt >= :since AND rs.completedAt < :until")
    Optional<Double> findScopedOverallAverageInWindow(@Param("pathFilter") String pathFilter,
                                                      @Param("exactOnly") boolean exactOnly,
                                                      @Param("since") LocalDateTime since,
                                                      @Param("until") LocalDateTime until);

    /** Distinct respondent count over an explicit [since, until) window. */
    @Query("SELECT COUNT(DISTINCT rs.id) FROM AnswerScale a " +
           "JOIN a.submission sub JOIN sub.session rs " +
           "WHERE a.submission.isCurrent = true " +
           "AND rs.completedAt IS NOT NULL " +
           "AND rs.user IS NOT NULL AND rs.user.orgUnit IS NOT NULL " +
           "AND (:pathFilter IS NULL " +
           "     OR rs.user.orgUnit.path = :pathFilter " +
           "     OR (:exactOnly = false AND rs.user.orgUnit.path LIKE CONCAT(:pathFilter, '/%'))) " +
           "AND rs.completedAt >= :since AND rs.completedAt < :until")
    long countScopedRespondentsInWindow(@Param("pathFilter") String pathFilter,
                                        @Param("exactOnly") boolean exactOnly,
                                        @Param("since") LocalDateTime since,
                                        @Param("until") LocalDateTime until);

    // -----------------------------------------------------------------------
    // Engagement composite (PULSE-WEB-4 / C-1): NORMALIZED-to-1..5 aggregates.
    //
    // SCALE value v in [minValue,maxValue] is normalized to 1..5 as
    //   1 + 4*(v - minValue)/(maxValue - minValue)
    // computed in SQL. Rows where maxValue <= minValue are skipped (guards the
    // divide-by-zero / degenerate-scale case). Aggregation is returned as a
    // (sumOfNormalized, count) pair so the service can compose it with the
    // psychometric-free RATING source (AnswerRatingRepository) without N+1.
    //
    // I-1 hygiene: these queries use the declared alias `sub.isCurrent` and
    // `rs` for the session (single join), not `a.submission.isCurrent` (which
    // would re-walk the association).
    // -----------------------------------------------------------------------

    /**
     * Single row [sumNormalized (double), count (long)] of SCALE answers in scope+window.
     * Returned as a single-element {@code List<Object[]>} (not a bare {@code Object[]}) to
     * avoid Hibernate's tuple-vs-array unwrapping ambiguity for multi-select aggregates.
     */
    @Query("SELECT COALESCE(SUM(1.0 + 4.0 * (a.value - a.minValue) / (a.maxValue - a.minValue)), 0.0), COUNT(a) " +
           "FROM AnswerScale a JOIN a.submission sub JOIN sub.session rs " +
           "WHERE sub.isCurrent = true " +
           "AND rs.completedAt IS NOT NULL " +
           "AND rs.user IS NOT NULL AND rs.user.orgUnit IS NOT NULL " +
           "AND a.maxValue > a.minValue " +
           "AND (:pathFilter IS NULL " +
           "     OR rs.user.orgUnit.path = :pathFilter " +
           "     OR (:exactOnly = false AND rs.user.orgUnit.path LIKE CONCAT(:pathFilter, '/%'))) " +
           "AND rs.completedAt >= :since AND rs.completedAt < :until")
    List<Object[]> sumNormalizedInWindow(@Param("pathFilter") String pathFilter,
                                         @Param("exactOnly") boolean exactOnly,
                                         @Param("since") LocalDateTime since,
                                         @Param("until") LocalDateTime until);

    /**
     * Per-form [formTitle, sumNormalized (double), count (long)] of SCALE answers.
     * (Per-form distinct respondents are derived separately, combined across BOTH answer
     * sources via {@link #findRespondentFormSessionsInWindow}, to avoid double-counting a
     * session that answered both a SCALE and a RATING question on the same form.)
     */
    @Query("SELECT sub.question.form.title, " +
           "COALESCE(SUM(1.0 + 4.0 * (a.value - a.minValue) / (a.maxValue - a.minValue)), 0.0), " +
           "COUNT(a) " +
           "FROM AnswerScale a JOIN a.submission sub JOIN sub.session rs " +
           "WHERE sub.isCurrent = true " +
           "AND rs.completedAt IS NOT NULL " +
           "AND rs.user IS NOT NULL AND rs.user.orgUnit IS NOT NULL " +
           "AND a.maxValue > a.minValue " +
           "AND (:pathFilter IS NULL " +
           "     OR rs.user.orgUnit.path = :pathFilter " +
           "     OR (:exactOnly = false AND rs.user.orgUnit.path LIKE CONCAT(:pathFilter, '/%'))) " +
           "AND rs.completedAt >= :since AND rs.completedAt < :until " +
           "GROUP BY sub.question.form.id, sub.question.form.title")
    List<Object[]> findNormalizedFormSumsInWindow(@Param("pathFilter") String pathFilter,
                                                  @Param("exactOnly") boolean exactOnly,
                                                  @Param("since") LocalDateTime since,
                                                  @Param("until") LocalDateTime until);

    /**
     * Distinct [formTitle, sessionId] pairs (SCALE source) within scope+window. The service
     * unions these with the RATING source's per-form sessions so the per-form respondent
     * count is the distinct session count across BOTH sources (no double-count). Bounded:
     * one row per (form, respondent) — not per answer.
     */
    @Query("SELECT DISTINCT sub.question.form.title, rs.id " +
           "FROM AnswerScale a JOIN a.submission sub JOIN sub.session rs " +
           "WHERE sub.isCurrent = true " +
           "AND rs.completedAt IS NOT NULL " +
           "AND rs.user IS NOT NULL AND rs.user.orgUnit IS NOT NULL " +
           "AND a.maxValue > a.minValue " +
           "AND (:pathFilter IS NULL " +
           "     OR rs.user.orgUnit.path = :pathFilter " +
           "     OR (:exactOnly = false AND rs.user.orgUnit.path LIKE CONCAT(:pathFilter, '/%'))) " +
           "AND rs.completedAt >= :since AND rs.completedAt < :until")
    List<Object[]> findRespondentFormSessionsInWindow(@Param("pathFilter") String pathFilter,
                                                      @Param("exactOnly") boolean exactOnly,
                                                      @Param("since") LocalDateTime since,
                                                      @Param("until") LocalDateTime until);

    /** Normalized 1..5 histogram [bucket (int 1..5), count (long)] of SCALE answers, rounded. */
    @Query("SELECT CAST(ROUND(1.0 + 4.0 * (a.value - a.minValue) / (a.maxValue - a.minValue)) AS integer), COUNT(a) " +
           "FROM AnswerScale a JOIN a.submission sub JOIN sub.session rs " +
           "WHERE sub.isCurrent = true " +
           "AND rs.completedAt IS NOT NULL " +
           "AND rs.user IS NOT NULL AND rs.user.orgUnit IS NOT NULL " +
           "AND a.maxValue > a.minValue " +
           "AND (:pathFilter IS NULL " +
           "     OR rs.user.orgUnit.path = :pathFilter " +
           "     OR (:exactOnly = false AND rs.user.orgUnit.path LIKE CONCAT(:pathFilter, '/%'))) " +
           "AND rs.completedAt >= :since AND rs.completedAt < :until " +
           "GROUP BY CAST(ROUND(1.0 + 4.0 * (a.value - a.minValue) / (a.maxValue - a.minValue)) AS integer)")
    List<Object[]> findNormalizedDistributionInWindow(@Param("pathFilter") String pathFilter,
                                                       @Param("exactOnly") boolean exactOnly,
                                                       @Param("since") LocalDateTime since,
                                                       @Param("until") LocalDateTime until);

    /** Distinct respondents (SCALE source) over scope+window, returned as a Set of session ids for cross-source union. */
    @Query("SELECT DISTINCT rs.id FROM AnswerScale a " +
           "JOIN a.submission sub JOIN sub.session rs " +
           "WHERE sub.isCurrent = true " +
           "AND rs.completedAt IS NOT NULL " +
           "AND rs.user IS NOT NULL AND rs.user.orgUnit IS NOT NULL " +
           "AND a.maxValue > a.minValue " +
           "AND (:pathFilter IS NULL " +
           "     OR rs.user.orgUnit.path = :pathFilter " +
           "     OR (:exactOnly = false AND rs.user.orgUnit.path LIKE CONCAT(:pathFilter, '/%'))) " +
           "AND rs.completedAt >= :since AND rs.completedAt < :until")
    List<UUID> findRespondentSessionIdsInWindow(@Param("pathFilter") String pathFilter,
                                                @Param("exactOnly") boolean exactOnly,
                                                @Param("since") LocalDateTime since,
                                                @Param("until") LocalDateTime until);
}

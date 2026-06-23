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
}

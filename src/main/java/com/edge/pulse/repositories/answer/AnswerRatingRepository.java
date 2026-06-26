package com.edge.pulse.repositories.answer;

import com.edge.pulse.data.models.answer.AnswerRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnswerRatingRepository extends JpaRepository<AnswerRating, UUID> {

    List<AnswerRating> findBySubmissionId(UUID submissionId);

    List<AnswerRating> findBySubmissionIdIn(Collection<UUID> submissionIds);

    @Query("SELECT COUNT(DISTINCT a.submission.id) FROM AnswerRating a " +
           "WHERE a.submission.question.id = :qid " +
           "AND a.submission.isCurrent = true " +
           "AND a.submission.session.completedAt IS NOT NULL")
    long countResponsesByQuestionId(@Param("qid") UUID questionId);

    @Query("SELECT AVG(CAST(a.stars AS double)) FROM AnswerRating a " +
           "WHERE a.submission.question.id = :qid " +
           "AND a.submission.isCurrent = true " +
           "AND a.submission.session.completedAt IS NOT NULL")
    Optional<Double> findAverageByQuestionId(@Param("qid") UUID questionId);

    /**
     * Returns rows of [subjectLabel (String), avgStars (Double)] per subject label
     * for a given question, across all completed current submissions.
     */
    @Query("SELECT a.subjectLabel, AVG(CAST(a.stars AS double)) FROM AnswerRating a " +
           "WHERE a.submission.question.id = :qid " +
           "AND a.submission.isCurrent = true " +
           "AND a.submission.session.completedAt IS NOT NULL " +
           "GROUP BY a.subjectLabel " +
           "ORDER BY a.subjectLabel ASC")
    List<Object[]> findAverageBySubjectForQuestion(@Param("qid") UUID questionId);

    @Query("SELECT AVG(CAST(a.stars AS double)) FROM AnswerRating a " +
           "WHERE a.submission.isCurrent = true " +
           "AND a.submission.session.completedAt IS NOT NULL")
    Optional<Double> findGlobalAverage();

    @Query("SELECT COUNT(DISTINCT a.submission.session.id) FROM AnswerRating a " +
           "WHERE a.submission.isCurrent = true " +
           "AND a.submission.session.completedAt IS NOT NULL")
    long countDistinctCompletedSessions();

    @Query("SELECT COUNT(DISTINCT a.submission.id) FROM AnswerRating a " +
           "WHERE a.submission.question.id = :qid " +
           "AND a.submission.isCurrent = true " +
           "AND a.submission.session.completedAt IS NOT NULL " +
           "AND (a.submission.session.user.orgUnit.path = :pathPrefix " +
           "     OR a.submission.session.user.orgUnit.path LIKE CONCAT(:pathPrefix, '/%'))")
    long countResponsesByQuestionIdAndPath(@Param("qid") UUID questionId, @Param("pathPrefix") String pathPrefix);

    @Query("SELECT AVG(CAST(a.stars AS double)) FROM AnswerRating a " +
           "WHERE a.submission.question.id = :qid " +
           "AND a.submission.isCurrent = true " +
           "AND a.submission.session.completedAt IS NOT NULL " +
           "AND (a.submission.session.user.orgUnit.path = :pathPrefix " +
           "     OR a.submission.session.user.orgUnit.path LIKE CONCAT(:pathPrefix, '/%'))")
    Optional<Double> findAverageByQuestionIdAndPath(@Param("qid") UUID questionId, @Param("pathPrefix") String pathPrefix);

    @Query("SELECT a.subjectLabel, AVG(CAST(a.stars AS double)) FROM AnswerRating a " +
           "WHERE a.submission.question.id = :qid " +
           "AND a.submission.isCurrent = true " +
           "AND a.submission.session.completedAt IS NOT NULL " +
           "AND (a.submission.session.user.orgUnit.path = :pathPrefix " +
           "     OR a.submission.session.user.orgUnit.path LIKE CONCAT(:pathPrefix, '/%')) " +
           "GROUP BY a.subjectLabel " +
           "ORDER BY a.subjectLabel ASC")
    List<Object[]> findAverageBySubjectForQuestionAndPath(@Param("qid") UUID questionId, @Param("pathPrefix") String pathPrefix);

    // -----------------------------------------------------------------------
    // Engagement composite (PULSE-WEB-4 / C-1): per-submission RATING rows for
    // normalization to 1..5. RATING is sourced from SURVEY forms.
    //
    // MULTI_RATING stores one row per subjectLabel within a submission, so we
    // first collapse to one value per submission (AVG over subjects). maxStars
    // is constant within a submission, so MAX(a.maxStars) returns it safely.
    // The service then normalizes each submission to 1..5 as
    //   1 + 4*(avgStars - 1)/(maxStars - 1)   (identity when maxStars == 5).
    //
    // Returns one bounded row per qualifying submission (NOT per answer/subject)
    // → no N+1; the caller aggregates in-memory and composes with the SCALE
    // source. I-1 hygiene: single session join via the `rs` alias.
    // Rows are restricted to SURVEY-type forms (RATING is a survey instrument).
    // -----------------------------------------------------------------------

    /** Per-submission [formTitle, sessionId (UUID), avgStars (double), maxStars (int)] within scope+window. */
    @Query("SELECT sub.question.form.title, rs.id, AVG(CAST(a.stars AS double)), MAX(a.maxStars) " +
           "FROM AnswerRating a JOIN a.submission sub JOIN sub.session rs " +
           "WHERE sub.isCurrent = true " +
           "AND rs.completedAt IS NOT NULL " +
           "AND rs.user IS NOT NULL AND rs.user.orgUnit IS NOT NULL " +
           "AND sub.question.form.formType = com.edge.pulse.data.enums.FormType.SURVEY " +
           "AND (:pathFilter IS NULL " +
           "     OR rs.user.orgUnit.path = :pathFilter " +
           "     OR (:exactOnly = false AND rs.user.orgUnit.path LIKE CONCAT(:pathFilter, '/%'))) " +
           "AND rs.completedAt >= :since AND rs.completedAt < :until " +
           "GROUP BY sub.id, sub.question.form.title, rs.id")
    List<Object[]> findSubmissionRatingsInWindow(@Param("pathFilter") String pathFilter,
                                                 @Param("exactOnly") boolean exactOnly,
                                                 @Param("since") LocalDateTime since,
                                                 @Param("until") LocalDateTime until);

    /**
     * Per-submission [formTitle, sessionId (UUID), userId (UUID), avgStars (double), maxStars (int)]
     * within scope+window. The extra {@code userId} column allows the service to build a
     * distinct-user set (participation-rate numerator) alongside the session set (k-anonymity gate).
     */
    @Query("SELECT sub.question.form.title, rs.id, rs.user.id, AVG(CAST(a.stars AS double)), MAX(a.maxStars) " +
           "FROM AnswerRating a JOIN a.submission sub JOIN sub.session rs " +
           "WHERE sub.isCurrent = true " +
           "AND rs.completedAt IS NOT NULL " +
           "AND rs.user IS NOT NULL AND rs.user.orgUnit IS NOT NULL " +
           "AND sub.question.form.formType = com.edge.pulse.data.enums.FormType.SURVEY " +
           "AND (:pathFilter IS NULL " +
           "     OR rs.user.orgUnit.path = :pathFilter " +
           "     OR (:exactOnly = false AND rs.user.orgUnit.path LIKE CONCAT(:pathFilter, '/%'))) " +
           "AND rs.completedAt >= :since AND rs.completedAt < :until " +
           "GROUP BY sub.id, sub.question.form.title, rs.id, rs.user.id")
    List<Object[]> findSubmissionRatingsWithUserInWindow(@Param("pathFilter") String pathFilter,
                                                         @Param("exactOnly") boolean exactOnly,
                                                         @Param("since") LocalDateTime since,
                                                         @Param("until") LocalDateTime until);
}

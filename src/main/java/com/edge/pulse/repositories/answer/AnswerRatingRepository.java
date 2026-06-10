package com.edge.pulse.repositories.answer;

import com.edge.pulse.data.models.answer.AnswerRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}

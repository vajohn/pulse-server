package com.edge.pulse.repositories.answer;

import com.edge.pulse.data.models.AnswerChoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnswerChoiceRepository extends JpaRepository<AnswerChoice, UUID> {

    Optional<AnswerChoice> findBySubmissionId(UUID submissionId);

    List<AnswerChoice> findBySubmissionIdIn(Collection<UUID> submissionIds);

    @Query("SELECT COUNT(a) FROM AnswerChoice a " +
           "WHERE a.submission.question.id = :qid " +
           "AND a.submission.isCurrent = true " +
           "AND a.submission.session.completedAt IS NOT NULL")
    long countByQuestionId(@Param("qid") UUID questionId);

    /**
     * Returns rows of [optionLabel (String), count (Long)] sorted by count descending.
     * Only counts current, completed submissions.
     */
    @Query("SELECT a.candidateAnswer.label, COUNT(a) FROM AnswerChoice a " +
           "WHERE a.submission.question.id = :qid " +
           "AND a.submission.isCurrent = true " +
           "AND a.submission.session.completedAt IS NOT NULL " +
           "GROUP BY a.candidateAnswer.id, a.candidateAnswer.label " +
           "ORDER BY COUNT(a) DESC")
    List<Object[]> findDistributionByQuestionId(@Param("qid") UUID questionId);

    @Query("SELECT COUNT(a) FROM AnswerChoice a " +
           "WHERE a.submission.question.id = :qid " +
           "AND a.submission.isCurrent = true " +
           "AND a.submission.session.completedAt IS NOT NULL " +
           "AND (a.submission.session.user.orgUnit.path = :pathPrefix " +
           "     OR a.submission.session.user.orgUnit.path LIKE CONCAT(:pathPrefix, '/%'))")
    long countByQuestionIdAndPath(@Param("qid") UUID questionId, @Param("pathPrefix") String pathPrefix);

    @Query("SELECT a.candidateAnswer.label, COUNT(a) FROM AnswerChoice a " +
           "WHERE a.submission.question.id = :qid " +
           "AND a.submission.isCurrent = true " +
           "AND a.submission.session.completedAt IS NOT NULL " +
           "AND (a.submission.session.user.orgUnit.path = :pathPrefix " +
           "     OR a.submission.session.user.orgUnit.path LIKE CONCAT(:pathPrefix, '/%')) " +
           "GROUP BY a.candidateAnswer.id, a.candidateAnswer.label " +
           "ORDER BY COUNT(a) DESC")
    List<Object[]> findDistributionByQuestionIdAndPath(@Param("qid") UUID questionId, @Param("pathPrefix") String pathPrefix);

    /**
     * Returns all current AnswerChoice rows for a session with candidateAnswer eagerly joined.
     * Used by the scoring engine to evaluate cognitive (CHOICE) items — avoids N+1.
     */
    @Query("""
        SELECT ac FROM AnswerChoice ac
        JOIN FETCH ac.candidateAnswer
        JOIN FETCH ac.submission sub
        JOIN FETCH sub.question
        WHERE sub.session.id = :sessionId
          AND sub.isCurrent = true
    """)
    List<AnswerChoice> findCurrentBySessionId(@Param("sessionId") UUID sessionId);

}

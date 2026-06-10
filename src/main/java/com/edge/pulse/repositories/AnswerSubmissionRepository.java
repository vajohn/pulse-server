package com.edge.pulse.repositories;

import com.edge.pulse.data.models.AnswerSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnswerSubmissionRepository extends JpaRepository<AnswerSubmission, UUID> {

    List<AnswerSubmission> findBySessionIdAndIsCurrentTrue(UUID sessionId);

    Optional<AnswerSubmission> findBySessionIdAndQuestionIdAndIsCurrentTrue(
            UUID sessionId, UUID questionId);

    @Query("SELECT a FROM AnswerSubmission a " +
           "WHERE a.session.id = :sessionId " +
           "AND a.question.id = :questionId " +
           "ORDER BY a.version ASC")
    List<AnswerSubmission> findVersionHistory(
            @Param("sessionId") UUID sessionId,
            @Param("questionId") UUID questionId);

    @Modifying
    @Query("UPDATE AnswerSubmission a SET a.isCurrent = false " +
           "WHERE a.session.id = :sessionId " +
           "AND a.question.id = :questionId " +
           "AND a.isCurrent = true")
    void markPreviousVersionsNotCurrent(
            @Param("sessionId") UUID sessionId,
            @Param("questionId") UUID questionId);

    @Query("SELECT COUNT(DISTINCT a.question.id) FROM AnswerSubmission a " +
           "WHERE a.session.id = :sessionId AND a.isCurrent = true")
    int countDistinctQuestionsAnswered(@Param("sessionId") UUID sessionId);

    /** Batch: returns [sessionId, distinctAnsweredCount] pairs for multiple sessions. */
    @Query("SELECT a.session.id, COUNT(DISTINCT a.question.id) FROM AnswerSubmission a " +
           "WHERE a.session.id IN :sessionIds AND a.isCurrent = true " +
           "GROUP BY a.session.id")
    List<Object[]> countDistinctQuestionsAnsweredBySessionIds(@Param("sessionIds") Collection<UUID> sessionIds);
}

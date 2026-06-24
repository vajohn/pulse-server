package com.edge.pulse.repositories.answer;

import com.edge.pulse.data.models.AnswerAdjective;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnswerAdjectiveRepository extends JpaRepository<AnswerAdjective, UUID> {

    Optional<AnswerAdjective> findBySubmissionId(UUID submissionId);

    /**
     * Loads all current adjective answers for a session — used by the scoring
     * engine to batch-evaluate ADJECTIVE_CHECKLIST items without N+1.
     */
    @Query("""
        SELECT aa FROM AnswerAdjective aa
        JOIN FETCH aa.submission sub
        JOIN FETCH sub.question
        WHERE sub.session.id = :sessionId
          AND sub.isCurrent = true
    """)
    List<AnswerAdjective> findCurrentBySessionId(@Param("sessionId") UUID sessionId);

    /**
     * CONSOLIDATED accrual (Phase 3, Fix B): the user's CURRENT adjective answers for a set of
     * question ids across ALL their completed sessions on a form since the consolidation window opened.
     */
    @Query("""
        SELECT aa FROM AnswerAdjective aa
        JOIN FETCH aa.submission sub
        JOIN FETCH sub.question q
        JOIN sub.session rs
        WHERE rs.user.id = :userId
          AND q.form.id = :formId
          AND sub.isCurrent = true
          AND rs.completedAt IS NOT NULL
          AND rs.completedAt >= :since
          AND q.id IN :questionIds
    """)
    List<AnswerAdjective> findCurrentForUserFormQuestionsSince(@Param("userId") UUID userId,
                                                               @Param("formId") UUID formId,
                                                               @Param("questionIds") Collection<UUID> questionIds,
                                                               @Param("since") LocalDateTime since);
}

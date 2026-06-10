package com.edge.pulse.repositories;

import com.edge.pulse.data.models.Question;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    List<Question> findByFormIdOrderByDisplayOrderAsc(UUID formId);

    @Query("SELECT q FROM Question q WHERE q.form.id = :formId " +
           "AND (q.effectiveDate IS NULL OR q.effectiveDate <= CURRENT_TIMESTAMP) " +
           "AND (q.expirationDate IS NULL OR q.expirationDate > CURRENT_TIMESTAMP) " +
           "ORDER BY q.displayOrder ASC")
    List<Question> findActiveByFormId(@Param("formId") UUID formId);

    /**
     * Same as {@link #findActiveByFormId} but eagerly fetches {@code candidateAnswers}
     * in the same query to avoid N+1 when building the psychometric session payload.
     * Uses {@code DISTINCT} to deduplicate Question rows produced by the LEFT JOIN.
     *
     * <p>Used exclusively by {@code PsychometricSessionService.startSession()}.
     */
    @EntityGraph(attributePaths = {"candidateAnswers"})
    @Query("SELECT DISTINCT q FROM Question q WHERE q.form.id = :formId " +
           "AND (q.effectiveDate IS NULL OR q.effectiveDate <= CURRENT_TIMESTAMP) " +
           "AND (q.expirationDate IS NULL OR q.expirationDate > CURRENT_TIMESTAMP) " +
           "ORDER BY q.displayOrder ASC")
    List<Question> findActiveByFormIdWithAnswers(@Param("formId") UUID formId);

    /** Batch: returns [formId, activeCount] pairs for multiple forms. */
    @Query("SELECT q.form.id, COUNT(q) FROM Question q " +
           "WHERE q.form.id IN :formIds " +
           "AND (q.effectiveDate IS NULL OR q.effectiveDate <= CURRENT_TIMESTAMP) " +
           "AND (q.expirationDate IS NULL OR q.expirationDate > CURRENT_TIMESTAMP) " +
           "GROUP BY q.form.id")
    List<Object[]> countActiveByFormIds(@Param("formIds") Collection<UUID> formIds);
}

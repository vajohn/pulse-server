package com.edge.pulse.repositories;

import com.edge.pulse.data.models.Form;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FormRepository extends JpaRepository<Form, UUID> {

    /**
     * Loads all forms with their questions in a single JOIN FETCH query,
     * eliminating the N+1 on the admin form list. Candidate answers are
     * excluded here (two-bag fetch causes MultipleBagFetchException); they
     * load lazily per question on the rare detail view.
     */
    @Query("SELECT DISTINCT f FROM Form f LEFT JOIN FETCH f.questions ORDER BY f.createdAt DESC")
    List<Form> findAllWithQuestions();

    /**
     * Loads a single form with its questions eagerly (no N+1 on questions).
     * Candidate answers load lazily within the same transaction when accessed.
     */
    @Query("SELECT f FROM Form f LEFT JOIN FETCH f.questions WHERE f.id = :id")
    Optional<Form> findByIdWithQuestions(@Param("id") UUID id);

    /**
     * Counts ALL questions per form (no date-window filter) — used for display totals only.
     * Returns [formId, count] pairs; single query, no N+1.
     *
     * <p><strong>Do NOT use for business logic that must match the assignment guard.</strong>
     * Use {@link #countActiveQuestionsByFormId} (single form) or
     * {@link com.edge.pulse.repositories.QuestionRepository#countActiveByFormIds} (batch)
     * for date-filtered active counts.
     */
    @Query("SELECT q.form.id, COUNT(q) FROM Question q WHERE q.form.id IN :formIds GROUP BY q.form.id")
    List<Object[]> countQuestionsByFormIdIn(@Param("formIds") Collection<UUID> formIds);

    /**
     * Returns the number of currently active questions for a single form.
     * A question is active when its effectiveDate window is open (mirrors
     * {@link com.edge.pulse.repositories.QuestionRepository#countActiveByFormIds}).
     * Used to guard assignment creation.
     */
    @Query("SELECT COUNT(q) FROM Question q WHERE q.form.id = :id " +
           "AND (q.effectiveDate IS NULL OR q.effectiveDate <= CURRENT_TIMESTAMP) " +
           "AND (q.expirationDate IS NULL OR q.expirationDate > CURRENT_TIMESTAMP)")
    long countActiveQuestionsByFormId(@Param("id") UUID id);
}

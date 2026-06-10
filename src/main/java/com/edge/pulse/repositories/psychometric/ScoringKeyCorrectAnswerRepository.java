package com.edge.pulse.repositories.psychometric;

import com.edge.pulse.data.models.psychometric.ScoringKeyCorrectAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ScoringKeyCorrectAnswerRepository extends JpaRepository<ScoringKeyCorrectAnswer, UUID> {

    /**
     * Batch-loads all correct-answer sets for the given scoring key items.
     * Used by the scoring engine to evaluate CHOICE_MULTIPLE items without N+1.
     */
    @Query("""
        SELECT ca FROM ScoringKeyCorrectAnswer ca
        JOIN FETCH ca.candidateAnswer
        WHERE ca.scoringKeyItem.id IN :itemIds
    """)
    List<ScoringKeyCorrectAnswer> findByItemIdIn(@Param("itemIds") Collection<UUID> itemIds);
}

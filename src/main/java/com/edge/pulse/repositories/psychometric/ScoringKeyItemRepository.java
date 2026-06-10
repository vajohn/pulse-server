package com.edge.pulse.repositories.psychometric;

import com.edge.pulse.data.models.psychometric.ScoringKeyItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScoringKeyItemRepository extends JpaRepository<ScoringKeyItem, UUID> {

    /**
     * Loads all scoring key items for a key version with scale, question, and correctAnswer
     * eagerly joined — prevents N+1 during the scoring loop.
     */
    @Query("""
        SELECT ki FROM ScoringKeyItem ki
        JOIN FETCH ki.scale
        JOIN FETCH ki.question
        LEFT JOIN FETCH ki.correctAnswer
        WHERE ki.scoringKey.id = :keyId
    """)
    List<ScoringKeyItem> findByScoringKeyIdWithDetails(@Param("keyId") UUID scoringKeyId);

    boolean existsByScoringKeyIdAndQuestionIdAndScaleId(UUID scoringKeyId, UUID questionId, UUID scaleId);

    long countByScoringKeyId(UUID scoringKeyId);
}

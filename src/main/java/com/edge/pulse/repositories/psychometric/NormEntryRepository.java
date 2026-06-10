package com.edge.pulse.repositories.psychometric;

import com.edge.pulse.data.models.psychometric.NormEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NormEntryRepository extends JpaRepository<NormEntry, UUID> {

    List<NormEntry> findByNormTableId(UUID normTableId);

    /** Loads all entries for a norm table with scale eagerly joined — prevents N+1 in the UI service path. */
    @Query("""
        SELECT ne FROM NormEntry ne
        JOIN FETCH ne.scale
        WHERE ne.normTable.id = :normTableId
        ORDER BY ne.scale.name, ne.stenScore
    """)
    List<NormEntry> findByNormTableIdWithScale(@Param("normTableId") UUID normTableId);

    List<NormEntry> findByNormTableIdAndScaleId(UUID normTableId, UUID scaleId);

    /**
     * Looks up the norm entry for a given raw score within the specified norm table and scale.
     * Uses half-open intervals: rawScoreMin <= rawScore < rawScoreMax, with a fallback to
     * the highest-sten row when the score equals the absolute maximum (e.g. perfect score).
     * Ordering by stenScore DESC and returning the first result ensures that when a score
     * lands exactly on a shared boundary the higher sten wins, preventing the
     * NonUniqueResultException that a strict equality-on-both-bounds query would throw.
     */
    @Query("""
        SELECT ne FROM NormEntry ne
        WHERE ne.normTable.id = :normTableId
          AND ne.scale.id = :scaleId
          AND ne.rawScoreMin <= :rawScore
          AND ne.rawScoreMax >= :rawScore
        ORDER BY ne.stenScore DESC
    """)
    List<NormEntry> findNormsForRawScore(@Param("normTableId") UUID normTableId,
                                          @Param("scaleId") UUID scaleId,
                                          @Param("rawScore") BigDecimal rawScore);

    long countByNormTableId(UUID normTableId);
}

package com.edge.pulse.repositories.psychometric;

import com.edge.pulse.data.models.psychometric.CompetencyScaleWeight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface CompetencyScaleWeightRepository
        extends JpaRepository<CompetencyScaleWeight, CompetencyScaleWeight.CompetencyScaleWeightId> {

    /** Used by ScoringService to look up all competency weights for a set of scored scales. */
    @Query("SELECT w FROM CompetencyScaleWeight w JOIN FETCH w.competency WHERE w.scale.id IN :scaleIds")
    List<CompetencyScaleWeight> findByScaleIdIn(@Param("scaleIds") Collection<UUID> scaleIds);

    /** Used by admin UI to show weights for a given competency. */
    @Query("SELECT w FROM CompetencyScaleWeight w JOIN FETCH w.scale WHERE w.competency.id = :competencyId")
    List<CompetencyScaleWeight> findByCompetencyIdWithScale(@Param("competencyId") UUID competencyId);

    /**
     * Batch version used by listCompetencies() to load all weights in one query (avoids N+1).
     * JOIN FETCHes scale so scale.name is available without further lazy loading.
     */
    @Query("SELECT w FROM CompetencyScaleWeight w JOIN FETCH w.scale WHERE w.competency.id IN :competencyIds")
    List<CompetencyScaleWeight> findAllByCompetencyIdIn(@Param("competencyIds") Collection<UUID> competencyIds);

    @Modifying
    @Query("DELETE FROM CompetencyScaleWeight w WHERE w.competency.id = :cId AND w.scale.id = :sId")
    void deleteByCompetencyIdAndScaleId(@Param("cId") UUID cId, @Param("sId") UUID sId);
}

package com.edge.pulse.repositories.psychometric;

import com.edge.pulse.data.models.psychometric.CompetencyScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompetencyScoreRepository extends JpaRepository<CompetencyScore, UUID> {

    @Query("SELECT cs FROM CompetencyScore cs JOIN FETCH cs.competency " +
           "WHERE cs.result.id = :resultId ORDER BY cs.competency.displayOrder")
    List<CompetencyScore> findByResultIdWithCompetency(@Param("resultId") UUID resultId);

    @Modifying
    @Query("DELETE FROM CompetencyScore cs WHERE cs.result.id = :resultId")
    void deleteByResultId(@Param("resultId") UUID resultId);

    /** Used by deleteCompetency to remove orphaned scores before the FK-constrained competency delete. */
    @Modifying
    @Query("DELETE FROM CompetencyScore cs WHERE cs.competency.id = :competencyId")
    void deleteByCompetencyId(@Param("competencyId") UUID competencyId);
}

package com.edge.pulse.repositories.psychometric;

import com.edge.pulse.data.models.psychometric.ScaleScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScaleScoreRepository extends JpaRepository<ScaleScore, UUID> {

    @Query("SELECT ss FROM ScaleScore ss JOIN FETCH ss.scale WHERE ss.result.id = :resultId ORDER BY ss.scale.displayOrder")
    List<ScaleScore> findByResultIdWithScale(@Param("resultId") UUID resultId);

    List<ScaleScore> findByResultId(UUID resultId);

    @Modifying
    @Query("DELETE FROM ScaleScore ss WHERE ss.result.id = :resultId")
    void deleteByResultId(@Param("resultId") UUID resultId);
}

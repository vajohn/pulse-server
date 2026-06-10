package com.edge.pulse.repositories.psychometric;

import com.edge.pulse.data.models.psychometric.PsychometricScale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PsychometricScaleRepository extends JpaRepository<PsychometricScale, UUID> {

    List<PsychometricScale> findByTestId(UUID testId);

    /** Returns all leaf scales (those that have a parent). */
    List<PsychometricScale> findByTestIdAndParentScaleIsNotNull(UUID testId);

    /** Returns all root scales (those with no parent — rolled up from children). */
    List<PsychometricScale> findByTestIdAndParentScaleIsNull(UUID testId);

    Optional<PsychometricScale> findByTestIdAndName(UUID testId, String name);

    @Query("SELECT s FROM PsychometricScale s WHERE s.test.id = :testId ORDER BY s.displayOrder")
    List<PsychometricScale> findByTestIdOrdered(@Param("testId") UUID testId);

    /** Counts scales per test — single query, no N+1. Returns [testId, count] pairs. */
    @Query("SELECT s.test.id, COUNT(s) FROM PsychometricScale s WHERE s.test.id IN :testIds GROUP BY s.test.id")
    List<Object[]> countByTestIdIn(@Param("testIds") Collection<UUID> testIds);

    /** Fast count for a single test (used in single-test operations). */
    @Query("SELECT COUNT(s) FROM PsychometricScale s WHERE s.test.id = :testId")
    int countByTestId(@Param("testId") UUID testId);
}

package com.edge.pulse.repositories.psychometric;

import com.edge.pulse.data.enums.NormStatus;
import com.edge.pulse.data.models.psychometric.NormTableVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NormTableVersionRepository extends JpaRepository<NormTableVersion, UUID> {

    List<NormTableVersion> findByTestId(UUID testId);

    Optional<NormTableVersion> findFirstByTestIdAndStatus(UUID testId, NormStatus status);

    @Query("SELECT MAX(v.version) FROM NormTableVersion v WHERE v.test.id = :testId")
    Optional<Integer> findMaxVersionByTestId(@Param("testId") UUID testId);

    /**
     * Deprecates all VALIDATED norm tables for a test — called before promoting a new one.
     *
     * <p>{@code flushAutomatically = true} is required alongside {@code clearAutomatically = true}.
     * In {@code AssessmentImporter.importPackage} this method runs immediately after
     * {@code saveScoringKey} has staged (but not yet flushed) the {@code ScoringKeyVersion} and
     * {@code ScoringKeyItem} inserts in the same transaction. Because this bulk UPDATE targets an
     * unrelated table, Hibernate's query-space auto-flush would NOT flush those scoring-key inserts;
     * the subsequent {@code clear()} would then discard them, so the import would report success
     * with the scoring key missing. Flushing before clearing guarantees they are written.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE NormTableVersion v SET v.status = 'DEPRECATED' WHERE v.test.id = :testId AND v.status = 'VALIDATED'")
    int deprecateValidatedNormsByTestId(@Param("testId") UUID testId);
}

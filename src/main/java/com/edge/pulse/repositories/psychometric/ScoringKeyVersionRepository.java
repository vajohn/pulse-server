package com.edge.pulse.repositories.psychometric;

import com.edge.pulse.data.enums.ScoringKeyStatus;
import com.edge.pulse.data.models.psychometric.ScoringKeyVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScoringKeyVersionRepository extends JpaRepository<ScoringKeyVersion, UUID> {

    List<ScoringKeyVersion> findByTestId(UUID testId);

    Optional<ScoringKeyVersion> findFirstByTestIdAndStatus(UUID testId, ScoringKeyStatus status);

    @Query("SELECT MAX(v.version) FROM ScoringKeyVersion v WHERE v.test.id = :testId")
    Optional<Integer> findMaxVersionByTestId(@Param("testId") UUID testId);

    /** Deprecates all currently ACTIVE keys for a test — called atomically before publishing a new one. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ScoringKeyVersion v SET v.status = 'DEPRECATED' WHERE v.test.id = :testId AND v.status = 'ACTIVE'")
    int deprecateActiveKeysByTestId(@Param("testId") UUID testId);
}

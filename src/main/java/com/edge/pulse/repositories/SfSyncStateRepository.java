package com.edge.pulse.repositories;

import com.edge.pulse.data.models.SfSyncState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SfSyncStateRepository extends JpaRepository<SfSyncState, UUID> {

    /** Most recent completed (SUCCESS or FAILED) sync run, newest first. */
    Optional<SfSyncState> findFirstByStatusOrderByStartedAtDesc(String status);

    /** Most recent run regardless of status. */
    Optional<SfSyncState> findFirstByOrderByStartedAtDesc();

    /** Most recent sync by type (FULL or DELTA). */
    Optional<SfSyncState> findFirstBySyncTypeOrderByStartedAtDesc(String syncType);
}

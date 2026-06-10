package com.edge.pulse.repositories;

import com.edge.pulse.data.models.UserSfProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSfProfileRepository extends JpaRepository<UserSfProfile, UUID> {

    /** Returns the most recent SF sync timestamp — used by delta sync to compute the since-window. */
    @Query("SELECT MAX(p.sfSyncedAt) FROM UserSfProfile p")
    Optional<LocalDateTime> findMaxSfSyncedAt();
}

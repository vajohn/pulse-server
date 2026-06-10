package com.edge.pulse.repositories;

import com.edge.pulse.data.models.AnonIdentity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnonIdentityRepository extends JpaRepository<AnonIdentity, UUID> {

    Optional<AnonIdentity> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AnonIdentity a WHERE a.orgUnit.id = :orgUnitId " +
           "AND a.form.id = :formId " +
           "AND a.windowStart = :windowStart " +
           "ORDER BY a.sequenceInWindow DESC")
    List<AnonIdentity> findLastInWindowForUpdate(
            @Param("orgUnitId") UUID orgUnitId,
            @Param("formId") UUID formId,
            @Param("windowStart") LocalDateTime windowStart);
}

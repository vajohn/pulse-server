package com.edge.pulse.repositories;

import com.edge.pulse.data.models.Session;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionRepository extends JpaRepository<Session, UUID> {
    Optional<Session> findByRefreshTokenHashAndRevokedAtIsNull(String refreshTokenHash);
    void deleteByExpiresAtBefore(LocalDateTime dateTime);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Session s WHERE s.refreshTokenHash = :hash AND s.revokedAt IS NULL")
    Optional<Session> findForRefreshByHash(@Param("hash") String refreshTokenHash);
}

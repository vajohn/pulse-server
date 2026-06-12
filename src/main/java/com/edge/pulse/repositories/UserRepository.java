package com.edge.pulse.repositories;

import com.edge.pulse.data.models.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByAzureAdId(String azureAdId);
    Optional<User> findByEmail(String email);
    List<User> findByOrgUnitId(UUID orgUnitId);
    long countByActiveTrue();

    // Spring Data derived: WHERE u.orgUnit.path LIKE :prefix%  AND u.active = true
    long countByOrgUnitPathStartingWithAndActiveTrue(String prefix);
    long countByOrgUnitIdAndActiveTrue(UUID orgUnitId);

    List<User> findByDisplayNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String displayName, String email);

    // Entra sync — stale count: never synced OR synced before threshold
    long countByEntraLastSyncedAtBeforeOrEntraLastSyncedAtIsNull(LocalDateTime threshold);

    @Query("SELECT MAX(u.entraLastSyncedAt) FROM User u")
    Optional<LocalDateTime> findMaxEntraLastSyncedAt();

    /** Returns the SF company_code for the given user (used by SCOPE_ENTITY resolution). */
    @Query("SELECT u.companyCode FROM User u WHERE u.id = :userId")
    Optional<String> findCompanyCodeById(UUID userId);

    Optional<User> findBySfUserId(String sfUserId);
    Optional<User> findByEmployeeId(String employeeId);

    /** Batch lookup by SF user IDs — used to scope profile pre-load to the current sync batch. */
    List<User> findAllBySfUserIdIn(Collection<String> sfUserIds);

    /** Eager-loads roles to avoid LazyInitializationException during role reconciliation. */
    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesByEmail(String email);
}

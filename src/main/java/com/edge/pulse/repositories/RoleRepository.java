package com.edge.pulse.repositories;

import com.edge.pulse.data.models.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByName(String name);

    /** Number of users assigned to the given role. Used by delete-guard in RoleManagementService. */
    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.id = :roleId")
    int countUsersByRoleId(UUID roleId);

    /**
     * Returns user counts for a batch of role IDs in one query.
     * Used by listRoles() to avoid an N+1 countUsersByRoleId call per role.
     * Result rows are Object[] pairs: [roleId (UUID), count (Long)].
     */
    @Query("SELECT r.id, COUNT(u) FROM Role r LEFT JOIN r.users u WHERE r.id IN :roleIds GROUP BY r.id")
    List<Object[]> countUsersByRoleIdsBatch(List<UUID> roleIds);

    /** Convenience: returns a Map<roleId, userCount> for the given role IDs. */
    default Map<UUID, Integer> userCountsByRoleId(List<UUID> roleIds) {
        return countUsersByRoleIdsBatch(roleIds).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> ((Long) row[1]).intValue()
                ));
    }

    /** All role names for display. */
    @Query("SELECT r.name FROM Role r ORDER BY r.name")
    List<String> findAllNames();
}

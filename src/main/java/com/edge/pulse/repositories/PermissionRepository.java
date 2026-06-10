package com.edge.pulse.repositories;

import com.edge.pulse.data.models.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    Optional<Permission> findByName(String name);

    boolean existsByName(String name);

    /** Batch-load permissions by name. Used by setPermissions() to avoid per-name queries. */
    Set<Permission> findAllByNameIn(Set<String> names);

    /** Returns the names of all persisted permissions — used by PermissionSeeder to detect gaps. */
    @Query("SELECT p.name FROM Permission p")
    Set<String> findAllNames();

    /** Returns permission names for all permissions assigned to the named role. */
    @Query("SELECT p.name FROM Permission p JOIN p.roles r WHERE r.name = :roleName")
    Set<String> findPermissionNamesByRoleName(String roleName);
}

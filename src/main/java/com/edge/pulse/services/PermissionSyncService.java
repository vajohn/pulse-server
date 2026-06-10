package com.edge.pulse.services;

import com.edge.pulse.data.enums.PermissionName;
import com.edge.pulse.data.models.Permission;
import com.edge.pulse.repositories.PermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Ensures all 55 permissions defined in {@link PermissionName} exist in the DB
 * before any request is served.
 *
 * <p>This seeder is additive-only: it only inserts permissions that are missing.
 * It never deletes permissions — doing so would cascade to role_permissions and
 * user_permissions rows, breaking existing access grants.
 *
 * <p>Runs on {@link ApplicationReadyEvent} so it executes AFTER Flyway migrations
 * complete and AFTER the JPA context is fully initialised. Safe to run on every
 * startup (idempotent).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PermissionSyncService {

    private final PermissionRepository permissionRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void syncPermissions() {
        int inserted = 0;
        int repaired = 0;

        for (PermissionName perm : PermissionName.values()) {
            // Single findByName replaces the previous existsByName + findByName two-query pattern.
            // Optional.isEmpty() → insert; Optional.isPresent() + stale description → repair.
            Optional<Permission> existing = permissionRepository.findByName(perm.name());
            if (existing.isEmpty()) {
                permissionRepository.save(
                        Permission.builder()
                                .name(perm.name())
                                .description(perm.getDescription())
                                .build()
                );
                inserted++;
            } else if (existing.get().getName().equals(existing.get().getDescription())) {
                // Repair stale descriptions left by V10 migration which seeded permissions
                // using `description = name` (raw string) instead of the canonical description.
                // On each subsequent startup we correct any permission whose description still
                // equals its name so that human-readable descriptions propagate automatically.
                existing.get().setDescription(perm.getDescription());
                permissionRepository.save(existing.get());
                repaired++;
            }
        }

        if (inserted > 0 || repaired > 0) {
            log.info("PermissionSyncService: inserted={} repaired-descriptions={} (total defined: {})",
                    inserted, repaired, PermissionName.values().length);
        } else {
            log.debug("PermissionSyncService: all {} permissions already present and correct",
                    PermissionName.values().length);
        }
    }
}

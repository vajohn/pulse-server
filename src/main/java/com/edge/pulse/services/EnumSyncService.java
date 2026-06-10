package com.edge.pulse.services;

import com.edge.pulse.data.enums.PermissionName;
import com.edge.pulse.data.models.Permission;
import com.edge.pulse.repositories.PermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Additive permission seeder — inserts any {@link PermissionName} values missing
 * from the {@code permissions} table on every startup.
 *
 * <p>Roles and role-permission assignments are now managed via {@code AdminRoleController}
 * and Flyway migrations — not by this service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnumSyncService {
    private final PermissionRepository permissionRepository;
    private final PermissionCacheService permissionCacheService;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void syncEnums() {
        syncPermissions();
        permissionCacheService.evictCache();
        log.info("Permission sync complete");
    }

    private void syncPermissions() {
        Set<String> existing = permissionRepository.findAllNames();

        for (PermissionName permName : PermissionName.values()) {
            if (!existing.contains(permName.name())) {
                Permission p = Permission.builder()
                        .name(permName.name())
                        .description(permName.getDescription())
                        .build();
                permissionRepository.save(p);
                log.info("Inserted permission: {}", permName.name());
                continue;
            }
            // Update description if it changed
            permissionRepository.findByName(permName.name()).ifPresent(perm -> {
                if (!permName.getDescription().equals(perm.getDescription())) {
                    perm.setDescription(permName.getDescription());
                    permissionRepository.save(perm);
                    log.info("Updated permission description: {}", permName.name());
                }
            });
        }
    }
}

package com.edge.pulse.services;

import com.edge.pulse.data.enums.PermissionName;
import com.edge.pulse.data.models.Permission;
import com.edge.pulse.repositories.PermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionSyncServiceTest {

    @Mock private PermissionRepository permissionRepository;

    private PermissionSyncService service;

    @BeforeEach
    void setUp() {
        service = new PermissionSyncService(permissionRepository);
    }

    // Helper: build a Permission whose description already matches the canonical value (healthy state).
    private Permission healthyPermission(PermissionName perm) {
        return Permission.builder()
                .name(perm.name())
                .description(perm.getDescription())
                .build();
    }

    // Helper: build a Permission whose description == name (stale V10 seed state).
    private Permission stalePermission(PermissionName perm) {
        return Permission.builder()
                .name(perm.name())
                .description(perm.name())   // V10 migration: description = name
                .build();
    }

    @Test
    void syncPermissions_allMissing_insertsAllPermissions() {
        // All permissions absent from DB
        when(permissionRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(permissionRepository.save(any(Permission.class))).thenAnswer(i -> i.getArgument(0));

        service.syncPermissions();

        int expectedCount = PermissionName.values().length;
        verify(permissionRepository, times(expectedCount)).save(any(Permission.class));
    }

    @Test
    void syncPermissions_allPresent_withCorrectDescriptions_insertsNothing() {
        // All permissions present with canonical descriptions — nothing to do
        when(permissionRepository.findByName(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            PermissionName perm = PermissionName.valueOf(name);
            return Optional.of(healthyPermission(perm));
        });

        service.syncPermissions();

        verify(permissionRepository, never()).save(any(Permission.class));
    }

    @Test
    void syncPermissions_idempotent_secondCallSavesNothing() {
        // First call: all missing
        when(permissionRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(permissionRepository.save(any(Permission.class))).thenAnswer(i -> i.getArgument(0));
        service.syncPermissions();

        // Second call: all now present with correct descriptions
        reset(permissionRepository);
        when(permissionRepository.findByName(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            PermissionName perm = PermissionName.valueOf(name);
            return Optional.of(healthyPermission(perm));
        });

        service.syncPermissions();

        verify(permissionRepository, never()).save(any(Permission.class));
    }

    @Test
    void syncPermissions_insertedPermissionsCarryCanonicalDescription() {
        // All present with correct descriptions, except FORM_READ which is missing
        when(permissionRepository.findByName(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            if ("FORM_READ".equals(name)) return Optional.empty();
            PermissionName perm = PermissionName.valueOf(name);
            return Optional.of(healthyPermission(perm));
        });
        when(permissionRepository.save(any(Permission.class))).thenAnswer(i -> i.getArgument(0));

        service.syncPermissions();

        ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
        verify(permissionRepository, times(1)).save(captor.capture());
        Permission saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("FORM_READ");
        assertThat(saved.getDescription()).isEqualTo(PermissionName.FORM_READ.getDescription());
        assertThat(saved.getDescription()).isNotEqualTo("FORM_READ"); // must not be the raw name
    }

    @Test
    void syncPermissions_repairsStaleDescriptions_fromV10Migration() {
        // All permissions present but with stale description = name (V10 inline seed state).
        // Every one must be repaired with the canonical description.
        when(permissionRepository.findByName(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            PermissionName perm = PermissionName.valueOf(name);
            return Optional.of(stalePermission(perm));
        });
        when(permissionRepository.save(any(Permission.class))).thenAnswer(i -> i.getArgument(0));

        service.syncPermissions();

        int expectedCount = PermissionName.values().length;
        ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
        verify(permissionRepository, times(expectedCount)).save(captor.capture());

        // Every saved permission must now carry the canonical description, not the raw name
        for (Permission saved : captor.getAllValues()) {
            PermissionName perm = PermissionName.valueOf(saved.getName());
            assertThat(saved.getDescription())
                    .as("description for %s must not equal its name", saved.getName())
                    .isNotEqualTo(saved.getName());
            assertThat(saved.getDescription()).isEqualTo(perm.getDescription());
        }
    }

    @Test
    void syncPermissions_mixedState_insertsAndRepairsIndependently() {
        // USR_READ: missing → will be inserted
        // FORM_READ: present with stale description → will be repaired
        // All others: present with correct description → will be skipped
        when(permissionRepository.findByName(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            if ("USR_READ".equals(name)) return Optional.empty();
            PermissionName perm = PermissionName.valueOf(name);
            if ("FORM_READ".equals(name)) return Optional.of(stalePermission(perm));
            return Optional.of(healthyPermission(perm));
        });
        when(permissionRepository.save(any(Permission.class))).thenAnswer(i -> i.getArgument(0));

        service.syncPermissions();

        // Exactly 2 saves: one insert (USR_READ) + one repair (FORM_READ)
        ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
        verify(permissionRepository, times(2)).save(captor.capture());
        List<String> savedNames = captor.getAllValues().stream()
                .map(Permission::getName).toList();
        assertThat(savedNames).containsExactlyInAnyOrder("USR_READ", "FORM_READ");
    }

    @Test
    void syncPermissions_covers59Permissions() {
        // 55 original + SCOPE_ALL + AI_ALL + SYS_ALL + ASSESS_APPROVE = 59
        assertThat(PermissionName.values()).hasSize(59);
    }
}

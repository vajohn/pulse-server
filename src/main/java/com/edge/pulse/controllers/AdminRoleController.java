package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.AdminRoleDto;
import com.edge.pulse.data.dto.AssignableRoleDto;
import com.edge.pulse.data.dto.CreateRoleRequest;
import com.edge.pulse.data.dto.PermissionDto;
import com.edge.pulse.data.dto.SetRolePermissionsRequest;
import com.edge.pulse.services.RoleManagementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin endpoints for role and permission management.
 *
 * <p>All endpoints require at minimum a ROLE_READ or ROLE_* permission.
 * Mutations require ROLE_ALL or the specific ROLE_CREATE / ROLE_UPDATE / ROLE_DELETE permission.
 *
 * <p>Base path: {@code /api/admin/roles}
 */
@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
@Tag(name = "Admin — Roles")
public class AdminRoleController {

    private final RoleManagementService roleManagementService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ALL', 'ROLE_READ')")
    public ResponseEntity<List<AdminRoleDto>> listRoles() {
        return ResponseEntity.ok(roleManagementService.listRoles());
    }

    /** Lightweight {id, name} list for the user-detail role-assignment dialog. */
    @GetMapping("/assignable")
    @PreAuthorize("hasAnyAuthority('USR_ROLE_ASSIGN', 'USR_ALL')")
    public ResponseEntity<List<AssignableRoleDto>> listAssignableRoles() {
        return ResponseEntity.ok(roleManagementService.listAssignableRoles());
    }

    /** All permissions in the DB for the role-editor permission picker. */
    @GetMapping("/permissions")
    @PreAuthorize("hasAnyAuthority('ROLE_ALL', 'ROLE_READ')")
    public ResponseEntity<List<PermissionDto>> listAllPermissions() {
        return ResponseEntity.ok(roleManagementService.listAllPermissions());
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ALL', 'ROLE_CREATE')")
    public ResponseEntity<AdminRoleDto> createRole(
            @Valid @RequestBody CreateRoleRequest request,
            Authentication auth) {
        UUID actorId = (UUID) auth.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roleManagementService.createRole(request, actorId));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAnyAuthority('ROLE_ALL', 'ROLE_UPDATE')")
    public ResponseEntity<AdminRoleDto> setPermissions(
            @PathVariable UUID id,
            @Valid @RequestBody SetRolePermissionsRequest request,
            Authentication auth) {
        UUID actorId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(roleManagementService.setPermissions(id, request, auth, actorId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ALL', 'ROLE_DELETE')")
    public ResponseEntity<Void> deleteRole(
            @PathVariable UUID id,
            Authentication auth) {
        UUID actorId = (UUID) auth.getPrincipal();
        roleManagementService.deleteRole(id, actorId);
        return ResponseEntity.noContent().build();
    }
}

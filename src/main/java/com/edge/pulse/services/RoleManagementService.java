package com.edge.pulse.services;

import com.edge.pulse.data.dto.AdminRoleDto;
import com.edge.pulse.data.dto.AssignableRoleDto;
import com.edge.pulse.data.dto.CreateRoleRequest;
import com.edge.pulse.data.dto.PermissionDto;
import com.edge.pulse.data.dto.SetRolePermissionsRequest;
import com.edge.pulse.data.models.Permission;
import com.edge.pulse.data.models.Role;
import com.edge.pulse.repositories.PermissionRepository;
import com.edge.pulse.repositories.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Business logic for role CRUD and permission assignment.
 *
 * <p>Security invariants (enforced here, not in the controller):
 * <ul>
 *   <li>Only callers with {@code ROLE_ALL} can grant SCOPE_* permissions.</li>
 *   <li>Callers without {@code ROLE_ALL} cannot grant permissions they don't themselves hold.</li>
 *   <li>A role cannot be deleted while it has assigned users.</li>
 *   <li>The cache entry for the mutated role is evicted after every write.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RoleManagementService {

    private static final Set<String> SCOPE_PERMISSIONS =
            Set.of("SCOPE_TEAM", "SCOPE_ENTITY", "SCOPE_ORG_WIDE", "SCOPE_ALL");

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionCacheService permissionCacheService;
    private final AuditService auditService;

    // ── Read ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AdminRoleDto> listRoles() {
        List<Role> roles = roleRepository.findAll();
        if (roles.isEmpty()) return List.of();
        Map<UUID, Integer> userCounts = roleRepository.userCountsByRoleId(
                roles.stream().map(Role::getId).toList());
        return roles.stream()
                .map(r -> toDto(r, userCounts.getOrDefault(r.getId(), 0)))
                .sorted(Comparator.comparing(AdminRoleDto::name))
                .toList();
    }

    /**
     * Returns all roles as lightweight {id, name} projections.
     * Used by the user-detail screen (USR_ROLE_ASSIGN gate) so admins can pick a role
     * without needing ROLE_READ access to the full role definition.
     */
    @Transactional(readOnly = true)
    public List<AssignableRoleDto> listAssignableRoles() {
        return roleRepository.findAll().stream()
                .map(r -> new AssignableRoleDto(r.getId(), r.getName()))
                .sorted(Comparator.comparing(AssignableRoleDto::name))
                .toList();
    }

    /**
     * Returns all 55 permissions from the DB with name, description, and group prefix.
     * Used by the role-editor dialog (ROLE_READ gate) to populate the permission picker
     * dynamically instead of relying on the Flutter-side hardcoded constant list.
     */
    @Transactional(readOnly = true)
    public List<PermissionDto> listAllPermissions() {
        return permissionRepository.findAll().stream()
                .map(p -> new PermissionDto(p.getName(), p.getDescription(),
                        PermissionDto.groupOf(p.getName())))
                .sorted(Comparator.comparing(PermissionDto::group).thenComparing(PermissionDto::name))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminRoleDto getRole(UUID id) {
        Role role = findOrThrow(id);
        int userCount = roleRepository.countUsersByRoleId(id);
        return toDto(role, userCount);
    }

    // ── Create ────────────────────────────────────────────────────────────────

    public AdminRoleDto createRole(CreateRoleRequest request, UUID actorId) {
        if (roleRepository.findByName(request.name()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Role already exists: " + request.name());
        }
        Role role;
        try {
            role = roleRepository.save(
                    Role.builder().name(request.name()).permissions(new HashSet<>()).build());
        } catch (DataIntegrityViolationException e) {
            // Concurrent request with same name raced past the findByName check
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Role already exists: " + request.name());
        }
        auditService.logAction(actorId, "ROLE_CREATE", "ROLE", role.getId(),
                auditService.buildDetail("roleName", request.name()), null);
        return toDto(role, 0);
    }

    // ── Update permissions ────────────────────────────────────────────────────

    public AdminRoleDto setPermissions(UUID roleId, SetRolePermissionsRequest request,
                                       Authentication callerAuth, UUID actorId) {
        Set<String> callerAuthorities = callerAuth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toSet());

        Set<String> requestedPermissions = request.permissions();

        // SCOPE permissions require ROLE_ALL
        if (!callerAuthorities.contains("ROLE_ALL")) {
            Set<String> scopeRequested = requestedPermissions.stream()
                    .filter(SCOPE_PERMISSIONS::contains)
                    .collect(Collectors.toSet());
            if (!scopeRequested.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "SCOPE permissions require ROLE_ALL authority to grant: " + scopeRequested);
            }

            // Privilege escalation guard — cannot grant permissions not held by caller
            Set<String> forbidden = requestedPermissions.stream()
                    .filter(p -> !callerAuthorities.contains(p))
                    .collect(Collectors.toSet());
            if (!forbidden.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Cannot grant permissions you do not hold: " + forbidden);
            }
        }

        // Batch-load all requested permissions in one query
        Set<Permission> newPerms = permissionRepository.findAllByNameIn(requestedPermissions);
        Set<String> foundNames = newPerms.stream().map(Permission::getName).collect(Collectors.toSet());
        Set<String> unknown = requestedPermissions.stream()
                .filter(p -> !foundNames.contains(p))
                .collect(Collectors.toSet());
        if (!unknown.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown permissions: " + unknown);
        }

        Role role = findOrThrow(roleId);
        role.setPermissions(newPerms);
        role = roleRepository.save(role);
        permissionCacheService.evictRole(role.getName());

        auditService.logAction(actorId, "ROLE_PERMISSIONS_SET", "ROLE", roleId,
                auditService.buildDetail("roleName", role.getName(),
                        "permissionCount", String.valueOf(newPerms.size())), null);
        int userCount = roleRepository.countUsersByRoleId(roleId);
        return toDto(role, userCount);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    public void deleteRole(UUID roleId, UUID actorId) {
        Role role = findOrThrow(roleId);
        int userCount = roleRepository.countUsersByRoleId(roleId);
        if (userCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Role has " + userCount + " assigned user(s) and cannot be deleted");
        }
        String roleName = role.getName();
        roleRepository.delete(role);
        permissionCacheService.evictRole(roleName);
        auditService.logAction(actorId, "ROLE_DELETE", "ROLE", roleId,
                auditService.buildDetail("roleName", roleName), null);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private AdminRoleDto toDto(Role role, int userCount) {
        List<PermissionDto> perms = role.getPermissions() == null
                ? List.of()
                : role.getPermissions().stream()
                        .map(p -> new PermissionDto(p.getName(), p.getDescription(),
                                PermissionDto.groupOf(p.getName())))
                        .sorted(Comparator.comparing(PermissionDto::name))
                        .toList();
        return new AdminRoleDto(role.getId(), role.getName(), perms, userCount);
    }

    private Role findOrThrow(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Role not found: " + id));
    }
}

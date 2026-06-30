package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.*;
import com.edge.pulse.services.OrgUnitScopeService;
import com.edge.pulse.services.RoleChangeService;
import com.edge.pulse.services.UserService;
import com.edge.pulse.mappers.RoleChangeMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {
    private final UserService userService;
    private final OrgUnitScopeService scopeService;
    private final RoleChangeService roleChangeService;
    private final RoleChangeMapper roleChangeMapper;

    @GetMapping
    @PreAuthorize("hasAuthority('USR_READ')")
    public ResponseEntity<Page<UserSummary>> getUsers(
            @RequestParam(required = false) UUID orgUnitId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> roleNames,
            @RequestParam(required = false) Boolean noRoles,
            @RequestParam(required = false) String permission,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean neverLoggedIn,
            @RequestParam(required = false) Integer staleDays,
            @RequestParam(required = false) String syncSource,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        UUID authUserId = (UUID) auth.getPrincipal();
        UserFilter filter = new UserFilter(
                q,
                roleNames == null ? List.of() : roleNames,
                noRoles != null && noRoles,
                permission,
                status,
                neverLoggedIn != null && neverLoggedIn,
                staleDays,
                syncSource);
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("email").ascending());
        return ResponseEntity.ok(userService.getUsersPage(orgUnitId, authUserId, filter, pageable));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('USR_READ')")
    public ResponseEntity<Page<UserSummary>> searchUsers(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(userService.searchUsers(q, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USR_READ')")
    public ResponseEntity<UserSummary> getUser(@PathVariable UUID id, Authentication auth) {
        UUID authUserId = (UUID) auth.getPrincipal();
        if (!scopeService.canAccess(authUserId, id)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(userService.getUserSummary(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USR_UPDATE')")
    public ResponseEntity<UserSummary> updateUser(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateUserRequest request,
            Authentication auth) {
        UUID authUserId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(userService.updateUser(id, request, authUserId));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('USR_ROLE_ASSIGN')")
    public ResponseEntity<UserSummary> updateUserRoles(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateUserRolesRequest request,
            Authentication auth) {
        UUID authUserId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(userService.assignRoles(id, request.roles(), authUserId));
    }

    @PutMapping("/{id}/org-unit")
    @PreAuthorize("hasAuthority('ORG_MOVE_USER')")
    public ResponseEntity<UserSummary> moveUserToOrgUnit(
            @PathVariable UUID id,
            @RequestBody @Valid MoveUserOrgUnitRequest request,
            Authentication auth) {
        UUID authUserId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(userService.moveUserToOrgUnit(id, request.orgUnitId(), authUserId));
    }

    @PostMapping("/{id}/role-request")
    @PreAuthorize("hasAuthority('USR_ROLE_ASSIGN')")
    public ResponseEntity<RoleChangeRequestDto> requestRoleChange(
            @PathVariable UUID id,
            @RequestBody @Valid CreateRoleChangeRequest request,
            Authentication auth) {
        UUID authUserId = (UUID) auth.getPrincipal();
        if (!scopeService.canAccess(authUserId, id)) {
            return ResponseEntity.status(403).build();
        }
        var changeRequest = roleChangeService.requestRoleChange(
                authUserId, id, request.roleName(), request.action());
        return ResponseEntity.ok(roleChangeMapper.toDto(changeRequest));
    }
}

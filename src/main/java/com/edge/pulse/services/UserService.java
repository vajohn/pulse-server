package com.edge.pulse.services;

import com.edge.pulse.data.dto.UpdateUserRequest;
import com.edge.pulse.data.dto.UserSummary;
import com.edge.pulse.data.models.OrganizationalUnit;
import com.edge.pulse.data.models.Role;
import com.edge.pulse.data.models.User;
import com.edge.pulse.repositories.OrganizationalUnitRepository;
import com.edge.pulse.repositories.RoleRepository;
import com.edge.pulse.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OrganizationalUnitRepository orgUnitRepository;
    private final OrgUnitScopeService scopeService;
    private final PermissionCacheService permissionCacheService;
    private final AuditService auditService;
    private final FormCacheService formCacheService;

    // -----------------------------------------------------------------------
    // Admin user management
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<UserSummary> getUsers(UUID orgUnitId, UUID authUserId) {
        List<User> users = orgUnitId != null
                ? userRepository.findByOrgUnitId(orgUnitId)
                : userRepository.findAll();
        return scopeService.filterByScope(authUserId, users)
                .stream()
                .map(permissionCacheService::toUserSummary)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<UserSummary> getUsersPage(UUID orgUnitId, UUID authUserId, Pageable pageable) {
        List<UserSummary> all = getUsers(orgUnitId, authUserId);
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), all.size());
        List<UserSummary> pageContent = start >= all.size() ? List.of() : all.subList(start, end);
        return new PageImpl<>(pageContent, pageable, all.size());
    }

    @Transactional(readOnly = true)
    public Page<UserSummary> searchUsers(String query, Pageable pageable) {
        String q = query == null ? "" : query.strip();
        if (q.length() > 50) {
            q = q.substring(0, 50);
        }
        List<User> matches = q.isEmpty()
                ? userRepository.findAll()
                : userRepository.findByDisplayNameContainingIgnoreCaseOrEmailContainingIgnoreCase(q, q);
        List<UserSummary> summaries = matches.stream()
                .map(permissionCacheService::toUserSummary)
                .collect(Collectors.toList());
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), summaries.size());
        List<UserSummary> pageContent = start >= summaries.size() ? List.of() : summaries.subList(start, end);
        return new PageImpl<>(pageContent, pageable, summaries.size());
    }

    @Transactional(readOnly = true)
    public UserSummary getUserSummary(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return permissionCacheService.toUserSummary(user);
    }

    public UserSummary updateUser(UUID id, UpdateUserRequest request, UUID authUserId) {
        if (!scopeService.canAccess(authUserId, id)) {
            throw new AccessDeniedException("Access denied to user: " + id);
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (request.displayName() != null) user.setDisplayName(request.displayName());
        if (request.department() != null)  user.setDepartment(request.department());
        if (request.division() != null)    user.setDivision(request.division());
        if (request.costCenter() != null)  user.setCostCenter(request.costCenter());
        if (request.employeeId() != null)  user.setEmployeeId(request.employeeId());

        userRepository.save(user);
        auditService.logAction(authUserId, "USER_UPDATE", "USER", id, null, null);
        return permissionCacheService.toUserSummary(user);
    }

    public UserSummary assignRoles(UUID userId, List<String> roleNames, UUID authUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Set<Role> newRoles = new HashSet<>();
        for (String roleName : roleNames) {
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));
            newRoles.add(role);
        }
        user.setRoles(newRoles);
        userRepository.save(user);
        // Evict immediately so the next request resolves the new permissions.
        permissionCacheService.evictCache();
        auditService.logAction(authUserId, "ROLE_ASSIGN", "USER", userId,
                auditService.buildDetail("roles", roleNames), null);
        return permissionCacheService.toUserSummary(user);
    }

    public UserSummary moveUserToOrgUnit(UUID userId, UUID newOrgUnitId, UUID authUserId) {
        if (!scopeService.canAccess(authUserId, userId)) {
            throw new AccessDeniedException("Access denied to user: " + userId);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        OrganizationalUnit orgUnit = orgUnitRepository.findById(newOrgUnitId)
                .orElseThrow(() -> new IllegalArgumentException("Org unit not found: " + newOrgUnitId));
        user.setOrgUnit(orgUnit);
        userRepository.save(user);
        formCacheService.evict(FormCacheService.userAssignmentsKey(userId));
        auditService.logAction(authUserId, "USER_MOVE_ORG_UNIT", "USER", userId,
                auditService.buildDetail("newOrgUnitId", newOrgUnitId.toString()), null);
        return permissionCacheService.toUserSummary(user);
    }

    // -----------------------------------------------------------------------
    // Self-service profile (UserController)
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public UserSummary getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return permissionCacheService.toUserSummary(user);
    }

    public UserSummary updateProfile(UUID userId, String displayName, String department) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (displayName != null) user.setDisplayName(displayName);
        if (department != null)  user.setDepartment(department);
        user = userRepository.save(user);
        return permissionCacheService.toUserSummary(user);
    }
}

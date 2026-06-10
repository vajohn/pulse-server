package com.edge.pulse.services;

import com.edge.pulse.data.enums.RoleChangeAction;
import com.edge.pulse.data.enums.RoleChangeStatus;
import com.edge.pulse.data.models.Role;
import com.edge.pulse.data.models.RoleChangeRequest;
import com.edge.pulse.data.models.User;
import com.edge.pulse.repositories.RoleChangeRequestRepository;
import com.edge.pulse.repositories.RoleRepository;
import com.edge.pulse.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleChangeService {
    private final RoleChangeRequestRepository roleChangeRequestRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditService auditService;
    private final PermissionCacheService permissionCacheService;

    /**
     * Manager creates a role change request → status = PENDING.
     */
    @Transactional
    public RoleChangeRequest requestRoleChange(UUID requesterId, UUID targetUserId,
                                                String roleName, RoleChangeAction action) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new IllegalArgumentException("Requester not found"));
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Target user not found"));

        // Validate role exists
        roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));

        RoleChangeRequest request = RoleChangeRequest.builder()
                .targetUser(targetUser)
                .requestedBy(requester)
                .roleName(roleName)
                .action(action)
                .status(RoleChangeStatus.PENDING)
                .build();

        RoleChangeRequest saved = roleChangeRequestRepository.save(request);

        auditService.logAction(requesterId, "ROLE_CHANGE_REQUEST",
                "ROLE_CHANGE_REQUEST", saved.getId(),
                auditService.buildDetail("targetUserId", targetUserId, "roleName", roleName, "action", action),
                null);

        return saved;
    }

    /**
     * HR approves or rejects a role change request.
     * If approved, applies the role change immediately.
     */
    @Transactional
    public RoleChangeRequest reviewRequest(UUID reviewerId, UUID requestId,
                                            RoleChangeStatus status, String comment) {
        if (status != RoleChangeStatus.APPROVED && status != RoleChangeStatus.REJECTED) {
            throw new IllegalArgumentException("Status must be APPROVED or REJECTED");
        }

        RoleChangeRequest request = roleChangeRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        if (request.getStatus() != RoleChangeStatus.PENDING) {
            throw new IllegalStateException("Request has already been reviewed");
        }

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new IllegalArgumentException("Reviewer not found"));

        request.setReviewedBy(reviewer);
        request.setStatus(status);
        request.setReviewComment(comment);
        request.setReviewedAt(LocalDateTime.now());

        if (status == RoleChangeStatus.APPROVED) {
            applyRoleChange(request);
        }

        RoleChangeRequest saved = roleChangeRequestRepository.save(request);

        auditService.logAction(reviewerId, "ROLE_CHANGE_" + status,
                "ROLE_CHANGE_REQUEST", requestId,
                auditService.buildDetail("targetUserId", request.getTargetUser().getId(), "roleName", request.getRoleName()),
                null);

        return saved;
    }

    public List<RoleChangeRequest> getPendingRequests() {
        return roleChangeRequestRepository.findByStatus(RoleChangeStatus.PENDING);
    }

    public long getPendingCount() {
        return roleChangeRequestRepository.countByStatus(RoleChangeStatus.PENDING);
    }

    private void applyRoleChange(RoleChangeRequest request) {
        User targetUser = request.getTargetUser();
        Role role = roleRepository.findByName(request.getRoleName())
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + request.getRoleName()));

        if (request.getAction() == RoleChangeAction.GRANT) {
            targetUser.getRoles().add(role);
        } else if (request.getAction() == RoleChangeAction.REVOKE) {
            targetUser.getRoles().removeIf(r -> r.getName().equals(request.getRoleName()));
        }

        userRepository.save(targetUser);
        // Evict permission cache so next request resolves updated permissions immediately.
        permissionCacheService.evictCache();
        log.info("Applied role change: {} {} for user {}",
                request.getAction(), request.getRoleName(), targetUser.getId());
    }
}

package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.ReviewRoleChangeRequest;
import com.edge.pulse.data.dto.RoleChangeRequestDto;
import com.edge.pulse.data.models.RoleChangeRequest;
import com.edge.pulse.mappers.RoleChangeMapper;
import com.edge.pulse.services.RoleChangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/approvals")
@RequiredArgsConstructor
public class AdminApprovalController {
    private final RoleChangeService roleChangeService;
    private final RoleChangeMapper roleChangeMapper;

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ASSIGN_APPROVE')")
    public ResponseEntity<List<RoleChangeRequestDto>> getPendingApprovals() {
        List<RoleChangeRequestDto> result = roleChangeService.getPendingRequests().stream()
                .map(roleChangeMapper::toDto)
                .toList();
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ASSIGN_APPROVE')")
    public ResponseEntity<RoleChangeRequestDto> reviewRequest(
            @PathVariable UUID id,
            @RequestBody @Valid ReviewRoleChangeRequest request,
            Authentication auth) {
        UUID authUserId = (UUID) auth.getPrincipal();
        RoleChangeRequest reviewed = roleChangeService.reviewRequest(
                authUserId, id, request.status(), request.comment());
        return ResponseEntity.ok(roleChangeMapper.toDto(reviewed));
    }
}

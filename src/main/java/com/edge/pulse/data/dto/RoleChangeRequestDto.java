package com.edge.pulse.data.dto;

import com.edge.pulse.data.enums.RoleChangeAction;
import com.edge.pulse.data.enums.RoleChangeStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record RoleChangeRequestDto(
    UUID id,
    UUID targetUserId,
    String targetUserName,
    UUID requestedById,
    String requestedByName,
    String roleName,
    RoleChangeAction action,
    RoleChangeStatus status,
    UUID reviewedById,
    String reviewedByName,
    String reviewComment,
    LocalDateTime createdAt,
    LocalDateTime reviewedAt
) {}

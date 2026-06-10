package com.edge.pulse.data.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuditLogDto(
    UUID id,
    UUID userId,
    String userEmail,
    String action,
    String entityType,
    UUID entityId,
    String details,
    String ipAddress,
    LocalDateTime createdAt
) {}

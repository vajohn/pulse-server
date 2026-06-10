package com.edge.pulse.data.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AssignmentDto(
    UUID id,
    UUID formId,
    String formTitle,
    UUID orgUnitId,
    String orgUnitName,
    UUID userId,
    String userDisplayName,
    UUID assignedById,
    String assignedByName,
    LocalDateTime assignedAt,
    LocalDateTime dueDate,
    LocalDateTime startsAt,
    LocalDateTime expiresAt,
    boolean mandatory,
    boolean active,
    boolean includeChildren,
    boolean allowResubmission,
    String formType
) {
}

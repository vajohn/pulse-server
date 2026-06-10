package com.edge.pulse.data.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateAssignmentRequest(
    UUID formId,
    UUID orgUnitId,
    UUID userId,
    LocalDateTime startsAt,
    LocalDateTime expiresAt,
    LocalDateTime dueDate,
    boolean mandatory,
    boolean includeChildren,
    boolean allowResubmission
) {
}

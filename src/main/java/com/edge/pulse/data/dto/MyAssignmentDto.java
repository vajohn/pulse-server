package com.edge.pulse.data.dto;

import com.edge.pulse.data.enums.AssignmentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record MyAssignmentDto(
    UUID assignmentId,
    UUID formId,
    /** Non-null for PSYCHOMETRIC assignments only; null for SURVEY / QUIZ. */
    UUID testId,
    String formTitle,
    String formDescription,
    boolean mandatory,
    LocalDateTime startsAt,
    LocalDateTime expiresAt,
    LocalDateTime dueDate,
    boolean allowResubmission,
    AssignmentStatus status,
    UUID sessionId,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    int answeredCount,
    int totalQuestions,
    /** SURVEY | PSYCHOMETRIC | QUIZ — used by Flutter to route to the correct flow. */
    String formType
) {
}

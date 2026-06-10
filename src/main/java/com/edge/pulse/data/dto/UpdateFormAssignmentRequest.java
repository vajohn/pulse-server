package com.edge.pulse.data.dto;

import java.time.LocalDateTime;

public record UpdateFormAssignmentRequest(
    LocalDateTime dueDate,
    LocalDateTime startsAt,
    LocalDateTime expiresAt,
    Boolean mandatory,
    Boolean allowResubmission
) {}

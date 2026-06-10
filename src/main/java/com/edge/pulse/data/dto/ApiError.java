package com.edge.pulse.data.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ApiError(
    int status,
    String message,
    LocalDateTime timestamp,
    List<FieldError> errors
) {
    public ApiError(int status, String message) {
        this(status, message, LocalDateTime.now(), List.of());
    }

    public ApiError(int status, String message, List<FieldError> fieldErrors) {
        this(status, message, LocalDateTime.now(), fieldErrors);
    }

    /** Per-field validation error surfaced to the client. */
    public record FieldError(String field, String message) {}
}

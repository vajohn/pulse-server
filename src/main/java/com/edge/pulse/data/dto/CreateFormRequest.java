package com.edge.pulse.data.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateFormRequest(
    @NotBlank String title,
    String description,
    /**
     * Anonymity window length in minutes. Null = use the service default (60).
     * Must be >= 1: anon-identity windowing divides by this value, so 0 caused a
     * divide-by-zero at anonymous session open. See AnonIdentityService.
     */
    @Min(value = 1, message = "anonWindowMinutes must be at least 1") Integer anonWindowMinutes
) {}

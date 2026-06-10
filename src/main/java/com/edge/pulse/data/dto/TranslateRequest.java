package com.edge.pulse.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/admin/translate} (single text).
 */
public record TranslateRequest(
    @NotBlank String text,
    @NotBlank String fromLocale,
    @NotBlank String toLocale
) {}

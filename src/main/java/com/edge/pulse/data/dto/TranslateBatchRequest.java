package com.edge.pulse.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for {@code POST /api/admin/translate/batch} (multiple texts).
 * Maximum 50 items per call — matches Azure Cognitive Services Translator v3 batch limit.
 */
public record TranslateBatchRequest(
    @NotEmpty @Size(max = 50, message = "Batch size must not exceed 50 items") List<@NotBlank String> texts,
    @NotBlank String fromLocale,
    @NotBlank String toLocale
) {}

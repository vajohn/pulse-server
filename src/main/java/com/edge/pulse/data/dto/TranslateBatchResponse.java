package com.edge.pulse.data.dto;

import java.util.List;

/**
 * Response body for {@code POST /api/admin/translate/batch}.
 * Flutter reads {@code json['translatedTexts']} and {@code json['provider']}.
 * Field names must not change — matched by Flutter {@code AdminTranslationApi}.
 */
public record TranslateBatchResponse(
    List<String> translatedTexts,
    String provider
) {}

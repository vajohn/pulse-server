package com.edge.pulse.data.dto;

/**
 * Response body for {@code POST /api/admin/translate} (single text).
 * Flutter reads {@code json['translatedText']}, {@code json['provider']},
 * {@code json['cached']} — field names must not change.
 */
public record TranslateResponse(
    String translatedText,
    String provider,
    boolean cached
) {}

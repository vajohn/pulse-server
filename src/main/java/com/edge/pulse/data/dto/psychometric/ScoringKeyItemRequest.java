package com.edge.pulse.data.dto.psychometric;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One item in a UI-driven scoring key PUT payload.
 *
 * <p>The caller sends one entry per question that belongs to the key.
 * Questions omitted from the list are not scored.
 */
public record ScoringKeyItemRequest(
        @NotNull UUID questionId,
        @NotNull UUID scaleId,
        /** "FORWARD" or "REVERSE" — defaults to FORWARD when omitted. */
        String direction,
        /** Scoring weight — defaults to 1.0 when null. */
        @DecimalMin("0.001") BigDecimal weight,
        /** Keyed correct answer for CHOICE_SINGLE; null for SCALE/ADJECTIVE/FORCED_CHOICE. */
        UUID correctAnswerId,
        /** Partial-credit mode for CHOICE_MULTIPLE items. */
        boolean partialCredit
) {}

package com.edge.pulse.util;

import java.util.Locale;

/**
 * Canonicalises an instrument display name so the same instrument cannot be stored twice under
 * cosmetic variations (e.g. {@code BigFive}, {@code big_five}, {@code Big-Five}, {@code Big  Five},
 * and {@code bigfive} all collapse to the same canonical {@code bigfive}). The
 * {@code psychometric_instrument.canonical_name} UNIQUE constraint is the structural guard; this
 * normaliser produces the value stored there and used for lookup. Mirrors
 * {@link EmailNormalizer}'s single-source-of-truth discipline.
 *
 * <p>Rules: (1) lowercase using {@link Locale#ROOT} (locale-independent — avoids the Turkish
 * dotless-i hazard), (2) strip ALL non-alphanumeric characters (no spaces retained). Null in →
 * null out; an all-separator/blank input → empty string. No camelCase-splitting is applied —
 * removing separators entirely means {@code BigFive} and {@code bigfive} collapse to the same
 * result without any boundary detection.
 */
public final class InstrumentNormalizer {

    private InstrumentNormalizer() {
    }

    public static String canonical(String raw) {
        if (raw == null) {
            return null;
        }
        // 1. Lowercase (locale-independent).
        String lowered = raw.toLowerCase(Locale.ROOT);
        // 2. Strip every non-alphanumeric character — no spaces, no separators of any kind.
        return lowered.replaceAll("[^a-z0-9]", "");
    }
}

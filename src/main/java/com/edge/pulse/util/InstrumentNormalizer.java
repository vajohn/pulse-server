package com.edge.pulse.util;

import java.util.Locale;

/**
 * Canonicalises an instrument display name so the same instrument cannot be stored twice under
 * cosmetic variations (e.g. {@code BigFive}, {@code big_five}, {@code Big-Five}, {@code Big  Five}
 * all collapse to {@code big five}). The {@code psychometric_instrument.canonical_name} UNIQUE
 * constraint is the structural guard; this normaliser produces the value stored there and used for
 * lookup. Mirrors {@link EmailNormalizer}'s single-source-of-truth discipline.
 *
 * <p>Rules: (1) split camelCase boundaries by inserting a space between a lower/digit and an
 * upper, (2) lowercase using {@link Locale#ROOT} (locale-independent — avoids the Turkish
 * dotless-i hazard), (3) replace every run of non-alphanumeric characters with a single space,
 * (4) trim. Null in → null out; an all-separator input → empty string.
 */
public final class InstrumentNormalizer {

    private InstrumentNormalizer() {
    }

    public static String canonical(String raw) {
        if (raw == null) {
            return null;
        }
        // 1. Split camelCase: insert a space at lower/digit → upper boundaries.
        String spaced = raw.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ");
        // 2. Lowercase (locale-independent).
        String lowered = spaced.toLowerCase(Locale.ROOT);
        // 3. Collapse non-alphanumeric runs to a single space, then 4. trim.
        return lowered.replaceAll("[^a-z0-9]+", " ").trim();
    }
}

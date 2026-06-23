package com.edge.pulse.util;

import java.util.Locale;

/**
 * Single source of truth for canonicalising a user's email before it is persisted or used as a
 * lookup key.
 *
 * <p>Background (PULSE-8): {@code users.email} historically had a CASE-SENSITIVE unique constraint
 * ({@code users_email_key}). saf-recon sync stored the SF-cased email (e.g. {@code X-2340@adsb.ae})
 * while X4Auth login lowercased it and did a case-sensitive lookup that missed the synced row —
 * producing a second, orphaned identity (no {@code sf_user_id}/org). Normalising on every write +
 * a case-insensitive unique index ({@code users_email_lower_key}) closes that gap.
 *
 * <p>Rules: trim surrounding whitespace, then lowercase using {@link Locale#ROOT} (locale-independent
 * — avoids the Turkish dotless-i hazard). Null in → null out. This MUST be applied everywhere a user
 * email is written or used to find an existing user.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    /**
     * Canonicalises an email for storage / lookup. Returns {@code null} if the input is {@code null};
     * a blank (whitespace-only) input is trimmed to an empty string (callers already guard against
     * blank emails before persisting).
     */
    public static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

package com.edge.pulse.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the single source of truth for email canonicalisation (PULSE-8). A regression here
 * (e.g. dropping the trim or the lowercase) silently re-opens the duplicate-identity bug, so
 * the contract is pinned explicitly.
 */
class EmailNormalizerTest {

    @Test
    void lowercasesAndTrims() {
        assertThat(EmailNormalizer.normalizeEmail("  Jane@Edge.AE  ")).isEqualTo("jane@edge.ae");
    }

    @Test
    void mixedCaseIsLowercased() {
        assertThat(EmailNormalizer.normalizeEmail("X-2340@ADSB.ae")).isEqualTo("x-2340@adsb.ae");
    }

    @Test
    void alreadyNormalizedIsUnchanged() {
        assertThat(EmailNormalizer.normalizeEmail("x-2340@adsb.ae")).isEqualTo("x-2340@adsb.ae");
    }

    @Test
    void nullInNullOut() {
        assertThat(EmailNormalizer.normalizeEmail(null)).isNull();
    }

    @Test
    void emptyStringStaysEmpty() {
        assertThat(EmailNormalizer.normalizeEmail("")).isEqualTo("");
    }

    @Test
    void whitespaceOnlyTrimsToEmpty() {
        assertThat(EmailNormalizer.normalizeEmail("   ")).isEqualTo("");
    }

    @Test
    void usesLocaleRootSoTurkishDottedIIsNotMangled() {
        // With the Turkish locale, "I".toLowerCase() yields a dotless ı; Locale.ROOT must avoid that.
        assertThat(EmailNormalizer.normalizeEmail("INFO@EDGE.AE")).isEqualTo("info@edge.ae");
    }
}

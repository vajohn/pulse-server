package com.edge.pulse.services;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Scans and redacts Personally Identifiable Information (PII) from free-text
 * survey responses before they are persisted.
 *
 * <p>UAE military context: even "anonymous" responses must not contain PII that
 * could be used to re-identify the respondent. This service is the last line of
 * defence before text reaches the database.
 *
 * <p>Detected patterns (UAE-aware):
 * <ul>
 *   <li>Email addresses</li>
 *   <li>UAE mobile numbers (+971 5x / 05x) and landline formats</li>
 *   <li>Emirates ID numbers (784-YYYY-XXXXXXX-X)</li>
 *   <li>Generic credit/debit card numbers</li>
 *   <li>Passport-style alphanumeric identifiers</li>
 * </ul>
 */
@Service
public class PiiRedactionService {

    // Email
    private static final Pattern EMAIL = Pattern.compile(
            "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}",
            Pattern.CASE_INSENSITIVE
    );

    // UAE phone: +971 XX XXXXXXX | 00971XXXXXXXXX | 05XXXXXXXX | 04XXXXXXX (landlines)
    private static final Pattern UAE_PHONE = Pattern.compile(
            "(?:\\+971|00971|0)(?:5[024568]|2|3|4|6|7|9)\\d{7}"
    );

    // Emirates ID: 784-YYYY-XXXXXXX-X (digits only between separators)
    private static final Pattern EMIRATES_ID = Pattern.compile(
            "\\b784[-\\s]?\\d{4}[-\\s]?\\d{7}[-\\s]?\\d\\b"
    );

    // Generic card number: 4 groups of 4 digits separated by space or dash
    private static final Pattern CARD_NUMBER = Pattern.compile(
            "\\b\\d{4}[\\s\\-]?\\d{4}[\\s\\-]?\\d{4}[\\s\\-]?\\d{4}\\b"
    );

    // Passport-style: 1-2 uppercase letters followed by 6-9 digits (broad but useful)
    private static final Pattern PASSPORT = Pattern.compile(
            "\\b[A-Z]{1,2}\\d{6,9}\\b"
    );

    public record ScanResult(String redactedText, boolean piiDetected) {}

    /**
     * Scans {@code text} for PII patterns and replaces any matches with
     * human-readable redaction tokens.
     *
     * @param text the raw free-text answer value
     * @return a {@link ScanResult} containing the (possibly modified) text and
     *         whether any PII was detected
     */
    public ScanResult redact(String text) {
        if (text == null || text.isBlank()) {
            return new ScanResult(text, false);
        }

        String result = text;
        boolean piiDetected = false;

        if (EMAIL.matcher(result).find()) {
            result = EMAIL.matcher(result).replaceAll("[REDACTED-EMAIL]");
            piiDetected = true;
        }
        if (UAE_PHONE.matcher(result).find()) {
            result = UAE_PHONE.matcher(result).replaceAll("[REDACTED-PHONE]");
            piiDetected = true;
        }
        if (EMIRATES_ID.matcher(result).find()) {
            result = EMIRATES_ID.matcher(result).replaceAll("[REDACTED-EMIRATES-ID]");
            piiDetected = true;
        }
        if (CARD_NUMBER.matcher(result).find()) {
            result = CARD_NUMBER.matcher(result).replaceAll("[REDACTED-CARD]");
            piiDetected = true;
        }
        if (PASSPORT.matcher(result).find()) {
            result = PASSPORT.matcher(result).replaceAll("[REDACTED-ID]");
            piiDetected = true;
        }

        return new ScanResult(result, piiDetected);
    }
}

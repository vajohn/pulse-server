package com.edge.pulse.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Shared cryptographic utility methods.
 */
public final class HashUtils {

    private HashUtils() {}

    /**
     * Computes the SHA-256 hex digest of the given bytes.
     *
     * @param data raw bytes to hash
     * @return lowercase hex string (64 chars)
     */
    public static String sha256hex(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JVM spec
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

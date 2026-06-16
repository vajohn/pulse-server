package com.edge.pulse.mappers;

import java.util.UUID;

/**
 * Formats a Spark nomination's UUID into a short, support-quotable reference.
 * Uses the trailing (random) hex of the id — never the leading bits, which are
 * the UUIDv7 timestamp and would collide across nominations created together.
 */
public final class SparkReference {

    private SparkReference() {
    }

    public static String format(UUID id) {
        String hex = id.toString().replace("-", "");
        return "NOM-" + hex.substring(hex.length() - 8).toUpperCase();
    }
}

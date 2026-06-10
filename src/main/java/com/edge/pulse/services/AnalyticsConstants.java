package com.edge.pulse.services;

/**
 * Shared constants for analytics computations across the survey and psychometric
 * analytics services.
 */
public final class AnalyticsConstants {

    /**
     * Minimum number of respondents before aggregate statistics are surfaced.
     * Enforced consistently across all analytics methods to protect individual privacy.
     */
    public static final int MIN_RESPONDENTS = 5;

    private AnalyticsConstants() {}
}

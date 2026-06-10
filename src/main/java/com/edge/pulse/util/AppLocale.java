package com.edge.pulse.util;

import java.util.Set;

/**
 * Supported locale codes for the Pulse translation feature.
 * Must remain in sync with Flutter {@code AppLocale} constants.
 */
public final class AppLocale {
    public static final String EN = "en";
    public static final String AR = "ar";
    public static final Set<String> ALL = Set.of(EN, AR);

    private AppLocale() {}

    public static boolean isSupported(String locale) {
        return locale != null && ALL.contains(locale);
    }
}

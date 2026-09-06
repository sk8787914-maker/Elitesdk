package com.elite.core;

import java.util.Locale;

/**
 * Validates Facebook authentication hosts without accepting lookalike domains.
 * This is the focused Facebook compatibility code carried over from the source library.
 */
final class FacebookAuthHost {
    private FacebookAuthHost() {
    }

    static boolean matches(String rawHost) {
        String host = rawHost == null ? "" : rawHost.toLowerCase(Locale.US);
        return "facebook.com".equals(host)
                || "www.facebook.com".equals(host)
                || "m.facebook.com".equals(host)
                || "web.facebook.com".equals(host)
                || host.endsWith(".facebook.com");
    }
}

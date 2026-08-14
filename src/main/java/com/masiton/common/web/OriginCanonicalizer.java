package com.masiton.common.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class OriginCanonicalizer {

    private OriginCanonicalizer() {
    }

    public static String canonicalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Origin must be present");
        }

        URI origin = URI.create(value);
        String scheme = origin.getScheme() == null ? null : origin.getScheme().toLowerCase(Locale.ROOT);
        boolean validScheme = "http".equals(scheme) || "https".equals(scheme);
        String rawAuthority = origin.getRawAuthority();
        int port = origin.getPort();
        boolean hasOnlyOriginComponents = origin.getRawUserInfo() == null
                && (origin.getRawPath() == null || origin.getRawPath().isEmpty())
                && origin.getRawQuery() == null
                && origin.getRawFragment() == null;
        boolean validPort = port >= -1 && port <= 65535
                && rawAuthority != null
                && !rawAuthority.endsWith(":");
        if (!validScheme || origin.getHost() == null || !hasOnlyOriginComponents || !validPort) {
            throw new IllegalArgumentException("Origin must be an HTTP(S) origin");
        }
        if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
            port = -1;
        }
        try {
            return new URI(scheme, null, origin.getHost().toLowerCase(Locale.ROOT), port, null, null, null)
                    .toASCIIString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Origin must be an HTTP(S) origin", exception);
        }
    }

    public static boolean matches(String candidate, String expected) {
        try {
            return canonicalize(expected).equals(canonicalize(candidate));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}

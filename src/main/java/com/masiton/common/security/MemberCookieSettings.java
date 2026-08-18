package com.masiton.common.security;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import com.masiton.common.web.OriginCanonicalizer;

public record MemberCookieSettings(
        String cookieName,
        Duration refreshTokenTtl,
        String path,
        boolean secure,
        String sameSite,
        String publicBaseUrl
) {

    public MemberCookieSettings {
        try {
            List<String> origins = Arrays.stream(publicBaseUrl.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(MemberCookieSettings::canonicalOrigin)
                    .distinct()
                    .toList();
            if (origins.isEmpty()) {
                throw new IllegalStateException("Member allowed origins must not be empty");
            }
            publicBaseUrl = String.join(",", origins);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Member public base URL must be an HTTP(S) origin", exception);
        }
    }

    public List<String> allowedOrigins() {
        return Arrays.asList(publicBaseUrl.split(","));
    }

    private static String canonicalOrigin(String value) {
        String canonical = OriginCanonicalizer.canonicalize(value);
        if (canonical.startsWith("http://")
                && !(canonical.startsWith("http://localhost") || canonical.startsWith("http://127.0.0.1"))) {
            throw new IllegalStateException("Only loopback HTTP origins are allowed outside TLS profiles");
        }
        return canonical;
    }
}

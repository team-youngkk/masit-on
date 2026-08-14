package com.masiton.common.security;

import java.time.Duration;

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
            publicBaseUrl = OriginCanonicalizer.canonicalize(publicBaseUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Member public base URL must be an HTTP(S) origin", exception);
        }
    }
}

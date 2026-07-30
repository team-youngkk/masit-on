package com.masiton.common.security;

import java.time.Duration;

public record MemberCookieSettings(
        String cookieName,
        Duration refreshTokenTtl,
        String path,
        boolean secure,
        String sameSite,
        String publicBaseUrl
) {
}

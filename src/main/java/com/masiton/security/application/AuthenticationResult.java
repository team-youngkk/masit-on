package com.masiton.security.application;

public record AuthenticationResult(
        String accessToken,
        String refreshToken,
        long expiresInSeconds
) {
}

package com.masiton.member.application;

public record MemberAuthenticationResult(String accessToken, String refreshToken, long expiresInSeconds, String role) {
    public MemberAuthenticationResult(String accessToken, String refreshToken, long expiresInSeconds) {
        this(accessToken, refreshToken, expiresInSeconds, "MEMBER");
    }
}

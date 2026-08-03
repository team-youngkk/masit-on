package com.masiton.member.application;

public record MemberAuthenticationResult(String accessToken, String refreshToken, long expiresInSeconds) {
}

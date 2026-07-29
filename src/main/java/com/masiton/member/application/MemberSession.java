package com.masiton.member.application;

public record MemberSession(String memberId, String sessionId, String refreshToken) {
}

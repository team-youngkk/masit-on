package com.masiton.common.security;

public interface MemberSessionAccessChecker {

    AccessDecision check(String memberId, String sessionId);

    default AccessDecision check(String memberId, String sessionId, String role) {
        return check(memberId, sessionId);
    }

    enum AccessDecision {
        ALLOWED,
        DENIED,
        UNAVAILABLE
    }
}

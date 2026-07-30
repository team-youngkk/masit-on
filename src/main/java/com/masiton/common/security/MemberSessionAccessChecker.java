package com.masiton.common.security;

public interface MemberSessionAccessChecker {

    AccessDecision check(String memberId, String sessionId);

    enum AccessDecision {
        ALLOWED,
        DENIED,
        UNAVAILABLE
    }
}

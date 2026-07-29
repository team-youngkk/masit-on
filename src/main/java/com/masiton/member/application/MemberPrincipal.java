package com.masiton.member.application;

/**
 * Minimal member identity passed from the security boundary to member use cases.
 */
public record MemberPrincipal(String memberId, String sessionId) {

    public MemberPrincipal {
        if (memberId == null || memberId.isBlank() || sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Member principal requires an id and session id");
        }
    }
}

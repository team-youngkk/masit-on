package com.masiton.member.application;

import com.masiton.member.domain.model.MemberRole;

/**
 * Minimal member identity passed from the security boundary to member use cases.
 */
public record MemberPrincipal(String memberId, String sessionId, MemberRole role) {

    public MemberPrincipal(String memberId, String sessionId) {
        this(memberId, sessionId, MemberRole.MEMBER);
    }

    public MemberPrincipal {
        if (memberId == null || memberId.isBlank() || sessionId == null || sessionId.isBlank() || role == null) {
            throw new IllegalArgumentException("Member principal requires an id and session id");
        }
    }
}

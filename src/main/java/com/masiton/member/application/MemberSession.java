package com.masiton.member.application;

import java.util.Set;

public record MemberSession(String memberId, String sessionId, String refreshToken, Set<String> revokedSessionIds) {

    public MemberSession {
        revokedSessionIds = Set.copyOf(revokedSessionIds);
    }
}

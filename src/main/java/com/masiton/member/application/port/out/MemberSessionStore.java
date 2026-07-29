package com.masiton.member.application.port.out;

import java.time.Duration;

import com.masiton.member.application.MemberSession;

public interface MemberSessionStore {

    MemberSession issue(String memberId, Duration ttl);

    MemberSession rotate(String refreshToken, Duration ttl);

    boolean matches(String memberId, String refreshToken);

    void revoke(String memberId, String sessionId);

    void revokeAll(String memberId);
}

package com.masiton.member.application.port.out;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import com.masiton.member.application.MemberSession;
import com.masiton.member.application.MemberSessionOwner;

public interface MemberSessionStore {

    MemberSession issue(String memberId, Duration ttl);

    MemberSession rotate(String refreshToken, Duration ttl);

    Optional<MemberSessionOwner> findSession(String refreshToken);

    Set<String> activeSessionIds(String memberId);

    boolean matches(String memberId, String refreshToken);

    void revoke(String memberId, String sessionId);

    Set<String> revokeAll(String memberId);
}

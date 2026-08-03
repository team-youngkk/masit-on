package com.masiton.member.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.masiton.member.domain.model.MemberActionPurpose;
import com.masiton.member.domain.model.MemberActionToken;

public interface MemberActionTokenRepository {
    void replace(MemberActionToken token, Instant issuedAt);
    Optional<MemberActionToken> consume(String rawToken, MemberActionPurpose purpose, Instant now);
    void deleteByMemberId(UUID memberId);
}

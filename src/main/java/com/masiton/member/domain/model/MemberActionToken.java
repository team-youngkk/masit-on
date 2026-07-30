package com.masiton.member.domain.model;

import java.time.Instant;
import java.util.UUID;

public record MemberActionToken(UUID id, UUID memberId, byte[] tokenHash, MemberActionPurpose purpose, Instant expiresAt) {
    public MemberActionToken(UUID memberId, byte[] tokenHash, MemberActionPurpose purpose, Instant expiresAt) {
        this(UUID.randomUUID(), memberId, tokenHash, purpose, expiresAt);
    }
}

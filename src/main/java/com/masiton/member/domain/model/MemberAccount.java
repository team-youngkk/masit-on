package com.masiton.member.domain.model;

import java.time.Instant;
import java.util.UUID;

public record MemberAccount(UUID id, String email, String passwordHash, MemberStatus status, MemberRole role,
        Instant emailVerifiedAt, Instant deletionRequestedAt, Instant createdAt) {
    public MemberAccount(UUID id, String email, String passwordHash, MemberStatus status,
            Instant emailVerifiedAt, Instant deletionRequestedAt, Instant createdAt) {
        this(id, email, passwordHash, status, MemberRole.MEMBER, emailVerifiedAt, deletionRequestedAt, createdAt);
    }
    public boolean canAuthenticate() {
        return status == MemberStatus.ACTIVE;
    }
}

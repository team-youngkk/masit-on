package com.masiton.member.application;

import java.time.Instant;
import java.util.UUID;

public record MemberSessionRevocation(UUID sessionId, Instant revokedAt, Instant expiresAt) {

    public MemberSessionRevocation {
        if (!expiresAt.isAfter(revokedAt)) {
            throw new IllegalArgumentException("Session revocation expiry must be after revocation time");
        }
    }
}

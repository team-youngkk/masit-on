package com.masiton.member.domain.model;

import java.time.Instant;
import java.util.UUID;

public record MemberActionToken(UUID memberId, byte[] tokenHash, MemberActionPurpose purpose, Instant expiresAt) {
}

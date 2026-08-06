package com.masiton.common.idempotency.application;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public record IdempotencyRecord(
        UUID id,
        IdempotencyActorType actorType,
        UUID actorId,
        IdempotencyApiScope apiScope,
        byte[] keyHash,
        byte[] requestHash,
        IdempotencyResponse response,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt
) {

    public IdempotencyRecord {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(actorType, "actorType must not be null");
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(apiScope, "apiScope must not be null");
        keyHash = copyHash(keyHash, "keyHash");
        requestHash = copyHash(requestHash, "requestHash");
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    @Override
    public byte[] keyHash() {
        return Arrays.copyOf(keyHash, keyHash.length);
    }

    @Override
    public byte[] requestHash() {
        return Arrays.copyOf(requestHash, requestHash.length);
    }

    public boolean isExpiredAt(OffsetDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public boolean hasSameRequest(byte[] otherRequestHash) {
        return MessageDigestSupport.constantTimeEquals(requestHash, otherRequestHash);
    }

    private static byte[] copyHash(byte[] hash, String name) {
        Objects.requireNonNull(hash, name + " must not be null");
        if (hash.length != 32) {
            throw new IllegalArgumentException(name + " must be 32 bytes");
        }
        return Arrays.copyOf(hash, hash.length);
    }

    private static final class MessageDigestSupport {

        private MessageDigestSupport() {
        }

        private static boolean constantTimeEquals(byte[] left, byte[] right) {
            return right != null && java.security.MessageDigest.isEqual(left, right);
        }
    }
}

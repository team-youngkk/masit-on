package com.masiton.common.idempotency.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class IdempotencyRequest {

    private static final int MINIMUM_KEY_LENGTH = 8;
    private static final int MAXIMUM_KEY_LENGTH = 128;
    private static final int SHA_256_LENGTH = 32;

    private final IdempotencyActorType actorType;
    private final UUID actorId;
    private final IdempotencyApiScope apiScope;
    private final byte[] keyHash;
    private final byte[] requestHash;

    private IdempotencyRequest(
            IdempotencyActorType actorType,
            UUID actorId,
            IdempotencyApiScope apiScope,
            byte[] keyHash,
            byte[] requestHash
    ) {
        this.actorType = Objects.requireNonNull(actorType, "actorType must not be null");
        this.actorId = Objects.requireNonNull(actorId, "actorId must not be null");
        this.apiScope = Objects.requireNonNull(apiScope, "apiScope must not be null");
        this.keyHash = Arrays.copyOf(keyHash, keyHash.length);
        this.requestHash = validateRequestHash(requestHash);
    }

    public static IdempotencyRequest of(
            IdempotencyActorType actorType,
            UUID actorId,
            IdempotencyApiScope apiScope,
            String rawKey,
            byte[] requestHash
    ) {
        validateRawKey(rawKey);
        return new IdempotencyRequest(actorType, actorId, apiScope, sha256(rawKey), requestHash);
    }

    public IdempotencyActorType actorType() {
        return actorType;
    }

    public UUID actorId() {
        return actorId;
    }

    public IdempotencyApiScope apiScope() {
        return apiScope;
    }

    public byte[] keyHash() {
        return Arrays.copyOf(keyHash, keyHash.length);
    }

    public byte[] requestHash() {
        return Arrays.copyOf(requestHash, requestHash.length);
    }

    private static void validateRawKey(String rawKey) {
        if (rawKey == null) {
            throw new InvalidIdempotencyKeyException();
        }
        int length = rawKey.codePointCount(0, rawKey.length());
        if (length < MINIMUM_KEY_LENGTH || length > MAXIMUM_KEY_LENGTH) {
            throw new InvalidIdempotencyKeyException();
        }
    }

    private static byte[] validateRequestHash(byte[] requestHash) {
        Objects.requireNonNull(requestHash, "requestHash must not be null");
        if (requestHash.length != SHA_256_LENGTH) {
            throw new IllegalArgumentException("requestHash must be a 32-byte SHA-256 hash");
        }
        return Arrays.copyOf(requestHash, requestHash.length);
    }

    private static byte[] sha256(String rawKey) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(rawKey.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

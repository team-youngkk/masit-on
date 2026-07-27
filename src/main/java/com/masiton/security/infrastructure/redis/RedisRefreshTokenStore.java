package com.masiton.security.infrastructure.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.masiton.security.application.InvalidRefreshTokenException;
import com.masiton.security.application.RefreshTokenRotation;
import com.masiton.security.application.port.out.RefreshTokenStore;

@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:refresh:";
    private static final String INDEX_KEY_PREFIX = "auth:refresh:index:";
    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local stored = redis.call('GET', KEYS[1])
            if not stored then
              redis.call('DEL', KEYS[2])
              return 0
            end
            local record = cjson.decode(stored)
            if record.tokenFamilyId ~= ARGV[2] then
              redis.call('DEL', KEYS[2])
              return 0
            end
            if record.tokenHash ~= ARGV[1] then
              redis.call('DEL', KEYS[1])
              return -1
            end
            redis.call('SET', KEYS[1], ARGV[3], 'EX', ARGV[5])
            redis.call('SET', KEYS[3], ARGV[4], 'EX', ARGV[5])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RefreshTokenFactory refreshTokenFactory;

    public RedisRefreshTokenStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RefreshTokenFactory refreshTokenFactory
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.refreshTokenFactory = refreshTokenFactory;
    }

    @Override
    public RefreshTokenRotation issue(String adminId, Duration ttl) {
        String familyId = UUID.randomUUID().toString();
        String token = refreshTokenFactory.create();
        redisTemplate.opsForValue().set(key(adminId), serialize(record(token, familyId, ttl)), ttl);
        redisTemplate.opsForValue().set(indexKey(token), reference(adminId, familyId), ttl);
        return new RefreshTokenRotation(adminId, token);
    }

    @Override
    public RefreshTokenRotation rotate(String refreshToken, Duration ttl) {
        RefreshTokenReference reference = readReference(redisTemplate.opsForValue().get(indexKey(refreshToken)));
        String nextToken = refreshTokenFactory.create();
        Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(key(reference.adminId()), indexKey(refreshToken), indexKey(nextToken)),
                hash(refreshToken),
                reference.familyId(),
                serialize(record(nextToken, reference.familyId(), ttl)),
                reference(reference.adminId(), reference.familyId()),
                String.valueOf(ttl.toSeconds())
        );
        if (result == null || result != 1L) {
            throw new InvalidRefreshTokenException();
        }
        return new RefreshTokenRotation(reference.adminId(), nextToken);
    }

    @Override
    public boolean matches(String adminId, String refreshToken) {
        String serialized = redisTemplate.opsForValue().get(key(adminId));
        if (serialized == null) {
            return false;
        }
        try {
            return MessageDigest.isEqual(
                    read(serialized).tokenHash().getBytes(StandardCharsets.US_ASCII),
                    hash(refreshToken).getBytes(StandardCharsets.US_ASCII)
            );
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public void revoke(String adminId) {
        redisTemplate.delete(key(adminId));
    }

    private RefreshTokenRecord record(String token, String familyId, Duration ttl) {
        Instant createdAt = Instant.now();
        return new RefreshTokenRecord(hash(token), familyId, createdAt, createdAt.plus(ttl));
    }

    private String serialize(RefreshTokenRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize refresh-token state", exception);
        }
    }

    private RefreshTokenRecord read(String value) {
        try {
            return objectMapper.readValue(value, RefreshTokenRecord.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not read refresh-token state", exception);
        }
    }

    private RefreshTokenReference readReference(String value) {
        if (value == null) {
            throw new InvalidRefreshTokenException();
        }
        String[] parts = value.split("\\|", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        return new RefreshTokenReference(parts[0], parts[1]);
    }

    private String reference(String adminId, String familyId) {
        return adminId + "|" + familyId;
    }

    private String key(String adminId) {
        return KEY_PREFIX + adminId;
    }

    private String indexKey(String token) {
        return INDEX_KEY_PREFIX + hash(token);
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record RefreshTokenRecord(String tokenHash, String tokenFamilyId, Instant createdAt, Instant expiresAt) {
    }

    private record RefreshTokenReference(String adminId, String familyId) {
    }
}

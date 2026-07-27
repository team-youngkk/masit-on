package com.masiton.security.infrastructure.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
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
    private static final int TOKEN_BYTES = 48;
    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local stored = redis.call('GET', KEYS[1])
            if not stored then return 0 end
            local record = cjson.decode(stored)
            if record.tokenHash ~= ARGV[1] then
              redis.call('DEL', KEYS[1])
              return -1
            end
            redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
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
        String token = refreshTokenFactory.create(adminId);
        redisTemplate.opsForValue().set(key(adminId), serialize(record(token, ttl)), ttl);
        return new RefreshTokenRotation(adminId, token);
    }

    @Override
    public RefreshTokenRotation rotate(String refreshToken, Duration ttl) {
        String adminId = refreshTokenFactory.extractAdminId(refreshToken)
                .orElseThrow(InvalidRefreshTokenException::new);
        String nextToken = refreshTokenFactory.create(adminId);
        Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                java.util.List.of(key(adminId)),
                hash(refreshToken),
                serialize(record(nextToken, ttl)),
                String.valueOf(ttl.toSeconds())
        );
        if (result == null || result != 1L) {
            throw new InvalidRefreshTokenException();
        }
        return new RefreshTokenRotation(adminId, nextToken);
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

    private RefreshTokenRecord record(String token, Duration ttl) {
        Instant createdAt = Instant.now();
        return new RefreshTokenRecord(hash(token), UUID.randomUUID().toString(), createdAt, createdAt.plus(ttl));
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

    private String key(String adminId) {
        return KEY_PREFIX + adminId;
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
}

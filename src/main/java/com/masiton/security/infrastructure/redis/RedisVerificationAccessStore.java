package com.masiton.security.infrastructure.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import com.masiton.security.application.port.out.VerificationAccessStore;
import com.masiton.security.application.port.out.VerificationStoreUnavailableException;

@Component
public class RedisVerificationAccessStore implements VerificationAccessStore {

    private static final String SESSION_PREFIX = "auth:verification:session:";
    private static final String LOGIN_PREFIX = "auth:verification:failure:login-id:";
    private static final String SOURCE_PREFIX = "auth:verification:failure:source:";
    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>("""
            local attempts = redis.call('INCR', KEYS[1])
            if attempts == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return attempts
            """, Long.class);

    private final StringRedisTemplate redis;
    private final com.masiton.security.infrastructure.configuration.VerificationAccessProperties properties;

    public RedisVerificationAccessStore(StringRedisTemplate redis,
            com.masiton.security.infrastructure.configuration.VerificationAccessProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public void save(String rawSessionId, Duration ttl) {
        execute(() -> redis.opsForValue().set(sessionKey(rawSessionId), "1", ttl));
    }

    public boolean exists(String rawSessionId) {
        return query(() -> Boolean.TRUE.equals(redis.hasKey(sessionKey(rawSessionId))));
    }

    public void delete(String rawSessionId) {
        execute(() -> redis.delete(sessionKey(rawSessionId)));
    }

    public boolean isBlocked(String loginId, String source) {
        return query(() -> count(loginKey(loginId)) >= properties.getMaxAttempts()
                || count(sourceKey(source)) >= properties.getMaxAttempts());
    }

    public void recordFailure(String loginId, String source) {
        String ttl = String.valueOf(properties.getFailureTtl().toSeconds());
        execute(() -> {
            redis.execute(INCREMENT_WITH_TTL, List.of(loginKey(loginId)), ttl);
            redis.execute(INCREMENT_WITH_TTL, List.of(sourceKey(source)), ttl);
        });
    }

    public void clearFailures(String loginId, String source) {
        execute(() -> redis.delete(List.of(loginKey(loginId), sourceKey(source))));
    }

    String sessionKey(String rawSessionId) { return SESSION_PREFIX + hash(rawSessionId); }
    private String loginKey(String loginId) { return LOGIN_PREFIX + hash(loginId); }
    private String sourceKey(String source) { return SOURCE_PREFIX + hash(source); }

    private long count(String key) {
        String value = redis.opsForValue().get(key);
        return value == null ? 0 : Long.parseLong(value);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void execute(Runnable action) {
        try { action.run(); } catch (DataAccessException exception) { throw new VerificationStoreUnavailableException(exception); }
    }

    private <T> T query(java.util.function.Supplier<T> action) {
        try { return action.get(); } catch (DataAccessException exception) { throw new VerificationStoreUnavailableException(exception); }
    }
}

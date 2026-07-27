package com.masiton.security.infrastructure.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.masiton.security.application.port.out.LoginFailureStore;
import com.masiton.security.infrastructure.configuration.SecurityProperties;

@Component
public class RedisLoginFailureStore implements LoginFailureStore {

    private static final String KEY_PREFIX = "auth:login-failure:";
    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>("""
            local attempts = redis.call('INCR', KEYS[1])
            if attempts == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return attempts
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final SecurityProperties properties;

    public RedisLoginFailureStore(StringRedisTemplate redisTemplate, SecurityProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public boolean isBlocked(String loginId) {
        String count = redisTemplate.opsForValue().get(key(loginId));
        return count != null && Long.parseLong(count) >= properties.getLoginFailure().getMaxAttempts();
    }

    @Override
    public void recordFailure(String loginId) {
        Duration ttl = properties.getLoginFailure().getTtl();
        redisTemplate.execute(INCREMENT_WITH_TTL, List.of(key(loginId)), String.valueOf(ttl.toSeconds()));
    }

    @Override
    public void clear(String loginId) {
        redisTemplate.delete(key(loginId));
    }

    private String key(String loginId) {
        return KEY_PREFIX + hash(loginId);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

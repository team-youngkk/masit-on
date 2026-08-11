package com.masiton.restaurant.infrastructure.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.masiton.restaurant.application.port.out.NaturalLanguageRateLimitPort;

/** 출처 원문을 키에 저장하지 않는 Redis 기반 자연어 요청 제한이다. */
@Component
public class RedisNaturalLanguageRateLimitStore implements NaturalLanguageRateLimitPort {

    private static final String PREFIX = "restaurant:natural-language:rate-limit:";
    private static final int WINDOW_SECONDS = 60;
    private static final int LIMIT = 60;
    private static final DefaultRedisScript<Long> TRY_ACQUIRE = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            if count > tonumber(ARGV[2]) then
              return 0
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisNaturalLanguageRateLimitStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryAcquire(String clientAddress) {
        Long acquired = redisTemplate.execute(
                TRY_ACQUIRE,
                List.of(PREFIX + hash(clientAddress)),
                String.valueOf(WINDOW_SECONDS),
                String.valueOf(LIMIT));
        return Long.valueOf(1).equals(acquired);
    }

    private String hash(String clientAddress) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(clientAddress.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

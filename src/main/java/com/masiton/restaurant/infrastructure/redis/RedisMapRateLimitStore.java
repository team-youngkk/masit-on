package com.masiton.restaurant.infrastructure.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.masiton.restaurant.application.port.out.MapRateLimitPort;

/**
 * BR-MAP-004: 클라이언트 요청 출처 기준 초당 최대 4회로 지도 영역 조회를 제한한다.
 * 1초 자체 만료 창 안의 원자적 INCR+EXPIRE로 구현하며 별도 좌표·쿼리 문자열은 저장하지 않는다.
 * 클라이언트 주소는 원문 대신 해시로만 키에 남긴다.
 */
@Component
public class RedisMapRateLimitStore implements MapRateLimitPort {

    private static final String PREFIX = "restaurant:map:rate-limit:";
    private static final int WINDOW_SECONDS = 1;
    private static final int LIMIT = 4;

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

    public RedisMapRateLimitStore(StringRedisTemplate redisTemplate) {
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

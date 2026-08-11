package com.masiton.restaurant.infrastructure.redis;

import java.time.Clock;
import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.masiton.restaurant.application.port.out.CourseRouteQuotaPort;
import com.masiton.restaurant.infrastructure.external.config.KakaoMobilityProperties;

/**
 * KST 기준 월별 Mobility 호출 permit을 Redis에서 원자적으로 예약한다.
 * Redis 장애도 유료·quota 초과를 막기 위해 permit 거부로 처리한다.
 */
public final class RedisCourseRouteQuota implements CourseRouteQuotaPort {

    private static final String KEY_PREFIX = "restaurant:course-route:quota:";
    private static final String RATE_KEY_PREFIX = "restaurant:course-route:rate:";
    private static final String IN_FLIGHT_KEY = "restaurant:course-route:in-flight";
    private static final int IN_FLIGHT_TTL_SECONDS = 10;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final DefaultRedisScript<Long> TRY_ACQUIRE = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[2])
            end
            if count > tonumber(ARGV[1]) then
              redis.call('DECR', KEYS[1])
              return 0
            end
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> TRY_ACQUIRE_REQUEST = new DefaultRedisScript<>("""
            local rate = redis.call('INCR', KEYS[1])
            if rate == 1 then
              redis.call('EXPIRE', KEYS[1], 2)
            end
            local in_flight = redis.call('INCR', KEYS[2])
            if in_flight == 1 then
              redis.call('EXPIRE', KEYS[2], ARGV[3])
            end
            if rate > tonumber(ARGV[1]) or in_flight > tonumber(ARGV[2]) then
              redis.call('DECR', KEYS[1])
              redis.call('DECR', KEYS[2])
              return 0
            end
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE_REQUEST = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if not current then
              return 0
            end
            local remaining = redis.call('DECR', KEYS[1])
            if remaining <= 0 then
              redis.call('DEL', KEYS[1])
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final KakaoMobilityProperties properties;
    private final Clock clock;

    public RedisCourseRouteQuota(
            StringRedisTemplate redisTemplate,
            KakaoMobilityProperties properties,
            Clock clock) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public boolean tryAcquireMonthlyPermit() {
        try {
            ZonedDateTime now = ZonedDateTime.now(clock.withZone(BUSINESS_ZONE));
            YearMonth month = YearMonth.from(now);
            long secondsUntilNextMonth = Math.max(
                    1,
                    Duration.between(now.toInstant(), month.plusMonths(1).atDay(1)
                            .atStartOfDay(BUSINESS_ZONE).toInstant()).getSeconds());
            Long acquired = redisTemplate.execute(
                    TRY_ACQUIRE,
                    List.of(KEY_PREFIX + month),
                    String.valueOf(properties.getMonthlyQuota()),
                    String.valueOf(secondsUntilNextMonth));
            return Long.valueOf(1).equals(acquired);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public boolean tryAcquireRequestPermit() {
        try {
            long second = ZonedDateTime.now(clock.withZone(BUSINESS_ZONE)).toEpochSecond();
            Long acquired = redisTemplate.execute(
                    TRY_ACQUIRE_REQUEST,
                    List.of(RATE_KEY_PREFIX + second, IN_FLIGHT_KEY),
                    String.valueOf(properties.getRequestsPerSecond()),
                    String.valueOf(properties.getMaxConcurrentRequests()),
                    String.valueOf(IN_FLIGHT_TTL_SECONDS));
            return Long.valueOf(1).equals(acquired);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public void releaseRequestPermit() {
        try {
            redisTemplate.execute(RELEASE_REQUEST, List.of(IN_FLIGHT_KEY));
        } catch (RuntimeException ignored) {
            // TTL keeps a crashed request from holding the permit forever.
        }
    }
}

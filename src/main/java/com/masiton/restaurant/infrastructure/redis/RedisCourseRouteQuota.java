package com.masiton.restaurant.infrastructure.redis;

import java.time.Clock;
import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import com.masiton.restaurant.application.port.out.CourseRouteQuotaPort;
import com.masiton.restaurant.application.port.out.CourseRouteQuotaUnavailableException;
import com.masiton.restaurant.infrastructure.external.config.KakaoMobilityProperties;

/**
 * KST 기준 월별 Mobility 호출 permit을 Redis에서 원자적으로 예약한다.
 * Redis 장애도 유료·quota 초과를 막기 위해 permit 거부로 처리한다.
 */
public final class RedisCourseRouteQuota implements CourseRouteQuotaPort {

    private static final Logger log = LoggerFactory.getLogger(RedisCourseRouteQuota.class);

    private static final String KEY_PREFIX = "restaurant:course-route:quota:";
    private static final String RATE_KEY_PREFIX = "restaurant:course-route:rate:";
    private static final String IN_FLIGHT_KEY = "restaurant:course-route:in-flight";
    private static final int MIN_IN_FLIGHT_TTL_SECONDS = 10;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final DefaultRedisScript<Long> TRY_ACQUIRE = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[2])
            end
            if count > tonumber(ARGV[1]) then
              redis.call('DECR', KEYS[1])
              return -tonumber(ARGV[1])
            end
            return count
            """, Long.class);
    private static final DefaultRedisScript<Long> TRY_ACQUIRE_REQUEST = new DefaultRedisScript<>("""
            local rate = redis.call('INCR', KEYS[1])
            if rate == 1 then
              redis.call('EXPIRE', KEYS[1], 2)
            end
            local now = tonumber(redis.call('TIME')[1])
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', now)
            local in_flight = redis.call('ZCARD', KEYS[2])
            if rate > tonumber(ARGV[1]) then
              redis.call('DECR', KEYS[1])
              return -1
            end
            if in_flight >= tonumber(ARGV[2]) then
              redis.call('DECR', KEYS[1])
              return -2
            end
            redis.call('ZADD', KEYS[2], now + tonumber(ARGV[3]), ARGV[4])
            redis.call('EXPIRE', KEYS[2], ARGV[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE_REQUEST = new DefaultRedisScript<>("""
            if redis.call('ZREM', KEYS[1], ARGV[1]) == 0 then
              return 0
            end
            if redis.call('ZCARD', KEYS[1]) == 0 then
              redis.call('DEL', KEYS[1])
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final KakaoMobilityProperties properties;
    private final Clock clock;
    private final Counter calls;
    private final Counter monthlyBlocked;
    private final Counter requestRateBlocked;
    private final Counter requestConcurrencyBlocked;
    private final Counter redisBlocked;
    private final AtomicInteger monthlyUsage = new AtomicInteger();
    private final AtomicInteger monthlyRemaining = new AtomicInteger();
    private final AtomicReference<YearMonth> warnedMonth = new AtomicReference<>();
    private final ThreadLocal<String> requestLeaseToken = new ThreadLocal<>();

    public RedisCourseRouteQuota(
            StringRedisTemplate redisTemplate,
            KakaoMobilityProperties properties,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.clock = clock;
        this.calls = Counter.builder("masiton.restaurant.course.route.calls")
                .description("Kakao Mobility course route call permits acquired")
                .register(meterRegistry);
        this.monthlyBlocked = blockedCounter(meterRegistry, "monthly");
        this.requestRateBlocked = blockedCounter(meterRegistry, "request_rate");
        this.requestConcurrencyBlocked = blockedCounter(meterRegistry, "request_concurrency");
        this.redisBlocked = blockedCounter(meterRegistry, "redis");
        Gauge.builder("masiton.restaurant.course.route.monthly.quota.usage", monthlyUsage, AtomicInteger::get)
                .description("Current Kakao Mobility monthly quota usage")
                .register(meterRegistry);
        Gauge.builder("masiton.restaurant.course.route.monthly.quota.remaining", monthlyRemaining, AtomicInteger::get)
                .description("Remaining Kakao Mobility monthly quota")
                .register(meterRegistry);
        monthlyRemaining.set(properties.getMonthlyQuota());
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
            if (acquired == null) {
                throw unavailable(null);
            }
            int usage = acquired < 0 ? properties.getMonthlyQuota() : acquired.intValue();
            recordMonthlyUsage(month, usage);
            if (acquired < 0) {
                monthlyBlocked.increment();
                return false;
            }
            calls.increment();
            return true;
        } catch (RuntimeException exception) {
            if (exception instanceof CourseRouteQuotaUnavailableException unavailable) {
                throw unavailable;
            }
            throw unavailable(exception);
        }
    }

    @Override
    public boolean tryAcquireRequestPermit() {
        try {
            long second = ZonedDateTime.now(clock.withZone(BUSINESS_ZONE)).toEpochSecond();
            String leaseToken = UUID.randomUUID().toString();
            Long acquired = redisTemplate.execute(
                    TRY_ACQUIRE_REQUEST,
                    List.of(RATE_KEY_PREFIX + second, IN_FLIGHT_KEY),
                    String.valueOf(properties.getRequestsPerSecond()),
                    String.valueOf(properties.getMaxConcurrentRequests()),
                    String.valueOf(inFlightTtlSeconds()),
                    leaseToken);
            if (Long.valueOf(1).equals(acquired)) {
                requestLeaseToken.set(leaseToken);
                return true;
            }
            if (Long.valueOf(-1).equals(acquired)) {
                requestRateBlocked.increment();
            } else if (Long.valueOf(-2).equals(acquired)) {
                requestConcurrencyBlocked.increment();
            } else if (acquired == null) {
                throw unavailable(null);
            } else {
                throw unavailable(null);
            }
            return false;
        } catch (RuntimeException exception) {
            requestLeaseToken.remove();
            throw unavailable(exception);
        }
    }

    @Override
    public void releaseRequestPermit() {
        String leaseToken = requestLeaseToken.get();
        if (leaseToken == null) {
            return;
        }
        try {
            redisTemplate.execute(RELEASE_REQUEST, List.of(IN_FLIGHT_KEY), leaseToken);
        } catch (RuntimeException ignored) {
            // TTL keeps a crashed request from holding the permit forever.
        } finally {
            requestLeaseToken.remove();
        }
    }

    private int inFlightTtlSeconds() {
        long totalTimeoutSeconds = properties.getTotalTimeout().toSeconds();
        return Math.toIntExact(Math.max(MIN_IN_FLIGHT_TTL_SECONDS, totalTimeoutSeconds + 1));
    }

    private CourseRouteQuotaUnavailableException unavailable(Throwable cause) {
        redisBlocked.increment();
        return new CourseRouteQuotaUnavailableException(cause);
    }

    private Counter blockedCounter(MeterRegistry meterRegistry, String reason) {
        return Counter.builder("masiton.restaurant.course.route.blocked")
                .description("Kakao Mobility course route requests blocked")
                .tag("reason", reason)
                .register(meterRegistry);
    }

    private void recordMonthlyUsage(YearMonth month, int usage) {
        monthlyUsage.set(usage);
        monthlyRemaining.set(Math.max(0, properties.getMonthlyQuota() - usage));
        int warningThreshold = (int) Math.ceil(properties.getMonthlyQuota() * 0.8);
        if (usage >= warningThreshold && !month.equals(warnedMonth.getAndSet(month))) {
            log.warn("Kakao Mobility monthly quota reached warning threshold: usage={}/{}",
                    usage, properties.getMonthlyQuota());
        }
    }
}

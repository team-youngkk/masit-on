package com.masiton.ai.infrastructure.redis;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import com.masiton.ai.application.port.out.YoutubeChannelBackfillQuotaPort;
import com.masiton.ai.application.port.out.YoutubeChannelBackfillQuotaUnavailableException;
import com.masiton.ai.infrastructure.worker.YoutubeChannelBackfillProperties;

/** YouTube channels/playlist 호출과 백필 전용 예산을 Redis Lua로 원자 예약한다. */
public final class RedisYoutubeChannelBackfillQuota implements YoutubeChannelBackfillQuotaPort {

    private static final String PROVIDER_KEY_PREFIX = "ai:youtube:quota:provider:";
    private static final String BACKFILL_KEY_PREFIX = "ai:youtube:quota:backfill:";
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>("""
            local provider = redis.call('INCRBY', KEYS[1], ARGV[1])
            local backfill = redis.call('INCRBY', KEYS[2], ARGV[1])
            if provider == tonumber(ARGV[1]) then redis.call('EXPIRE', KEYS[1], ARGV[4]) end
            if backfill == tonumber(ARGV[1]) then redis.call('EXPIRE', KEYS[2], ARGV[4]) end
            if provider > tonumber(ARGV[2]) or backfill > tonumber(ARGV[3]) then
              redis.call('DECRBY', KEYS[1], ARGV[1])
              redis.call('DECRBY', KEYS[2], ARGV[1])
              return 0
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final YoutubeChannelBackfillProperties properties;
    private final Clock clock;
    private final Counter reserved;
    private final Counter blocked;
    private final Counter redisUnavailable;

    public RedisYoutubeChannelBackfillQuota(StringRedisTemplate redisTemplate,
                                            YoutubeChannelBackfillProperties properties,
                                            Clock clock,
                                            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.clock = clock;
        this.reserved = Counter.builder("masiton.ai.youtube.backfill.quota.reserved")
                .description("YouTube backfill API call quota permits acquired")
                .register(meterRegistry);
        this.blocked = Counter.builder("masiton.ai.youtube.backfill.quota.blocked")
                .description("YouTube backfill API call quota permits blocked")
                .register(meterRegistry);
        this.redisUnavailable = Counter.builder("masiton.ai.youtube.backfill.quota.unavailable")
                .description("YouTube backfill quota store failures")
                .register(meterRegistry);
    }

    @Override
    public boolean tryReserve(int cost) {
        if (cost <= 0) throw new IllegalArgumentException("YouTube quota cost must be positive");
        try {
            Window window = window();
            Long result = redisTemplate.execute(RESERVE,
                    List.of(PROVIDER_KEY_PREFIX + window.bucket(), BACKFILL_KEY_PREFIX + window.bucket()),
                    String.valueOf(cost), String.valueOf(properties.getProviderQuotaLimit()),
                    String.valueOf(properties.getBackfillQuotaLimit()), String.valueOf(window.ttlSeconds()));
            if (result == null) throw unavailable(null);
            if (result == 0L) {
                blocked.increment();
                return false;
            }
            reserved.increment(cost);
            return true;
        } catch (YoutubeChannelBackfillQuotaUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private Window window() {
        Duration duration = properties.getQuotaWindow();
        if (duration == null || duration.isZero() || duration.isNegative()) throw unavailable(null);
        long seconds = Math.max(1L, duration.toSeconds());
        long epochSecond = Instant.now(clock).getEpochSecond();
        long bucket = Math.floorDiv(epochSecond, seconds);
        long nextBoundary = Math.addExact(Math.multiplyExact(bucket + 1, seconds), 1L);
        return new Window(bucket, Math.max(1L, nextBoundary - epochSecond));
    }

    private YoutubeChannelBackfillQuotaUnavailableException unavailable(Throwable cause) {
        redisUnavailable.increment();
        return new YoutubeChannelBackfillQuotaUnavailableException(cause);
    }

    private record Window(long bucket, long ttlSeconds) { }
}

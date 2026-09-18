package com.masiton.ai.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import com.masiton.ai.infrastructure.worker.YoutubeChannelBackfillProperties;
import com.masiton.test.FullContextIntegrationTest;
import com.masiton.test.TestProfile;

@SpringBootTest
@TestProfile
@ResourceLock("shared-test-infrastructure")
@DisplayName("Redis YouTube 채널 백필 quota")
class RedisYoutubeChannelBackfillQuotaIntegrationTest extends FullContextIntegrationTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        deleteRedisKeys(redisTemplate, "ai:youtube:quota:provider:*", "ai:youtube:quota:backfill:*");
    }

    @Test
    @DisplayName("provider와 백필 전용 quota를 함께 원자 예약하고 초과분을 되돌린다")
    void reserve_provider와백필Quota_초과분을원자적으로되돌린다() {
        YoutubeChannelBackfillProperties properties = properties(5, 3);
        RedisYoutubeChannelBackfillQuota quota = new RedisYoutubeChannelBackfillQuota(
                redisTemplate, properties,
                Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC),
                new SimpleMeterRegistry());

        assertThat(quota.tryReserve(1)).isTrue();
        assertThat(quota.tryReserve(1)).isTrue();
        assertThat(quota.tryReserve(1)).isTrue();
        assertThat(quota.tryReserve(1)).isFalse();

        Set<String> providerKeys = redisTemplate.keys("ai:youtube:quota:provider:*");
        Set<String> backfillKeys = redisTemplate.keys("ai:youtube:quota:backfill:*");
        assertThat(providerKeys).hasSize(1);
        assertThat(backfillKeys).hasSize(1);
        assertThat(redisTemplate.opsForValue().get(providerKeys.iterator().next())).isEqualTo("3");
        assertThat(redisTemplate.opsForValue().get(backfillKeys.iterator().next())).isEqualTo("3");
    }

    private YoutubeChannelBackfillProperties properties(long providerLimit, long backfillLimit) {
        YoutubeChannelBackfillProperties properties = new YoutubeChannelBackfillProperties();
        properties.setProviderQuotaLimit(providerLimit);
        properties.setBackfillQuotaLimit(backfillLimit);
        return properties;
    }
}

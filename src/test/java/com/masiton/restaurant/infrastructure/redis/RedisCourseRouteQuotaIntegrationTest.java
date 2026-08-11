package com.masiton.restaurant.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.junit.jupiter.api.extension.ExtendWith;

import com.masiton.restaurant.infrastructure.external.config.KakaoMobilityProperties;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@SpringBootTest
@com.masiton.test.TestProfile
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Redis 코스 경로 quota")
class RedisCourseRouteQuotaIntegrationTest {

    private static final int REDIS_PORT = 6379;
    private static final String IN_FLIGHT_KEY = "restaurant:course-route:in-flight";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.10-alpine")
            .withDatabaseName("masiton")
            .withUsername("masiton")
            .withPassword("masiton_local");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.8-alpine")
            .withExposedPorts(REDIS_PORT)
            .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void registerDependencies(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearRedis() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    @DisplayName("월 quota가 80% 경고·100% 차단을 적용하고 호출·차단 지표를 기록한다")
    void 월Quota_80퍼센트경고와100퍼센트차단_호출차단지표를기록한다(CapturedOutput output) {
        KakaoMobilityProperties properties = properties();
        properties.setMonthlyQuota(5);
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        RedisCourseRouteQuota quota = quota(properties, meterRegistry);

        assertThat(quota.tryAcquireMonthlyPermit()).isTrue();
        assertThat(quota.tryAcquireMonthlyPermit()).isTrue();
        assertThat(quota.tryAcquireMonthlyPermit()).isTrue();
        assertThat(quota.tryAcquireMonthlyPermit()).isTrue();

        assertThat(meterRegistry.get("masiton.restaurant.course.route.calls").counter().count()).isEqualTo(4);
        assertThat(meterRegistry.get("masiton.restaurant.course.route.monthly.quota.usage").gauge().value())
                .isEqualTo(4);
        assertThat(meterRegistry.get("masiton.restaurant.course.route.monthly.quota.remaining").gauge().value())
                .isEqualTo(1);
        assertThat(output).contains("monthly quota reached warning threshold");

        assertThat(quota.tryAcquireMonthlyPermit()).isTrue();
        assertThat(quota.tryAcquireMonthlyPermit()).isFalse();

        assertThat(meterRegistry.get("masiton.restaurant.course.route.calls").counter().count()).isEqualTo(5);
        assertThat(meterRegistry.get("masiton.restaurant.course.route.blocked")
                .tag("reason", "monthly").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.get("masiton.restaurant.course.route.monthly.quota.usage").gauge().value())
                .isEqualTo(5);
        assertThat(meterRegistry.get("masiton.restaurant.course.route.monthly.quota.remaining").gauge().value())
                .isEqualTo(0);
    }

    @Test
    @DisplayName("성공적인 동시성 permit 획득마다 in-flight TTL을 갱신한다")
    void 동시성Permit연속획득_inFlightTTL을갱신한다() {
        KakaoMobilityProperties properties = properties();
        properties.setMaxConcurrentRequests(2);
        RedisCourseRouteQuota quota = quota(properties, new SimpleMeterRegistry());

        assertThat(quota.tryAcquireRequestPermit()).isTrue();
        redisTemplate.expire(IN_FLIGHT_KEY, Duration.ofSeconds(1));
        Long shortenedTtl = redisTemplate.getExpire(IN_FLIGHT_KEY, TimeUnit.SECONDS);

        assertThat(quota.tryAcquireRequestPermit()).isTrue();
        Long refreshedTtl = redisTemplate.getExpire(IN_FLIGHT_KEY, TimeUnit.SECONDS);

        assertThat(shortenedTtl).isBetween(0L, 1L);
        assertThat(refreshedTtl).isGreaterThan(shortenedTtl);
        quota.releaseRequestPermit();
        quota.releaseRequestPermit();
    }

    @Test
    @DisplayName("전체 timeout보다 긴 lease를 사용한다")
    void 전체Timeout보다긴Lease를사용한다() {
        KakaoMobilityProperties properties = properties();
        properties.setTotalTimeout(Duration.ofSeconds(11));
        properties.setMaxConcurrentRequests(1);
        RedisCourseRouteQuota quota = quota(properties, new SimpleMeterRegistry());

        assertThat(quota.tryAcquireRequestPermit()).isTrue();
        assertThat(redisTemplate.getExpire(IN_FLIGHT_KEY, TimeUnit.SECONDS)).isGreaterThanOrEqualTo(11);
        quota.releaseRequestPermit();
    }

    @Test
    @DisplayName("10초 이상 유지된 이전 요청의 release는 새 세대 permit을 감소시키지 않는다")
    void 십초이상유지된이전요청의Release가새세대를감소시키지않는다() {
        KakaoMobilityProperties properties = properties();
        properties.setMaxConcurrentRequests(1);
        RedisCourseRouteQuota firstRequest = quota(properties, new SimpleMeterRegistry());
        RedisCourseRouteQuota secondRequest = quota(properties, new SimpleMeterRegistry());

        assertThat(firstRequest.tryAcquireRequestPermit()).isTrue();

        Awaitility.await()
                .atMost(Duration.ofSeconds(12))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> redisTemplate.getExpire(IN_FLIGHT_KEY, TimeUnit.SECONDS) < 0);
        assertThat(secondRequest.tryAcquireRequestPermit()).isTrue();

        firstRequest.releaseRequestPermit();

        assertThat(redisTemplate.opsForZSet().size(IN_FLIGHT_KEY)).isEqualTo(1);
        secondRequest.releaseRequestPermit();
    }

    @Test
    @DisplayName("정상 요청이 계속 들어와도 만료된 stale lease를 원자적으로 정리한다")
    void 정상요청이지속되는동안만료된StaleLease를정리한다() {
        KakaoMobilityProperties properties = properties();
        properties.setMaxConcurrentRequests(2);
        RedisCourseRouteQuota staleRequest = quota(properties, new SimpleMeterRegistry());
        RedisCourseRouteQuota liveRequest = quota(properties, new SimpleMeterRegistry());

        assertThat(staleRequest.tryAcquireRequestPermit()).isTrue();

        Awaitility.await()
                .atMost(Duration.ofSeconds(13))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    assertThat(liveRequest.tryAcquireRequestPermit()).isTrue();
                    liveRequest.releaseRequestPermit();
                    assertThat(redisTemplate.hasKey(IN_FLIGHT_KEY)).isFalse();
                });
    }

    private RedisCourseRouteQuota quota(KakaoMobilityProperties properties, MeterRegistry meterRegistry) {
        return new RedisCourseRouteQuota(
                redisTemplate,
                properties,
                Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC),
                meterRegistry);
    }

    private KakaoMobilityProperties properties() {
        KakaoMobilityProperties properties = new KakaoMobilityProperties();
        properties.setRequestsPerSecond(20);
        properties.setMaxConcurrentRequests(20);
        return properties;
    }
}

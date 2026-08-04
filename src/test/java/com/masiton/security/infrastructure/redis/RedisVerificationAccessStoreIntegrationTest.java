package com.masiton.security.infrastructure.redis;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.masiton.security.infrastructure.configuration.VerificationAccessProperties;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DisplayName("RedisVerificationAccessStore")
class RedisVerificationAccessStoreIntegrationTest {

    private static final int REDIS_PORT = 6379;

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.8-alpine")
            .withExposedPorts(REDIS_PORT);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisVerificationAccessStore store;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT)));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();

        VerificationAccessProperties properties = new VerificationAccessProperties();
        store = new RedisVerificationAccessStore(redisTemplate, properties);
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    @DisplayName("세션은 원문이 아니라 SHA-256 해시 키로 저장하고 조회·삭제한다")
    void save_원문대신해시키로저장한다() {
        store.save("raw-session-id", Duration.ofDays(7));

        assertThat(store.exists("raw-session-id")).isTrue();
        assertThat(store.exists("other-session-id")).isFalse();
        assertThat(redisTemplate.keys("auth:verification:session:*"))
                .singleElement()
                .satisfies(key -> assertThat(key).doesNotContain("raw-session-id"));

        store.delete("raw-session-id");

        assertThat(store.exists("raw-session-id")).isFalse();
    }

    @Test
    @DisplayName("로그인 ID 실패는 출처와 무관하게 최대 허용 횟수에서 차단한다")
    void isBlocked_로그인ID실패_최대허용횟수에서차단() {
        VerificationAccessProperties properties = new VerificationAccessProperties();
        int maxAttempts = properties.getMaxAttempts();

        for (int attempt = 1; attempt < maxAttempts; attempt++) {
            store.recordFailure("participant", "198.51.100." + attempt);
            assertThat(store.isBlocked("participant", "203.0.113.1")).isFalse();
        }
        store.recordFailure("participant", "198.51.100.99");

        assertThat(store.isBlocked("participant", "203.0.113.1")).isTrue();
    }

    @Test
    @DisplayName("출처 실패는 로그인 ID와 무관하게 최대 허용 횟수에서 차단한다")
    void isBlocked_출처실패_최대허용횟수에서차단() {
        VerificationAccessProperties properties = new VerificationAccessProperties();
        int maxAttempts = properties.getMaxAttempts();

        for (int attempt = 1; attempt < maxAttempts; attempt++) {
            store.recordFailure("participant-" + attempt, "198.51.100.20");
            assertThat(store.isBlocked("someone-else", "198.51.100.20")).isFalse();
        }
        store.recordFailure("participant-last", "198.51.100.20");

        assertThat(store.isBlocked("someone-else", "198.51.100.20")).isTrue();
    }

    @Test
    @DisplayName("실패 기록 정리는 로그인 ID와 출처 제한을 함께 해제한다")
    void clearFailures_로그인ID와출처제한을함께해제한다() {
        VerificationAccessProperties properties = new VerificationAccessProperties();
        int maxAttempts = properties.getMaxAttempts();
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            store.recordFailure("participant", "198.51.100.20");
        }
        assertThat(store.isBlocked("participant", "198.51.100.20")).isTrue();

        store.clearFailures("participant", "198.51.100.20");

        assertThat(store.isBlocked("participant", "198.51.100.20")).isFalse();
    }
}

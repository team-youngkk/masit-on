package com.masiton.security.infrastructure.redis;

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

import com.masiton.security.infrastructure.configuration.SecurityProperties;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DisplayName("RedisLoginFailureStore")
class RedisLoginFailureStoreIntegrationTest {

    private static final int REDIS_PORT = 6379;

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.8-alpine")
            .withExposedPorts(REDIS_PORT);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisLoginFailureStore store;
    private SecurityProperties properties;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT)));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();

        properties = new SecurityProperties();
        store = new RedisLoginFailureStore(redisTemplate, properties);
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    @DisplayName("서로 다른 출처의 실패 bucket은 서로 영향을 주지 않는다")
    void recordFailure_서로다른출처_실패bucket독립() {
        int maxAttempts = properties.getLoginFailure().getMaxAttempts();
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            store.recordFailure("admin-a", "198.51.100.10");
        }

        assertThat(store.isBlocked("admin-a", "203.0.113.10")).isTrue();
        assertThat(store.isBlocked("admin-b", "203.0.113.10")).isFalse();
        assertThat(store.isBlocked("admin-b", "198.51.100.10")).isTrue();
    }

    @Test
    @DisplayName("같은 출처의 실패는 최대 시도 횟수 경계에서 차단한다")
    void recordFailure_같은출처_최대시도횟수에서차단() {
        int maxAttempts = properties.getLoginFailure().getMaxAttempts();
        for (int attempt = 0; attempt < maxAttempts - 1; attempt++) {
            store.recordFailure("admin-" + attempt, "198.51.100.10");
            assertThat(store.isBlocked("other-admin", "198.51.100.10")).isFalse();
        }

        store.recordFailure("admin-last", "198.51.100.10");

        assertThat(store.isBlocked("other-admin", "198.51.100.10")).isTrue();
    }

    @Test
    @DisplayName("성공 처리의 clear는 로그인 ID와 출처의 실패 제한을 함께 해제한다")
    void clear_성공로그인_로그인ID와출처제한해제() {
        int maxAttempts = properties.getLoginFailure().getMaxAttempts();
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            store.recordFailure("admin", "198.51.100.10");
        }
        assertThat(store.isBlocked("admin", "198.51.100.10")).isTrue();

        store.clear("admin", "198.51.100.10");

        assertThat(store.isBlocked("admin", "198.51.100.10")).isFalse();
    }
}

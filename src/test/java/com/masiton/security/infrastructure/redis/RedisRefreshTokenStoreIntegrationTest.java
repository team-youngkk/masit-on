package com.masiton.security.infrastructure.redis;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.masiton.security.application.InvalidRefreshTokenException;
import com.masiton.security.application.RefreshTokenRotation;
import com.masiton.security.application.port.out.LoginFailureStore;
import com.masiton.security.application.port.out.RefreshTokenStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@com.masiton.test.TestProfile
@Testcontainers
@DisplayName("Redis Refresh Token 저장소")
class RedisRefreshTokenStoreIntegrationTest {

    private static final int REDIS_PORT = 6379;

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.8-alpine")
            .withExposedPorts(REDIS_PORT);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
    }

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private LoginFailureStore loginFailureStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearRedis() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    @DisplayName("임의로 만든 Token은 다른 관리자의 활성 세션을 폐기하지 못한다")
    void rotate_임의Token_활성세션을유지한다() {
        RefreshTokenRotation issued = refreshTokenStore.issue("admin-a", Duration.ofDays(14));

        assertThatThrownBy(() -> refreshTokenStore.rotate("forged-refresh-token", Duration.ofDays(14)))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(refreshTokenStore.matches("admin-a", issued.refreshToken())).isTrue();
    }

    @Test
    @DisplayName("회전된 이전 Token의 재사용은 같은 Token 계열을 폐기한다")
    void rotate_이전Token재사용_현재세션을폐기한다() {
        RefreshTokenRotation issued = refreshTokenStore.issue("admin-a", Duration.ofDays(14));
        RefreshTokenRotation rotated = refreshTokenStore.rotate(issued.refreshToken(), Duration.ofDays(14));

        assertThatThrownBy(() -> refreshTokenStore.rotate(issued.refreshToken(), Duration.ofDays(14)))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(refreshTokenStore.matches("admin-a", rotated.refreshToken())).isFalse();
    }

    @Test
    @DisplayName("로그인 실패는 다섯 번째부터 남은 TTL 동안 차단한다")
    void 로그인실패_다섯번째_차단한다() {
        for (int attempt = 0; attempt < 4; attempt++) {
            loginFailureStore.recordFailure("admin-login", "127.0.0.1");
            assertThat(loginFailureStore.isBlocked("admin-login", "127.0.0.1")).isFalse();
        }

        loginFailureStore.recordFailure("admin-login", "127.0.0.1");

        assertThat(loginFailureStore.isBlocked("admin-login", "127.0.0.1")).isTrue();
    }

    @Test
    @DisplayName("같은 출처의 다른 로그인 ID 다섯 번 실패도 차단한다")
    void 로그인실패_같은출처다른로그인ID_차단한다() {
        for (int attempt = 0; attempt < 5; attempt++) {
            loginFailureStore.recordFailure("admin-" + attempt, "127.0.0.1");
        }

        assertThat(loginFailureStore.isBlocked("another-admin", "127.0.0.1")).isTrue();
    }
}

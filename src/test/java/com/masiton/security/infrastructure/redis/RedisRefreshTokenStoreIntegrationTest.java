package com.masiton.security.infrastructure.redis;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masiton.security.application.InvalidRefreshTokenException;
import com.masiton.security.application.RefreshTokenRotation;
import com.masiton.security.application.port.out.LoginFailureStore;
import com.masiton.security.application.port.out.RefreshTokenStore;
import com.masiton.member.application.InvalidMemberSessionException;
import com.masiton.member.application.MemberSession;
import com.masiton.member.application.port.out.MemberSessionStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
@com.masiton.test.TestProfile
@Testcontainers
@DisplayName("Redis Refresh Token 저장소")
class RedisRefreshTokenStoreIntegrationTest {

    private static final int REDIS_PORT = 6379;

    /*
     * 이 테스트는 Redis만 검증하지만 @SpringBootTest가 전체 컨텍스트를 띄우므로
     * Flyway와 JPA가 기동 시점에 PostgreSQL을 요구한다. application-test.yml의
     * datasource는 localhost:5432를 가리키므로 컨테이너를 함께 띄우지 않으면
     * 컨텍스트 로딩이 실패한다.
     */
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.10-alpine")
            .withDatabaseName("masiton")
            .withUsername("masiton")
            .withPassword("masiton_local");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.8-alpine")
            .withExposedPorts(REDIS_PORT);

    @DynamicPropertySource
    static void registerDependencies(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
    }

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private MemberSessionStore memberSessionStore;

    @Autowired
    private LoginFailureStore loginFailureStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private Clock memberSessionClock;

    @BeforeEach
    void clearRedis() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        when(memberSessionClock.instant()).thenAnswer(ignored -> Instant.now());
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
    @Test
    @DisplayName("회원은 최대 세 개의 Redis refresh 세션만 유지한다")
    void memberSession_최대세개_가장오래된세션폐기() {
        MemberSession first = memberSessionStore.issue("member-a", Duration.ofDays(14));
        memberSessionStore.issue("member-a", Duration.ofDays(14));
        memberSessionStore.issue("member-a", Duration.ofDays(14));
        memberSessionStore.rotate(first.refreshToken(), Duration.ofDays(14));
        MemberSession fourth = memberSessionStore.issue("member-a", Duration.ofDays(14));

        assertThat(memberSessionStore.matches("member-a", first.refreshToken())).isFalse();
        assertThat(memberSessionStore.matches("member-a", fourth.refreshToken())).isTrue();
        assertThat(fourth.revokedSessionIds()).containsExactly(first.sessionId());
    }

    @Test
    @DisplayName("회원 refresh token 재사용은 원자적으로 현재 세션까지 폐기한다")
    void memberSession_회전된토큰재사용_현재세션폐기() {
        MemberSession issued = memberSessionStore.issue("member-a", Duration.ofDays(14));
        MemberSession rotated = memberSessionStore.rotate(issued.refreshToken(), Duration.ofDays(14));

        assertThatThrownBy(() -> memberSessionStore.rotate(issued.refreshToken(), Duration.ofDays(14)))
                .isInstanceOf(InvalidMemberSessionException.class);
        assertThat(memberSessionStore.matches("member-a", rotated.refreshToken())).isFalse();
        assertThatThrownBy(() -> memberSessionStore.rotate(rotated.refreshToken(), Duration.ofDays(14)))
                .isInstanceOf(InvalidMemberSessionException.class);
    }

    @Test
    @DisplayName("같은 createdAt의 네 번째 발급도 신규 세션을 퇴출하지 않는다")
    void memberSession_동일밀리초_신규세션퇴출방지() {
        when(memberSessionClock.instant()).thenReturn(Instant.parse("2026-07-29T10:00:00Z"));

        MemberSession first = memberSessionStore.issue("member-a", Duration.ofDays(14));
        memberSessionStore.issue("member-a", Duration.ofDays(14));
        memberSessionStore.issue("member-a", Duration.ofDays(14));
        MemberSession fourth = memberSessionStore.issue("member-a", Duration.ofDays(14));

        assertThat(memberSessionStore.matches("member-a", first.refreshToken())).isFalse();
        assertThat(memberSessionStore.matches("member-a", fourth.refreshToken())).isTrue();
    }

    @Test
    @DisplayName("전체 폐기 전에 시작된 발급은 전체 폐기 뒤 실행돼도 세션을 만들지 않는다")
    void memberSession_전체폐기후늦게실행된발급_거부() throws Exception {
        CountDownLatch issuePrepared = new CountDownLatch(1);
        CountDownLatch continueIssue = new CountDownLatch(1);
        when(memberSessionClock.instant()).thenAnswer(ignored -> {
            issuePrepared.countDown();
            if (!continueIssue.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for session generation change");
            }
            return Instant.parse("2030-01-01T00:00:00Z");
        });

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<MemberSession> issued = executor.submit(
                    () -> memberSessionStore.issue("member-a", Duration.ofDays(14)));
            assertThat(issuePrepared.await(5, TimeUnit.SECONDS)).isTrue();

            memberSessionStore.revokeAll("member-a");
            continueIssue.countDown();

            assertThatThrownBy(issued::get)
                    .hasCauseInstanceOf(InvalidMemberSessionException.class);
            assertThat(redisTemplate.opsForZSet().size("auth:member:sessions:member-a")).isZero();

            when(memberSessionClock.instant()).thenReturn(Instant.parse("2020-01-01T00:00:00Z"));
            MemberSession issuedAfterRevocation = memberSessionStore.issue("member-a", Duration.ofDays(14));
            assertThat(memberSessionStore.matches("member-a", issuedAfterRevocation.refreshToken())).isTrue();
        }
    }
}

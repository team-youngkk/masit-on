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

import com.masiton.member.application.MemberDeletionCleanupService;
import com.masiton.member.application.InvalidMemberSessionException;
import com.masiton.member.application.MemberSession;
import com.masiton.member.application.MemberSessionRevocation;
import com.masiton.member.application.MemberSessionRevocationRecoveryService;
import com.masiton.member.application.port.out.MemberSessionRevocationRecoveryQueue;
import com.masiton.member.application.port.out.MemberSessionStore;
import com.masiton.member.application.port.out.MemberRateLimitStore;
import com.masiton.test.FullContextIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
@com.masiton.test.TestProfile
@DisplayName("Redis Refresh Token 저장소")
class RedisRefreshTokenStoreIntegrationTest extends com.masiton.test.FullContextIntegrationTest {

    @Autowired
    private MemberSessionStore memberSessionStore;

    @Autowired
    private MemberSessionRevocationRecoveryQueue memberSessionRevocationRecoveryQueue;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private MemberRateLimitStore memberRateLimitStore;

    @MockitoBean
    private Clock memberSessionClock;

    @MockitoBean
    private MemberDeletionCleanupService memberDeletionCleanupService;

    @MockitoBean
    private MemberSessionRevocationRecoveryService memberSessionRevocationRecoveryService;

    @BeforeEach
    void clearRedis() {
        FullContextIntegrationTest.deleteRedisKeys(
                redisTemplate,
                "auth:session:*",
                "auth:member:session:revocation:recovery:*",
                "auth:member:rate-limit:*");
        when(memberSessionClock.instant()).thenAnswer(ignored -> Instant.now());
    }

    @Test
    @DisplayName("회원 인증 메일 요청은 60초 cooldown과 출처별 시간당 20회 제한을 원자적으로 적용한다")
    void 회원인증메일요청_cooldown과출처한도_원자적적용() throws Exception {
        assertThat(memberRateLimitStore.tryAcquireAccountActionRequest("member@example.com", "198.51.100.10")).isTrue();
        assertThat(memberRateLimitStore.tryAcquireAccountActionRequest("member@example.com", "198.51.100.10")).isFalse();

        ExecutorService executor = Executors.newFixedThreadPool(25);
        CountDownLatch ready = new CountDownLatch(25);
        CountDownLatch start = new CountDownLatch(1);
        java.util.List<Future<Boolean>> results = new java.util.ArrayList<>();
        for (int index = 0; index < 25; index++) {
            int current = index;
            results.add(executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return memberRateLimitStore.tryAcquireAccountActionRequest(
                        "member-" + current + "@example.com", "203.0.113.10");
            }));
        }
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        long accepted = 0;
        for (Future<Boolean> result : results) {
            if (result.get(5, TimeUnit.SECONDS)) {
                accepted++;
            }
        }
        executor.shutdownNow();

        assertThat(accepted).isEqualTo(20);
        assertTtlRange("auth:member:rate-limit:email-cooldown:*", 60L);
        assertTtlRange("auth:member:rate-limit:email-daily:*", 86_400L);
        assertTtlRange("auth:member:rate-limit:account-action-source:*", 3_600L);
    }

    @Test
    @DisplayName("회원 인증 메일 요청은 이메일별 하루 5회까지만 허용한다")
    void 회원인증메일요청_이메일일일한도_다섯회() {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(memberRateLimitStore.tryAcquireAccountActionRequest("member@example.com", "198.51.100." + attempt))
                    .isTrue();
            redisTemplate.delete(redisTemplate.keys("auth:member:rate-limit:email-cooldown:*"));
        }

        assertThat(memberRateLimitStore.tryAcquireAccountActionRequest("member@example.com", "203.0.113.10")).isFalse();
    }

    @Test
    @DisplayName("회원 로그인 실패는 5회 계정출처 제한과 15분 TTL을 적용한다")
    void 회원로그인실패_다섯번째부터차단하고TTL을설정한다() {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(memberRateLimitStore.tryAcquireLoginSourceAttempt("198.51.100.10")).isTrue();
            assertThat(memberRateLimitStore.tryRecordLoginFailure("member@example.com", "198.51.100.10")).isTrue();
        }

        assertThat(memberRateLimitStore.isLoginBlocked("member@example.com", "198.51.100.10")).isTrue();
        java.util.Set<String> keys = redisTemplate.keys("auth:member:rate-limit:login-*");
        assertThat(keys).hasSize(3);
        assertThat(keys).allSatisfy(key -> assertThat(redisTemplate.getExpire(key, TimeUnit.SECONDS))
                .isBetween(1L, 900L));
    }

    @Test
    @DisplayName("회원 로그인 실패는 이메일 10회와 출처 50회 제한을 각각 적용한다")
    void 회원로그인실패_이메일과출처집계한도_적용() {
        for (int attempt = 0; attempt < 10; attempt++) {
            assertThat(memberRateLimitStore.tryAcquireLoginSourceAttempt("198.51.100." + attempt)).isTrue();
            assertThat(memberRateLimitStore.tryRecordLoginFailure(
                    "member@example.com", "198.51.100." + attempt)).isTrue();
        }
        assertThat(memberRateLimitStore.isLoginBlocked("member@example.com", "203.0.113.200")).isTrue();

        FullContextIntegrationTest.deleteRedisKeys(redisTemplate, "auth:member:rate-limit:*");
        for (int attempt = 0; attempt < 50; attempt++) {
            assertThat(memberRateLimitStore.tryAcquireLoginSourceAttempt("203.0.113.10")).isTrue();
            assertThat(memberRateLimitStore.tryRecordLoginFailure(
                    "member-" + attempt + "@example.com", "203.0.113.10")).isTrue();
        }
        assertThat(memberRateLimitStore.isLoginBlocked("new-member@example.com", "203.0.113.10")).isTrue();
    }

    @Test
    @DisplayName("동시 로그인 실패는 계정출처 5회와 계정 10회 경계를 원자적으로 지킨다")
    void 회원로그인실패_동시요청_원자한도적용() throws Exception {
        assertThat(concurrentFailureRecords(20, attempt -> "198.51.100.10")).isEqualTo(5L);
        assertThat(memberRateLimitStore.isLoginBlocked("member@example.com", "198.51.100.10")).isTrue();

        FullContextIntegrationTest.deleteRedisKeys(redisTemplate, "auth:member:rate-limit:*");

        assertThat(concurrentFailureRecords(20, attempt -> "198.51.100." + attempt)).isEqualTo(10L);
        assertThat(memberRateLimitStore.isLoginBlocked("member@example.com", "203.0.113.200")).isTrue();
    }

    @Test
    @DisplayName("성공 로그인은 계정 기반 실패 제한을 소모하지 않는다")
    void 회원로그인성공_계정실패한도_소모하지않는다() {
        for (int attempt = 0; attempt < 10; attempt++) {
            assertThat(memberRateLimitStore.tryAcquireLoginSourceAttempt("198.51.100.10")).isTrue();
        }

        assertThat(memberRateLimitStore.isLoginBlocked("member@example.com", "198.51.100.10")).isFalse();
        assertThat(redisTemplate.keys("auth:member:rate-limit:login-email*")).isEmpty();
    }

    private long concurrentFailureRecords(int attempts, java.util.function.IntFunction<String> sourceFactory)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(attempts)) {
            java.util.List<Future<Boolean>> results = new java.util.ArrayList<>();
            for (int attempt = 0; attempt < attempts; attempt++) {
                int currentAttempt = attempt;
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for concurrent login failures");
                    }
                    return memberRateLimitStore.tryRecordLoginFailure(
                            "member@example.com", sourceFactory.apply(currentAttempt));
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return results.stream().filter(this::completedWithTrue).count();
        }
    }

    private boolean completedWithTrue(Future<Boolean> result) {
        try {
            return result.get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private void assertTtlRange(String pattern, long maximumSeconds) {
        java.util.Set<String> keys = redisTemplate.keys(pattern);
        assertThat(keys).isNotEmpty();
        assertThat(keys).allSatisfy(key -> assertThat(redisTemplate.getExpire(key, TimeUnit.SECONDS))
                .isBetween(1L, maximumSeconds));
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
    @DisplayName("같은 회원의 세 번째와 네 번째 동시 로그인 뒤 가장 오래된 세션만 퇴출한다")
    void memberSession_동시네번째로그인_가장오래된세션만폐기() throws Exception {
        when(memberSessionClock.instant()).thenReturn(Instant.parse("2026-07-29T10:00:00Z"));
        MemberSession first = memberSessionStore.issue("member-a", Duration.ofDays(14));
        MemberSession second = memberSessionStore.issue("member-a", Duration.ofDays(14));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<MemberSession> third = executor.submit(() -> issueWhenReleased(ready, start));
            Future<MemberSession> fourth = executor.submit(() -> issueWhenReleased(ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            MemberSession concurrentlyIssuedA = third.get(5, TimeUnit.SECONDS);
            MemberSession concurrentlyIssuedB = fourth.get(5, TimeUnit.SECONDS);

            assertThat(memberSessionStore.matches("member-a", first.refreshToken())).isFalse();
            assertThat(memberSessionStore.matches("member-a", second.refreshToken())).isTrue();
            assertThat(memberSessionStore.matches("member-a", concurrentlyIssuedA.refreshToken())).isTrue();
            assertThat(memberSessionStore.matches("member-a", concurrentlyIssuedB.refreshToken())).isTrue();
            assertThat(redisTemplate.opsForZSet().size("auth:session:account:member-a")).isEqualTo(3L);
            assertThat(java.util.stream.Stream.of(concurrentlyIssuedA, concurrentlyIssuedB)
                    .flatMap(session -> session.revokedSessionIds().stream()))
                    .containsExactly(first.sessionId());
        }
    }

    @Test
    @DisplayName("같은 Refresh Token의 동시 재발급은 한 요청만 성공하고 계열을 폐기한다")
    void memberSession_동일RefreshToken동시회전_한요청만성공() throws Exception {
        MemberSession issued = memberSessionStore.issue("member-a", Duration.ofDays(14));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<MemberSession> first = executor.submit(() -> rotateWhenReleased(issued.refreshToken(), ready, start));
            Future<MemberSession> second = executor.submit(() -> rotateWhenReleased(issued.refreshToken(), ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            java.util.List<Future<MemberSession>> attempts = java.util.List.of(first, second);
            long succeeded = attempts.stream().filter(this::completedSuccessfully).count();
            assertThat(succeeded).isEqualTo(1L);
            assertThat(attempts).filteredOn(this::completedExceptionally).allSatisfy(attempt ->
                    assertThatThrownBy(attempt::get).hasCauseInstanceOf(InvalidMemberSessionException.class));
            attempts.stream().filter(this::completedSuccessfully).forEach(attempt -> {
                try {
                    assertThat(memberSessionStore.matches("member-a", attempt.get().refreshToken())).isFalse();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            });
        }
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
            assertThat(redisTemplate.opsForZSet().size("auth:session:account:member-a")).isZero();

            when(memberSessionClock.instant()).thenReturn(Instant.parse("2020-01-01T00:00:00Z"));
            MemberSession issuedAfterRevocation = memberSessionStore.issue("member-a", Duration.ofDays(14));
            assertThat(memberSessionStore.matches("member-a", issuedAfterRevocation.refreshToken())).isTrue();
        }
    }

    @Test
    @DisplayName("회원 세션 한도 초과 폐기 전에 복구 큐에 sid와 폐기 시각을 남긴다")
    void memberSession_한도초과폐기_복구큐선적재() {
        Instant now = Instant.parse("2026-07-29T10:00:00Z");
        when(memberSessionClock.instant()).thenReturn(now);

        MemberSession first = memberSessionStore.issue("member-a", Duration.ofDays(14));
        memberSessionStore.issue("member-a", Duration.ofDays(14));
        memberSessionStore.issue("member-a", Duration.ofDays(14));
        memberSessionStore.issue("member-a", Duration.ofDays(14));

        assertThat(memberSessionRevocationRecoveryQueue.claimDue(now, 50)).containsExactly(
                new MemberSessionRevocation(
                        java.util.UUID.fromString(first.sessionId()), now, now.plus(Duration.ofDays(14))));
    }

    @Test
    @DisplayName("레거시 회원 세션도 한도 초과 폐기 전에 복구 큐에 적재한다")
    void memberSession_레거시만료시각_한도초과폐기_복구큐선적재() {
        Instant now = Instant.parse("2026-07-29T10:00:00Z");
        when(memberSessionClock.instant()).thenReturn(now);

        MemberSession first = memberSessionStore.issue("member-a", Duration.ofDays(14));
        replaceWithLegacySessionRecord(first);
        memberSessionStore.issue("member-a", Duration.ofDays(14));
        memberSessionStore.issue("member-a", Duration.ofDays(14));
        memberSessionStore.issue("member-a", Duration.ofDays(14));

        assertThat(memberSessionRevocationRecoveryQueue.claimDue(now, 50)).containsExactly(
                new MemberSessionRevocation(
                        java.util.UUID.fromString(first.sessionId()), now, now.plus(Duration.ofDays(14))));
    }

    @Test
    @DisplayName("레거시 회원 세션 전체 폐기도 복구 큐에 적재한다")
    void memberSession_레거시만료시각_전체폐기_복구큐선적재() {
        // REVOKE_ALL_SCRIPT는 memberSessionClock이 아닌 Redis TIME으로 만료를 판정하므로
        // 실제 Redis 시각에 맞춘 fixture를 사용해야 레거시 세션이 테스트 실행일에 만료되지 않는다.
        // 복구 큐 score도 Redis TIME이므로 JVM 시각과의 오차를 흡수할 상한 여유가 필요하다.
        Instant now = Instant.now();
        when(memberSessionClock.instant()).thenReturn(now);

        MemberSession issued = memberSessionStore.issue("member-a", Duration.ofDays(14));
        replaceWithLegacySessionRecord(issued);

        assertThat(memberSessionStore.revokeAll("member-a")).containsExactly(issued.sessionId());
        assertThat(memberSessionRevocationRecoveryQueue.claimDue(now.plusSeconds(10), 50))
                .extracting(MemberSessionRevocation::sessionId)
                .containsExactly(java.util.UUID.fromString(issued.sessionId()));
    }

    @Test
    @DisplayName("레거시 회원 세션의 refresh token 재사용도 복구 큐에 적재한다")
    void memberSession_레거시만료시각_refreshToken재사용_복구큐선적재() {
        Instant now = Instant.parse("2026-07-29T10:00:00Z");
        when(memberSessionClock.instant()).thenReturn(now);

        MemberSession issued = memberSessionStore.issue("member-a", Duration.ofDays(14));
        memberSessionStore.rotate(issued.refreshToken(), Duration.ofDays(14));
        replaceWithLegacySessionRecord(issued);

        assertThatThrownBy(() -> memberSessionStore.rotate(issued.refreshToken(), Duration.ofDays(14)))
                .isInstanceOf(InvalidMemberSessionException.class);
        assertThat(memberSessionRevocationRecoveryQueue.claimDue(now, 50)).containsExactly(
                new MemberSessionRevocation(
                        java.util.UUID.fromString(issued.sessionId()), now, now.plus(Duration.ofDays(14))));
    }

    private void replaceWithLegacySessionRecord(MemberSession session) {
        String key = "auth:session:data:" + session.sessionId();
        String serialized = redisTemplate.opsForValue().get(key);
        String legacySerialized = serialized.replaceFirst(",\\s*\\\"expiresAtEpochMillis\\\"\\s*:\\s*\\d+(?=\\s*})", "");

        assertThat(legacySerialized).doesNotContain("expiresAtEpochMillis");
        redisTemplate.opsForValue().set(key, legacySerialized, Duration.ofDays(14));
    }

    private MemberSession issueWhenReleased(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return memberSessionStore.issue("member-a", Duration.ofDays(14));
    }

    private MemberSession rotateWhenReleased(String refreshToken, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return memberSessionStore.rotate(refreshToken, Duration.ofDays(14));
    }

    private boolean completedSuccessfully(Future<MemberSession> attempt) {
        try {
            attempt.get(5, TimeUnit.SECONDS);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean completedExceptionally(Future<MemberSession> attempt) {
        return !completedSuccessfully(attempt);
    }
}

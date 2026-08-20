package com.masiton.member.infrastructure.redis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.masiton.member.application.MemberSessionRevocation;

import static org.assertj.core.api.Assertions.assertThat;

import com.masiton.test.FullContextIntegrationTest;

@ResourceLock("shared-test-infrastructure")
@DisplayName("RedisMemberSessionRevocationRecoveryQueue")
class RedisMemberSessionRevocationRecoveryQueueIntegrationTest {

    private static final int REDIS_PORT = 6379;

    private LettuceConnectionFactory connectionFactory;
    private RedisMemberSessionRevocationRecoveryQueue recoveryQueue;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                FullContextIntegrationTest.REDIS.getHost(),
                FullContextIntegrationTest.REDIS.getMappedPort(REDIS_PORT)));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        FullContextIntegrationTest.deleteRedisKeys(
                redisTemplate, "auth:member:session:revocation:recovery:*");
        recoveryQueue = new RedisMemberSessionRevocationRecoveryQueue(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    @DisplayName("폐기 정보를 보존하고 실패 뒤 15분 후 다시 청구하며 성공 시 제거한다")
    void 복구큐_폐기정보_15분재시도후성공시제거() {
        Instant now = Instant.parse("2026-07-30T03:10:00Z");
        MemberSessionRevocation revocation = new MemberSessionRevocation(
                UUID.randomUUID(), now, now.plusSeconds(60 * 60));

        recoveryQueue.enqueue(revocation, now);

        assertThat(recoveryQueue.claimDue(now, 50)).containsExactly(revocation);
        assertThat(recoveryQueue.claimDue(now.plusSeconds(14 * 60), 50)).isEmpty();
        assertThat(recoveryQueue.claimDue(now.plusSeconds(15 * 60), 50)).containsExactly(revocation);

        recoveryQueue.complete(revocation);

        assertThat(recoveryQueue.claimDue(now.plusSeconds(30 * 60), 50)).isEmpty();
    }

    @Test
    @DisplayName("동일 sid 폐기는 가장 이른 폐기 시각과 가장 늦은 만료 시각을 보존한다")
    void 복구큐_동일sid_폐기시각병합() {
        Instant now = Instant.parse("2026-07-30T03:10:00Z");
        UUID sessionId = UUID.randomUUID();
        MemberSessionRevocation first = new MemberSessionRevocation(sessionId, now, now.plusSeconds(60));
        MemberSessionRevocation second = new MemberSessionRevocation(sessionId, now.plusSeconds(10), now.plusSeconds(120));

        recoveryQueue.enqueue(first, now);
        recoveryQueue.enqueue(second, now.plusSeconds(10));

        assertThat(recoveryQueue.claimDue(now.plusSeconds(10), 50)).containsExactly(
                new MemberSessionRevocation(sessionId, first.revokedAt(), second.expiresAt()));
    }
}

package com.masiton.member.infrastructure.redis;

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

import com.masiton.member.application.port.out.MemberRateLimitStore;
import com.masiton.member.infrastructure.configuration.MemberRateLimitProperties;

import static org.assertj.core.api.Assertions.assertThat;

import com.masiton.test.FullContextIntegrationTest;

@DisplayName("RedisMemberRateLimitStore")
class RedisMemberRateLimitStoreIntegrationTest {

    private static final int REDIS_PORT = 6379;

    private LettuceConnectionFactory connectionFactory;
    private RedisMemberRateLimitStore store;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                FullContextIntegrationTest.REDIS.getHost(),
                FullContextIntegrationTest.REDIS.getMappedPort(REDIS_PORT)));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();

        MemberRateLimitProperties properties = new MemberRateLimitProperties();
        properties.setSecret("test-secret");
        store = new RedisMemberRateLimitStore(redisTemplate, properties);
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    @DisplayName("이메일 인증 제출은 10분 동안 출처당 10회만 허용하고 11회째는 Retry-After를 돌려준다")
    void acquireEmailVerificationAttempt_10회허용후11회차단() {
        for (int attempt = 1; attempt <= 10; attempt++) {
            assertThat(store.acquireEmailVerificationAttempt("198.51.100.20"))
                    .isEqualTo(MemberRateLimitStore.VerificationAttemptResult.permit());
        }

        MemberRateLimitStore.VerificationAttemptResult blocked = store.acquireEmailVerificationAttempt("198.51.100.20");

        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isBetween(1L, 600L);
        assertThat(store.acquireEmailVerificationAttempt("198.51.100.20").allowed()).isFalse();
    }
}

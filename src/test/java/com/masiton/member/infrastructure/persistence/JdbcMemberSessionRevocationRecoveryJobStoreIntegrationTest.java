package com.masiton.member.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masiton.member.application.MemberSessionRevocation;
import com.masiton.member.application.port.out.MemberSessionRevocationRecoveryJobStore;
import com.masiton.test.TestProfile;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestProfile
@Testcontainers
@DisplayName("회원 세션 폐기 복구 작업 PostgreSQL")
class JdbcMemberSessionRevocationRecoveryJobStoreIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.10-alpine")
            .withDatabaseName("masiton")
            .withUsername("masiton")
            .withPassword("masiton_test");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MemberSessionRevocationRecoveryJobStore recoveryJobs;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("복구 작업은 병합해 청구하고 실패 재예약과 성공 제거를 지원한다")
    void 복구작업_병합청구_재예약_성공제거() {
        Instant now = Instant.parse("2026-07-30T03:10:00Z");
        UUID sessionId = UUID.randomUUID();
        MemberSessionRevocation first = new MemberSessionRevocation(sessionId, now, now.plusSeconds(60));
        MemberSessionRevocation second = new MemberSessionRevocation(
                sessionId, now.plusSeconds(10), now.plusSeconds(120));

        recoveryJobs.enqueue(first, now);
        recoveryJobs.enqueue(second, now.plusSeconds(10));

        assertThat(recoveryJobs.claimDue(now.plusSeconds(10), 50)).containsExactly(
                new MemberSessionRevocation(sessionId, first.revokedAt(), second.expiresAt()));
        assertThat(nextAttemptAt(sessionId)).isEqualTo(now.plusSeconds(15 * 60 + 10));
        assertThat(attemptCount(sessionId)).isEqualTo(1);

        recoveryJobs.reschedule(sessionId, now.plusSeconds(20));

        assertThat(nextAttemptAt(sessionId)).isEqualTo(now.plusSeconds(15 * 60 + 20));
        assertThat(recoveryJobs.findUnresolvedBefore(now.plusSeconds(60 * 60), now.plusSeconds(20), 10))
                .containsExactly(sessionId);

        recoveryJobs.complete(sessionId);

        assertThat(recoveryJobs.findUnresolvedBefore(now.plusSeconds(60 * 60), now.plusSeconds(20), 10)).isEmpty();
    }

    private Instant nextAttemptAt(UUID sessionId) {
        return jdbcTemplate.queryForObject(
                "SELECT next_attempt_at FROM member_session_revocation_recovery WHERE session_id = ?",
                (resultSet, rowNum) -> resultSet.getTimestamp(1).toInstant(), sessionId);
    }

    private int attemptCount(UUID sessionId) {
        return jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM member_session_revocation_recovery WHERE session_id = ?", Integer.class, sessionId);
    }
}

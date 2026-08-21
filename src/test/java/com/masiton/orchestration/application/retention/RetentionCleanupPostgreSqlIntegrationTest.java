package com.masiton.orchestration.application.retention;

import java.nio.ByteBuffer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masiton.orchestration.application.retention.port.out.RetentionCleanupStore;
import com.masiton.test.TestProfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;

/**
 * TST-E2-LIFE-001: 90일 알림 정리와 1년 제보·신고 식별 제거 배치의 중간 실패를
 * 실제 PostgreSQL 트랜잭션 경계에서 검증한다. cutoff는 고정 값으로 직접 전달하고
 * Clock에 의존하지 않는다.
 */
@SpringBootTest
@TestProfile
@DisplayName("2차 확장 보존 정리 PostgreSQL 통합")
class RetentionCleanupPostgreSqlIntegrationTest extends com.masiton.test.FullContextIntegrationTest {

    private static final int BATCH_SIZE = RetentionCleanupService.BATCH_SIZE;

    @Autowired
    private RetentionCleanupBatchCommand batches;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private RetentionCleanupStore retentionCleanupStore;

    @BeforeEach
    void setUp() {
        Mockito.reset(retentionCleanupStore);
        jdbcTemplate.execute("TRUNCATE TABLE notification, moderation_history, report, submission CASCADE");
    }

    @Test
    @DisplayName("알림 정리 배치가 중간에 실패하면 이전 배치는 커밋된 채 남고 재실행이 같은 cutoff로 수렴한다")
    void 알림정리_중간실패_커밋경계유지와재수렴() {
        // given
        UUID memberId = insertMember();
        OffsetDateTime cutoff = OffsetDateTime.parse("2028-01-01T00:00:00+09:00");
        int total = BATCH_SIZE + 500;
        List<UUID> submissionIds = new ArrayList<>();
        for (int index = 0; index < total; index++) {
            submissionIds.add(insertRejectedSubmission(memberId, cutoff.minusDays(1), index));
        }
        for (int index = 0; index < total; index++) {
            OffsetDateTime createdAt = cutoff.minusSeconds(total - index);
            insertNotification(memberId, submissionIds.get(index), UUID.randomUUID(), createdAt);
        }
        AtomicInteger invocationCount = new AtomicInteger();
        doAnswer(invocation -> {
            if (invocationCount.incrementAndGet() == 2) {
                throw new RuntimeException("Simulated notification retention batch failure");
            }
            return invocation.callRealMethod();
        }).when(retentionCleanupStore).deleteExpiredNotifications(any(), anyInt());

        // when: 첫 배치는 커밋되고, 두 번째 배치는 실패해 아무 것도 지우지 못한다
        int firstBatch = batches.deleteExpiredNotifications(cutoff);
        assertThat(firstBatch).isEqualTo(BATCH_SIZE);
        assertThatThrownBy(() -> batches.deleteExpiredNotifications(cutoff))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Simulated notification retention batch failure");

        // then: 실패한 배치는 어떤 행도 지우지 못해 남은 후보 수는 (total - 첫 배치) 다.
        assertThat(count("notification", memberId)).isEqualTo(total - BATCH_SIZE);

        // when: 같은 cutoff로 재실행하면 남은 후보를 모두 수렴시킨다 (최신 200개는 보존)
        int secondAttemptBatch = batches.deleteExpiredNotifications(cutoff);
        assertThat(secondAttemptBatch).isEqualTo(total - 200 - BATCH_SIZE);
        assertThat(count("notification", memberId)).isEqualTo(200);

        // then: 더 남은 후보가 없어 재실행은 0건으로 수렴한다 (동일 cutoff, 재실행 안전)
        assertThat(batches.deleteExpiredNotifications(cutoff)).isZero();
        assertThat(count("notification", memberId)).isEqualTo(200);
    }

    @Test
    @DisplayName("제보 회원 식별 제거 배치가 중간에 실패하면 이전 배치는 커밋된 채 남고 재실행이 같은 cutoff로 수렴한다")
    void 제보식별제거_중간실패_커밋경계유지와재수렴() {
        // given
        UUID memberId = insertMember();
        OffsetDateTime participationCutoff = OffsetDateTime.parse("2027-03-01T09:00:00+09:00");
        OffsetDateTime unlinkedAt = OffsetDateTime.parse("2028-03-01T09:00:00+09:00");
        int total = BATCH_SIZE + 300;
        List<UUID> submissionIds = new ArrayList<>();
        for (int index = 0; index < total; index++) {
            submissionIds.add(insertRejectedSubmission(memberId, participationCutoff.minusDays(1), index));
        }
        AtomicInteger invocationCount = new AtomicInteger();
        doAnswer(invocation -> {
            if (invocationCount.incrementAndGet() == 2) {
                throw new RuntimeException("Simulated submission unlink batch failure");
            }
            return invocation.callRealMethod();
        }).when(retentionCleanupStore).unlinkExpiredSubmissionMembers(any(), any(), anyInt());

        // when: 첫 배치는 커밋되고, 두 번째 배치는 실패해 아무 것도 연결 제거하지 못한다
        int firstBatch = batches.unlinkExpiredSubmissionMembers(participationCutoff, unlinkedAt);
        assertThat(firstBatch).isEqualTo(BATCH_SIZE);
        assertThatThrownBy(() -> batches.unlinkExpiredSubmissionMembers(participationCutoff, unlinkedAt))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Simulated submission unlink batch failure");

        // then: 실패한 배치는 어떤 행도 연결 제거하지 못해 남은 연결 건수는 (total - 첫 배치) 다.
        assertThat(count("submission", memberId)).isEqualTo(total - BATCH_SIZE);
        assertThat(unlinkedSubmissionCount()).isEqualTo(BATCH_SIZE);

        // when: 같은 cutoff로 재실행하면 남은 후보를 모두 수렴시킨다.
        int secondAttemptBatch = batches.unlinkExpiredSubmissionMembers(participationCutoff, unlinkedAt);
        assertThat(secondAttemptBatch).isEqualTo(total - BATCH_SIZE);
        assertThat(count("submission", memberId)).isZero();
        assertThat(unlinkedSubmissionCount()).isEqualTo(total);

        // then: 더 남은 후보가 없어 재실행은 0건으로 수렴한다 (동일 cutoff, 재실행 안전)
        assertThat(batches.unlinkExpiredSubmissionMembers(participationCutoff, unlinkedAt)).isZero();
    }

    private UUID insertMember() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO member_account (id, email, password_hash, email_verified_at, status)
                VALUES (?, ?, 'password-hash', CURRENT_TIMESTAMP, 'ACTIVE')
                """, id, id + "@example.com");
        return id;
    }

    private UUID insertRejectedSubmission(UUID memberId, OffsetDateTime terminalAt, int fingerprintSeed) {
        UUID id = UUID.randomUUID();
        byte[] fingerprint = new byte[32];
        ByteBuffer.wrap(fingerprint).putInt(fingerprintSeed);
        jdbcTemplate.update("INSERT INTO submission (id, member_id, target_type, candidate, target_fingerprint, "
                        + "description, status, member_reason, created_at, updated_at, terminal_at) VALUES "
                        + "(?, ?, 'RESTAURANT', '{}'::jsonb, ?, '보존 정리 배치 실패 테스트 제보입니다', 'REJECTED', "
                        + "'검토 종료', ?, ?, ?)",
                id, memberId, fingerprint, terminalAt, terminalAt, terminalAt);
        return id;
    }

    private void insertNotification(UUID memberId, UUID submissionId, UUID notificationId, OffsetDateTime createdAt) {
        jdbcTemplate.update("INSERT INTO notification (id, member_id, submission_id, status, title, message, created_at) "
                        + "VALUES (?, ?, ?, 'REJECTED', '처리 결과', '처리 결과를 확인하세요', ?)",
                notificationId, memberId, submissionId, createdAt);
    }

    private long count(String tableName, UUID memberId) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + tableName + " WHERE member_id = ?", Long.class, memberId);
        return value == null ? 0 : value;
    }

    private long unlinkedSubmissionCount() {
        Long value = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM submission WHERE member_unlinked_at IS NOT NULL", Long.class);
        return value == null ? 0 : value;
    }
}

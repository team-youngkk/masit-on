package com.masiton.member.application;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masiton.member.application.port.out.MemberDeletionJobStore;
import com.masiton.member.infrastructure.persistence.JdbcMemberDeletionJobStore;
import com.masiton.orchestration.application.retention.RetentionCleanupBatchCommand;
import com.masiton.test.TestProfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestProfile
@Testcontainers
@Import(MemberDeletionCleanupPostgreSqlIntegrationTest.LateFailureConfiguration.class)
@DisplayName("회원 탈퇴 정리 PostgreSQL 통합")
class MemberDeletionCleanupPostgreSqlIntegrationTest {

    private static final UUID REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID FOOD_CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");

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
    private MemberDeletionJobStore jobs;

    @Autowired
    private MemberDeletionCleanupService cleanupService;

    @Autowired
    private MemberDeletionCleanupCommandService cleanupCommands;

    @Autowired
    private LateFailureMemberDeletionJobStore lateFailureJobs;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RetentionCleanupBatchCommand retentionCleanupBatch;

    @Test
    @DisplayName("작업을 등록하고 청구한 뒤 재예약 및 완료 상태를 PostgreSQL에 반영한다")
    void 작업_등록_청구_재예약_완료_상태를_반영한다() {
        // given
        UUID memberId = UUID.randomUUID();
        Instant requestedAt = Instant.parse("2026-07-30T01:00:00Z");
        jobs.enqueue(memberId, requestedAt);
        jobs.enqueue(memberId, requestedAt.plusSeconds(1));

        // when
        List<UUID> claimed = jobs.claimDue(requestedAt, 1);

        // then
        assertThat(claimed).containsExactly(memberId);
        assertThat(count("member_deletion_job", memberId)).isEqualTo(1);
        assertThat(jobTimestamp("requested_at", memberId)).isEqualTo(requestedAt);
        assertThat(jobTimestamp("last_attempt_at", memberId)).isEqualTo(requestedAt);
        assertThat(jobTimestamp("next_attempt_at", memberId)).isEqualTo(requestedAt.plusSeconds(15 * 60));
        assertThat(jobAttemptCount(memberId)).isEqualTo(1);

        Instant rescheduledAt = Instant.parse("2026-07-30T01:05:00Z");
        jobs.reschedule(memberId, rescheduledAt);

        assertThat(jobTimestamp("next_attempt_at", memberId)).isEqualTo(rescheduledAt.plusSeconds(15 * 60));

        jobs.complete(memberId);

        assertThat(count("member_deletion_job", memberId)).isZero();
    }

    @Test
    @DisplayName("탈퇴 작업은 Action Token을 지우고 회원 및 개인화 관계를 물리 삭제한다")
    void 탈퇴_작업은_ActionToken과_개인화_관계를_물리_삭제한다() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID restaurantId = insertRestaurant();
        UUID submissionId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        Instant now = Instant.now();
        insertDeletionPendingMember(memberId, now);
        insertActionToken(memberId, now);
        insertParticipation(memberId, restaurantId, submissionId, reportId, now);
        jdbcTemplate.update("INSERT INTO favorite (member_id, restaurant_id, favorited_at) VALUES (?, ?, ?)",
                memberId, restaurantId, asOffsetDateTime(now));
        jdbcTemplate.update("INSERT INTO recent_restaurant_view (member_id, restaurant_id, last_viewed_at) VALUES (?, ?, ?)",
                memberId, restaurantId, asOffsetDateTime(now));
        jobs.enqueue(memberId, now);

        // when
        cleanupService.run();

        // then
        assertThat(memberAccountCount(memberId)).isZero();
        assertThat(count("member_action_token", memberId)).isZero();
        assertThat(count("favorite", memberId)).isZero();
        assertThat(count("recent_restaurant_view", memberId)).isZero();
        assertThat(participationMemberId("submission", submissionId)).isNull();
        assertThat(participationMemberId("report", reportId)).isNull();
        assertThat(participationUnlinkedAt("submission", submissionId)).isNotNull();
        assertThat(participationUnlinkedAt("report", reportId)).isNotNull();
        assertThat(count("member_deletion_job", memberId)).isZero();
    }

    @Test
    @DisplayName("완료 삭제 직후 실패하면 모든 회원 탈퇴 정리 변경을 롤백한다")
    void 정리_완료직후실패_모든회원탈퇴정리변경을롤백한다() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID restaurantId = insertRestaurant();
        UUID submissionId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        Instant now = Instant.now();
        insertDeletionPendingMember(memberId, now);
        insertActionToken(memberId, now);
        insertParticipation(memberId, restaurantId, submissionId, reportId, now);
        jdbcTemplate.update("INSERT INTO favorite (member_id, restaurant_id, favorited_at) VALUES (?, ?, ?)",
                memberId, restaurantId, asOffsetDateTime(now));
        jdbcTemplate.update("INSERT INTO recent_restaurant_view (member_id, restaurant_id, last_viewed_at) VALUES (?, ?, ?)",
                memberId, restaurantId, asOffsetDateTime(now));
        jobs.enqueue(memberId, now);
        lateFailureJobs.failAfterCompleting(memberId);

        // when
        try {
            assertThatThrownBy(() -> cleanupCommands.cleanup(memberId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("late cleanup failure");
        } finally {
            lateFailureJobs.disableFailure();
        }

        // then
        assertThat(memberAccountCount(memberId)).isEqualTo(1);
        assertThat(count("member_action_token", memberId)).isEqualTo(1);
        assertThat(count("favorite", memberId)).isEqualTo(1);
        assertThat(count("recent_restaurant_view", memberId)).isEqualTo(1);
        assertThat(participationMemberId("submission", submissionId)).isEqualTo(memberId);
        assertThat(participationMemberId("report", reportId)).isEqualTo(memberId);
        assertThat(participationUnlinkedAt("submission", submissionId)).isNull();
        assertThat(participationUnlinkedAt("report", reportId)).isNull();
        assertThat(count("member_deletion_job", memberId)).isEqualTo(1);
    }

    @Test
    @DisplayName("종료 1년 cutoff와 알림 90일·최신 200개 경계를 PostgreSQL에서 정리한다")
    void 보존정리_cutoff와_최신200개_경계() {
        // given
        UUID memberId = UUID.randomUUID();
        Instant now = Instant.parse("2028-03-01T00:00:00Z");
        OffsetDateTime unlinkCutoff = asOffsetDateTime(now.minusSeconds(365L * 24 * 60 * 60));
        OffsetDateTime notificationCutoff = asOffsetDateTime(now.minusSeconds(90L * 24 * 60 * 60));
        insertDeletionPendingMember(memberId, now);
        UUID expiredSubmissionId = insertRejectedSubmission(memberId, unlinkCutoff, 0);
        UUID boundaryReportId = insertRejectedReport(memberId, UUID.randomUUID(), unlinkCutoff, 0);
        UUID futureSubmissionId = insertRejectedSubmission(memberId, unlinkCutoff.plusNanos(1_000), 1);

        UUID oldestNotificationId = null;
        UUID boundaryNotificationId = null;
        for (int index = 0; index < 201; index++) {
            UUID submissionId = insertRejectedSubmission(memberId, asOffsetDateTime(now), index + 10);
            UUID notificationId = UUID.randomUUID();
            OffsetDateTime createdAt = index == 0
                    ? notificationCutoff.minusSeconds(1)
                    : notificationCutoff.plusSeconds(index - 1L);
            insertNotification(memberId, submissionId, notificationId, createdAt);
            if (index == 0) {
                oldestNotificationId = notificationId;
            } else if (index == 1) {
                boundaryNotificationId = notificationId;
            }
        }

        // when
        int unlinkedSubmissions = retentionCleanupBatch.unlinkExpiredSubmissionMembers(unlinkCutoff, asOffsetDateTime(now));
        int unlinkedReports = retentionCleanupBatch.unlinkExpiredReportMembers(unlinkCutoff, asOffsetDateTime(now));
        int deletedNotifications = retentionCleanupBatch.deleteExpiredNotifications(notificationCutoff);

        // then
        assertThat(unlinkedSubmissions).isEqualTo(1);
        assertThat(unlinkedReports).isEqualTo(1);
        assertThat(participationMemberId("submission", expiredSubmissionId)).isNull();
        assertThat(participationMemberId("report", boundaryReportId)).isNull();
        assertThat(participationMemberId("submission", futureSubmissionId)).isEqualTo(memberId);
        assertThat(deletedNotifications).isEqualTo(1);
        assertThat(rowCount("notification", oldestNotificationId)).isZero();
        assertThat(rowCount("notification", boundaryNotificationId)).isEqualTo(1);
    }

    private UUID insertRestaurant() {
        UUID restaurantId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO restaurant (id, region_id, food_category_id, name, kakao_place_id, "
                        + "kakao_place_url, road_address, phone_number) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                restaurantId, REGION_ID, FOOD_CATEGORY_ID, "삭제 정리 테스트 식당 " + suffix,
                "cleanup-" + suffix, "https://example.com/places/" + suffix,
                "서울특별시 종로구 테스트로 1", "02-1234-5678");
        return restaurantId;
    }

    private void insertDeletionPendingMember(UUID memberId, Instant now) {
        jdbcTemplate.update("INSERT INTO member_account (id, email, password_hash, email_verified_at, status, "
                        + "deletion_requested_at, created_at, updated_at) VALUES (?, ?, ?, ?, 'DELETION_PENDING', ?, ?, ?)",
                memberId, "cleanup-" + memberId + "@example.com", "password-hash", asOffsetDateTime(now),
                asOffsetDateTime(now), asOffsetDateTime(now), asOffsetDateTime(now));
    }

    private void insertActionToken(UUID memberId, Instant now) {
        byte[] tokenHash = UUID.randomUUID().toString().replace("-", "").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        jdbcTemplate.update("INSERT INTO member_action_token (id, member_id, token_hash, purpose, status, issued_at, expires_at) "
                        + "VALUES (?, ?, ?, 'PASSWORD_RESET', 'ISSUED', ?, ?)",
                UUID.randomUUID(), memberId, tokenHash, asOffsetDateTime(now), asOffsetDateTime(now.plusSeconds(60)));
    }

    private void insertParticipation(
            UUID memberId,
            UUID restaurantId,
            UUID submissionId,
            UUID reportId,
            Instant now
    ) {
        jdbcTemplate.update("INSERT INTO submission (id, member_id, target_type, candidate, target_fingerprint, "
                        + "description, status, created_at, updated_at) VALUES (?, ?, 'RESTAURANT', '{}'::jsonb, ?, "
                        + "'탈퇴 연결 제거 테스트 제보입니다', 'RECEIVED', ?, ?)",
                submissionId, memberId, new byte[32], asOffsetDateTime(now), asOffsetDateTime(now));
        jdbcTemplate.update("INSERT INTO report (id, member_id, target_type, target_id, report_type, description, "
                        + "status, created_at, updated_at) VALUES (?, ?, 'RESTAURANT', ?, 'ERROR', "
                        + "'탈퇴 연결 제거 테스트 신고입니다', 'RECEIVED', ?, ?)",
                reportId, memberId, restaurantId, asOffsetDateTime(now), asOffsetDateTime(now));
    }

    private UUID insertRejectedSubmission(UUID memberId, OffsetDateTime terminalAt, int fingerprintSeed) {
        UUID id = UUID.randomUUID();
        byte[] fingerprint = new byte[32];
        java.nio.ByteBuffer.wrap(fingerprint).putInt(fingerprintSeed);
        jdbcTemplate.update("INSERT INTO submission (id, member_id, target_type, candidate, target_fingerprint, "
                        + "description, status, member_reason, created_at, updated_at, terminal_at) VALUES "
                        + "(?, ?, 'RESTAURANT', '{}'::jsonb, ?, '보존 경계 테스트 제보입니다', 'REJECTED', "
                        + "'검토 종료', ?, ?, ?)",
                id, memberId, fingerprint, terminalAt, terminalAt, terminalAt);
        return id;
    }

    private UUID insertRejectedReport(UUID memberId, UUID targetId, OffsetDateTime terminalAt, int suffix) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO report (id, member_id, target_type, target_id, report_type, description, "
                        + "status, member_reason, created_at, updated_at, terminal_at) VALUES "
                        + "(?, ?, 'RESTAURANT', ?, 'ERROR', ?, 'REJECTED', '검토 종료', ?, ?, ?)",
                id, memberId, targetId, "보존 경계 테스트 신고입니다 " + suffix, terminalAt, terminalAt, terminalAt);
        return id;
    }

    private void insertNotification(UUID memberId, UUID submissionId, UUID notificationId, OffsetDateTime createdAt) {
        jdbcTemplate.update("INSERT INTO notification (id, member_id, submission_id, status, title, message, created_at) "
                        + "VALUES (?, ?, ?, 'REJECTED', '처리 결과', '처리 결과를 확인하세요', ?)",
                notificationId, memberId, submissionId, createdAt);
    }

    private UUID participationMemberId(String tableName, UUID id) {
        return jdbcTemplate.queryForObject("SELECT member_id FROM " + tableName + " WHERE id = ?",
                (resultSet, rowNum) -> resultSet.getObject(1, UUID.class), id);
    }

    private OffsetDateTime participationUnlinkedAt(String tableName, UUID id) {
        return jdbcTemplate.queryForObject("SELECT member_unlinked_at FROM " + tableName + " WHERE id = ?",
                (resultSet, rowNum) -> resultSet.getObject(1, OffsetDateTime.class), id);
    }

    private long rowCount(String tableName, UUID id) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + tableName + " WHERE id = ?", Long.class, id);
    }

    private long count(String tableName, UUID memberId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + tableName + " WHERE member_id = ?", Long.class, memberId);
    }

    private long memberAccountCount(UUID memberId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM member_account WHERE id = ?", Long.class, memberId);
    }

    private Instant jobTimestamp(String columnName, UUID memberId) {
        return jdbcTemplate.queryForObject("SELECT " + columnName + " FROM member_deletion_job WHERE member_id = ?",
                (resultSet, rowNum) -> resultSet.getTimestamp(1).toInstant(), memberId);
    }

    private int jobAttemptCount(UUID memberId) {
        return jdbcTemplate.queryForObject("SELECT attempt_count FROM member_deletion_job WHERE member_id = ?", Integer.class, memberId);
    }

    private OffsetDateTime asOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LateFailureConfiguration {
        @Bean
        @Primary
        LateFailureMemberDeletionJobStore lateFailureMemberDeletionJobStore(JdbcMemberDeletionJobStore delegate) {
            return new LateFailureMemberDeletionJobStore(delegate);
        }
    }

    static class LateFailureMemberDeletionJobStore implements MemberDeletionJobStore {
        private final MemberDeletionJobStore delegate;
        private UUID memberIdToFail;

        LateFailureMemberDeletionJobStore(MemberDeletionJobStore delegate) {
            this.delegate = delegate;
        }

        void failAfterCompleting(UUID memberId) {
            memberIdToFail = memberId;
        }

        void disableFailure() {
            memberIdToFail = null;
        }

        @Override
        public void enqueue(UUID memberId, Instant now) {
            delegate.enqueue(memberId, now);
        }

        @Override
        public List<UUID> claimDue(Instant now, int limit) {
            return delegate.claimDue(now, limit);
        }

        @Override
        public boolean hasExceededOneHour(UUID memberId, Instant now) {
            return delegate.hasExceededOneHour(memberId, now);
        }

        @Override
        public void reschedule(UUID memberId, Instant now) {
            delegate.reschedule(memberId, now);
        }

        @Override
        public void complete(UUID memberId) {
            delegate.complete(memberId);
            if (memberId.equals(memberIdToFail)) {
                throw new IllegalStateException("late cleanup failure");
            }
        }
    }
}

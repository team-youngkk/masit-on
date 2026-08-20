package com.masiton.notification.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
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

import com.masiton.common.web.BusinessException;
import com.masiton.notification.application.port.in.NotificationPage;
import com.masiton.notification.application.port.in.NotificationUseCase;
import com.masiton.notification.domain.model.NotificationCreationResult;
import com.masiton.notification.domain.model.NotificationRequestType;
import com.masiton.notification.domain.model.NotificationStatus;
import com.masiton.orchestration.application.retention.port.out.RetentionCleanupStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@com.masiton.test.TestProfile
@DisplayName("회원 알림 JDBC 저장·조회")
class JdbcNotificationAdapterIntegrationTest extends com.masiton.test.FullContextIntegrationTest {

    // 보존 범위를 다루지 않는 시나리오에서는 모든 행이 항상 보존되도록 아주 오래된 cutoff를 쓴다.
    private static final OffsetDateTime NO_RETENTION_CUTOFF = OffsetDateTime.parse("1970-01-01T00:00:00Z");
    private static final int RETENTION_LIMIT = 200;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JdbcNotificationAdapter adapter;

    @Autowired
    private RetentionCleanupStore retentionCleanupStore;

    // NotificationService(실제 Bean, 시스템 Clock)를 통해 보존 cutoff·한도를 단일 출처에서 받는다.
    @Autowired
    private NotificationUseCase notificationUseCase;

    @BeforeEach
    void clearNotificationRelations() {
        jdbcTemplate.update("DELETE FROM notification");
        jdbcTemplate.update("DELETE FROM report");
        jdbcTemplate.update("DELETE FROM submission");
        jdbcTemplate.update("DELETE FROM member_account");
    }

    @Test
    @DisplayName("목록은 생성 시각 내림차순이며 동일 시각은 알림 ID 오름차순으로 안정 정렬해 페이지를 나눈다")
    void findByMember_동일생성시각_ID오름차순으로안정정렬한다() {
        // given
        UUID memberId = UUID.randomUUID();
        insertMember(memberId);
        UUID submissionId = insertSubmission(memberId);
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-08-03T00:00:00Z");
        UUID firstId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID secondId = UUID.fromString("00000000-0000-4000-8000-000000000002");
        UUID thirdId = UUID.fromString("00000000-0000-4000-8000-000000000003");
        insertNotificationForSubmission(thirdId, memberId, submissionId, NotificationStatus.COMPLETED, createdAt);
        insertNotificationForSubmission(firstId, memberId, submissionId, NotificationStatus.IN_REVIEW, createdAt);
        insertNotificationForSubmission(secondId, memberId, submissionId, NotificationStatus.ACCEPTED, createdAt);
        // 반환되는 첫 페이지 안의 알림 하나만 미리 읽어, 회원 전체 기준 unreadCount(2)와
        // 이번 페이지 항목만 세는 잘못된 집계(1)가 서로 다른 값이 되게 한다.
        adapter.markAsRead(memberId, firstId, OffsetDateTime.parse("2026-08-03T00:30:00Z"),
                NO_RETENTION_CUTOFF, RETENTION_LIMIT);

        // when
        NotificationPage firstPage = adapter.findByMember(memberId, NO_RETENTION_CUTOFF, RETENTION_LIMIT, 1, 2);
        NotificationPage secondPage = adapter.findByMember(memberId, NO_RETENTION_CUTOFF, RETENTION_LIMIT, 2, 2);

        // then
        assertThat(firstPage.items()).extracting(item -> item.notificationId())
                .containsExactly(firstId, secondId);
        assertThat(firstPage.items().get(0).read()).isTrue();
        assertThat(firstPage.items().get(1).read()).isFalse();
        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(firstPage.hasNext()).isTrue();
        // unreadCount는 이번 페이지의 2건이 아니라 회원 전체 기준 2건(secondId, thirdId)이어야 한다.
        assertThat(firstPage.unreadCount()).isEqualTo(2);
        assertThat(secondPage.items()).extracting(item -> item.notificationId())
                .containsExactly(thirdId);
        assertThat(secondPage.hasNext()).isFalse();
        // 페이지가 바뀌어도(이 페이지는 미읽음 1건뿐) 회원 전체 기준 값은 같아야 한다.
        assertThat(secondPage.unreadCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("결과 범위를 벗어난 페이지도 오류 없이 빈 목록을 반환한다")
    void findByMember_범위밖페이지_빈목록을반환한다() {
        // given
        UUID memberId = UUID.randomUUID();
        insertMember(memberId);
        UUID submissionId = insertSubmission(memberId);
        insertNotificationForSubmission(UUID.randomUUID(), memberId, submissionId,
                NotificationStatus.IN_REVIEW, OffsetDateTime.parse("2026-08-03T00:00:00Z"));

        // when
        NotificationPage page = adapter.findByMember(memberId, NO_RETENTION_CUTOFF, RETENTION_LIMIT, Integer.MAX_VALUE, 20);

        // then
        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    @DisplayName("신규 알림 생성은 요청 종류를 파생해 목록에 그대로 반영한다")
    void insertIfAbsent_신규생성_목록에요청종류반영() {
        // given
        UUID memberId = UUID.randomUUID();
        insertMember(memberId);
        UUID reportId = insertReport(memberId);
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-08-03T00:00:00Z");

        // when
        NotificationCreationResult result = adapter.insertIfAbsent(
                UUID.randomUUID(), memberId, NotificationRequestType.REPORT, reportId,
                NotificationStatus.IN_REVIEW, "신고 검토가 시작되었습니다.", "접수한 신고를 검토하고 있습니다.", createdAt);
        NotificationPage page = adapter.findByMember(memberId, NO_RETENTION_CUTOFF, RETENTION_LIMIT, 1, 20);

        // then
        assertThat(result.created()).isTrue();
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).notificationId()).isEqualTo(result.notificationId());
        assertThat(page.items().get(0).requestType()).isEqualTo(NotificationRequestType.REPORT);
        assertThat(page.items().get(0).requestId()).isEqualTo(reportId);
        assertThat(page.items().get(0).read()).isFalse();
        assertThat(page.items().get(0).readAt()).isNull();
    }

    @Test
    @DisplayName("같은 요청·상태 알림을 동시에 생성해도 한 건으로 수렴한다")
    void insertIfAbsent_동시생성_한건으로수렴한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        insertMember(memberId);
        UUID submissionId = insertSubmission(memberId);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        // when
        List<NotificationCreationResult> results;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<NotificationCreationResult>> futures = List.of(
                    executor.submit(() -> insertAfterStart(memberId, submissionId, ready, start)),
                    executor.submit(() -> insertAfterStart(memberId, submissionId, ready, start)));
            ready.await();
            start.countDown();
            results = futures.stream().map(this::join).toList();
        }

        // then
        assertThat(results.get(0).notificationId()).isEqualTo(results.get(1).notificationId());
        assertThat(results.stream().filter(NotificationCreationResult::created).count()).isEqualTo(1);
        assertThat(countBySubmission(submissionId)).isEqualTo(1);
    }

    @Test
    @DisplayName("읽은 알림에 같은 요청·상태로 재시도해도 본문과 읽음 상태를 덮어쓰지 않는다")
    void insertIfAbsent_읽은알림에재시도_본문과읽음상태보존한다() {
        // given
        UUID memberId = UUID.randomUUID();
        insertMember(memberId);
        UUID submissionId = insertSubmission(memberId);
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-08-03T00:00:00Z");
        NotificationCreationResult created = adapter.insertIfAbsent(
                UUID.randomUUID(), memberId, NotificationRequestType.SUBMISSION, submissionId,
                NotificationStatus.IN_REVIEW, "제보 검토가 시작되었습니다.", "접수한 제보를 검토하고 있습니다.", createdAt);
        Optional<OffsetDateTime> readAt = adapter.markAsRead(
                memberId, created.notificationId(), OffsetDateTime.parse("2026-08-03T01:00:00Z"),
                NO_RETENTION_CUTOFF, RETENTION_LIMIT);

        // when
        NotificationCreationResult retried = adapter.insertIfAbsent(
                UUID.randomUUID(), memberId, NotificationRequestType.SUBMISSION, submissionId,
                NotificationStatus.IN_REVIEW, "다른 제목", "다른 본문", createdAt.plusSeconds(1));

        // then
        assertThat(retried.created()).isFalse();
        assertThat(retried.notificationId()).isEqualTo(created.notificationId());
        assertThat(titleOf(retried.notificationId())).isEqualTo("제보 검토가 시작되었습니다.");
        assertThat(messageOf(retried.notificationId())).isEqualTo("접수한 제보를 검토하고 있습니다.");
        assertThat(readAtOf(retried.notificationId())).isEqualTo(readAt.orElseThrow());
    }

    @Test
    @DisplayName("정확한 미읽음 수는 100건을 넘어도 축약 없이 그대로 센다")
    void countUnread_100건초과_정확한수를반환한다() {
        // given
        UUID memberId = UUID.randomUUID();
        insertMember(memberId);
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-08-03T00:00:00Z");
        for (int index = 0; index < 101; index++) {
            UUID reportId = insertReport(memberId);
            insertNotificationForReport(UUID.randomUUID(), memberId, reportId,
                    NotificationStatus.IN_REVIEW, createdAt.plusSeconds(index));
        }

        // when
        int unreadCount = adapter.countUnread(memberId, NO_RETENTION_CUTOFF, RETENTION_LIMIT);

        // then
        assertThat(unreadCount).isEqualTo(101);
    }

    @Test
    @DisplayName("개별 읽음은 두 번 호출해도 최초 readAt을 유지한다")
    void markAsRead_두번호출_최초readAt유지한다() {
        // given
        UUID memberId = UUID.randomUUID();
        insertMember(memberId);
        UUID submissionId = insertSubmission(memberId);
        UUID notificationId = UUID.randomUUID();
        insertNotificationForSubmission(notificationId, memberId, submissionId,
                NotificationStatus.IN_REVIEW, OffsetDateTime.parse("2026-08-03T00:00:00Z"));

        // when
        Optional<OffsetDateTime> first = adapter.markAsRead(
                memberId, notificationId, OffsetDateTime.parse("2026-08-03T01:00:00Z"),
                NO_RETENTION_CUTOFF, RETENTION_LIMIT);
        Optional<OffsetDateTime> second = adapter.markAsRead(
                memberId, notificationId, OffsetDateTime.parse("2026-08-03T02:00:00Z"),
                NO_RETENTION_CUTOFF, RETENTION_LIMIT);

        // then
        assertThat(first).isPresent();
        assertThat(second).contains(first.orElseThrow());
        assertThat(readAtOf(notificationId)).isEqualTo(first.orElseThrow());
    }

    @Test
    @DisplayName("동시 개별 읽음 요청은 최초 readAt만 남긴다")
    void markAsRead_동시요청_최초readAt만남긴다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        insertMember(memberId);
        UUID submissionId = insertSubmission(memberId);
        UUID notificationId = UUID.randomUUID();
        insertNotificationForSubmission(notificationId, memberId, submissionId,
                NotificationStatus.IN_REVIEW, OffsetDateTime.parse("2026-08-03T00:00:00Z"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        // when
        List<Optional<OffsetDateTime>> results;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Optional<OffsetDateTime>>> futures = List.of(
                    executor.submit(() -> readAfterStart(memberId, notificationId,
                            OffsetDateTime.parse("2026-08-03T01:00:00Z"), ready, start)),
                    executor.submit(() -> readAfterStart(memberId, notificationId,
                            OffsetDateTime.parse("2026-08-03T02:00:00Z"), ready, start)));
            ready.await();
            start.countDown();
            results = futures.stream().map(this::join).toList();
        }

        // then
        assertThat(results.get(0)).isPresent();
        assertThat(results.get(1)).isEqualTo(results.get(0));
        assertThat(readAtOf(notificationId)).isEqualTo(results.get(0).orElseThrow());
    }

    @Test
    @DisplayName("전체 읽음은 요청 시각 이후 생성된 알림을 건드리지 않고 unreadCount에 반영한다")
    void markAllAsRead_요청시각이후생성_건드리지않는다() {
        // given
        UUID memberId = UUID.randomUUID();
        insertMember(memberId);
        UUID beforeSubmissionId = insertSubmission(memberId);
        UUID afterSubmissionId = insertSubmission(memberId);
        UUID beforeNotificationId = UUID.randomUUID();
        UUID afterNotificationId = UUID.randomUUID();
        OffsetDateTime requestTime = OffsetDateTime.parse("2026-08-03T10:00:00Z");
        insertNotificationForSubmission(beforeNotificationId, memberId, beforeSubmissionId,
                NotificationStatus.IN_REVIEW, requestTime.minusMinutes(1));
        insertNotificationForSubmission(afterNotificationId, memberId, afterSubmissionId,
                NotificationStatus.ACCEPTED, requestTime.plusMinutes(1));

        // when
        int updatedCount = adapter.markAllAsRead(memberId, requestTime, NO_RETENTION_CUTOFF, RETENTION_LIMIT);

        // then
        assertThat(updatedCount).isEqualTo(1);
        assertThat(readAtOf(beforeNotificationId)).isNotNull();
        assertThat(readAtOf(afterNotificationId)).isNull();
        assertThat(adapter.countUnread(memberId, NO_RETENTION_CUTOFF, RETENTION_LIMIT)).isEqualTo(1);
    }

    @Test
    @DisplayName("반복되는 전체 읽음은 0건으로 성공한다")
    void markAllAsRead_반복요청_0건으로성공한다() {
        // given
        UUID memberId = UUID.randomUUID();
        insertMember(memberId);
        UUID submissionId = insertSubmission(memberId);
        UUID notificationId = UUID.randomUUID();
        OffsetDateTime requestTime = OffsetDateTime.parse("2026-08-03T10:00:00Z");
        insertNotificationForSubmission(notificationId, memberId, submissionId,
                NotificationStatus.IN_REVIEW, requestTime.minusMinutes(1));

        // when
        int first = adapter.markAllAsRead(memberId, requestTime, NO_RETENTION_CUTOFF, RETENTION_LIMIT);
        int second = adapter.markAllAsRead(memberId, requestTime, NO_RETENTION_CUTOFF, RETENTION_LIMIT);

        // then
        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
    }

    @Test
    @DisplayName("다른 회원의 알림은 목록·미읽음 수·개별 읽음·전체 읽음에서 조회되거나 변경되지 않는다")
    void 알림_다른회원_전영역에서은닉한다() {
        // given
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        insertMember(ownerId);
        insertMember(otherId);
        UUID ownerSubmissionId = insertSubmission(ownerId);
        UUID otherSubmissionId = insertSubmission(otherId);
        UUID ownerNotificationId = UUID.randomUUID();
        UUID otherNotificationId = UUID.randomUUID();
        insertNotificationForSubmission(ownerNotificationId, ownerId, ownerSubmissionId,
                NotificationStatus.IN_REVIEW, OffsetDateTime.parse("2026-08-03T00:00:00Z"));
        insertNotificationForSubmission(otherNotificationId, otherId, otherSubmissionId,
                NotificationStatus.ACCEPTED, OffsetDateTime.parse("2026-08-03T00:00:00Z"));

        // when
        NotificationPage ownerViewOfOther =
                adapter.findByMember(ownerId, NO_RETENTION_CUTOFF, RETENTION_LIMIT, 1, 20);
        Optional<OffsetDateTime> crossReadResult = adapter.markAsRead(
                ownerId, otherNotificationId, OffsetDateTime.parse("2026-08-03T01:00:00Z"),
                NO_RETENTION_CUTOFF, RETENTION_LIMIT);
        int updatedCount = adapter.markAllAsRead(
                ownerId, OffsetDateTime.parse("2026-08-03T02:00:00Z"), NO_RETENTION_CUTOFF, RETENTION_LIMIT);
        int otherUnreadCount = adapter.countUnread(otherId, NO_RETENTION_CUTOFF, RETENTION_LIMIT);

        // then
        assertThat(ownerViewOfOther.items()).extracting(item -> item.notificationId())
                .containsExactly(ownerNotificationId);
        assertThat(crossReadResult).isEmpty();
        // owner의 전체 읽음은 owner 소유 1건만 갱신하고 other 소유 알림은 건드리지 않아야 한다.
        assertThat(updatedCount).isEqualTo(1);
        assertThat(readAtOf(ownerNotificationId)).isNotNull();
        assertThat(readAtOf(otherNotificationId)).isNull();
        assertThat(otherUnreadCount).isEqualTo(1);
    }

    @Test
    @DisplayName("보존 범위를 벗어난 알림은 실제 서비스 조회·읽음 전 영역에서 제외되고, "
            + "다른 회원의 데이터가 섞여도 회원별 순위가 cleanup 삭제 대상과 정확히 일치한다")
    void getNotifications_보존범위밖알림_실서비스조회에서제외하고회원별순위로cleanup과여집합일치한다() {
        // given
        // NotificationService는 실제 시스템 Clock으로 cutoff를 스스로 계산한다. 이 테스트는 그
        // 진짜 서비스 호출(notificationUseCase)을 조회·읽음 경로의 단일 출처로 쓰고, cleanup에도
        // 같은 cutoff를 그대로 넘긴다. 프로덕션의 90일/200개 상수가 테스트의 가정과 어긋나면
        // 경계 픽스처가 반대편으로 넘어가 이 테스트가 실패로 드러낸다(리터럴 두 벌을 따로 넘기지 않음).
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cutoff = now.minusDays(90);

        UUID memberId = UUID.randomUUID();
        insertMember(memberId);
        List<UUID> recentIds = new ArrayList<>();
        // cutoff 이후(회원별 순위 1~199): 시간 조건만으로 보존된다.
        for (int index = 0; index < 199; index++) {
            UUID submissionId = insertSubmission(memberId);
            UUID notificationId = UUID.randomUUID();
            insertNotificationForSubmission(notificationId, memberId, submissionId,
                    NotificationStatus.IN_REVIEW, now.minusHours(1).plusSeconds(index));
            recentIds.add(notificationId);
        }
        // 경계(회원별 순위 200): cutoff 이전이지만 최신 200위 안에 들어 순위 조건으로 보존되어야 한다.
        UUID boundarySubmissionId = insertSubmission(memberId);
        UUID boundaryNotificationId = UUID.randomUUID();
        insertNotificationForSubmission(boundaryNotificationId, memberId, boundarySubmissionId,
                NotificationStatus.IN_REVIEW, cutoff.minusSeconds(1));
        // 제외 대상(회원별 순위 201): cutoff 이전이며 200위 밖이라 cleanup 삭제 조건과 정확히 일치해야 한다.
        UUID excludedSubmissionId = insertSubmission(memberId);
        UUID excludedNotificationId = UUID.randomUUID();
        insertNotificationForSubmission(excludedNotificationId, memberId, excludedSubmissionId,
                NotificationStatus.IN_REVIEW, cutoff.minusDays(1));

        // 다른 회원의 알림을 owner의 경계 알림보다 더 최근 시각으로 섞어 넣는다. 순위가
        // member_id 파티션 없이 테이블 전체로 계산된다면 owner의 경계 알림이 200위 밖으로
        // 밀려나야 하므로, 순위가 회원별로 독립 계산된다는 사실이 깨지면 이 테스트가 실패한다.
        UUID otherId = UUID.randomUUID();
        insertMember(otherId);
        List<UUID> otherIds = new ArrayList<>();
        for (int index = 0; index < 50; index++) {
            UUID otherSubmissionId = insertSubmission(otherId);
            UUID otherNotificationId = UUID.randomUUID();
            insertNotificationForSubmission(otherNotificationId, otherId, otherSubmissionId,
                    NotificationStatus.IN_REVIEW, now.minusMinutes(30).plusSeconds(index));
            otherIds.add(otherNotificationId);
        }

        // when
        NotificationPage page = notificationUseCase.getNotifications(memberId, 1, 200);

        // then
        assertThat(page.totalElements()).isEqualTo(200);
        assertThat(page.unreadCount()).isEqualTo(200);
        assertThat(page.items()).extracting(item -> item.notificationId())
                .contains(boundaryNotificationId)
                .doesNotContain(excludedNotificationId);

        // 보존 범위 밖 알림의 개별 읽음은 존재하지 않는 알림과 같은 404로 수렴해야 한다(ADR-DATA-012 7절).
        assertThatThrownBy(() -> notificationUseCase.markAsRead(memberId, excludedNotificationId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).code())
                        .isEqualTo("NOTIFICATION_NOT_FOUND"));

        int updatedCount = notificationUseCase.markAllAsRead(memberId).updatedCount();
        assertThat(updatedCount).isEqualTo(200);
        assertThat(readAtOf(boundaryNotificationId)).isNotNull();
        assertThat(readAtOf(excludedNotificationId)).isNull();

        // 여집합 일치: 조회에서 숨겨진 행만 cleanup이 삭제해야 하고, 다른 회원의 데이터가
        // 섞여 있어도 owner 관점의 삭제 건수는 그 데이터를 넣기 전과 같아야 한다(정확히 1건).
        int deletedByCleanup = retentionCleanupStore.deleteExpiredNotifications(cutoff, 1_000);
        assertThat(deletedByCleanup).isEqualTo(1);
        assertThat(rowExists(excludedNotificationId)).isFalse();
        assertThat(rowExists(boundaryNotificationId)).isTrue();
        assertThat(recentIds).allMatch(this::rowExists);
        assertThat(otherIds).allMatch(this::rowExists);
    }

    private NotificationCreationResult insertAfterStart(
            UUID memberId, UUID submissionId, CountDownLatch ready, CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        return adapter.insertIfAbsent(
                UUID.randomUUID(), memberId, NotificationRequestType.SUBMISSION, submissionId,
                NotificationStatus.IN_REVIEW, "제보 검토가 시작되었습니다.", "접수한 제보를 검토하고 있습니다.",
                OffsetDateTime.parse("2026-08-03T00:00:00Z"));
    }

    private Optional<OffsetDateTime> readAfterStart(
            UUID memberId, UUID notificationId, OffsetDateTime readAt, CountDownLatch ready, CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        return adapter.markAsRead(memberId, notificationId, readAt, NO_RETENTION_CUTOFF, RETENTION_LIMIT);
    }

    private <T> T join(Future<T> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new IllegalStateException("동시성 테스트 결과 대기가 실패했습니다.", exception);
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 대기가 중단되었습니다.", exception);
        }
    }

    private void insertMember(UUID memberId) {
        jdbcTemplate.update("""
                INSERT INTO member_account
                    (id, email, password_hash, email_verified_at, status)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, 'ACTIVE')
                """, memberId, memberId + "@example.com", "password-hash");
    }

    private UUID insertSubmission(UUID memberId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO submission
                    (id, member_id, target_type, candidate, target_fingerprint, description, status,
                     created_at, updated_at)
                VALUES (?, ?, 'RESTAURANT', '{}'::jsonb, ?, '알림 통합 테스트용 제보 설명입니다', 'RECEIVED',
                     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, memberId, randomFingerprint());
        return id;
    }

    private UUID insertReport(UUID memberId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO report
                    (id, member_id, target_type, target_id, report_type, description, status,
                     created_at, updated_at)
                VALUES (?, ?, 'RESTAURANT', ?, 'ERROR', '알림 통합 테스트용 신고 설명입니다', 'RECEIVED',
                     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, memberId, UUID.randomUUID());
        return id;
    }

    private byte[] randomFingerprint() {
        byte[] fingerprint = new byte[32];
        ThreadLocalRandom.current().nextBytes(fingerprint);
        return fingerprint;
    }

    private void insertNotificationForSubmission(
            UUID id, UUID memberId, UUID submissionId, NotificationStatus status, OffsetDateTime createdAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO notification (id, member_id, submission_id, status, title, message, created_at)
                VALUES (?, ?, ?, ?, '제목', '본문', ?)
                """, id, memberId, submissionId, status.name(), createdAt);
    }

    private void insertNotificationForReport(
            UUID id, UUID memberId, UUID reportId, NotificationStatus status, OffsetDateTime createdAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO notification (id, member_id, report_id, status, title, message, created_at)
                VALUES (?, ?, ?, ?, '제목', '본문', ?)
                """, id, memberId, reportId, status.name(), createdAt);
    }

    private OffsetDateTime readAtOf(UUID notificationId) {
        return jdbcTemplate.queryForObject(
                "SELECT read_at FROM notification WHERE id = ?",
                (resultSet, rowNumber) -> resultSet.getObject("read_at", OffsetDateTime.class),
                notificationId);
    }

    private String titleOf(UUID notificationId) {
        return jdbcTemplate.queryForObject(
                "SELECT title FROM notification WHERE id = ?", String.class, notificationId);
    }

    private String messageOf(UUID notificationId) {
        return jdbcTemplate.queryForObject(
                "SELECT message FROM notification WHERE id = ?", String.class, notificationId);
    }

    private boolean rowExists(UUID notificationId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM notification WHERE id = ?)", Boolean.class, notificationId);
        return Boolean.TRUE.equals(exists);
    }

    private long countBySubmission(UUID submissionId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notification WHERE submission_id = ?", Long.class, submissionId);
        return count == null ? 0 : count;
    }
}

package com.masiton.participation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import com.masiton.common.idempotency.application.IdempotencyActorType;
import com.masiton.common.idempotency.application.IdempotencyApiScope;
import com.masiton.common.idempotency.application.IdempotencyRequest;
import com.masiton.common.idempotency.application.IdempotencyResponse;
import com.masiton.common.idempotency.application.port.in.IdempotentCreationUseCase;
import com.masiton.common.web.BusinessException;
import com.masiton.participation.application.AdminParticipationService;
import com.masiton.participation.application.AdminParticipationView;
import com.masiton.participation.application.ParticipationException;
import com.masiton.participation.application.ParticipationRequest;
import com.masiton.participation.application.ParticipationView;
import com.masiton.participation.application.port.in.AdminParticipationUseCase;
import com.masiton.participation.application.port.in.ParticipationUseCase;
import com.masiton.participation.application.port.out.AdminParticipationStore;
import com.masiton.participation.application.port.out.ParticipationCompletionReader;
import com.masiton.participation.domain.ModerationActionType;
import com.masiton.participation.domain.ParticipationStatus;
import com.masiton.participation.domain.ParticipationTargetType;
import com.masiton.participation.domain.ReportType;
import com.masiton.test.FullContextIntegrationTest;

import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.masiton.notification.application.port.in.CreateNotificationUseCase;
import com.masiton.notification.domain.model.NotificationRequestType;
import com.masiton.notification.domain.model.NotificationStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest
@DisplayName("회원 제보·신고 PostgreSQL 통합")
class ParticipationPostgreSqlIntegrationTest extends FullContextIntegrationTest {

    private static final UUID REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");

    @Autowired
    private ParticipationUseCase useCase;
    @Autowired
    private AdminParticipationUseCase adminUseCase;
    @Autowired
    private IdempotentCreationUseCase idempotency;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private CreateNotificationUseCase createNotificationUseCase;

    @BeforeEach
    void clearRequests() {
        jdbcTemplate.execute("TRUNCATE TABLE idempotency_record, notification, moderation_history, report, submission CASCADE");
    }

    @Test
    @DisplayName("관리자 상태 전이는 요청과 감사 이력 및 알림을 같은 트랜잭션에 저장한다")
    void 관리자검토_정상전이_감사이력과알림을저장한다() {
        UUID adminId = insertAdmin();
        UUID memberId = insertMember();
        UUID submissionId = createSubmission(memberId, 91).requestId();

        adminUseCase.updateSubmission(submissionId, adminId,
                new AdminParticipationUseCase.UpdateStatusCommand(
                        ParticipationStatus.IN_REVIEW, null, "검토를 시작합니다", null), "trace-audit");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM moderation_history WHERE submission_id = ? AND to_status = 'IN_REVIEW'",
                Long.class, submissionId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notification WHERE submission_id = ? AND status = 'IN_REVIEW' AND member_id = ?",
                Long.class, submissionId, memberId)).isEqualTo(1L);
        assertThat(adminUseCase.getSubmission(submissionId).moderationHistory())
                .singleElement().satisfies(history -> assertThat(history.traceId()).isEqualTo("trace-audit"));
        verify(createNotificationUseCase).create(
                memberId, NotificationRequestType.SUBMISSION, submissionId, NotificationStatus.IN_REVIEW);
    }

    @Test
    @DisplayName("TST-E2-ATOMIC-001: 알림 저장 실패 주입 시 상태 변경과 이력이 함께 롤백된다")
    void TST_E2_ATOMIC_001_알림저장실패_전체롤백된다() {
        UUID adminId = insertAdmin();
        UUID memberId = insertMember();
        UUID submissionId = createSubmission(memberId, 91).requestId();

        doThrow(new RuntimeException("Simulated Notification Storage Failure"))
                .when(createNotificationUseCase).create(any(), any(), any(), any());

        assertThatThrownBy(() -> adminUseCase.updateSubmission(submissionId, adminId,
                new AdminParticipationUseCase.UpdateStatusCommand(
                        ParticipationStatus.IN_REVIEW, null, "검토 시작", null), "trace-fail"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated Notification Storage Failure");

        // Verify state transition rolled back to RECEIVED
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM submission WHERE id = ?", String.class, submissionId))
                .isEqualTo("RECEIVED");
        // Verify moderation_history rolled back (0 rows)
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM moderation_history WHERE submission_id = ?",
                Long.class, submissionId)).isEqualTo(0L);
        // Verify notification rolled back (0 rows)
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notification WHERE submission_id = ?",
                Long.class, submissionId)).isEqualTo(0L);
    }

    @Test
    @DisplayName("동시 경쟁 상태 전이는 하나만 성공하고 원본 데이터는 변경하지 않는다")
    void 관리자검토_동시경쟁전이_하나만성공한다() throws Exception {
        UUID adminId = insertAdmin();
        UUID restaurantId = insertRestaurant();
        ParticipationView.Report report = createReport(insertMember(), new ParticipationRequest.Report(
                ParticipationTargetType.RESTAURANT, restaurantId, ReportType.ERROR,
                "동시 검토 전이를 확인하기 위한 신고입니다", null));
        adminUseCase.updateReport(report.requestId(), adminId,
                new AdminParticipationUseCase.UpdateStatusCommand(
                        ParticipationStatus.IN_REVIEW, null, null, null), "trace-start");
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> accepted = executor.submit(() -> transitionReport(
                    report.requestId(), adminId, ParticipationStatus.ACCEPTED, null, start));
            Future<Boolean> rejected = executor.submit(() -> transitionReport(
                    report.requestId(), adminId, ParticipationStatus.REJECTED, "처리하지 않습니다", start));
            start.countDown();
            assertThat(List.of(accepted.get(10, TimeUnit.SECONDS), rejected.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT publication_status FROM restaurant WHERE id = ?", String.class, restaurantId))
                .isEqualTo("PUBLIC");
    }

    @Test
    @DisplayName("완료는 실제 원본 상태와 신고 대상 일치를 검증한다")
    void 관리자완료_원본조치검증_확인된결과만허용한다() {
        UUID adminId = insertAdmin();
        UUID restaurantId = insertRestaurant();
        ParticipationView.Report report = createReport(insertMember(), new ParticipationRequest.Report(
                ParticipationTargetType.RESTAURANT, restaurantId, ReportType.ERROR,
                "비공개 조치 완료 여부를 확인하기 위한 신고입니다", null));
        adminUseCase.updateReport(report.requestId(), adminId,
                new AdminParticipationUseCase.UpdateStatusCommand(ParticipationStatus.IN_REVIEW, null, null, null), "t1");
        adminUseCase.updateReport(report.requestId(), adminId,
                new AdminParticipationUseCase.UpdateStatusCommand(ParticipationStatus.ACCEPTED, null, null, null), "t2");
        AdminParticipationView.Result result = new AdminParticipationView.Result(
                ModerationActionType.HIDDEN, ParticipationTargetType.RESTAURANT, restaurantId);

        assertThatThrownBy(() -> adminUseCase.updateReport(report.requestId(), adminId,
                new AdminParticipationUseCase.UpdateStatusCommand(
                        ParticipationStatus.COMPLETED, "숨김 처리했습니다", null, result), "t3"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("SOURCE_ACTION_NOT_COMPLETED"));
        jdbcTemplate.update("UPDATE restaurant SET publication_status = 'PRIVATE', updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                restaurantId);
        assertThat(adminUseCase.updateReport(report.requestId(), adminId,
                new AdminParticipationUseCase.UpdateStatusCommand(
                        ParticipationStatus.COMPLETED, "숨김 처리했습니다", null, result), "t4").status())
                .isEqualTo(ParticipationStatus.COMPLETED);
    }

    @Test
    @DisplayName("제보 완료는 결과 행이 접수 후보와 연결된 경우에만 허용한다")
    void 관리자제보완료_후보불일치_원본조치미완료를반환한다() {
        UUID adminId = insertAdmin();
        ParticipationView.Submission submission = createSubmission(insertMember(), Map.of(
                "name", "후보 연결 맛집", "roadAddress", "서울시 후보로 1"));
        adminUseCase.updateSubmission(submission.requestId(), adminId,
                new AdminParticipationUseCase.UpdateStatusCommand(ParticipationStatus.IN_REVIEW, null, null, null), "s1");
        adminUseCase.updateSubmission(submission.requestId(), adminId,
                new AdminParticipationUseCase.UpdateStatusCommand(ParticipationStatus.ACCEPTED, null, null, null), "s2");
        UUID restaurantId = insertRestaurant();
        AdminParticipationView.Result result = new AdminParticipationView.Result(
                ModerationActionType.CREATED, ParticipationTargetType.RESTAURANT, restaurantId);

        assertThatThrownBy(() -> adminUseCase.updateSubmission(submission.requestId(), adminId,
                new AdminParticipationUseCase.UpdateStatusCommand(
                        ParticipationStatus.COMPLETED, "등록을 완료했습니다", null, result), "s3"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("SOURCE_ACTION_NOT_COMPLETED"));
        jdbcTemplate.update("UPDATE restaurant SET name = ?, road_address = ? WHERE id = ?",
                "후보 연결 맛집", "서울시 후보로 1", restaurantId);
        assertThat(adminUseCase.updateSubmission(submission.requestId(), adminId,
                new AdminParticipationUseCase.UpdateStatusCommand(
                        ParticipationStatus.COMPLETED, "등록을 완료했습니다", null, result), "s4").status())
                .isEqualTo(ParticipationStatus.COMPLETED);
    }

    @Test
    @DisplayName("제보·신고 합산 일일 5건을 넘는 요청은 거부한다")
    void 접수_합산다섯건초과_여섯번째를거부한다() {
        UUID memberId = insertMember();
        for (int index = 0; index < 5; index++) {
            createSubmission(memberId, index);
        }

        assertThatThrownBy(() -> createSubmission(memberId, 6))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.code()).isEqualTo("DAILY_REQUEST_LIMIT_EXCEEDED");
                });
        assertThat(count("submission", memberId)).isEqualTo(5);
    }

    @Test
    @DisplayName("동시 신규 접수도 회원 행 잠금으로 합산 5건에 수렴한다")
    void 접수_동시여섯건_다섯건만저장한다() throws Exception {
        UUID memberId = insertMember();
        CountDownLatch ready = new CountDownLatch(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Boolean> accepted = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(6)) {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int index = 0; index < 6; index++) {
                int sequence = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        createSubmissionDirect(memberId, sequence);
                        return true;
                    } catch (BusinessException exception) {
                        assertThat(exception.code()).isEqualTo("DAILY_REQUEST_LIMIT_EXCEEDED");
                        return false;
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Boolean> future : futures) {
                accepted.add(future.get(20, TimeUnit.SECONDS));
            }
        }

        assertThat(accepted).containsExactlyInAnyOrder(true, true, true, true, true, false);
        assertThat(count("submission", memberId)).isEqualTo(5);
    }

    @Test
    @DisplayName("열린 중복 제보는 기존 요청을 안내하고 다른 회원 상세은 숨긴다")
    void 제보_열린중복과다른회원조회_중복안내와404를반환한다() {
        UUID ownerId = insertMember();
        UUID otherId = insertMember();
        ParticipationView.Submission created = createSubmission(ownerId, 1);

        assertThatThrownBy(() -> createSubmission(ownerId, 1))
                .isInstanceOfSatisfying(ParticipationException.class,
                        exception -> assertThat(exception.code()).isEqualTo("DUPLICATE_OPEN_SUBMISSION"));
        assertThat(useCase.getSubmission(ownerId, created.requestId()).requestId())
                .isEqualTo(created.requestId());
        assertThatThrownBy(() -> useCase.getSubmission(otherId, created.requestId()))
                .isInstanceOfSatisfying(ParticipationException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.code()).isEqualTo("SUBMISSION_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("신고 접수는 기존 맛집 공개 상태를 바꾸지 않는다")
    void 신고_정상접수_원본공개상태를유지한다() {
        UUID memberId = insertMember();
        UUID restaurantId = insertRestaurant();
        ParticipationRequest.Report request = new ParticipationRequest.Report(
                ParticipationTargetType.RESTAURANT,
                restaurantId,
                ReportType.CLOSED,
                "현장 안내문에서 폐업 사실을 확인했습니다.",
                "https://example.com/evidence");

        ParticipationView.Report created = createReport(memberId, request);

        assertThat(created.status().name()).isEqualTo("RECEIVED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT publication_status FROM restaurant WHERE id = ?", String.class, restaurantId))
                .isEqualTo("PUBLIC");
    }

    @Test
    @DisplayName("비공개 대상은 존재 여부를 숨기고 신고를 받지 않는다")
    void 신고_비공개대상_404로숨긴다() {
        UUID memberId = insertMember();
        UUID restaurantId = insertRestaurant();
        jdbcTemplate.update(
                "UPDATE restaurant SET publication_status = 'PRIVATE' WHERE id = ?", restaurantId);
        ParticipationRequest.Report request = new ParticipationRequest.Report(
                ParticipationTargetType.RESTAURANT, restaurantId, ReportType.ERROR,
                "비공개 대상을 추측해 신고합니다.", null);

        assertThatThrownBy(() -> createReport(memberId, request))
                .isInstanceOfSatisfying(ParticipationException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.code()).isEqualTo("PARTICIPATION_TARGET_NOT_FOUND");
                });
        assertThat(count("report", memberId)).isZero();
    }

    @Test
    @DisplayName("스크립트 입력과 대상에 맞지 않는 신고 유형을 거부한다")
    void 접수_악성입력과잘못된유형_저장하지않는다() {
        UUID memberId = insertMember();
        UUID restaurantId = insertRestaurant();

        assertThatThrownBy(() -> createSubmission(memberId, Map.of(
                "name", "<script>alert(1)</script>", "roadAddress", "서울특별시 테스트로 1")))
                .isInstanceOf(BusinessException.class);
        ParticipationRequest.Report invalid = new ParticipationRequest.Report(
                ParticipationTargetType.RESTAURANT, restaurantId, ReportType.WRONG_RELATIONSHIP,
                "잘못된 연결이라고 생각합니다.", null);
        assertThatThrownBy(() -> createReport(memberId, invalid))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVALID_FIELD_VALUE"));
        assertThat(count("submission", memberId)).isZero();
        assertThat(count("report", memberId)).isZero();
    }

    private ParticipationView.Submission createSubmission(UUID memberId, int sequence) {
        return createSubmission(memberId, Map.of(
                "name", "새 맛집 " + sequence,
                "roadAddress", "서울특별시 테스트로 " + sequence));
    }

    private ParticipationView.Submission createSubmissionDirect(UUID memberId, int sequence) {
        return useCase.createSubmission(memberId, new ParticipationRequest.Submission(
                ParticipationTargetType.RESTAURANT,
                Map.of("name", "동시 접수 맛집 " + sequence,
                        "roadAddress", "서울특별시 동시접수로 " + sequence),
                "동시 접수 원자성을 검증하는 제보입니다.",
                null));
    }

    private ParticipationView.Submission createSubmission(UUID memberId, Map<String, Object> candidate) {
        ParticipationRequest.Submission request = new ParticipationRequest.Submission(
                ParticipationTargetType.RESTAURANT,
                candidate,
                "새로운 맛집 등록을 제안합니다.",
                "https://example.com/evidence");
        final ParticipationView.Submission[] created = new ParticipationView.Submission[1];
        String key = "submission-" + UUID.randomUUID();
        idempotency.execute(idempotencyRequest(memberId, IdempotencyApiScope.MEMBER_SUBMISSIONS, key), () -> {
            created[0] = useCase.createSubmission(memberId, request);
            return new IdempotencyResponse(201, "{\"requestId\":\"" + created[0].requestId() + "\"}", created[0].requestId());
        });
        return created[0];
    }

    private ParticipationView.Report createReport(UUID memberId, ParticipationRequest.Report request) {
        final ParticipationView.Report[] created = new ParticipationView.Report[1];
        String key = "report-" + UUID.randomUUID();
        idempotency.execute(idempotencyRequest(memberId, IdempotencyApiScope.MEMBER_REPORTS, key), () -> {
            created[0] = useCase.createReport(memberId, request);
            return new IdempotencyResponse(201, "{\"requestId\":\"" + created[0].requestId() + "\"}", created[0].requestId());
        });
        return created[0];
    }

    private IdempotencyRequest idempotencyRequest(UUID memberId, IdempotencyApiScope scope, String key) {
        return IdempotencyRequest.of(
                IdempotencyActorType.MEMBER, memberId, scope, key, sha256(key));
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private UUID insertMember() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO member_account (id, email, password_hash, email_verified_at, status)
                VALUES (?, ?, 'password-hash', CURRENT_TIMESTAMP, 'ACTIVE')
                """, id, id + "@example.com");
        return id;
    }

    private UUID insertAdmin() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO admin_account (id, login_id, password_hash) VALUES (?, ?, 'hash')",
                id, "admin-" + id);
        return id;
    }

    private boolean transitionReport(
            UUID requestId, UUID adminId, ParticipationStatus status, String reason, CountDownLatch start)
            throws InterruptedException {
        start.await(10, TimeUnit.SECONDS);
        try {
            adminUseCase.updateReport(requestId, adminId,
                    new AdminParticipationUseCase.UpdateStatusCommand(status, reason, null, null),
                    "trace-" + status);
            return true;
        } catch (BusinessException exception) {
            assertThat(exception.code()).isEqualTo("INVALID_STATUS_TRANSITION");
            return false;
        }
    }

    private UUID insertRestaurant() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO restaurant
                    (id, region_id, food_category_id, name, kakao_place_id, kakao_place_url,
                     road_address, phone_number, publication_status, lifecycle_status)
                VALUES (?, ?, ?, '신고 대상 맛집', ?, ?, '서울특별시 테스트로 1',
                        '02-1234-5678', 'PUBLIC', 'ACTIVE')
                """, id, REGION_ID, CATEGORY_ID, "KAKAO-" + id, "https://example.com/place/" + id);
        return id;
    }

    private long count(String table, UUID memberId) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE member_id = ?", Long.class, memberId);
        return value == null ? 0 : value;
    }
}

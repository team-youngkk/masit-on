package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import com.masiton.ai.application.port.out.AiExtractionAdminQueryPort;
import com.masiton.ai.application.port.out.AiRegistrationUnitReviewStore;
import com.masiton.ai.application.port.out.AiRegistrationUnitStore;
import com.masiton.common.security.LegacyAdminActorResolver;
import com.masiton.common.web.BusinessException;
import com.masiton.orchestration.application.port.in.AdjustRegisteredCategoryUseCase;
import com.masiton.orchestration.application.port.in.AutoRegisterVerifiedContentUseCase;
import com.masiton.orchestration.application.port.in.DecomposeRegistrationUnitsUseCase;
import com.masiton.orchestration.application.port.in.ExecuteRegistrationUnitUseCase;
import com.masiton.orchestration.application.port.in.ExecuteRegistrationUnitUseCase.CategoryDecision;
import com.masiton.orchestration.application.port.in.ExecuteRegistrationUnitUseCase.PlaceDecision;
import com.masiton.orchestration.application.port.in.ExecuteRegistrationUnitUseCase.RegistrationUnitExecutionResult;
import com.masiton.orchestration.application.port.in.ResolveFoodCategoryUseCase;
import com.masiton.orchestration.application.port.in.RollbackAiRegisteredContentUseCase;

import tools.jackson.databind.ObjectMapper;

/**
 * PR #244 리뷰 지적사항: {@code lockByJobAndUnitId}의 {@code FOR UPDATE NOWAIT}는 그 조회 문장이
 * 끝나면 풀리므로, Kakao·YouTube 외부 호출 뒤의 최종 상태 전이가 {@code expectedReviewStatus}
 * 조건부 갱신으로 실패했을 때(동시 요청이 먼저 반영) 이 서비스가 {@code AIEXTRACT_CONCURRENT_REQUEST_CONFLICT}로
 * 응답하는지 검증한다.
 */
@DisplayName("등록 단위 명령 서비스")
class RegistrationUnitCommandServiceTest {

    private static final UUID JOB_ID = UUID.randomUUID();
    private static final UUID UNIT_ID = UUID.randomUUID();
    private static final UUID SNAPSHOT_ID = UUID.randomUUID();

    private final AiExtractionAdminQueryPort port = mock(AiExtractionAdminQueryPort.class);
    private final AiRegistrationUnitStore registrationUnitStore = mock(AiRegistrationUnitStore.class);
    private final AiRegistrationUnitReviewStore registrationUnitReviewStore = mock(AiRegistrationUnitReviewStore.class);
    private final DecomposeRegistrationUnitsUseCase decomposeRegistrationUnits =
            mock(DecomposeRegistrationUnitsUseCase.class);
    private final ExecuteRegistrationUnitUseCase executeRegistrationUnit = mock(ExecuteRegistrationUnitUseCase.class);
    private final ResolveFoodCategoryUseCase resolveFoodCategory = mock(ResolveFoodCategoryUseCase.class);
    private final RollbackAiRegisteredContentUseCase rollbackUseCase = mock(RollbackAiRegisteredContentUseCase.class);
    private final AdjustRegisteredCategoryUseCase adjustCategoryUseCase = mock(AdjustRegisteredCategoryUseCase.class);
    private final RegistrationUnitConfirmCommitService confirmCommitService =
            mock(RegistrationUnitConfirmCommitService.class);
    private final LegacyAdminActorResolver legacyAdminActorResolver = mock(LegacyAdminActorResolver.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private RegistrationUnitCommandService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationUnitCommandService(port, registrationUnitStore, registrationUnitReviewStore,
                decomposeRegistrationUnits, executeRegistrationUnit, resolveFoodCategory, rollbackUseCase,
                adjustCategoryUseCase, confirmCommitService, legacyAdminActorResolver, objectMapper);

        when(port.jobVideoReference(JOB_ID)).thenReturn(Optional.of(
                new AiExtractionAdminQueryPort.JobVideoReference("channel-1", "video-1",
                        "https://www.youtube.com/watch?v=video-1")));
        when(port.snapshotCandidates(SNAPSHOT_ID)).thenReturn(Optional.of(
                new AiExtractionAdminQueryPort.SnapshotCandidateJson(null, null, null, null)));
        DecomposeRegistrationUnitsUseCase.RegistrationUnitBundle bundle =
                new DecomposeRegistrationUnitsUseCase.RegistrationUnitBundle(1,
                        new DecomposeRegistrationUnitsUseCase.BoundCandidate("행복식당", null, null),
                        new DecomposeRegistrationUnitsUseCase.BoundCandidate("서울특별시 마포구", null, null),
                        null, null);
        when(decomposeRegistrationUnits.decompose(any())).thenReturn(List.of(bundle));
    }

    @Test
    @DisplayName("등록 단위 일괄 등록 중 동시 요청이 먼저 반영하면 409를 던지고 방금 만든 등록 콘텐츠를 하드 삭제로 보상한다")
    void registerUnit_동시요청이먼저반영하면_409충돌을던지고보상삭제한다() {
        // Given
        AiRegistrationUnitStore.RegistrationUnitRow unit = blockedUnitRow();
        when(registrationUnitStore.lockByJobAndUnitId(JOB_ID, UNIT_ID)).thenReturn(Optional.of(unit));
        RegistrationUnitExecutionResult result = confirmedResult();
        when(executeRegistrationUnit.execute(any())).thenReturn(result);
        when(registrationUnitStore.markRegistered(eq(UNIT_ID), eq("AUTO_BLOCKED"), any())).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> service.registerUnit(JOB_ID, UNIT_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(businessException.code()).isEqualTo("AIEXTRACT_CONCURRENT_REQUEST_CONFLICT");
                });
        AutoRegisterVerifiedContentUseCase.RegistrationResult registration = result.registration();
        verify(rollbackUseCase).discardFailedRegistration(registration.restaurantId(),
                registration.restaurantCreated(), registration.creatorId(), registration.creatorCreated(),
                registration.videoId(), registration.videoCreated(), registration.visitId(),
                registration.visitCreated());
    }

    @Test
    @DisplayName("등록 단위 일괄 등록 중 저장이 unique 제약을 위반하면 409를 던지고 방금 만든 등록 콘텐츠를 보상 삭제한다")
    void registerUnit_저장이unique제약을위반하면_409충돌을던지고보상삭제한다() {
        // Given
        AiRegistrationUnitStore.RegistrationUnitRow unit = blockedUnitRow();
        when(registrationUnitStore.lockByJobAndUnitId(JOB_ID, UNIT_ID)).thenReturn(Optional.of(unit));
        when(executeRegistrationUnit.execute(any())).thenReturn(confirmedResult());
        when(registrationUnitStore.markRegistered(eq(UNIT_ID), eq("AUTO_BLOCKED"), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        // When / Then
        assertThatThrownBy(() -> service.registerUnit(JOB_ID, UNIT_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).code())
                        .isEqualTo("AIEXTRACT_CONCURRENT_REQUEST_CONFLICT"));
        verify(rollbackUseCase).discardFailedRegistration(any(), anyBoolean(), any(), anyBoolean(), any(),
                anyBoolean(), any(), anyBoolean());
    }

    @Test
    @DisplayName("등록 단위 일괄 등록 중 감사 저장이 일반 런타임 예외로 실패해도 보상 삭제 후 원래 예외를 그대로 전달한다")
    void registerUnit_일반런타임예외로실패하면_보상삭제후원래예외를전달한다() {
        // Given
        AiRegistrationUnitStore.RegistrationUnitRow unit = blockedUnitRow();
        when(registrationUnitStore.lockByJobAndUnitId(JOB_ID, UNIT_ID)).thenReturn(Optional.of(unit));
        when(executeRegistrationUnit.execute(any())).thenReturn(confirmedResult());
        IllegalStateException injected = new IllegalStateException("audit adapter failure");
        when(registrationUnitStore.markRegistered(eq(UNIT_ID), eq("AUTO_BLOCKED"), any())).thenThrow(injected);

        // When / Then
        assertThatThrownBy(() -> service.registerUnit(JOB_ID, UNIT_ID)).isSameAs(injected);
        verify(rollbackUseCase).discardFailedRegistration(any(), anyBoolean(), any(), anyBoolean(), any(),
                anyBoolean(), any(), anyBoolean());
    }

    @Test
    @DisplayName("등록 단위 일괄 등록이 성공하면 AUTO_CONFIRMED 결과를 반환한다")
    void registerUnit_성공하면_AUTO_CONFIRMED결과를반환한다() {
        // Given
        AiRegistrationUnitStore.RegistrationUnitRow unit = blockedUnitRow();
        when(registrationUnitStore.lockByJobAndUnitId(JOB_ID, UNIT_ID)).thenReturn(Optional.of(unit));
        when(executeRegistrationUnit.execute(any())).thenReturn(confirmedResult());
        when(registrationUnitStore.markRegistered(eq(UNIT_ID), eq("AUTO_BLOCKED"), any())).thenReturn(true);

        // When
        RegistrationUnitCommandService.RegistrationExecutionView view = service.registerUnit(JOB_ID, UNIT_ID);

        // Then
        assertThat(view.reviewStatus()).isEqualTo("AUTO_CONFIRMED");
        assertThat(view.unitId()).isEqualTo(UNIT_ID);
    }

    @Test
    @DisplayName("review CONFIRM 중 동시 요청이 먼저 반영하면 409 AIEXTRACT_CONCURRENT_REQUEST_CONFLICT를 던지고 감사 이력을 남기지 않는다")
    void review_CONFIRM중_동시요청이먼저반영하면_409충돌을던진다() {
        // Given
        AiRegistrationUnitStore.RegistrationUnitRow unit = blockedUnitRow();
        when(registrationUnitStore.findByJobId(JOB_ID)).thenReturn(List.of(unit));
        when(registrationUnitStore.lockByJobAndUnitId(JOB_ID, UNIT_ID)).thenReturn(Optional.of(unit));
        when(executeRegistrationUnit.execute(any())).thenReturn(confirmedResult());
        when(confirmCommitService.commit(eq(UNIT_ID), eq("AUTO_BLOCKED"), any(), eq(SNAPSHOT_ID), any(), any(),
                any(), anyString(), any())).thenReturn(false);

        // When / Then
        UUID adminId = UUID.randomUUID();
        assertThatThrownBy(() -> service.review(JOB_ID, "CONFIRM", UNIT_ID.toString(), "사유",
                "https://place.map.kakao.com/1", null, List.of(), adminId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).code())
                        .isEqualTo("AIEXTRACT_CONCURRENT_REQUEST_CONFLICT"));
        verify(registrationUnitReviewStore, never()).insert(any());
        verify(rollbackUseCase).discardFailedRegistration(any(), anyBoolean(), any(), anyBoolean(), any(),
                anyBoolean(), any(), anyBoolean());
    }

    @Test
    @DisplayName("review CONFIRM 중 감사 저장이 일반 런타임 예외로 실패해도 보상 삭제 후 원래 예외를 그대로 전달한다")
    void review_CONFIRM중_일반런타임예외로실패하면_보상삭제후원래예외를전달한다() {
        // Given
        AiRegistrationUnitStore.RegistrationUnitRow unit = blockedUnitRow();
        when(registrationUnitStore.findByJobId(JOB_ID)).thenReturn(List.of(unit));
        when(registrationUnitStore.lockByJobAndUnitId(JOB_ID, UNIT_ID)).thenReturn(Optional.of(unit));
        when(executeRegistrationUnit.execute(any())).thenReturn(confirmedResult());
        IllegalStateException injected = new IllegalStateException("audit adapter failure");
        when(confirmCommitService.commit(eq(UNIT_ID), eq("AUTO_BLOCKED"), any(), eq(SNAPSHOT_ID), any(), any(),
                any(), anyString(), any())).thenThrow(injected);

        // When / Then
        UUID adminId = UUID.randomUUID();
        assertThatThrownBy(() -> service.review(JOB_ID, "CONFIRM", UNIT_ID.toString(), "사유",
                "https://place.map.kakao.com/1", null, List.of(), adminId)).isSameAs(injected);
        verify(rollbackUseCase).discardFailedRegistration(any(), anyBoolean(), any(), anyBoolean(), any(),
                anyBoolean(), any(), anyBoolean());
    }

    @Test
    @DisplayName("DISCARD 중 동시 요청이 먼저 반영하면 409 AIEXTRACT_CONCURRENT_REQUEST_CONFLICT를 던진다")
    void review_DISCARD중_동시요청이먼저반영하면_409충돌을던진다() {
        // Given
        AiRegistrationUnitStore.RegistrationUnitRow unit = blockedUnitRow();
        when(registrationUnitStore.findByJobId(JOB_ID)).thenReturn(List.of(unit));
        when(registrationUnitStore.lockByJobAndUnitId(JOB_ID, UNIT_ID)).thenReturn(Optional.of(unit));
        when(registrationUnitStore.discard(eq(UNIT_ID), eq("AUTO_BLOCKED"), any())).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> service.review(JOB_ID, "DISCARD", UNIT_ID.toString(), "사유",
                null, null, List.of(), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).code())
                        .isEqualTo("AIEXTRACT_CONCURRENT_REQUEST_CONFLICT"));
        verify(registrationUnitReviewStore, never()).insert(any());
    }

    @Test
    @DisplayName("ROLLBACK 중 동시 요청이 먼저 반영하면 409 AIEXTRACT_CONCURRENT_REQUEST_CONFLICT를 던진다")
    void review_ROLLBACK중_동시요청이먼저반영하면_409충돌을던진다() {
        // Given
        AiRegistrationUnitStore.RegistrationUnitRow unit = registeredUnitRow("AUTO_CONFIRMED");
        when(registrationUnitStore.findByJobId(JOB_ID)).thenReturn(List.of(unit));
        when(registrationUnitStore.lockByJobAndUnitId(JOB_ID, UNIT_ID)).thenReturn(Optional.of(unit));
        when(registrationUnitStore.rollback(eq(UNIT_ID), eq("AUTO_CONFIRMED"), any())).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> service.review(JOB_ID, "ROLLBACK", UNIT_ID.toString(), "오등록",
                null, null, List.of(), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).code())
                        .isEqualTo("AIEXTRACT_CONCURRENT_REQUEST_CONFLICT"));
        verify(registrationUnitReviewStore, never()).insert(any());
    }

    @Test
    @DisplayName("ADJUST_CATEGORY 중 동시 요청이 먼저 반영하면 409 AIEXTRACT_CONCURRENT_REQUEST_CONFLICT를 던진다")
    void review_ADJUST_CATEGORY중_동시요청이먼저반영하면_409충돌을던진다() {
        // Given
        AiRegistrationUnitStore.RegistrationUnitRow unit = registeredUnitRow("AUTO_CONFIRMED");
        UUID foodCategoryId = UUID.randomUUID();
        when(registrationUnitStore.findByJobId(JOB_ID)).thenReturn(List.of(unit));
        when(registrationUnitStore.lockByJobAndUnitId(JOB_ID, UNIT_ID)).thenReturn(Optional.of(unit));
        when(resolveFoodCategory.findActiveCategoryName(foodCategoryId)).thenReturn(Optional.of("일식"));
        when(registrationUnitStore.adjustCategory(eq(UNIT_ID), eq("AUTO_CONFIRMED"), any())).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> service.review(JOB_ID, "ADJUST_CATEGORY", UNIT_ID.toString(), "카테고리 오분류",
                null, foodCategoryId.toString(), List.of(), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).code())
                        .isEqualTo("AIEXTRACT_CONCURRENT_REQUEST_CONFLICT"));
        verify(registrationUnitReviewStore, never()).insert(any());
    }

    @Test
    @DisplayName("AUTO_BLOCKED 등록 단위 일괄 폐기는 AUTO_BLOCKED만 폐기하고 나머지 상태는 건드리지 않는다")
    void discardAllBlocked_AUTO_BLOCKED만폐기하고_나머지는건드리지않는다() {
        // Given
        UUID firstBlockedId = UUID.randomUUID();
        UUID secondBlockedId = UUID.randomUUID();
        UUID confirmedId = UUID.randomUUID();
        AiRegistrationUnitStore.RegistrationUnitRow first = blockedUnitRow(firstBlockedId);
        AiRegistrationUnitStore.RegistrationUnitRow second = blockedUnitRow(secondBlockedId);
        AiRegistrationUnitStore.RegistrationUnitRow confirmed = registeredUnitRow(confirmedId, "AUTO_CONFIRMED");
        when(registrationUnitStore.findByJobId(JOB_ID)).thenReturn(List.of(first, second, confirmed));
        when(registrationUnitStore.lockByJobAndUnitId(JOB_ID, firstBlockedId)).thenReturn(Optional.of(first));
        when(registrationUnitStore.lockByJobAndUnitId(JOB_ID, secondBlockedId)).thenReturn(Optional.of(second));
        when(registrationUnitStore.discard(eq(firstBlockedId), eq("AUTO_BLOCKED"), any())).thenReturn(true);
        when(registrationUnitStore.discard(eq(secondBlockedId), eq("AUTO_BLOCKED"), any())).thenReturn(true);

        // When
        UUID adminId = UUID.randomUUID();
        List<UUID> discardedUnitIds = service.discardAllBlocked(JOB_ID, "여러 건 동시 처리 사유", adminId);

        // Then
        assertThat(discardedUnitIds).containsExactlyInAnyOrder(firstBlockedId, secondBlockedId);
        verify(registrationUnitStore, never()).lockByJobAndUnitId(JOB_ID, confirmedId);
        verify(registrationUnitStore, never()).discard(eq(confirmedId), any(), any());
    }

    @Test
    @DisplayName("AUTO_BLOCKED 등록 단위가 없으면 예외 없이 빈 결과를 반환한다")
    void discardAllBlocked_AUTO_BLOCKED이없으면_예외없이빈결과를반환한다() {
        // Given
        AiRegistrationUnitStore.RegistrationUnitRow confirmed = registeredUnitRow(UUID.randomUUID(), "AUTO_CONFIRMED");
        when(registrationUnitStore.findByJobId(JOB_ID)).thenReturn(List.of(confirmed));

        // When
        List<UUID> discardedUnitIds = service.discardAllBlocked(JOB_ID, "사유", UUID.randomUUID());

        // Then
        assertThat(discardedUnitIds).isEmpty();
        verify(registrationUnitStore, never()).lockByJobAndUnitId(any(), any());
    }

    @Test
    @DisplayName("처리 도중 한 단위가 동시 요청으로 이미 바뀌어 있으면 그 단위만 건너뛰고 나머지는 폐기한다")
    void discardAllBlocked_동시요청으로바뀐단위는건너뛰고_나머지는폐기한다() {
        // Given
        UUID staleId = UUID.randomUUID();
        UUID normalId = UUID.randomUUID();
        AiRegistrationUnitStore.RegistrationUnitRow stale = blockedUnitRow(staleId);
        AiRegistrationUnitStore.RegistrationUnitRow normal = blockedUnitRow(normalId);
        when(registrationUnitStore.findByJobId(JOB_ID)).thenReturn(List.of(stale, normal));
        when(registrationUnitStore.lockByJobAndUnitId(JOB_ID, staleId)).thenReturn(Optional.empty());
        when(registrationUnitStore.lockByJobAndUnitId(JOB_ID, normalId)).thenReturn(Optional.of(normal));
        when(registrationUnitStore.discard(eq(normalId), eq("AUTO_BLOCKED"), any())).thenReturn(true);

        // When
        List<UUID> discardedUnitIds = service.discardAllBlocked(JOB_ID, "사유", UUID.randomUUID());

        // Then
        assertThat(discardedUnitIds).containsExactly(normalId);
        verify(registrationUnitStore, never()).discard(eq(staleId), any(), any());
    }

    private AiRegistrationUnitStore.RegistrationUnitRow blockedUnitRow(UUID unitId) {
        return new AiRegistrationUnitStore.RegistrationUnitRow(unitId, SNAPSHOT_ID, 1, "행복식당", "AUTO_BLOCKED",
                "PLACE_NOT_FOUND", null, null, null, null, null, null, List.of(), null, OffsetDateTime.now(), null,
                null);
    }

    private AiRegistrationUnitStore.RegistrationUnitRow registeredUnitRow(UUID unitId, String reviewStatus) {
        return new AiRegistrationUnitStore.RegistrationUnitRow(unitId, SNAPSHOT_ID, 1, "행복식당", reviewStatus, null,
                "{\"kakaoPlaceUrl\":\"https://place.map.kakao.com/1\",\"roadAddress\":\"서울특별시 마포구 월드컵로 1\","
                        + "\"matchedBy\":\"NAME_AND_DISTRICT\"}",
                "{\"foodCategoryName\":\"한식\",\"resolvedBy\":\"KAKAO_PLACE_CATEGORY\"}",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(), "WORKER",
                OffsetDateTime.now(), null, null);
    }

    private AiRegistrationUnitStore.RegistrationUnitRow blockedUnitRow() {
        return new AiRegistrationUnitStore.RegistrationUnitRow(UNIT_ID, SNAPSHOT_ID, 1, "행복식당", "AUTO_BLOCKED",
                "PLACE_NOT_FOUND", null, null, null, null, null, null, List.of(), null, OffsetDateTime.now(), null,
                null);
    }

    private AiRegistrationUnitStore.RegistrationUnitRow registeredUnitRow(String reviewStatus) {
        return new AiRegistrationUnitStore.RegistrationUnitRow(UNIT_ID, SNAPSHOT_ID, 1, "행복식당", reviewStatus, null,
                "{\"kakaoPlaceUrl\":\"https://place.map.kakao.com/1\",\"roadAddress\":\"서울특별시 마포구 월드컵로 1\","
                        + "\"matchedBy\":\"NAME_AND_DISTRICT\"}",
                "{\"foodCategoryName\":\"한식\",\"resolvedBy\":\"KAKAO_PLACE_CATEGORY\"}",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(), "WORKER",
                OffsetDateTime.now(), null, null);
    }

    private RegistrationUnitExecutionResult confirmedResult() {
        AutoRegisterVerifiedContentUseCase.RegistrationResult registration =
                new AutoRegisterVerifiedContentUseCase.RegistrationResult(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        false, false, false, true);
        PlaceDecision placeDecision = new PlaceDecision("https://place.map.kakao.com/1", "서울특별시 마포구 월드컵로 1",
                "NAME_AND_DISTRICT");
        CategoryDecision categoryDecision = new CategoryDecision(UUID.randomUUID(), "한식", "KAKAO_PLACE_CATEGORY");
        return RegistrationUnitExecutionResult.confirmed(placeDecision, categoryDecision, registration);
    }
}

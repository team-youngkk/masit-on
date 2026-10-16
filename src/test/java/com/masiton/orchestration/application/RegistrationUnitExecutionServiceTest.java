package com.masiton.orchestration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.orchestration.application.port.in.AutoRegisterVerifiedContentUseCase;
import com.masiton.orchestration.application.port.in.ExecuteRegistrationUnitUseCase.RegistrationUnitExecutionCommand;
import com.masiton.orchestration.application.port.in.ExecuteRegistrationUnitUseCase.RegistrationUnitExecutionResult;
import com.masiton.orchestration.application.port.in.ResolveFoodCategoryUseCase;
import com.masiton.orchestration.application.port.in.ResolvePlaceIdentityUseCase;
import com.masiton.orchestration.application.port.out.DuplicateRegistrationCheckPort;
import com.masiton.orchestration.application.port.in.VerifyAiContentCandidateUseCase.Evidence;
import com.masiton.orchestration.application.port.in.VerifyAiContentCandidateUseCase.EvidenceType;
import com.masiton.orchestration.application.port.in.VerifyAiContentCandidateUseCase.VisitEvidenceCandidate;
import com.masiton.restaurant.application.port.in.ResolvePlacePhysicalReferenceUseCase;
import com.masiton.video.application.port.in.ResolveVerifiedVideoUseCase;
import com.masiton.video.application.port.out.VerifiedVideo;

/**
 * PR #244 리뷰 지적사항: {@code isDuplicate}가 {@code restaurantExists}만으로 판정하도록 정리한 뒤
 * (dead {@code visitExists} 분기 제거), 5단계 판정 전체의 정상·경계·중복 시나리오를 검증한다.
 * 이 서비스는 이전까지 직접 검증하는 테스트가 없었다.
 */
@DisplayName("등록 단위 실행(5단계 판정)")
class RegistrationUnitExecutionServiceTest {

    private static final UUID REGION_ID = UUID.randomUUID();
    private static final UUID FOOD_CATEGORY_ID = UUID.randomUUID();
    private static final String KAKAO_PLACE_ID = "kakao-1";
    private static final String KAKAO_PLACE_URL = "https://place.map.kakao.com/1";

    private final ResolvePlaceIdentityUseCase resolvePlaceIdentity = mock(ResolvePlaceIdentityUseCase.class);
    private final ResolveFoodCategoryUseCase resolveFoodCategory = mock(ResolveFoodCategoryUseCase.class);
    private final ResolvePlacePhysicalReferenceUseCase resolvePlacePhysicalReference =
            mock(ResolvePlacePhysicalReferenceUseCase.class);
    private final ResolveVerifiedVideoUseCase videoVerification = mock(ResolveVerifiedVideoUseCase.class);
    private final DuplicateRegistrationCheckPort duplicateRegistrationCheck = mock(DuplicateRegistrationCheckPort.class);
    private final AutoRegisterVerifiedContentUseCase autoRegister = mock(AutoRegisterVerifiedContentUseCase.class);

    private final RegistrationUnitExecutionService service = new RegistrationUnitExecutionService(
            resolvePlaceIdentity, resolveFoodCategory, resolvePlacePhysicalReference, videoVerification,
            autoRegister);

    @Test
    @DisplayName("상호명이 없으면 MISSING_REQUIRED_FIELD로 차단한다")
    void execute_상호명없음_MISSING_REQUIRED_FIELD로차단한다() {
        RegistrationUnitExecutionResult result = service.execute(command(null, "서울특별시 마포구 월드컵로 1"));

        assertThat(result.confirmed()).isFalse();
        assertThat(result.blockReason()).isEqualTo("MISSING_REQUIRED_FIELD");
    }

    @Test
    @DisplayName("Kakao 장소를 찾지 못하면 PLACE_NOT_FOUND로 차단한다")
    void execute_장소를찾지못하면_PLACE_NOT_FOUND로차단한다() {
        when(resolvePlaceIdentity.resolve(any())).thenReturn(ResolvePlaceIdentityUseCase.PlaceIdentityResult.notFound());

        RegistrationUnitExecutionResult result = service.execute(command("행복식당", "서울특별시 마포구 월드컵로 1"));

        assertThat(result.blockReason()).isEqualTo("PLACE_NOT_FOUND");
    }

    @Test
    @DisplayName("조건을 만족하는 장소가 여럿이면 PLACE_AMBIGUOUS로 차단한다")
    void execute_장소가모호하면_PLACE_AMBIGUOUS로차단한다() {
        when(resolvePlaceIdentity.resolve(any())).thenReturn(ResolvePlaceIdentityUseCase.PlaceIdentityResult.ambiguous());

        RegistrationUnitExecutionResult result = service.execute(command("행복식당", "서울특별시 마포구 월드컵로 1"));

        assertThat(result.blockReason()).isEqualTo("PLACE_AMBIGUOUS");
    }

    @Test
    @DisplayName("장소 조회 중 외부 호출이 실패하면 EXTERNAL_SERVICE_ERROR로 차단한다")
    void execute_장소조회외부호출실패_EXTERNAL_SERVICE_ERROR로차단한다() {
        when(resolvePlaceIdentity.resolve(any())).thenThrow(
                new com.masiton.restaurant.application.PlaceSearchFailedException(
                        new java.net.http.HttpTimeoutException("secret-response")));

        RegistrationUnitExecutionResult result = service.execute(command("행복식당", "서울특별시 마포구 월드컵로 1"));

        assertThat(result.blockReason()).isEqualTo("EXTERNAL_SERVICE_ERROR");
    }

    @Test
    @DisplayName("대표 카테고리를 확정하지 못하면 CATEGORY_UNRESOLVED로 차단한다")
    void execute_카테고리를확정하지못하면_CATEGORY_UNRESOLVED로차단한다() {
        stubConfirmedPlace();
        when(resolveFoodCategory.resolve(any()))
                .thenReturn(ResolveFoodCategoryUseCase.FoodCategoryResolutionResult.unresolved());

        RegistrationUnitExecutionResult result = service.execute(command("행복식당", "서울특별시 마포구 월드컵로 1"));

        assertThat(result.blockReason()).isEqualTo("CATEGORY_UNRESOLVED");
    }

    @Test
    @DisplayName("방문 근거가 확인되지 않으면 VISIT_EVIDENCE_REQUIRED로 차단한다")
    void execute_방문근거가없으면_VISIT_EVIDENCE_REQUIRED로차단한다() {
        stubConfirmedPlace();
        stubResolvedCategory();
        stubVerifiedVideo();

        RegistrationUnitExecutionResult result = service.execute(command("행복식당", "서울특별시 마포구 월드컵로 1"));

        assertThat(result.blockReason()).isEqualTo("VISIT_EVIDENCE_REQUIRED");
    }

    @Test
    @DisplayName("기존 맛집은 차단하지 않고 검증된 새 영상과 방문 등록을 진행한다")
    void execute_기존맛집_재사용등록을진행한다() {
        stubConfirmedPlace();
        stubResolvedCategory();
        stubVerifiedVideo();
        when(autoRegister.register(any())).thenReturn(new AutoRegisterVerifiedContentUseCase.RegistrationResult(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), false, true, true, true));

        RegistrationUnitExecutionResult result = service.execute(commandWithVisitEvidence());

        assertThat(result.confirmed()).isTrue();
        assertThat(result.registration().restaurantCreated()).isFalse();
        verify(autoRegister).register(any());
    }

    @Test
    @DisplayName("5단계 판정을 모두 통과하면 등록을 확정하고 결과를 반환한다")
    void execute_5단계를모두통과하면_등록을확정한다() {
        stubConfirmedPlace();
        stubResolvedCategory();
        stubVerifiedVideo();
        AutoRegisterVerifiedContentUseCase.RegistrationResult registration =
                new AutoRegisterVerifiedContentUseCase.RegistrationResult(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        false, false, false, true);
        when(autoRegister.register(any())).thenReturn(registration);

        RegistrationUnitExecutionResult result = service.execute(commandWithVisitEvidence());

        assertThat(result.confirmed()).isTrue();
        assertThat(result.registration()).isEqualTo(registration);
        assertThat(result.placeDecision().searchQuery()).isEqualTo("행복식당");
        assertThat(result.placeDecision().kakaoPlaceId()).isEqualTo(KAKAO_PLACE_ID);
        assertThat(result.placeDecision().kakaoPlaceUrl()).isEqualTo(KAKAO_PLACE_URL);
        assertThat(result.categoryDecision().foodCategoryId()).isEqualTo(FOOD_CATEGORY_ID);
        assertThat(result.categoryDecision().matchedMappingId()).isNotNull();
        verify(resolvePlaceIdentity).resolve(new ResolvePlaceIdentityUseCase.PlaceIdentityCommand(
                "행복식당", "서울특별시 마포구 월드컵로 1", "냉면"));
    }

    @Test
    @DisplayName("서버 결함은 외부 장애로 차단하지 않고 원래 예외를 전달한다")
    void execute_카테고리서버결함_원래예외전파() {
        stubConfirmedPlace();
        IllegalStateException failure = new IllegalStateException("secret-database-detail");
        when(resolveFoodCategory.resolve(any())).thenThrow(failure);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.execute(commandWithVisitEvidence()))
                .isSameAs(failure);

        verify(autoRegister, never()).register(any());
    }

    @Test
    @DisplayName("물리 검증 실패는 검색 단계와 구분하고 안전한 서버 traceId만 기록한다")
    void execute_물리검증시간초과_단계와TraceId기록() {
        stubConfirmedPlace();
        when(resolvePlacePhysicalReference.resolve(any(), any())).thenThrow(
                new com.masiton.restaurant.application.PlaceVerificationFailedException(
                        new java.net.http.HttpTimeoutException("secret-token")));
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(RegistrationUnitExecutionService.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        org.slf4j.MDC.put("traceId", "server-test-trace");
        try {
            RegistrationUnitExecutionResult result = service.execute(commandWithVisitEvidence());

            assertThat(result.blockReason()).isEqualTo("EXTERNAL_SERVICE_ERROR");
            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage()).contains("stage=PLACE_PHYSICAL_VERIFICATION",
                        "failureType=EXTERNAL_TIMEOUT", "traceId=server-test-trace")
                        .doesNotContain("secret-token", "행복식당", "월드컵로");
                assertThat(event.getThrowableProxy()).isNull();
            });
            verify(autoRegister, never()).register(any());
        } finally {
            org.slf4j.MDC.remove("traceId");
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    @DisplayName("YouTube 외부 조회 오류는 등록 저장 전에 차단한다")
    void execute_영상외부조회실패_등록저장0건() {
        stubConfirmedPlace();
        stubResolvedCategory();
        when(videoVerification.resolve(any())).thenThrow(new com.masiton.common.web.BusinessException(
                com.masiton.common.web.ErrorCode.EXTERNAL_SERVICE_ERROR));

        RegistrationUnitExecutionResult result = service.execute(commandWithVisitEvidence());

        assertThat(result.blockReason()).isEqualTo("EXTERNAL_SERVICE_ERROR");
        verify(autoRegister, never()).register(any());
    }

    @Test
    @DisplayName("주소 없이 결속된 위치를 사용해도 물리 검증 지역이 일치하면 등록한다")
    void execute_결속위치와물리검증지역일치_등록을확정한다() {
        // Given
        stubConfirmedPlace();
        stubResolvedCategory();
        stubVerifiedVideo();
        var registration = new AutoRegisterVerifiedContentUseCase.RegistrationResult(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), true, true, true, true);
        when(autoRegister.register(any())).thenReturn(registration);
        var base = commandWithVisitEvidence();
        var command = new RegistrationUnitExecutionCommand(base.restaurantName(), null, base.menu(),
                base.visitEvidence(), base.channelId(), base.videoId(), base.videoUrl(), null, null, null,
                "fixture-unit", "서울 마포구");
        // When
        var result = service.execute(command);
        // Then
        assertThat(result.confirmed()).isTrue();
        assertThat(result.placeDecision().roadAddress()).isEqualTo("서울특별시 마포구 월드컵로 1");
        verify(resolvePlaceIdentity).resolve(new ResolvePlaceIdentityUseCase.PlaceIdentityCommand(
                "행복식당", null, "냉면", "서울 마포구"));
    }

    @Test
    @DisplayName("위치로 검색한 장소라도 물리 재검증 지역이 바뀌면 저장하지 않는다")
    void execute_위치검색후물리지역불일치_저장0건으로보류한다() {
        // Given
        stubConfirmedPlace();
        when(resolvePlacePhysicalReference.resolve(any(), any())).thenReturn(Optional.of(
                new ResolvePlacePhysicalReferenceUseCase.VerifiedPlacePhysicalReference(
                        REGION_ID, "행복식당", KAKAO_PLACE_ID, KAKAO_PLACE_URL, "부산광역시 중구 중앙대로 1",
                        null, null, null)));
        var base = commandWithVisitEvidence();
        var command = new RegistrationUnitExecutionCommand(base.restaurantName(), null, base.menu(),
                base.visitEvidence(), base.channelId(), base.videoId(), base.videoUrl(), null, null, null,
                "fixture-unit", "서울 마포구");
        // When
        var result = service.execute(command);
        // Then
        assertThat(result.blockReason()).isEqualTo("PLACE_NOT_FOUND");
        verify(autoRegister, never()).register(any());
        verify(resolveFoodCategory, never()).resolve(any());
    }

    private void stubConfirmedPlace() {
        when(resolvePlaceIdentity.resolve(any())).thenReturn(ResolvePlaceIdentityUseCase.PlaceIdentityResult.confirmed(
                new ResolvePlaceIdentityUseCase.ConfirmedPlace(KAKAO_PLACE_URL, "서울특별시 마포구 월드컵로 1",
                        "NAME_AND_DISTRICT", "한식")));
        when(resolvePlacePhysicalReference.resolve(any(), any())).thenReturn(Optional.of(
                new ResolvePlacePhysicalReferenceUseCase.VerifiedPlacePhysicalReference(
                        REGION_ID, "행복식당", KAKAO_PLACE_ID, KAKAO_PLACE_URL, "서울특별시 마포구 월드컵로 1",
                        "02-1234-5678", java.math.BigDecimal.valueOf(37.5), java.math.BigDecimal.valueOf(126.9))));
    }

    private void stubResolvedCategory() {
        when(resolveFoodCategory.resolve(any())).thenReturn(ResolveFoodCategoryUseCase.FoodCategoryResolutionResult.resolved(
                new ResolveFoodCategoryUseCase.ResolvedFoodCategory(FOOD_CATEGORY_ID, "한식", "KAKAO_PLACE_CATEGORY",
                        UUID.randomUUID())));
    }

    private void stubVerifiedVideo() {
        when(videoVerification.resolve(any())).thenReturn(Optional.of(new VerifiedVideo(
                "video-1", "channel-1", "행복식당 영상", "https://example.com/thumb.jpg", "테스트 채널",
                "https://www.youtube.com/watch?v=video-1", OffsetDateTime.now(), OffsetDateTime.now())));
    }

    private RegistrationUnitExecutionCommand command(String restaurantName, String address) {
        return new RegistrationUnitExecutionCommand(restaurantName, address, "냉면", null, "channel-1", "video-1",
                URI.create("https://www.youtube.com/watch?v=video-1"));
    }

    private RegistrationUnitExecutionCommand commandWithVisitEvidence() {
        VisitEvidenceCandidate visitEvidence = new VisitEvidenceCandidate("행복식당 방문했습니다", 0.9,
                new Evidence(EvidenceType.TIMESTAMP, 1_000L, 2_000L, null, null, null));
        return new RegistrationUnitExecutionCommand("행복식당", "서울특별시 마포구 월드컵로 1", "냉면", visitEvidence,
                "channel-1", "video-1", URI.create("https://www.youtube.com/watch?v=video-1"));
    }
}

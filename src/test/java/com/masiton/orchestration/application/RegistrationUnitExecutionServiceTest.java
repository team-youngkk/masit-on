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
            duplicateRegistrationCheck, autoRegister);

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
        when(resolvePlaceIdentity.resolve(any())).thenThrow(new RuntimeException("Kakao timeout"));

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
    @DisplayName("같은 Kakao 장소 식별자의 맛집이 이미 있으면 유튜버·영상 조합과 무관하게 DUPLICATE_CONFLICT로 차단한다")
    void execute_같은맛집이이미있으면_DUPLICATE_CONFLICT로차단한다() {
        stubConfirmedPlace();
        stubResolvedCategory();
        stubVerifiedVideo();
        when(duplicateRegistrationCheck.restaurantExists(KAKAO_PLACE_ID)).thenReturn(true);

        RegistrationUnitExecutionResult result = service.execute(commandWithVisitEvidence());

        assertThat(result.blockReason()).isEqualTo("DUPLICATE_CONFLICT");
        verify(autoRegister, never()).register(any());
    }

    @Test
    @DisplayName("5단계 판정을 모두 통과하면 등록을 확정하고 결과를 반환한다")
    void execute_5단계를모두통과하면_등록을확정한다() {
        stubConfirmedPlace();
        stubResolvedCategory();
        stubVerifiedVideo();
        when(duplicateRegistrationCheck.restaurantExists(KAKAO_PLACE_ID)).thenReturn(false);
        AutoRegisterVerifiedContentUseCase.RegistrationResult registration =
                new AutoRegisterVerifiedContentUseCase.RegistrationResult(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        false, false, false, true);
        when(autoRegister.register(any())).thenReturn(registration);

        RegistrationUnitExecutionResult result = service.execute(commandWithVisitEvidence());

        assertThat(result.confirmed()).isTrue();
        assertThat(result.registration()).isEqualTo(registration);
        assertThat(result.placeDecision().kakaoPlaceUrl()).isEqualTo(KAKAO_PLACE_URL);
        assertThat(result.categoryDecision().foodCategoryId()).isEqualTo(FOOD_CATEGORY_ID);
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
        return new RegistrationUnitExecutionCommand(restaurantName, address, null, null, "channel-1", "video-1",
                URI.create("https://www.youtube.com/watch?v=video-1"));
    }

    private RegistrationUnitExecutionCommand commandWithVisitEvidence() {
        VisitEvidenceCandidate visitEvidence = new VisitEvidenceCandidate("행복식당 방문했습니다", 0.9,
                new Evidence(EvidenceType.TIMESTAMP, 1_000L, 2_000L, null, null, null));
        return new RegistrationUnitExecutionCommand("행복식당", "서울특별시 마포구 월드컵로 1", null, visitEvidence,
                "channel-1", "video-1", URI.create("https://www.youtube.com/watch?v=video-1"));
    }
}

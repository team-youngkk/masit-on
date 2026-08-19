package com.masiton.orchestration.application;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.masiton.orchestration.application.port.in.AutoRegisterVerifiedContentUseCase;
import com.masiton.orchestration.application.port.in.ExecuteRegistrationUnitUseCase;
import com.masiton.orchestration.application.port.in.ResolveFoodCategoryUseCase;
import com.masiton.orchestration.application.port.in.ResolvePlaceIdentityUseCase;
import com.masiton.orchestration.application.port.out.DuplicateRegistrationCheckPort;
import com.masiton.restaurant.application.port.in.ResolvePlacePhysicalReferenceUseCase;
import com.masiton.video.application.port.in.ResolveVerifiedVideoUseCase;
import com.masiton.video.application.port.out.VerifiedVideo;

/**
 * {@link ExecuteRegistrationUnitUseCase}의 구현체다. {@code BR-AIEXTRACT-011} 5단계를 순서대로
 * 수행한다.
 *
 * <p>1~2단계에서 관리자 보충 입력이 주어지면({@code suppliedKakaoPlaceUrl}·
 * {@code suppliedFoodCategoryId}) 해당 단계의 자동 판정을 생략하고 그 값을 그대로 채택한다.
 * 이때도 3~5단계(YouTube 메타데이터·방문 근거·중복)는 자동 실행과 동일하게 다시 수행한다
 * ({@code BR-AIEXTRACT-011}: "보조 입력 경로에서 관리자가 제출한 값도 기존 외부 검증을 우회하지
 * 않는다").</p>
 *
 * <p>{@code suppliedKakaoPlaceUrl}이 주어진 경로는 상호명 검색을 생략하므로 Kakao 분류 표현을
 * 얻을 수 없다. 이 경우 카테고리 2단계는 메뉴 표현만으로(2순위 근거만) 판정한다. 이는
 * {@code ResolvePlacePhysicalReferenceUseCase}가 물리 필드만 반환하고 분류 표현을 반환하지 않는
 * 기존 계약({@code PlaceVerificationPort}/{@code VerifiedPlace})의 제약에 따른 의도적 단순화다.</p>
 */
@Service
class RegistrationUnitExecutionService implements ExecuteRegistrationUnitUseCase {

    private static final String MATCHED_BY_MANUAL_OVERRIDE = "MANUAL_OVERRIDE";

    private final ResolvePlaceIdentityUseCase resolvePlaceIdentity;
    private final ResolveFoodCategoryUseCase resolveFoodCategory;
    private final ResolvePlacePhysicalReferenceUseCase resolvePlacePhysicalReference;
    private final ResolveVerifiedVideoUseCase videoVerification;
    private final DuplicateRegistrationCheckPort duplicateRegistrationCheck;
    private final AutoRegisterVerifiedContentUseCase autoRegister;

    RegistrationUnitExecutionService(
            ResolvePlaceIdentityUseCase resolvePlaceIdentity,
            ResolveFoodCategoryUseCase resolveFoodCategory,
            ResolvePlacePhysicalReferenceUseCase resolvePlacePhysicalReference,
            ResolveVerifiedVideoUseCase videoVerification,
            DuplicateRegistrationCheckPort duplicateRegistrationCheck,
            AutoRegisterVerifiedContentUseCase autoRegister) {
        this.resolvePlaceIdentity = resolvePlaceIdentity;
        this.resolveFoodCategory = resolveFoodCategory;
        this.resolvePlacePhysicalReference = resolvePlacePhysicalReference;
        this.videoVerification = videoVerification;
        this.duplicateRegistrationCheck = duplicateRegistrationCheck;
        this.autoRegister = autoRegister;
    }

    @Override
    public RegistrationUnitExecutionResult execute(RegistrationUnitExecutionCommand command) {
        Objects.requireNonNull(command, "command");
        if (blank(command.restaurantName()) || (blank(command.address()) && !command.hasSuppliedKakaoPlaceUrl())) {
            return blocked("MISSING_REQUIRED_FIELD");
        }

        PlaceStep placeStep;
        try {
            placeStep = resolvePlace(command);
        } catch (RuntimeException exception) {
            return blocked("EXTERNAL_SERVICE_ERROR");
        }
        if (placeStep == null) {
            return blocked("PLACE_NOT_FOUND");
        }
        if (placeStep.ambiguous()) {
            return blocked("PLACE_AMBIGUOUS");
        }

        CategoryStep categoryStep;
        try {
            categoryStep = resolveCategory(command, placeStep);
        } catch (RuntimeException exception) {
            return blocked("EXTERNAL_SERVICE_ERROR");
        }
        if (categoryStep == null) {
            return blocked("CATEGORY_UNRESOLVED");
        }

        VerifiedVideo verifiedVideo;
        try {
            Optional<VerifiedVideo> resolved = videoVerification.resolve(command.videoUrl());
            if (resolved.isEmpty() || !matchesVideo(command, resolved.get())) {
                return blocked("EXTERNAL_SERVICE_ERROR");
            }
            verifiedVideo = resolved.get();
        } catch (RuntimeException exception) {
            return blocked("EXTERNAL_SERVICE_ERROR");
        }

        if (!VisitEvidenceConfirmation.confirmsActualVisit(command.visitEvidence(), placeStep.name(),
                verifiedVideo.channelName())) {
            return blocked("VISIT_EVIDENCE_REQUIRED");
        }

        if (isDuplicate(placeStep)) {
            return blocked("DUPLICATE_CONFLICT");
        }

        AutoRegisterVerifiedContentUseCase.RegistrationResult registration = autoRegister.register(
                new AutoRegisterVerifiedContentUseCase.VerifiedContentCommand(
                        new AutoRegisterVerifiedContentUseCase.RestaurantCandidate(
                                placeStep.regionId(), categoryStep.foodCategoryId(), placeStep.name(),
                                placeStep.kakaoPlaceId(), placeStep.kakaoPlaceUrl(), placeStep.roadAddress(), null,
                                placeStep.phoneNumber(), placeStep.latitude(), placeStep.longitude()),
                        new AutoRegisterVerifiedContentUseCase.CreatorCandidate(
                                verifiedVideo.publisherExternalChannelId(), verifiedVideo.channelName(),
                                "https://www.youtube.com/channel/" + verifiedVideo.publisherExternalChannelId()),
                        new AutoRegisterVerifiedContentUseCase.VideoCandidate(
                                verifiedVideo.externalVideoId(), verifiedVideo.publisherExternalChannelId(),
                                verifiedVideo.title(), verifiedVideo.sourceUrl(), verifiedVideo.thumbnailUrl(),
                                verifiedVideo.publishedAt(), verifiedVideo.checkedAt()),
                        true));

        return RegistrationUnitExecutionResult.confirmed(
                new PlaceDecision(placeStep.kakaoPlaceUrl(), placeStep.roadAddress(), placeStep.matchedBy()),
                new CategoryDecision(categoryStep.foodCategoryId(), categoryStep.foodCategoryName(),
                        categoryStep.resolvedBy()),
                registration);
    }

    private PlaceStep resolvePlace(RegistrationUnitExecutionCommand command) {
        if (command.hasSuppliedKakaoPlaceUrl()) {
            URI suppliedUrl = URI.create(command.suppliedKakaoPlaceUrl());
            return resolvePlacePhysicalReference.resolve(command.restaurantName(), suppliedUrl)
                    .map(place -> new PlaceStep(false, place.regionId(), place.name(), place.kakaoPlaceId(),
                            place.kakaoPlaceUrl(), place.roadAddress(), place.phoneNumber(), place.latitude(),
                            place.longitude(), MATCHED_BY_MANUAL_OVERRIDE, null))
                    .orElse(null);
        }

        ResolvePlaceIdentityUseCase.PlaceIdentityResult identity = resolvePlaceIdentity.resolve(
                new ResolvePlaceIdentityUseCase.PlaceIdentityCommand(command.restaurantName(), command.address()));
        if (identity.status() == ResolvePlaceIdentityUseCase.PlaceIdentityStatus.PLACE_AMBIGUOUS) {
            return PlaceStep.ambiguousResult();
        }
        if (!identity.isConfirmed()) {
            return null;
        }
        ResolvePlaceIdentityUseCase.ConfirmedPlace confirmed = identity.confirmedPlace();
        return resolvePlacePhysicalReference
                .resolve(command.restaurantName(), URI.create(confirmed.kakaoPlaceUrl()))
                .map(place -> new PlaceStep(false, place.regionId(), place.name(), place.kakaoPlaceId(),
                        place.kakaoPlaceUrl(), place.roadAddress(), place.phoneNumber(), place.latitude(),
                        place.longitude(), confirmed.matchedBy(), confirmed.placeCategory()))
                .orElse(null);
    }

    private CategoryStep resolveCategory(RegistrationUnitExecutionCommand command, PlaceStep placeStep) {
        if (command.hasSuppliedFoodCategory()) {
            return new CategoryStep(command.suppliedFoodCategoryId(), command.suppliedFoodCategoryName(),
                    MATCHED_BY_MANUAL_OVERRIDE);
        }
        ResolveFoodCategoryUseCase.FoodCategoryResolutionResult resolution = resolveFoodCategory.resolve(
                new ResolveFoodCategoryUseCase.FoodCategoryResolutionCommand(
                        placeStep.kakaoPlaceCategoryHint(), command.menu()));
        if (!resolution.isResolved()) {
            return null;
        }
        ResolveFoodCategoryUseCase.ResolvedFoodCategory resolved = resolution.resolvedFoodCategory();
        return new CategoryStep(resolved.foodCategoryId(), resolved.foodCategoryName(), resolved.resolvedBy());
    }

    /**
     * 데이터 계약(third-expansion-ai-video-data-contract.md 11절)상 맛집은 재사용 대상이 아니다.
     * 같은 {@code kakaoPlaceId}의 맛집이 이미 있으면 이 방문의 유튜버·영상 조합과 무관하게 항상
     * {@code DUPLICATE_CONFLICT}로 차단한다.
     */
    private boolean isDuplicate(PlaceStep placeStep) {
        return duplicateRegistrationCheck.restaurantExists(placeStep.kakaoPlaceId());
    }

    private boolean matchesVideo(RegistrationUnitExecutionCommand command, VerifiedVideo video) {
        return command.videoId().equals(video.externalVideoId())
                && command.channelId().equals(video.publisherExternalChannelId())
                && nonBlank(video.channelName()) && nonBlank(video.title())
                && nonBlank(video.sourceUrl()) && nonBlank(video.thumbnailUrl());
    }

    private RegistrationUnitExecutionResult blocked(String reason) {
        return RegistrationUnitExecutionResult.blocked(reason);
    }

    private boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record PlaceStep(
            boolean ambiguous,
            java.util.UUID regionId,
            String name,
            String kakaoPlaceId,
            String kakaoPlaceUrl,
            String roadAddress,
            String phoneNumber,
            java.math.BigDecimal latitude,
            java.math.BigDecimal longitude,
            String matchedBy,
            String kakaoPlaceCategoryHint) {

        static PlaceStep ambiguousResult() {
            return new PlaceStep(true, null, null, null, null, null, null, null, null, null, null);
        }
    }

    private record CategoryStep(java.util.UUID foodCategoryId, String foodCategoryName, String resolvedBy) {
    }
}

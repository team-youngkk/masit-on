package com.masiton.orchestration.application.port.in;

import java.net.URI;
import java.util.UUID;

/**
 * {@code BR-AIEXTRACT-011}의 등록 단위 5단계 검증(장소 동일성 → 대표 카테고리 → YouTube 메타데이터
 * → 방문 근거 → 중복·원자성)을 하나로 묶어 실행하는 공개 계약이다. Worker 자동 실행
 * ({@code executed_by=WORKER})과 관리자 등록 단위 일괄 등록({@code executed_by=ADMIN}, API 3.6절)이
 * 같은 판정 규칙으로 이 use case를 호출한다.
 *
 * <p>{@code suppliedKakaoPlaceUrl}·{@code suppliedFoodCategoryId}는 {@code review}의
 * {@code CONFIRM} 보충 입력 재검증 경로에서만 채운다. 둘 다 {@code null}이면 완전 자동 판정이다.</p>
 *
 * <p>구현체는 이 메서드 전체를 {@code @Transactional}로 감싸지 않는다. Kakao·YouTube 외부 호출은
 * DB 트랜잭션 밖에서 수행하고, 검증을 모두 통과한 뒤에만
 * {@link AutoRegisterVerifiedContentUseCase#register}가 자신의 원자 트랜잭션으로 4종을 등록한다.</p>
 */
public interface ExecuteRegistrationUnitUseCase {

    RegistrationUnitExecutionResult execute(RegistrationUnitExecutionCommand command);

    /**
     * @param restaurantName 등록 단위의 상호명 후보
     * @param address 등록 단위의 주소 후보
     * @param menu 등록 단위의 메뉴 후보. 카테고리 2순위 근거로만 쓰인다
     * @param visitEvidence 등록 단위에 결속된 방문 근거 후보. 없으면 {@code null}
     * @param channelId 작업이 가진 YouTube 채널 식별자
     * @param videoId 작업이 가진 YouTube 영상 식별자
     * @param videoUrl 작업이 가진 YouTube 영상 URL
     * @param suppliedKakaoPlaceUrl {@code review}의 {@code CONFIRM} 보충 입력. 관리자가 확인한
     *                              Kakao 장소 URL이며, 주어지면 상호명 기반 자동 검색(1단계)을
     *                              생략하고 이 URL을 그대로 검증한다
     * @param suppliedFoodCategoryId {@code review}의 {@code CONFIRM} 보충 입력. 관리자가 고른
     *                               활성 카테고리 식별자이며, 주어지면 카테고리 자동 선정(2단계)을
     *                               생략한다
     */
    record RegistrationUnitExecutionCommand(
            String restaurantName,
            String address,
            String menu,
            VerifyAiContentCandidateUseCase.VisitEvidenceCandidate visitEvidence,
            String channelId,
            String videoId,
            URI videoUrl,
            String suppliedKakaoPlaceUrl,
            UUID suppliedFoodCategoryId,
            String suppliedFoodCategoryName) {

        public RegistrationUnitExecutionCommand(
                String restaurantName, String address, String menu,
                VerifyAiContentCandidateUseCase.VisitEvidenceCandidate visitEvidence,
                String channelId, String videoId, URI videoUrl) {
            this(restaurantName, address, menu, visitEvidence, channelId, videoId, videoUrl, null, null, null);
        }

        public boolean hasSuppliedKakaoPlaceUrl() {
            return suppliedKakaoPlaceUrl != null && !suppliedKakaoPlaceUrl.isBlank();
        }

        public boolean hasSuppliedFoodCategory() {
            return suppliedFoodCategoryId != null;
        }
    }

    record RegistrationUnitExecutionResult(
            boolean confirmed,
            String blockReason,
            PlaceDecision placeDecision,
            CategoryDecision categoryDecision,
            AutoRegisterVerifiedContentUseCase.RegistrationResult registration) {

        public static RegistrationUnitExecutionResult blocked(String blockReason) {
            return new RegistrationUnitExecutionResult(false, blockReason, null, null, null);
        }

        public static RegistrationUnitExecutionResult confirmed(PlaceDecision placeDecision,
                CategoryDecision categoryDecision, AutoRegisterVerifiedContentUseCase.RegistrationResult registration) {
            return new RegistrationUnitExecutionResult(true, null, placeDecision, categoryDecision, registration);
        }
    }

    /** {@code matchedBy}는 {@code NAME_AND_DISTRICT} 또는 보충 입력 경로의 {@code MANUAL_OVERRIDE}다. */
    record PlaceDecision(String kakaoPlaceUrl, String roadAddress, String matchedBy) {
    }

    /** {@code resolvedBy}는 {@code KAKAO_PLACE_CATEGORY}·{@code MENU_EXPRESSION}·{@code MANUAL_OVERRIDE}다. */
    record CategoryDecision(UUID foodCategoryId, String foodCategoryName, String resolvedBy) {
    }
}

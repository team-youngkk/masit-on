package com.masiton.ai.application;

import java.util.UUID;

/**
 * {@code RegistrationUnitAutoExecutionService}가 Worker 자동 실행 중 계산한 등록 단위 판정
 * 결과다. {@code BR-AIEXTRACT-011} 5단계를 모두 통과하면 {@code AUTO_CONFIRMED}로 등록 결과
 * 4종·{@code place_decision}·{@code category_decision}·{@code reused_resources}를 함께 담고,
 * 1~5단계 중 하나라도 실패하면 {@code AUTO_BLOCKED}로 {@code blockReason}만 담는다.
 *
 * <p>{@code ck_ai_registration_unit__registration_result_pair} 제약이 "등록 결과 컬럼은 모두
 * 존재하거나 모두 {@code NULL}"을 강제하므로, 이 레코드의 두 정적 팩터리({@link #blocked}·
 * {@link #confirmed})가 그 불변식을 지킨다.</p>
 */
record RegistrationUnitOutcome(
        int unitIndex,
        String restaurantName,
        String reviewStatus,
        String blockReason,
        String placeDecisionJson,
        String categoryDecisionJson,
        UUID registeredRestaurantId,
        UUID registeredCreatorId,
        UUID registeredVideoId,
        UUID registeredVisitId,
        String reusedResourcesJson) {

    static final String AUTO_BLOCKED = "AUTO_BLOCKED";
    static final String AUTO_CONFIRMED = "AUTO_CONFIRMED";

    static RegistrationUnitOutcome blocked(int unitIndex, String restaurantName, String blockReason) {
        return new RegistrationUnitOutcome(unitIndex, restaurantName, AUTO_BLOCKED, blockReason,
                null, null, null, null, null, null, null);
    }

    static RegistrationUnitOutcome confirmed(int unitIndex, String restaurantName, String placeDecisionJson,
                                             String categoryDecisionJson, UUID registeredRestaurantId,
                                             UUID registeredCreatorId, UUID registeredVideoId, UUID registeredVisitId,
                                             String reusedResourcesJson) {
        return new RegistrationUnitOutcome(unitIndex, restaurantName, AUTO_CONFIRMED, null, placeDecisionJson,
                categoryDecisionJson, registeredRestaurantId, registeredCreatorId, registeredVideoId,
                registeredVisitId, reusedResourcesJson);
    }
}

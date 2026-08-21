package com.masiton.orchestration.application.port.in;

import java.util.UUID;

/**
 * {@code BR-AIEXTRACT-010} 대표 음식 카테고리 자동 선정을 orchestration이 소유하는 공개 계약이다.
 * {@link ResolvePlaceIdentityUseCase}로 확정한 Kakao 장소의 분류 표현을 1순위, AI 메뉴 후보
 * 표현을 2순위 근거로 {@code food_category_mapping} 기준정보에 대조한다.
 */
public interface ResolveFoodCategoryUseCase {

    FoodCategoryResolutionResult resolve(FoodCategoryResolutionCommand command);

    /**
     * {@code review}의 {@code CONFIRM}·{@code ADJUST_CATEGORY} 보충 입력이 활성 기준정보를
     * 가리키는지 검증한다. 비활성·미존재 값은 빈 값을 반환한다.
     */
    java.util.Optional<String> findActiveCategoryName(java.util.UUID foodCategoryId);

    /**
     * {@code kakaoPlaceCategory}는 확정한 Kakao 장소의 원문 분류 표현({@code category_name}),
     * {@code menuExpression}은 AI 메뉴 후보 표현이다. 둘 다 없을 수 있다.
     */
    record FoodCategoryResolutionCommand(String kakaoPlaceCategory, String menuExpression) {
    }

    record FoodCategoryResolutionResult(FoodCategoryResolutionStatus status, ResolvedFoodCategory resolvedFoodCategory) {

        public FoodCategoryResolutionResult {
            if ((status == FoodCategoryResolutionStatus.RESOLVED) != (resolvedFoodCategory != null)) {
                throw new IllegalArgumentException(
                        "resolvedFoodCategory must be present only when status is RESOLVED.");
            }
        }

        public static FoodCategoryResolutionResult resolved(ResolvedFoodCategory resolvedFoodCategory) {
            return new FoodCategoryResolutionResult(FoodCategoryResolutionStatus.RESOLVED, resolvedFoodCategory);
        }

        public static FoodCategoryResolutionResult unresolved() {
            return new FoodCategoryResolutionResult(FoodCategoryResolutionStatus.CATEGORY_UNRESOLVED, null);
        }

        public boolean isResolved() {
            return status == FoodCategoryResolutionStatus.RESOLVED;
        }
    }

    /** {@code ai_registration_unit.block_reason}의 {@code CATEGORY_UNRESOLVED}와 확정 상태를 함께 표현한다. */
    enum FoodCategoryResolutionStatus {
        RESOLVED,
        CATEGORY_UNRESOLVED
    }

    /**
     * {@code resolvedBy}는 {@code KAKAO_PLACE_CATEGORY} 또는 {@code MENU_EXPRESSION}이다.
     * {@code matchedMappingId}는 orchestration 내부에서 판정에 사용한
     * {@code food_category_mapping} 행 식별자다. 등록 단위의 현재 {@code category_decision}
     * 저장 계약에는 포함하지 않는다.
     */
    record ResolvedFoodCategory(UUID foodCategoryId, String foodCategoryName, String resolvedBy, UUID matchedMappingId) {
    }
}

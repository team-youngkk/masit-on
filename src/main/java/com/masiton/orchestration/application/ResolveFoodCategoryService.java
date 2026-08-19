package com.masiton.orchestration.application;

import java.util.Objects;

import org.springframework.stereotype.Service;

import com.masiton.orchestration.application.port.in.ResolveFoodCategoryUseCase;
import com.masiton.restaurant.application.port.in.LookupFoodCategoryMappingUseCase;
import com.masiton.restaurant.application.port.in.LookupFoodCategoryMappingUseCase.MappingOutcome;
import com.masiton.restaurant.application.port.in.LookupFoodCategoryMappingUseCase.MappingResolution;
import com.masiton.restaurant.application.port.in.LookupFoodCategoryMappingUseCase.ResolvedMapping;

/**
 * {@code BR-AIEXTRACT-010} 대표 음식 카테고리 자동 선정을 수행한다. 확정한 Kakao 장소 분류를
 * 1순위, AI 메뉴 후보 표현을 2순위 근거로 {@code food_category_mapping} 기준정보에 대조한다.
 * 대조 순서·충돌 판정 자체는 restaurant 도메인({@link LookupFoodCategoryMappingUseCase})이
 * 소유하고, 이 서비스는 두 근거 순위 사이의 폴백 순서만 결정한다.
 */
@Service
class ResolveFoodCategoryService implements ResolveFoodCategoryUseCase {

    private static final String RESOLVED_BY_KAKAO_PLACE_CATEGORY = "KAKAO_PLACE_CATEGORY";
    private static final String RESOLVED_BY_MENU_EXPRESSION = "MENU_EXPRESSION";

    private final LookupFoodCategoryMappingUseCase lookupFoodCategoryMapping;

    ResolveFoodCategoryService(LookupFoodCategoryMappingUseCase lookupFoodCategoryMapping) {
        this.lookupFoodCategoryMapping = lookupFoodCategoryMapping;
    }

    @Override
    public FoodCategoryResolutionResult resolve(FoodCategoryResolutionCommand command) {
        Objects.requireNonNull(command, "command");

        MappingResolution kakaoResolution = lookupFoodCategoryMapping
                .resolveByKakaoPlaceCategory(command.kakaoPlaceCategory());
        if (kakaoResolution.outcome() == MappingOutcome.CONFLICT) {
            return FoodCategoryResolutionResult.unresolved();
        }
        if (kakaoResolution.outcome() == MappingOutcome.MATCHED) {
            return toResolutionResult(kakaoResolution.match(), RESOLVED_BY_KAKAO_PLACE_CATEGORY);
        }

        MappingResolution menuResolution = lookupFoodCategoryMapping
                .resolveByMenuExpression(command.menuExpression());
        if (menuResolution.outcome() != MappingOutcome.MATCHED) {
            return FoodCategoryResolutionResult.unresolved();
        }
        return toResolutionResult(menuResolution.match(), RESOLVED_BY_MENU_EXPRESSION);
    }

    @Override
    public java.util.Optional<String> findActiveCategoryName(java.util.UUID foodCategoryId) {
        return lookupFoodCategoryMapping.findActiveCategoryName(foodCategoryId);
    }

    private FoodCategoryResolutionResult toResolutionResult(ResolvedMapping matched, String resolvedBy) {
        return lookupFoodCategoryMapping.findCategoryName(matched.foodCategoryId())
                .map(foodCategoryName -> FoodCategoryResolutionResult.resolved(new ResolvedFoodCategory(
                        matched.foodCategoryId(), foodCategoryName, resolvedBy, matched.mappingId())))
                .orElseGet(FoodCategoryResolutionResult::unresolved);
    }
}

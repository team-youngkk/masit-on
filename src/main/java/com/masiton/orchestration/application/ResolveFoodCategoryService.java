package com.masiton.orchestration.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.masiton.orchestration.application.port.in.ResolveFoodCategoryUseCase;
import com.masiton.restaurant.application.port.in.LookupFoodCategoryMappingUseCase;
import com.masiton.restaurant.domain.model.FoodCategoryMapping;
import com.masiton.restaurant.domain.model.FoodCategoryMappingMatchType;
import com.masiton.restaurant.domain.model.FoodCategoryMappingSourceType;

/**
 * {@code BR-AIEXTRACT-010} 대표 음식 카테고리 자동 선정을 수행한다. 확정한 Kakao 장소 분류를
 * 1순위, AI 메뉴 후보 표현을 2순위 근거로 {@code food_category_mapping} 기준정보에 대조한다.
 */
@Service
class ResolveFoodCategoryService implements ResolveFoodCategoryUseCase {

    private final LookupFoodCategoryMappingUseCase lookupFoodCategoryMapping;

    ResolveFoodCategoryService(LookupFoodCategoryMappingUseCase lookupFoodCategoryMapping) {
        this.lookupFoodCategoryMapping = lookupFoodCategoryMapping;
    }

    @Override
    public FoodCategoryResolutionResult resolve(FoodCategoryResolutionCommand command) {
        Objects.requireNonNull(command, "command");

        MatchOutcome kakaoOutcome = resolveFromSource(
                command.kakaoPlaceCategory(), FoodCategoryMappingSourceType.KAKAO_PLACE_CATEGORY);
        if (kakaoOutcome.conflicted()) {
            return FoodCategoryResolutionResult.unresolved();
        }
        if (kakaoOutcome.matched() != null) {
            return toResolutionResult(kakaoOutcome.matched());
        }

        MatchOutcome menuOutcome = resolveFromSource(
                command.menuExpression(), FoodCategoryMappingSourceType.MENU_EXPRESSION);
        if (menuOutcome.conflicted() || menuOutcome.matched() == null) {
            return FoodCategoryResolutionResult.unresolved();
        }
        return toResolutionResult(menuOutcome.matched());
    }

    @Override
    public java.util.Optional<String> findActiveCategoryName(java.util.UUID foodCategoryId) {
        return lookupFoodCategoryMapping.findActiveCategoryName(foodCategoryId);
    }

    /**
     * 대조 순서(EXACT 우선, 그 안에서 priority 오름차순)로 정렬된 목록을 같은 순위(matchType,
     * priority) 구간으로 묶어 순회한다. 후보가 하나라도 있는 첫 구간만 채택하고, 그 안에서 서로
     * 다른 카테고리로 일치하면 임의로 고르지 않고 충돌로 처리한다.
     */
    private MatchOutcome resolveFromSource(String candidateValue, FoodCategoryMappingSourceType sourceType) {
        if (blank(candidateValue)) {
            return MatchOutcome.none();
        }
        String normalizedValue = normalize(candidateValue);
        List<FoodCategoryMapping> mappings =
                lookupFoodCategoryMapping.findActiveBySourceTypeOrderByMatchTypeThenPriority(sourceType);

        int index = 0;
        while (index < mappings.size()) {
            FoodCategoryMapping tierHead = mappings.get(index);
            int tierEnd = index;
            while (tierEnd < mappings.size() && sameTier(mappings.get(tierEnd), tierHead)) {
                tierEnd++;
            }

            List<FoodCategoryMapping> tierMatches = new ArrayList<>();
            for (int i = index; i < tierEnd; i++) {
                FoodCategoryMapping candidate = mappings.get(i);
                if (matches(candidate, normalizedValue)) {
                    tierMatches.add(candidate);
                }
            }
            if (!tierMatches.isEmpty()) {
                long distinctCategoryCount = tierMatches.stream()
                        .map(FoodCategoryMapping::getFoodCategoryId)
                        .distinct()
                        .count();
                return distinctCategoryCount > 1 ? MatchOutcome.conflict() : MatchOutcome.of(tierMatches.get(0));
            }
            index = tierEnd;
        }
        return MatchOutcome.none();
    }

    private FoodCategoryResolutionResult toResolutionResult(FoodCategoryMapping matched) {
        return lookupFoodCategoryMapping.findCategoryName(matched.getFoodCategoryId())
                .map(foodCategoryName -> FoodCategoryResolutionResult.resolved(new ResolvedFoodCategory(
                        matched.getFoodCategoryId(), foodCategoryName, matched.getSourceType().name(),
                        matched.getId())))
                .orElseGet(FoodCategoryResolutionResult::unresolved);
    }

    private boolean sameTier(FoodCategoryMapping left, FoodCategoryMapping right) {
        return left.getMatchType() == right.getMatchType() && left.getPriority() == right.getPriority();
    }

    private boolean matches(FoodCategoryMapping mapping, String normalizedValue) {
        return mapping.getMatchType() == FoodCategoryMappingMatchType.EXACT
                ? normalizedValue.equals(mapping.getPattern())
                : normalizedValue.contains(mapping.getPattern());
    }

    private String normalize(String value) {
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /** 한 근거 유형 안에서 채택한 첫 일치 구간의 결과다. {@code conflicted}면 매칭 행이 있어도 채택하지 않는다. */
    private record MatchOutcome(FoodCategoryMapping matched, boolean conflicted) {

        static MatchOutcome none() {
            return new MatchOutcome(null, false);
        }

        static MatchOutcome conflict() {
            return new MatchOutcome(null, true);
        }

        static MatchOutcome of(FoodCategoryMapping mapping) {
            return new MatchOutcome(mapping, false);
        }
    }
}

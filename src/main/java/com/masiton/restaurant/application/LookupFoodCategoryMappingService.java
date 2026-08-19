package com.masiton.restaurant.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.masiton.restaurant.application.port.in.LookupFoodCategoryMappingUseCase;
import com.masiton.restaurant.application.port.out.FoodCategoryMappingRepositoryPort;
import com.masiton.restaurant.application.port.out.FoodCategoryRepositoryPort;
import com.masiton.restaurant.domain.model.FoodCategory;
import com.masiton.restaurant.domain.model.FoodCategoryMapping;
import com.masiton.restaurant.domain.model.FoodCategoryMappingMatchType;
import com.masiton.restaurant.domain.model.FoodCategoryMappingSourceType;

/**
 * {@link LookupFoodCategoryMappingUseCase}의 구현체다. {@code BR-AIEXTRACT-010}의 대조 순서
 * (EXACT 우선, priority 오름차순)와 같은 순위 복수 일치 충돌 판정을 이 domain 안에서 수행하고,
 * 호출자에게는 {@code food_category_mapping} Aggregate를 넘기지 않는다.
 */
@Service
class LookupFoodCategoryMappingService implements LookupFoodCategoryMappingUseCase {

    private final FoodCategoryMappingRepositoryPort foodCategoryMappingRepository;
    private final FoodCategoryRepositoryPort foodCategoryRepository;

    LookupFoodCategoryMappingService(FoodCategoryMappingRepositoryPort foodCategoryMappingRepository,
                                      FoodCategoryRepositoryPort foodCategoryRepository) {
        this.foodCategoryMappingRepository = foodCategoryMappingRepository;
        this.foodCategoryRepository = foodCategoryRepository;
    }

    @Override
    public MappingResolution resolveByKakaoPlaceCategory(String kakaoPlaceCategory) {
        return resolveFromSource(kakaoPlaceCategory, FoodCategoryMappingSourceType.KAKAO_PLACE_CATEGORY);
    }

    @Override
    public MappingResolution resolveByMenuExpression(String menuExpression) {
        return resolveFromSource(menuExpression, FoodCategoryMappingSourceType.MENU_EXPRESSION);
    }

    @Override
    public Optional<String> findCategoryName(UUID foodCategoryId) {
        return foodCategoryRepository.findById(foodCategoryId).map(FoodCategory::getName);
    }

    @Override
    public Optional<String> findActiveCategoryName(UUID foodCategoryId) {
        return foodCategoryRepository.findById(foodCategoryId)
                .filter(FoodCategory::isActive)
                .map(FoodCategory::getName);
    }

    /**
     * 대조 순서(EXACT 우선, 그 안에서 priority 오름차순)로 정렬된 목록을 같은 순위(matchType,
     * priority) 구간으로 묶어 순회한다. 후보가 하나라도 있는 첫 구간만 채택하고, 그 안에서 서로
     * 다른 카테고리로 일치하면 임의로 고르지 않고 충돌로 처리한다.
     */
    private MappingResolution resolveFromSource(String candidateValue, FoodCategoryMappingSourceType sourceType) {
        if (blank(candidateValue)) {
            return MappingResolution.none();
        }
        String normalizedValue = normalize(candidateValue);
        List<FoodCategoryMapping> mappings =
                foodCategoryMappingRepository.findActiveBySourceTypeOrderByMatchTypeThenPriority(sourceType);

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
                if (distinctCategoryCount > 1) {
                    return MappingResolution.conflict();
                }
                FoodCategoryMapping matched = tierMatches.get(0);
                return MappingResolution.matched(matched.getId(), matched.getFoodCategoryId());
            }
            index = tierEnd;
        }
        return MappingResolution.none();
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
}

package com.masiton.restaurant.application.port.in;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.masiton.restaurant.domain.model.FoodCategoryMapping;
import com.masiton.restaurant.domain.model.FoodCategoryMappingSourceType;

/**
 * 다른 도메인이 {@code BR-AIEXTRACT-010} 대표 음식 카테고리 자동 선정에 필요한
 * {@code food_category_mapping}·{@code food_category} 기준정보를 조회할 때 쓰는 공개 계약이다.
 * {@code dependency-rules.md} 3절에 따라 orchestration은 이 domain의 {@code port.in}만 호출하고
 * {@code port.out}(Infrastructure Adapter용)은 직접 호출하지 않는다.
 */
public interface LookupFoodCategoryMappingUseCase {

    List<FoodCategoryMapping> findActiveBySourceTypeOrderByMatchTypeThenPriority(
            FoodCategoryMappingSourceType sourceType);

    Optional<String> findCategoryName(UUID foodCategoryId);

    /**
     * {@code BR-AIEXTRACT-011} 관리자 보충 입력(카테고리 보정)이 활성 기준정보를 가리키는지
     * 검증할 때 쓴다. 비활성·미존재 값은 빈 값을 반환한다.
     */
    Optional<String> findActiveCategoryName(UUID foodCategoryId);
}

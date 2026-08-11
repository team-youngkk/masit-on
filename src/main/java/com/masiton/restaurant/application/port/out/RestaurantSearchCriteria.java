package com.masiton.restaurant.application.port.out;

import java.util.Set;
import java.util.UUID;

/**
 * 이미 검증·정규화된 검색 조건이다. null 필드는 해당 조건을 적용하지 않음을 뜻한다.
 * candidateRestaurantIds는 null이면 Creator 조건 미지정, 빈 집합이면 공개 Creator의 유효 후보 없음을 뜻한다.
 */
public record RestaurantSearchCriteria(
        String normalizedQuery,
        UUID regionId,
        UUID foodCategoryId,
        Set<UUID> candidateRestaurantIds,
        Set<String> tags,
        int page,
        int size) {

    /** 기존 목록 조건 호출과의 호환성을 유지한다. */
    public RestaurantSearchCriteria(
            String normalizedQuery,
            UUID regionId,
            UUID foodCategoryId,
            Set<UUID> candidateRestaurantIds,
            int page,
            int size) {
        this(normalizedQuery, regionId, foodCategoryId, candidateRestaurantIds, Set.of(), page, size);
    }

    public RestaurantSearchCriteria {
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }
}

package com.masiton.restaurant.application.port.out;

import java.util.UUID;

/**
 * 이미 검증·정규화된 검색 조건이다. null 필드는 해당 조건을 적용하지 않음을 뜻한다.
 */
public record RestaurantSearchCriteria(
        String normalizedQuery,
        UUID regionId,
        UUID foodCategoryId,
        UUID creatorId,
        int page,
        int size) {
}

package com.masiton.restaurant.application.port.out;

import java.util.Set;
import java.util.UUID;

/**
 * 이미 검증·정규화된 지도 마커 조회 조건이다. null 필드는 해당 조건을 적용하지 않음을 뜻한다.
 * creatorId는 필터가 지정된 경우 대표 채널 이미지를 해당 채널로 제한한다.
 * candidateRestaurantIds는 null이면 Creator 조건 미지정, 빈 집합이면 공개 Creator의 유효 후보 없음을 뜻한다.
 */
public record RestaurantMapPointsCriteria(
        String normalizedQuery,
        UUID regionId,
        UUID foodCategoryId,
        Set<UUID> candidateRestaurantIds,
        UUID creatorId) {

    public RestaurantMapPointsCriteria(
            String normalizedQuery,
            UUID regionId,
            UUID foodCategoryId,
            Set<UUID> candidateRestaurantIds) {
        this(normalizedQuery, regionId, foodCategoryId, candidateRestaurantIds, null);
    }
}

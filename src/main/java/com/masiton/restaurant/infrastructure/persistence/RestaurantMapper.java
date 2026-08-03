package com.masiton.restaurant.infrastructure.persistence;

import com.masiton.restaurant.domain.model.Restaurant;

/**
 * RestaurantJpaEntity와 domain.model.Restaurant 간 변환만 담당한다.
 */
final class RestaurantMapper {

    private RestaurantMapper() {
    }

    static Restaurant toDomain(RestaurantJpaEntity entity) {
        return new Restaurant(
                entity.getId(),
                entity.getRegionId(),
                entity.getFoodCategoryId(),
                entity.getName(),
                entity.getKakaoPlaceId(),
                entity.getKakaoPlaceUrl(),
                entity.getRoadAddress(),
                entity.getDetailAddress(),
                entity.getPhoneNumber(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getPublicationStatus(),
                entity.getLifecycleStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt());
    }

    static RestaurantJpaEntity toEntity(Restaurant domain) {
        return new RestaurantJpaEntity(
                domain.getId(),
                domain.getRegionId(),
                domain.getFoodCategoryId(),
                domain.getName(),
                domain.getKakaoPlaceId(),
                domain.getKakaoPlaceUrl(),
                domain.getRoadAddress(),
                domain.getDetailAddress(),
                domain.getPhoneNumber(),
                domain.getLatitude(),
                domain.getLongitude(),
                domain.getPublicationStatus(),
                domain.getLifecycleStatus(),
                domain.getDeletedAt());
    }
}

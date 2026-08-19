package com.masiton.restaurant.infrastructure.persistence;

import com.masiton.restaurant.domain.model.FoodCategoryMapping;

/**
 * FoodCategoryMappingJpaEntity와 domain.model.FoodCategoryMapping 간 변환만 담당한다.
 */
final class FoodCategoryMappingMapper {

    private FoodCategoryMappingMapper() {
    }

    static FoodCategoryMapping toDomain(FoodCategoryMappingJpaEntity entity) {
        return new FoodCategoryMapping(
                entity.getId(),
                entity.getSourceType(),
                entity.getPattern(),
                entity.getMatchType(),
                entity.getFoodCategoryId(),
                entity.getPriority(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    static FoodCategoryMappingJpaEntity toEntity(FoodCategoryMapping domain) {
        return new FoodCategoryMappingJpaEntity(
                domain.getId(),
                domain.getSourceType(),
                domain.getPattern(),
                domain.getMatchType(),
                domain.getFoodCategoryId(),
                domain.getPriority(),
                domain.isActive());
    }
}

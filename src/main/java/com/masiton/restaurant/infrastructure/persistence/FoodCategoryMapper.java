package com.masiton.restaurant.infrastructure.persistence;

import com.masiton.restaurant.domain.model.FoodCategory;

/**
 * FoodCategoryJpaEntity와 domain.model.FoodCategory 간 변환만 담당한다.
 */
final class FoodCategoryMapper {

    private FoodCategoryMapper() {
    }

    static FoodCategory toDomain(FoodCategoryJpaEntity entity) {
        return new FoodCategory(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getSortOrder(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    static FoodCategoryJpaEntity toEntity(FoodCategory domain) {
        return new FoodCategoryJpaEntity(
                domain.getId(),
                domain.getCode(),
                domain.getName(),
                domain.getSortOrder(),
                domain.isActive());
    }
}

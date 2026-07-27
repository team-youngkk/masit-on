package com.masiton.restaurant.infrastructure.persistence;

import org.springframework.stereotype.Component;

import com.masiton.restaurant.domain.model.FoodCategory;

/**
 * FoodCategoryJpaEntity와 domain.model.FoodCategory 간 변환만 담당한다.
 */
@Component
class FoodCategoryMapper {

    public FoodCategory toDomain(FoodCategoryJpaEntity entity) {
        return new FoodCategory(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getSortOrder(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public FoodCategoryJpaEntity toJpaEntity(FoodCategory domain) {
        return new FoodCategoryJpaEntity(
                domain.getId(),
                domain.getCode(),
                domain.getName(),
                domain.getSortOrder(),
                domain.isActive());
    }
}

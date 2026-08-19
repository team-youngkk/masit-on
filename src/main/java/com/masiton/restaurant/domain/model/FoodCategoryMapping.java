package com.masiton.restaurant.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * food_category_mapping 테이블과 대응하는 순수 도메인 모델이다.
 * Spring/JPA에 의존하지 않는다. food_category는 다른 Aggregate를 직접 참조하지 않고
 * 식별자(UUID)로만 연관을 표현한다.
 */
public class FoodCategoryMapping {

    private final UUID id;
    private final FoodCategoryMappingSourceType sourceType;
    private final String pattern;
    private final FoodCategoryMappingMatchType matchType;
    private final UUID foodCategoryId;
    private final short priority;
    private final boolean active;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public FoodCategoryMapping(
            UUID id,
            FoodCategoryMappingSourceType sourceType,
            String pattern,
            FoodCategoryMappingMatchType matchType,
            UUID foodCategoryId,
            short priority,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this.id = id;
        this.sourceType = sourceType;
        this.pattern = pattern;
        this.matchType = matchType;
        this.foodCategoryId = foodCategoryId;
        this.priority = priority;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public FoodCategoryMappingSourceType getSourceType() {
        return sourceType;
    }

    public String getPattern() {
        return pattern;
    }

    public FoodCategoryMappingMatchType getMatchType() {
        return matchType;
    }

    public UUID getFoodCategoryId() {
        return foodCategoryId;
    }

    public short getPriority() {
        return priority;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}

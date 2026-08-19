package com.masiton.restaurant.infrastructure.persistence;

import java.util.UUID;

import com.masiton.common.persistence.BaseAuditable;
import com.masiton.restaurant.domain.model.FoodCategoryMappingMatchType;
import com.masiton.restaurant.domain.model.FoodCategoryMappingSourceType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * food_category_mapping 테이블과 매핑되는 JPA Entity다.
 * V8__add_ai_registration_unit_and_food_category_mapping.sql의 food_category_mapping 테이블
 * 정의와 컬럼이 대응해야 한다. food_category_id는 dependency-rules.md 3절에 따라 객체
 * 연관관계 대신 식별자(UUID) 컬럼으로만 매핑한다.
 */
@Entity
@Table(name = "food_category_mapping")
public class FoodCategoryMappingJpaEntity extends BaseAuditable {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 24)
    private FoodCategoryMappingSourceType sourceType;

    @Column(name = "pattern", nullable = false, length = 128)
    private String pattern;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 16)
    private FoodCategoryMappingMatchType matchType;

    @Column(name = "food_category_id", nullable = false)
    private UUID foodCategoryId;

    @Column(name = "priority", nullable = false)
    private short priority;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected FoodCategoryMappingJpaEntity() {
    }

    public FoodCategoryMappingJpaEntity(
            UUID id,
            FoodCategoryMappingSourceType sourceType,
            String pattern,
            FoodCategoryMappingMatchType matchType,
            UUID foodCategoryId,
            short priority,
            boolean active) {
        this.id = id;
        this.sourceType = sourceType;
        this.pattern = pattern;
        this.matchType = matchType;
        this.foodCategoryId = foodCategoryId;
        this.priority = priority;
        this.active = active;
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
}

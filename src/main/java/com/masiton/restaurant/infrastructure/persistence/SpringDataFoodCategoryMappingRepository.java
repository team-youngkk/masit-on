package com.masiton.restaurant.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.masiton.restaurant.domain.model.FoodCategoryMappingMatchType;
import com.masiton.restaurant.domain.model.FoodCategoryMappingSourceType;

/**
 * food_category_mapping 테이블에 대한 Spring Data JPA Repository다. Infrastructure 내부 전용 타입이다.
 */
interface SpringDataFoodCategoryMappingRepository extends JpaRepository<FoodCategoryMappingJpaEntity, UUID> {

    List<FoodCategoryMappingJpaEntity> findBySourceTypeAndMatchTypeAndActiveTrueOrderByPriorityAsc(
            FoodCategoryMappingSourceType sourceType, FoodCategoryMappingMatchType matchType);
}

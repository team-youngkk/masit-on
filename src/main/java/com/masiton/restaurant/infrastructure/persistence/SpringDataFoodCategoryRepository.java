package com.masiton.restaurant.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * food_category 테이블에 대한 Spring Data JPA Repository다. Infrastructure 내부 전용 타입이다.
 */
interface SpringDataFoodCategoryRepository extends JpaRepository<FoodCategoryJpaEntity, UUID> {

    Optional<FoodCategoryJpaEntity> findByName(String name);
}

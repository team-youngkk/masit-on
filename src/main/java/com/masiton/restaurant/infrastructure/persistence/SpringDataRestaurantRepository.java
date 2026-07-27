package com.masiton.restaurant.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * restaurant 테이블에 대한 Spring Data JPA Repository다. Infrastructure 내부 전용 타입이다.
 */
interface SpringDataRestaurantRepository extends JpaRepository<RestaurantJpaEntity, UUID> {
}

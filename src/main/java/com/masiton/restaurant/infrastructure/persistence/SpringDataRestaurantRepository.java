package com.masiton.restaurant.infrastructure.persistence;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

/**
 * restaurant 테이블에 대한 Spring Data JPA Repository다. Infrastructure 내부 전용 타입이다.
 */
interface SpringDataRestaurantRepository extends JpaRepository<RestaurantJpaEntity, UUID> {

    Optional<RestaurantJpaEntity> findByKakaoPlaceId(String kakaoPlaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select restaurant from RestaurantJpaEntity restaurant where restaurant.id = :id")
    Optional<RestaurantJpaEntity> findByIdForUpdate(UUID id);
}

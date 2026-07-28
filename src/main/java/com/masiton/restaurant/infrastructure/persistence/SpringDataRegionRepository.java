package com.masiton.restaurant.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * region 테이블에 대한 Spring Data JPA Repository다. Infrastructure 내부 전용 타입이다.
 */
interface SpringDataRegionRepository extends JpaRepository<RegionJpaEntity, UUID> {

    Optional<RegionJpaEntity> findByName(String name);
}

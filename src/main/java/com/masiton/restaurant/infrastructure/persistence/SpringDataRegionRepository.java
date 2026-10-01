package com.masiton.restaurant.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * region 테이블에 대한 Spring Data JPA Repository다. Infrastructure 내부 전용 타입이다.
 */
interface SpringDataRegionRepository extends JpaRepository<RegionJpaEntity, UUID> {

    @Query("""
            SELECT region FROM RegionJpaEntity region
            WHERE region.administrativeCode = :administrativeCode AND region.active = true
                AND (region.parentId IS NULL OR EXISTS (
                    SELECT province.id FROM RegionJpaEntity province
                    WHERE province.id = region.parentId AND province.active = true
                ))
            """)
    Optional<RegionJpaEntity> findByAdministrativeCode(@Param("administrativeCode") String administrativeCode);

    List<RegionJpaEntity> findByActiveTrueOrderBySortOrderAscAdministrativeCodeAsc();

    @Query("""
            SELECT child FROM RegionJpaEntity child, RegionJpaEntity province
            WHERE child.parentId = province.id AND province.parentId IS NULL
                AND province.name = :province AND child.name = :name
                AND province.active = true AND child.active = true
            """)
    Optional<RegionJpaEntity> findByProvinceAndName(@Param("province") String province, @Param("name") String name);
}

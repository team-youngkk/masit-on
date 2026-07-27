package com.masiton.restaurant.infrastructure.persistence;

import com.masiton.restaurant.domain.model.Region;

/**
 * RegionJpaEntity와 domain.model.Region 간 변환만 담당한다.
 */
final class RegionMapper {

    private RegionMapper() {
    }

    static Region toDomain(RegionJpaEntity entity) {
        return new Region(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getSortOrder(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    static RegionJpaEntity toEntity(Region domain) {
        return new RegionJpaEntity(
                domain.getId(),
                domain.getCode(),
                domain.getName(),
                domain.getSortOrder(),
                domain.isActive());
    }
}

package com.masiton.restaurant.infrastructure.persistence;

import org.springframework.stereotype.Component;

import com.masiton.restaurant.domain.model.Region;

/**
 * RegionJpaEntity와 domain.model.Region 간 변환만 담당한다.
 */
@Component
class RegionMapper {

    public Region toDomain(RegionJpaEntity entity) {
        return new Region(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getSortOrder(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public RegionJpaEntity toJpaEntity(Region domain) {
        return new RegionJpaEntity(
                domain.getId(),
                domain.getCode(),
                domain.getName(),
                domain.getSortOrder(),
                domain.isActive());
    }
}

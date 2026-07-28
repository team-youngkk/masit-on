package com.masiton.restaurant.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.masiton.restaurant.application.port.out.RegionRepositoryPort;
import com.masiton.restaurant.domain.model.Region;

/**
 * RegionRepositoryPort의 구현체다. SpringDataRegionRepository와 RegionMapper를 내부적으로 사용한다.
 */
@Component
class RegionPersistenceAdapter implements RegionRepositoryPort {

    private final SpringDataRegionRepository springDataRegionRepository;

    RegionPersistenceAdapter(SpringDataRegionRepository springDataRegionRepository) {
        this.springDataRegionRepository = springDataRegionRepository;
    }

    @Override
    public Region save(Region region) {
        RegionJpaEntity savedEntity = springDataRegionRepository.save(RegionMapper.toEntity(region));
        return RegionMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Region> findById(UUID id) {
        return springDataRegionRepository.findById(id).map(RegionMapper::toDomain);
    }

    @Override
    public Optional<Region> findByName(String name) {
        return springDataRegionRepository.findByName(name).map(RegionMapper::toDomain);
    }
}

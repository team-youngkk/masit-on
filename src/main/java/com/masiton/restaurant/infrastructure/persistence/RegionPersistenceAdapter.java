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
    private final RegionMapper regionMapper;

    public RegionPersistenceAdapter(
            SpringDataRegionRepository springDataRegionRepository, RegionMapper regionMapper) {
        this.springDataRegionRepository = springDataRegionRepository;
        this.regionMapper = regionMapper;
    }

    @Override
    public Region save(Region region) {
        RegionJpaEntity savedEntity = springDataRegionRepository.save(regionMapper.toJpaEntity(region));
        return regionMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Region> findById(UUID id) {
        return springDataRegionRepository.findById(id).map(regionMapper::toDomain);
    }
}

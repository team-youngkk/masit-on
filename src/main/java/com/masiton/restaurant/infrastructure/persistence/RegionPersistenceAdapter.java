package com.masiton.restaurant.infrastructure.persistence;

import java.util.List;
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
        return findByProvinceAndName("서울특별시", name);
    }

    @Override
    public Optional<Region> findByAdministrativeCode(String code) {
        return springDataRegionRepository.findByAdministrativeCode(code).map(RegionMapper::toDomain);
    }

    @Override
    public List<Region> findAllActive() {
        return springDataRegionRepository.findByActiveTrueOrderBySortOrderAscAdministrativeCodeAsc().stream()
                .map(RegionMapper::toDomain).toList();
    }

    @Override
    public Optional<Region> findByProvinceAndName(String province, String name) {
        return springDataRegionRepository.findByProvinceAndName(province, name).map(RegionMapper::toDomain);
    }
}

package com.masiton.restaurant.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.masiton.restaurant.application.port.out.FoodCategoryMappingRepositoryPort;
import com.masiton.restaurant.domain.model.FoodCategoryMapping;
import com.masiton.restaurant.domain.model.FoodCategoryMappingMatchType;
import com.masiton.restaurant.domain.model.FoodCategoryMappingSourceType;

/**
 * FoodCategoryMappingRepositoryPort의 구현체다.
 * SpringDataFoodCategoryMappingRepository와 FoodCategoryMappingMapper를 내부적으로 사용한다.
 */
@Component
class FoodCategoryMappingPersistenceAdapter implements FoodCategoryMappingRepositoryPort {

    private final SpringDataFoodCategoryMappingRepository springDataFoodCategoryMappingRepository;

    FoodCategoryMappingPersistenceAdapter(
            SpringDataFoodCategoryMappingRepository springDataFoodCategoryMappingRepository) {
        this.springDataFoodCategoryMappingRepository = springDataFoodCategoryMappingRepository;
    }

    @Override
    public FoodCategoryMapping save(FoodCategoryMapping foodCategoryMapping) {
        FoodCategoryMappingJpaEntity savedEntity =
                springDataFoodCategoryMappingRepository.save(FoodCategoryMappingMapper.toEntity(foodCategoryMapping));
        return FoodCategoryMappingMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<FoodCategoryMapping> findById(UUID id) {
        return springDataFoodCategoryMappingRepository.findById(id).map(FoodCategoryMappingMapper::toDomain);
    }

    @Override
    public List<FoodCategoryMapping> findActiveBySourceTypeOrderByMatchTypeThenPriority(
            FoodCategoryMappingSourceType sourceType) {
        List<FoodCategoryMappingJpaEntity> exact = springDataFoodCategoryMappingRepository
                .findBySourceTypeAndMatchTypeAndActiveTrueOrderByPriorityAsc(
                        sourceType, FoodCategoryMappingMatchType.EXACT);
        List<FoodCategoryMappingJpaEntity> partial = springDataFoodCategoryMappingRepository
                .findBySourceTypeAndMatchTypeAndActiveTrueOrderByPriorityAsc(
                        sourceType, FoodCategoryMappingMatchType.PARTIAL);
        return Stream.concat(exact.stream(), partial.stream()).map(FoodCategoryMappingMapper::toDomain).toList();
    }
}

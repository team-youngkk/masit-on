package com.masiton.restaurant.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.masiton.restaurant.application.port.out.FoodCategoryRepositoryPort;
import com.masiton.restaurant.domain.model.FoodCategory;

/**
 * FoodCategoryRepositoryPort의 구현체다.
 * SpringDataFoodCategoryRepository와 FoodCategoryMapper를 내부적으로 사용한다.
 */
@Component
class FoodCategoryPersistenceAdapter implements FoodCategoryRepositoryPort {

    private final SpringDataFoodCategoryRepository springDataFoodCategoryRepository;

    FoodCategoryPersistenceAdapter(SpringDataFoodCategoryRepository springDataFoodCategoryRepository) {
        this.springDataFoodCategoryRepository = springDataFoodCategoryRepository;
    }

    @Override
    public FoodCategory save(FoodCategory foodCategory) {
        FoodCategoryJpaEntity savedEntity =
                springDataFoodCategoryRepository.save(FoodCategoryMapper.toEntity(foodCategory));
        return FoodCategoryMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<FoodCategory> findById(UUID id) {
        return springDataFoodCategoryRepository.findById(id).map(FoodCategoryMapper::toDomain);
    }
}

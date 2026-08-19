package com.masiton.restaurant.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.masiton.restaurant.application.port.in.LookupFoodCategoryMappingUseCase;
import com.masiton.restaurant.application.port.out.FoodCategoryMappingRepositoryPort;
import com.masiton.restaurant.application.port.out.FoodCategoryRepositoryPort;
import com.masiton.restaurant.domain.model.FoodCategory;
import com.masiton.restaurant.domain.model.FoodCategoryMapping;
import com.masiton.restaurant.domain.model.FoodCategoryMappingSourceType;

/**
 * {@link LookupFoodCategoryMappingUseCase}의 구현체다. 두 {@code port.out}으로 위임만 하는
 * 얇은 경계이며, 이 domain 밖에서는 {@code port.out}이 아니라 이 유스케이스를 통해 호출한다.
 */
@Service
class LookupFoodCategoryMappingService implements LookupFoodCategoryMappingUseCase {

    private final FoodCategoryMappingRepositoryPort foodCategoryMappingRepository;
    private final FoodCategoryRepositoryPort foodCategoryRepository;

    LookupFoodCategoryMappingService(FoodCategoryMappingRepositoryPort foodCategoryMappingRepository,
                                      FoodCategoryRepositoryPort foodCategoryRepository) {
        this.foodCategoryMappingRepository = foodCategoryMappingRepository;
        this.foodCategoryRepository = foodCategoryRepository;
    }

    @Override
    public List<FoodCategoryMapping> findActiveBySourceTypeOrderByMatchTypeThenPriority(
            FoodCategoryMappingSourceType sourceType) {
        return foodCategoryMappingRepository.findActiveBySourceTypeOrderByMatchTypeThenPriority(sourceType);
    }

    @Override
    public Optional<String> findCategoryName(UUID foodCategoryId) {
        return foodCategoryRepository.findById(foodCategoryId).map(FoodCategory::getName);
    }

    @Override
    public Optional<String> findActiveCategoryName(UUID foodCategoryId) {
        return foodCategoryRepository.findById(foodCategoryId)
                .filter(FoodCategory::isActive)
                .map(FoodCategory::getName);
    }
}

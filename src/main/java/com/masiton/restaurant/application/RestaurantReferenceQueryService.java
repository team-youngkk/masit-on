package com.masiton.restaurant.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.restaurant.application.port.in.FindRestaurantReferenceUseCase;
import com.masiton.restaurant.application.port.out.RestaurantRepositoryPort;
import com.masiton.restaurant.domain.model.LifecycleStatus;
import com.masiton.restaurant.domain.model.PublicationStatus;

@Service
@Transactional(readOnly = true)
class RestaurantReferenceQueryService implements FindRestaurantReferenceUseCase {

    private final RestaurantRepositoryPort restaurantRepository;

    RestaurantReferenceQueryService(RestaurantRepositoryPort restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    @Override
    public Optional<RestaurantReference> findRestaurantReference(UUID restaurantId) {
        return restaurantRepository.findByIdForUpdate(restaurantId)
                .map(restaurant -> new RestaurantReference(
                        restaurant.getId(),
                        restaurant.getPublicationStatus() == PublicationStatus.PUBLIC
                                && restaurant.getLifecycleStatus() == LifecycleStatus.ACTIVE));
    }
}

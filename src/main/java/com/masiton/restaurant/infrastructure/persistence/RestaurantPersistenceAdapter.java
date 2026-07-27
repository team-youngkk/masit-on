package com.masiton.restaurant.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.masiton.restaurant.application.port.out.RestaurantRepositoryPort;
import com.masiton.restaurant.domain.model.Restaurant;

/**
 * RestaurantRepositoryPort의 구현체다.
 * SpringDataRestaurantRepository와 RestaurantMapper를 내부적으로 사용한다.
 */
@Component
class RestaurantPersistenceAdapter implements RestaurantRepositoryPort {

    private final SpringDataRestaurantRepository springDataRestaurantRepository;
    private final RestaurantMapper restaurantMapper;

    public RestaurantPersistenceAdapter(
            SpringDataRestaurantRepository springDataRestaurantRepository, RestaurantMapper restaurantMapper) {
        this.springDataRestaurantRepository = springDataRestaurantRepository;
        this.restaurantMapper = restaurantMapper;
    }

    @Override
    public Restaurant save(Restaurant restaurant) {
        RestaurantJpaEntity savedEntity =
                springDataRestaurantRepository.save(restaurantMapper.toJpaEntity(restaurant));
        return restaurantMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Restaurant> findById(UUID id) {
        return springDataRestaurantRepository.findById(id).map(restaurantMapper::toDomain);
    }
}

package com.masiton.restaurant.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;

    RestaurantPersistenceAdapter(
            SpringDataRestaurantRepository springDataRestaurantRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.springDataRestaurantRepository = springDataRestaurantRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Restaurant save(Restaurant restaurant) {
        RestaurantJpaEntity savedEntity =
                springDataRestaurantRepository.save(RestaurantMapper.toEntity(restaurant));
        return RestaurantMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Restaurant> insertIfAbsent(Restaurant restaurant) {
        UUID id = jdbcTemplate.query(
                        """
                        insert into restaurant (
                            id, region_id, food_category_id, name, kakao_place_id, kakao_place_url,
                            road_address, detail_address, phone_number, latitude, longitude,
                            publication_status, lifecycle_status
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict (kakao_place_id) do nothing
                        returning id
                        """,
                        resultSet -> resultSet.next() ? resultSet.getObject("id", UUID.class) : null,
                        restaurant.getId(),
                        restaurant.getRegionId(),
                        restaurant.getFoodCategoryId(),
                        restaurant.getName(),
                        restaurant.getKakaoPlaceId(),
                        restaurant.getKakaoPlaceUrl(),
                        restaurant.getRoadAddress(),
                        restaurant.getDetailAddress(),
                        restaurant.getPhoneNumber(),
                        restaurant.getLatitude(),
                        restaurant.getLongitude(),
                        restaurant.getPublicationStatus().name(),
                        restaurant.getLifecycleStatus().name());
        return id == null ? Optional.empty() : findById(id);
    }

    @Override
    public Optional<Restaurant> findById(UUID id) {
        return springDataRestaurantRepository.findById(id).map(RestaurantMapper::toDomain);
    }

    @Override
    public Optional<Restaurant> findByKakaoPlaceId(String kakaoPlaceId) {
        return springDataRestaurantRepository.findByKakaoPlaceId(kakaoPlaceId)
                .map(RestaurantMapper::toDomain);
    }
}

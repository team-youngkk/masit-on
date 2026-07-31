package com.masiton.restaurant.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.masiton.restaurant.domain.model.Restaurant;

/**
 * Restaurant 저장소에 대한 Application 출력 Port다.
 * Application은 이 인터페이스에만 의존하고 Infrastructure Adapter가 구현한다.
 */
public interface RestaurantRepositoryPort {

    Restaurant save(Restaurant restaurant);

    /**
     * Kakao place identity uniqueness is resolved without placing the PostgreSQL transaction into
     * an aborted state on a concurrent insert.
     */
    Optional<Restaurant> insertIfAbsent(Restaurant restaurant);

    Optional<Restaurant> findById(UUID id);

    Optional<Restaurant> findByKakaoPlaceId(String kakaoPlaceId);
}

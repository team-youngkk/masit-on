package com.masiton.orchestration.application.query;

import java.util.Optional;
import java.util.UUID;

/**
 * 공개 Restaurant 기본 정보를 조회하는 출력 Port다.
 * 구현은 {@code orchestration.infrastructure.query}에 둔다.
 */
public interface RestaurantDetailBaseQueryPort {

    Optional<RestaurantDetailBase> findPublicDetailById(UUID restaurantId);
}

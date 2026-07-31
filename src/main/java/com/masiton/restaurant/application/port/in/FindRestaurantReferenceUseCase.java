package com.masiton.restaurant.application.port.in;

import java.util.Optional;
import java.util.UUID;

/** Visit 같은 교차 도메인 유스케이스에 공개하는 Restaurant의 최소 참조 계약이다. */
public interface FindRestaurantReferenceUseCase {

    Optional<RestaurantReference> findRestaurantReference(UUID restaurantId);

    record RestaurantReference(UUID id, boolean publiclyVisible) { }
}

package com.masiton.restaurant.application.port.in;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Visit 같은 교차 도메인 유스케이스에 공개하는 Restaurant의 최소 참조 계약이다. */
public interface FindRestaurantReferenceUseCase {

    Optional<RestaurantReference> findRestaurantReference(UUID restaurantId);

    List<RestaurantReference> findRestaurantReferences(Collection<UUID> restaurantIds);

    record RestaurantReference(
            UUID id,
            String name,
            String roadAddress,
            String availability,
            boolean publiclyVisible
    ) {
        public RestaurantReference(UUID id, boolean publiclyVisible) {
            this(id, null, null, publiclyVisible ? "PUBLIC" : "PRIVATE", publiclyVisible);
        }
    }
}

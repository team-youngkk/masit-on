package com.masiton.orchestration.application.port.in;

import java.util.Optional;
import java.util.UUID;

import com.masiton.orchestration.application.query.RestaurantDetailResult;

public interface GetRestaurantDetailWithMemberContextQuery {

    RestaurantDetailResult getRestaurantDetail(UUID restaurantId, Optional<UUID> memberId);
}

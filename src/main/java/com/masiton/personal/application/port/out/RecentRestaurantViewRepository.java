package com.masiton.personal.application.port.out;

import java.time.Instant;
import java.util.UUID;

public interface RecentRestaurantViewRepository {
    void upsert(UUID memberId, UUID restaurantId, Instant viewedAt);
}

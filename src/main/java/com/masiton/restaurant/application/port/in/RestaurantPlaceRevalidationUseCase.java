package com.masiton.restaurant.application.port.in;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.masiton.restaurant.application.port.out.RestaurantPlaceRevalidationStore.Audit;
import com.masiton.restaurant.application.port.out.RestaurantPlaceRevalidationStore.State;

public interface RestaurantPlaceRevalidationUseCase {
    Optional<Result> run(UUID restaurantId);
    void poll();
    Optional<State> state(UUID restaurantId);
    List<Audit> audits(UUID restaurantId, int limit);
    record Result(UUID restaurantId, String outcome, OffsetDateTime nextAttemptAt) { }
}

package com.masiton.restaurant.application;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.masiton.restaurant.application.port.out.RestaurantPlaceRevalidationStore;

@Service
class RestaurantPlaceRevalidationPersistenceService {
    private final RestaurantPlaceRevalidationStore store;
    RestaurantPlaceRevalidationPersistenceService(RestaurantPlaceRevalidationStore store) { this.store = store; }
    @Transactional
    public boolean apply(RestaurantPlaceRevalidationStore.ClaimedRestaurant claimed,
                         RestaurantPlaceRevalidationStore.Decision decision, OffsetDateTime now) {
        return store.apply(claimed, decision, now);
    }
}

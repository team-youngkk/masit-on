package com.masiton.restaurant.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Qualifier;

import com.masiton.restaurant.application.port.out.RestaurantPlaceRevalidationStore;

/** 외부 호출 전에만 짧게 실행되는 claim 트랜잭션을 소유한다. */
@Service
public class RestaurantPlaceRevalidationClaimService {
    private final RestaurantPlaceRevalidationStore store;
    private final RestaurantPlaceRevalidationProperties properties;
    private final Clock clock;
    private final String owner = "restaurant-place-revalidation-" + UUID.randomUUID();

    public RestaurantPlaceRevalidationClaimService(RestaurantPlaceRevalidationStore store,
                                                   RestaurantPlaceRevalidationProperties properties,
                                                   @Qualifier("restaurantPlaceRevalidationClock") Clock clock) {
        this.store = store;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Optional<RestaurantPlaceRevalidationStore.ClaimedRestaurant> claim(UUID restaurantId) {
        OffsetDateTime now = now();
        return store.claim(restaurantId, now, now.plus(properties.getLeaseDuration()), owner);
    }

    @Transactional
    public List<RestaurantPlaceRevalidationStore.ClaimedRestaurant> claimDue() {
        OffsetDateTime now = now();
        return store.claimDue(now, now.plus(properties.getLeaseDuration()), owner, properties.getBatchSize());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}

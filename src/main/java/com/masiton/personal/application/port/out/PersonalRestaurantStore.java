package com.masiton.personal.application.port.out;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface PersonalRestaurantStore {

    void addFavorite(UUID memberId, UUID restaurantId, OffsetDateTime favoritedAt);

    void removeFavorite(UUID memberId, UUID restaurantId);

    boolean existsFavorite(UUID memberId, UUID restaurantId);

    void upsertRecentRestaurant(UUID memberId, UUID restaurantId, OffsetDateTime viewedAt);

    void pruneRecentRestaurantOverflow(UUID memberId, int limit);

    int deleteRecentRestaurantViewsBefore(OffsetDateTime cutoff);

    void removeRecentRestaurant(UUID memberId, UUID restaurantId);
}

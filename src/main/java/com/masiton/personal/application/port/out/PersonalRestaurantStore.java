package com.masiton.personal.application.port.out;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.masiton.personal.application.port.in.PersonalRestaurantPage;

public interface PersonalRestaurantStore {

    boolean isPublicRestaurant(UUID restaurantId);

    void addFavorite(UUID memberId, UUID restaurantId, OffsetDateTime favoritedAt);

    void removeFavorite(UUID memberId, UUID restaurantId);

    boolean existsFavorite(UUID memberId, UUID restaurantId);

    PersonalRestaurantPage findFavorites(UUID memberId, int page, int size);

    PersonalRestaurantPage findRecentRestaurants(
            UUID memberId, OffsetDateTime cutoff, int retentionLimit, int page, int size);

    void upsertRecentRestaurant(UUID memberId, UUID restaurantId, OffsetDateTime viewedAt);

    void pruneRecentRestaurantOverflow(UUID memberId, int limit);

    int deleteRecentRestaurantViewsBefore(OffsetDateTime cutoff);

    void removeRecentRestaurant(UUID memberId, UUID restaurantId);

}

package com.masiton.personal.application.port.in;

import java.util.UUID;

public interface PersonalRestaurantUseCase {

    boolean addFavorite(UUID memberId, UUID restaurantId);

    boolean removeFavorite(UUID memberId, UUID restaurantId);

    boolean isFavorite(UUID memberId, UUID restaurantId);

    PersonalRestaurantPage getFavorites(UUID memberId, int page, int size);

    PersonalRestaurantPage getRecentRestaurants(UUID memberId, int page, int size);

    boolean removeRecentRestaurant(UUID memberId, UUID restaurantId);
}

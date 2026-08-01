package com.masiton.personal.application.port.out;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.masiton.personal.application.port.in.PersonalRestaurantPage;

public interface PersonalRestaurantQueryPort {

    PersonalRestaurantPage findFavorites(UUID memberId, int page, int size);

    PersonalRestaurantPage findRecentRestaurants(
            UUID memberId, OffsetDateTime cutoff, int retentionLimit, int page, int size);
}

package com.masiton.personal.application.port.in;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PersonalRestaurantItem(
        UUID restaurantId,
        String name,
        String district,
        String category,
        OffsetDateTime occurredAt
) {
}

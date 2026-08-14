package com.masiton.restaurant.application.port.in;

import java.util.UUID;

public record RestaurantCourseSegment(
        UUID fromRestaurantId, UUID toRestaurantId, int distanceMeters, int durationSeconds) {
}

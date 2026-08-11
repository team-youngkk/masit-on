package com.masiton.restaurant.application.port.in;

import java.time.OffsetDateTime;
import java.util.List;

public record RestaurantCourseResult(
        List<RestaurantCourseStop> restaurants,
        List<RestaurantCourseSegment> segments,
        int totalDistanceMeters,
        int totalDurationSeconds,
        OffsetDateTime generatedAt,
        OffsetDateTime expiresAt) {
}

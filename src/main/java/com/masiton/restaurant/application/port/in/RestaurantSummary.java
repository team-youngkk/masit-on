package com.masiton.restaurant.application.port.in;

import java.util.List;
import java.util.UUID;

public record RestaurantSummary(
        UUID id,
        String name,
        String district,
        String category,
        List<VisitedCreatorSummary> visitedBy,
        int remainingVisitedByCount) {
}

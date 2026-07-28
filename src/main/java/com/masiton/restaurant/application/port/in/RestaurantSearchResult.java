package com.masiton.restaurant.application.port.in;

import java.util.List;

public record RestaurantSearchResult(
        List<RestaurantSummary> items,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean hasNext) {
}

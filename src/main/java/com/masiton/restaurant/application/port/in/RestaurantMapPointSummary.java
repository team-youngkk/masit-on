package com.masiton.restaurant.application.port.in;

import java.math.BigDecimal;
import java.util.UUID;

public record RestaurantMapPointSummary(
        UUID id,
        String name,
        String category,
        String addressSummary,
        BigDecimal latitude,
        BigDecimal longitude,
        String creatorProfileImageUrl) {

    public RestaurantMapPointSummary(
            UUID id,
            String name,
            String category,
            String addressSummary,
            BigDecimal latitude,
            BigDecimal longitude) {
        this(id, name, category, addressSummary, latitude, longitude, null);
    }
}

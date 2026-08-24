package com.masiton.restaurant.application.port.out;

import java.math.BigDecimal;
import java.util.UUID;

/** 지도 영역 조회 결과 한 행이다. */
public record RestaurantMapPointRow(
        UUID id,
        String name,
        String category,
        String addressSummary,
        BigDecimal latitude,
        BigDecimal longitude,
        String creatorProfileImageUrl) {

    public RestaurantMapPointRow(
            UUID id,
            String name,
            String category,
            String addressSummary,
            BigDecimal latitude,
            BigDecimal longitude) {
        this(id, name, category, addressSummary, latitude, longitude, null);
    }
}

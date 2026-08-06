package com.masiton.restaurant.application.port.out;

import java.util.UUID;

/** 인기 맛집 집계 Projection 한 행이다. 순위는 조회 순서에서 파생되므로 담지 않는다. */
public record PopularRestaurantRow(
        UUID restaurantId,
        String name,
        String roadAddress,
        String category,
        long favoriteCount) {
}

package com.masiton.restaurant.application.port.in;

import java.util.UUID;

/**
 * API-POPULAR-001 인기 맛집 한 건이다.
 * BR-POPULAR-002에 따라 집계 찜 수만 담고 회원 식별자와 개별 찜 여부는 담지 않는다.
 */
public record PopularRestaurantSummary(
        int rank,
        UUID restaurantId,
        String name,
        String roadAddress,
        String category,
        long favoriteCount) {
}

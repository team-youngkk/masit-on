package com.masiton.restaurant.presentation.rest;

import java.util.List;

import com.masiton.restaurant.application.port.in.PopularRestaurantSummary;

/**
 * docs/05-specs/api/discovery/popular-restaurant-api.md의 응답 스키마와 정확히 일치해야 한다.
 */
public record PopularRestaurantResponse(List<PopularRestaurantItem> items) {

    static PopularRestaurantResponse from(List<PopularRestaurantSummary> summaries) {
        List<PopularRestaurantItem> items = summaries.stream()
                .map(PopularRestaurantItem::from)
                .toList();
        return new PopularRestaurantResponse(items);
    }

    public record PopularRestaurantItem(
            int rank,
            String restaurantId,
            String name,
            String roadAddress,
            String category,
            long favoriteCount) {

        static PopularRestaurantItem from(PopularRestaurantSummary summary) {
            return new PopularRestaurantItem(
                    summary.rank(),
                    summary.restaurantId().toString(),
                    summary.name(),
                    summary.roadAddress(),
                    summary.category(),
                    summary.favoriteCount());
        }
    }
}

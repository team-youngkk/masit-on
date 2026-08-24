package com.masiton.restaurant.presentation.rest;

import java.math.BigDecimal;
import java.util.List;

import com.masiton.restaurant.application.port.in.RestaurantMapPointSummary;
import com.masiton.restaurant.application.port.in.RestaurantMapPointsResult;

/**
 * map-discovery-api.md의 지도 영역 조회 응답 형태와 정확히 일치해야 한다.
 */
public record RestaurantMapPointsResponse(
        RestaurantMapPointsResult.ResultStatus resultStatus, int limit, List<RestaurantPointItem> items) {

    static RestaurantMapPointsResponse from(RestaurantMapPointsResult result) {
        List<RestaurantPointItem> items = result.items().stream()
                .map(RestaurantPointItem::from)
                .toList();
        return new RestaurantMapPointsResponse(result.resultStatus(), result.limit(), items);
    }

    public record RestaurantPointItem(
            String id, String name, String category, String addressSummary, Coordinate coordinate,
            String creatorProfileImageUrl) {

        static RestaurantPointItem from(RestaurantMapPointSummary summary) {
            return new RestaurantPointItem(
                    summary.id().toString(),
                    summary.name(),
                    summary.category(),
                    summary.addressSummary(),
                    new Coordinate(summary.latitude(), summary.longitude()),
                    summary.creatorProfileImageUrl());
        }
    }

    public record Coordinate(BigDecimal latitude, BigDecimal longitude) {
    }
}

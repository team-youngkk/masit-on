package com.masiton.restaurant.presentation.rest;

import java.util.List;

import com.masiton.restaurant.application.port.in.RestaurantSearchResult;
import com.masiton.restaurant.application.port.in.RestaurantSummary;
import com.masiton.restaurant.application.port.in.VisitedCreatorSummary;

/**
 * response-contract.md, pagination-contract.md의 목록 응답 형태와 정확히 일치해야 한다.
 */
public record RestaurantSearchResponse(List<RestaurantItem> items, PageInfo page) {

    static RestaurantSearchResponse from(RestaurantSearchResult result) {
        List<RestaurantItem> items = result.items().stream()
                .map(RestaurantItem::from)
                .toList();
        return new RestaurantSearchResponse(
                items,
                new PageInfo(
                        result.pageNumber(),
                        result.pageSize(),
                        result.totalElements(),
                        result.totalPages(),
                        result.hasNext()));
    }

    public record RestaurantItem(
            String id,
            String name,
            String district,
            String category,
            List<VisitedByItem> visitedBy,
            int remainingVisitedByCount) {

        static RestaurantItem from(RestaurantSummary summary) {
            List<VisitedByItem> visitedBy = summary.visitedBy().stream()
                    .map(VisitedByItem::from)
                    .toList();
            return new RestaurantItem(
                    summary.id().toString(),
                    summary.name(),
                    summary.district(),
                    summary.category(),
                    visitedBy,
                    summary.remainingVisitedByCount());
        }
    }

    public record VisitedByItem(String id, String channelName) {

        static VisitedByItem from(VisitedCreatorSummary summary) {
            return new VisitedByItem(summary.id().toString(), summary.channelName());
        }
    }

    public record PageInfo(int number, int size, long totalElements, int totalPages, boolean hasNext) {
    }
}

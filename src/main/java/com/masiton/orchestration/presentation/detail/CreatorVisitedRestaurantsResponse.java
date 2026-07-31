package com.masiton.orchestration.presentation.detail;

import java.util.List;

import com.masiton.orchestration.application.port.in.CreatorVisitedRestaurantItem;
import com.masiton.orchestration.application.port.in.CreatorVisitedRestaurantsResult;

/**
 * API-CREATOR-DETAIL-002 성공 응답 계약이다. response-contract.md·pagination-contract.md의
 * 목록 응답 형태와 정확히 일치해야 한다.
 */
record CreatorVisitedRestaurantsResponse(List<Item> items, PageInfo page) {

    static CreatorVisitedRestaurantsResponse from(CreatorVisitedRestaurantsResult result) {
        List<Item> items = result.items().stream().map(Item::from).toList();
        return new CreatorVisitedRestaurantsResponse(
                items,
                new PageInfo(
                        result.pageNumber(),
                        result.pageSize(),
                        result.totalElements(),
                        result.totalPages(),
                        result.hasNext()));
    }

    record Item(String id, String name, String district, String category) {

        static Item from(CreatorVisitedRestaurantItem item) {
            return new Item(item.id().toString(), item.name(), item.district(), item.category());
        }
    }

    record PageInfo(int number, int size, long totalElements, int totalPages, boolean hasNext) {
    }
}

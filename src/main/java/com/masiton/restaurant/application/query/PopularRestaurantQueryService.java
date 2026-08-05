package com.masiton.restaurant.application.query;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.restaurant.application.port.in.PopularRestaurantSummary;
import com.masiton.restaurant.application.port.in.PopularRestaurantUseCase;
import com.masiton.restaurant.application.port.out.PopularRestaurantQueryPort;
import com.masiton.restaurant.application.port.out.PopularRestaurantRow;

/**
 * FR-POPULAR-001 인기 맛집 조회를 처리한다.
 * ADR-DATA-011에 따라 요청마다 현재 `favorite`를 집계하며 Snapshot·Batch·캐시를 두지 않는다.
 * 순위는 Query Port가 보장한 안정 정렬 순서에서 1부터 파생한다.
 */
@Service
public class PopularRestaurantQueryService implements PopularRestaurantUseCase {

    private static final int RESULT_LIMIT = 20;

    private final PopularRestaurantQueryPort popularRestaurantQueryPort;

    public PopularRestaurantQueryService(PopularRestaurantQueryPort popularRestaurantQueryPort) {
        this.popularRestaurantQueryPort = popularRestaurantQueryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PopularRestaurantSummary> findPopularRestaurants() {
        List<PopularRestaurantRow> rows = popularRestaurantQueryPort.findTopByFavoriteCount(RESULT_LIMIT);

        List<PopularRestaurantSummary> items = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            PopularRestaurantRow row = rows.get(index);
            items.add(new PopularRestaurantSummary(
                    index + 1,
                    row.restaurantId(),
                    row.name(),
                    row.roadAddress(),
                    row.category(),
                    row.favoriteCount()));
        }
        return List.copyOf(items);
    }
}

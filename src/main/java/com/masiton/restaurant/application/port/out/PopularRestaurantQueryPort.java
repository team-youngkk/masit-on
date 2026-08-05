package com.masiton.restaurant.application.port.out;

import java.util.List;

/**
 * API-POPULAR-001 인기 맛집 집계의 Query Port다.
 * dependency-rules.md 7절 읽기 모델 예외에 따라 읽기 전용 Projection만 제공하고 Favorite 원본을 변경하지 않는다.
 */
public interface PopularRestaurantQueryPort {

    /**
     * 요청 시점의 `favorite` 현재 행을 맛집별로 집계해 공개·활성 맛집만 남기고
     * 찜 수 내림차순, 맛집 ID 오름차순으로 최대 limit건을 반환한다.
     */
    List<PopularRestaurantRow> findTopByFavoriteCount(int limit);
}

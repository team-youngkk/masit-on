package com.masiton.restaurant.application.port.in;

import java.util.List;

/** FR-POPULAR-001 현재 찜 수 기준 상위 공개 맛집 조회다. */
public interface PopularRestaurantUseCase {

    /**
     * 현재 찜이 1건 이상인 공개·활성 맛집을 찜 수 내림차순, 맛집 ID 오름차순으로 상위 20개까지 반환한다.
     * 조건에 맞는 맛집이 없으면 빈 목록이다.
     */
    List<PopularRestaurantSummary> findPopularRestaurants();
}

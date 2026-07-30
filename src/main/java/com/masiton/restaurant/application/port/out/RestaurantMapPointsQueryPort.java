package com.masiton.restaurant.application.port.out;

import java.util.List;

/**
 * API-MAP-001 지도 영역 맛집 조회의 Query Port다.
 * dependency-rules.md 7절 읽기 모델 예외에 따라 Restaurant가 소유한 읽기 전용 Projection만 제공한다.
 */
public interface RestaurantMapPointsQueryPort {

    /**
     * 이름·id 오름차순으로 최대 fetchLimit건을 반환한다. 호출자는 fetchLimit을
     * 상한(200)보다 1 크게 넘겨 초과 여부를 판정한다.
     */
    List<RestaurantMapPointRow> findWithinBounds(RestaurantMapPointsCriteria criteria, int fetchLimit);
}

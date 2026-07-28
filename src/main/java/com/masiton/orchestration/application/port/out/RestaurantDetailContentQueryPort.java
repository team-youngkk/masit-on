package com.masiton.orchestration.application.port.out;

import java.util.List;
import java.util.UUID;

/**
 * query-composition.md 1절이 지정한 콘텐츠 Query Port다. 공개·유효 Visit를 기준으로
 * Creator·Video 표시 정보를 가져오는 읽기 Projection이며, restaurant·creator·video·visit
 * 상태 조건(BR-VISIT-005)은 이 Port 구현이 DB에서 먼저 적용한다.
 */
public interface RestaurantDetailContentQueryPort {

    /**
     * @param restaurantId 콘텐츠 조회 기준 Restaurant 식별자
     * @return 공개·유효 방문 관계 한 건당 한 Row. Creator·Video ID 기준 중복이 있을 수 있으며
     *         Application이 중복 제거와 정렬을 수행한다.
     */
    List<VisitContentRow> findValidVisitContentRowsByRestaurantId(UUID restaurantId);
}

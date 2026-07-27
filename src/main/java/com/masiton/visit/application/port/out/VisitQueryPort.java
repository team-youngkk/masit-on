package com.masiton.visit.application.port.out;

import java.util.List;
import java.util.UUID;

/**
 * Application이 Visit 기반 공개·유효 조회에 요구하는 계약이다.
 * Infrastructure Adapter가 구현하며, restaurant·creator·video 상태 조건(BR-VISIT-005)은
 * 이 Port 구현이 DB에서 먼저 적용한다(query-composition.md 5절).
 */
public interface VisitQueryPort {

    /**
     * @param creatorId 필터 기준 Creator 식별자
     * @return 공개·유효 방문 관계로 연결된 Restaurant ID 목록. 중복이 있어도 무방하며
     *         Application이 최종 중복 제거를 보장한다.
     */
    List<UUID> findDistinctValidRestaurantIdsByCreatorId(UUID creatorId);

    /**
     * @param restaurantId 콘텐츠 조회 기준 Restaurant 식별자
     * @return 공개·유효 방문 관계 한 건당 한 Row. Creator·Video ID 기준 중복이 있을 수 있으며
     *         Application이 중복 제거와 정렬을 수행한다.
     */
    List<VisitContentRow> findValidVisitContentRowsByRestaurantId(UUID restaurantId);
}

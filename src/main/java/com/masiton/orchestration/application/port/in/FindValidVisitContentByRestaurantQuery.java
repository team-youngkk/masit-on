package com.masiton.orchestration.application.port.in;

import java.util.UUID;

/**
 * BR-VISIT-004, BR-VISIT-005 근거 유스케이스다.
 * 맛집 상세의 방문 유튜버·관련 영상 콘텐츠를 API 계약(restaurant-detail-api.md)의
 * 정렬·중복 제거 규칙까지 적용해 반환한다. 맛집 기본 정보 조회는 이 Port 책임이 아니다.
 *
 * <p>query-composition.md 1절 결정: 맛집 상세 응답 조합은 orchestration의 전용 Application
 * Query 책임이며, Restaurant·Creator·Video·Visit 어느 도메인도 이 조합을 소유하지 않는다.
 */
public interface FindValidVisitContentByRestaurantQuery {

    /**
     * @param restaurantId 콘텐츠 조회 기준 Restaurant 식별자
     * @return 공개·유효 방문 관계에서 도출한 방문 유튜버·관련 영상 목록.
     *         관계가 없으면 두 목록 모두 빈 배열이다.
     */
    VisitContentResult findValidVisitContentByRestaurant(UUID restaurantId);
}

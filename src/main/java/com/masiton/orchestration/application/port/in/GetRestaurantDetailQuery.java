package com.masiton.orchestration.application.port.in;

import java.util.UUID;

import com.masiton.orchestration.application.query.RestaurantDetailResult;

/**
 * 맛집 상세 조회 유스케이스 계약이다. dependency-rules.md 3절: Controller는 Application
 * 입력 Port만 호출하고 구현 클래스({@code RestaurantDetailQueryService})에 직접 의존하지 않는다.
 */
public interface GetRestaurantDetailQuery {

    /**
     * @param restaurantId 상세 조회 기준 Restaurant 식별자
     * @return 공개 기본 정보와 방문 콘텐츠 상태를 조합한 결과. 맛집이 없거나 비공개·삭제면
     *         {@code RESTAURANT_NOT_FOUND} 코드의 예외를 던진다.
     */
    RestaurantDetailResult getRestaurantDetail(UUID restaurantId);
}

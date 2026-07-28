package com.masiton.restaurant.application.port.out;

import java.util.List;
import java.util.UUID;

/**
 * dependency-rules.md 7절 읽기 모델 예외에 따른 Query Port다.
 * 구현체는 자기 소유 테이블(restaurant, region, food_category)뿐 아니라 읽기 전용으로
 * 다른 도메인 소유 테이블(visit, creator, video)도 조회할 수 있다. Application은 이
 * 인터페이스가 반환하는 순수 값 타입만 알고 JPA·SQL 구현 방식은 알지 못한다.
 */
public interface RestaurantSearchQueryPort {

    RestaurantSearchQueryResult search(RestaurantSearchCriteria criteria);

    /**
     * 한 페이지 분량의 restaurantId에 대해 배치로 방문 유튜버를 조회한다(N+1 금지).
     * 정렬·상위 3명 제한·remainingVisitedByCount 계산은 Application이 수행한다.
     */
    List<VisitedByRow> findVisitedByRestaurantIds(List<UUID> restaurantIds);

    /**
     * creatorId 조건 검증용 읽기 전용 Projection이다.
     * 다른 도메인(creator)의 application.port.in을 직접 호출하지 않고, 이 Port가 이미
     * visit·creator·video 테이블을 읽기 전용으로 조회하는 것과 같은 방식으로 공개 여부만 확인한다.
     */
    boolean existsPublicCreator(UUID creatorId);
}

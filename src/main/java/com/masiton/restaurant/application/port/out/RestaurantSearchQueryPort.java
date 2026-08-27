package com.masiton.restaurant.application.port.out;

import java.util.List;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * dependency-rules.md 7절 읽기 모델 예외에 따른 Query Port다.
 * 구현체는 자기 소유 테이블(restaurant, region, food_category)뿐 아니라 읽기 전용으로
 * 다른 도메인 소유 테이블(visit, creator, video)도 조회할 수 있다. Application은 이
 * 인터페이스가 반환하는 순수 값 타입만 알고 JPA·SQL 구현 방식은 알지 못한다.
 */
public interface RestaurantSearchQueryPort {

    RestaurantSearchQueryResult search(RestaurantSearchCriteria criteria);

    /** 공개·활성 맛집이 실제로 사용하는 지역·음식 종류를 정렬해 반환한다. */
    RestaurantFilterOptionNames findAvailableFilterOptions();

    /**
     * 한 페이지 분량의 restaurantId에 대해 배치로 방문 유튜버를 조회한다(N+1 금지).
     * 정렬·상위 3명 제한·remainingVisitedByCount 계산은 Application이 수행한다.
     */
    List<VisitedByRow> findVisitedByRestaurantIds(List<UUID> restaurantIds);

    /** 요청된 태그 코드 중 현재 검색에 허용되는 ACTIVE 정의를 반환한다. */
    Set<String> findActiveTagCodes(Collection<String> tagCodes);
}

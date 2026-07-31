package com.masiton.orchestration.application.port.out;

import java.util.List;

/**
 * RestaurantSearchQueryResult(restaurant 모듈)와 같은 형태로 페이지 한 조회분의 Row와 조건에
 * 일치하는 전체 개수를 함께 담는다.
 */
public record CreatorVisitedRestaurantPageResult(List<CreatorVisitedRestaurantRow> rows, long totalElements) {
}

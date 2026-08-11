package com.masiton.restaurant.presentation.rest;

import java.util.List;

/**
 * API-DISCOVERY-COURSE-001 요청 본문. 식별자는 불투명 문자열로 받는다.
 * 근거: docs/05-specs/api/common/identifier-contract.md
 */
public record RestaurantCourseRouteRequest(List<String> restaurantIds) {
}

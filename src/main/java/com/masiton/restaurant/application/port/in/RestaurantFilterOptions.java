package com.masiton.restaurant.application.port.in;

import java.util.List;

/**
 * 공개·활성 맛집이 실제로 사용하는 구조화 필터 선택지다.
 */
public record RestaurantFilterOptions(
        List<String> districts,
        List<String> categories) {
}

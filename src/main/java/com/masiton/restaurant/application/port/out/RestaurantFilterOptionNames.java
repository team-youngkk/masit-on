package com.masiton.restaurant.application.port.out;

import java.util.List;

/**
 * 공개 맛집 필터 선택지 조회 결과다.
 */
public record RestaurantFilterOptionNames(
        List<String> districtNames,
        List<String> categoryNames) {
}

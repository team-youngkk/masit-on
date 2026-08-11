package com.masiton.restaurant.application.course;

import java.util.List;

import com.masiton.restaurant.domain.model.Restaurant;

/**
 * 코스 입력 검증 실패 시 문제가 된 선택 맛집을 식별하기 위한 안전한 상세 정보다.
 * 좌표와 내부 자원 상태는 포함하지 않는다.
 */
public record RestaurantCourseSelectionDetails(
        List<RestaurantCourseFailureDetails.SelectedRestaurant> selectedRestaurants) {

    public RestaurantCourseSelectionDetails {
        selectedRestaurants = List.copyOf(selectedRestaurants);
    }

    public static RestaurantCourseSelectionDetails of(Restaurant restaurant, int inputOrder) {
        return new RestaurantCourseSelectionDetails(List.of(
                new RestaurantCourseFailureDetails.SelectedRestaurant(
                        restaurant.getId().toString(), restaurant.getName(), inputOrder)));
    }
}

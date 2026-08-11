package com.masiton.restaurant.application.course;

import java.util.List;

import com.masiton.restaurant.domain.model.Restaurant;

/**
 * 코스 경로 실패 시 클라이언트가 재선택·재조회할 수 있도록 제공하는 안전한 컨텍스트다.
 * 좌표, 외부 원문 응답과 내부 예외 정보는 포함하지 않는다.
 */
public record RestaurantCourseFailureDetails(
        List<SelectedRestaurant> selectedRestaurants,
        String failureCategory,
        RetryGuidance retryGuidance) {

    public RestaurantCourseFailureDetails {
        selectedRestaurants = List.copyOf(selectedRestaurants);
    }

    public static RestaurantCourseFailureDetails of(List<Restaurant> restaurants, String failureCategory) {
        List<SelectedRestaurant> selected = new java.util.ArrayList<>(restaurants.size());
        for (int i = 0; i < restaurants.size(); i++) {
            Restaurant restaurant = restaurants.get(i);
            selected.add(new SelectedRestaurant(restaurant.getId().toString(), restaurant.getName(), i + 1));
        }
        return new RestaurantCourseFailureDetails(
                selected,
                failureCategory,
                new RetryGuidance("RESELECT_OR_RETRY", "선택 맛집을 바꾸거나 잠시 후 다시 조회해 주세요."));
    }

    public record SelectedRestaurant(String restaurantId, String name, int inputOrder) {
    }

    public record RetryGuidance(String action, String message) {
    }
}

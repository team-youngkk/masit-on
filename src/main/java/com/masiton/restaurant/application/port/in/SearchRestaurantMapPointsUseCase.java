package com.masiton.restaurant.application.port.in;

/** API-MAP-001 지도 영역 맛집 조회를 처리한다. */
public interface SearchRestaurantMapPointsUseCase {

    RestaurantMapPointsResult search(SearchRestaurantMapPointsCommand command);
}

package com.masiton.restaurant.application.port.in;

/**
 * API-DISCOVERY-001 맛집 목록 및 조건 검색 유스케이스다.
 */
public interface SearchRestaurantsUseCase {

    RestaurantSearchResult search(SearchRestaurantsCommand command);
}

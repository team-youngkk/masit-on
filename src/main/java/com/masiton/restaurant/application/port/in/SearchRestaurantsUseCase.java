package com.masiton.restaurant.application.port.in;

/**
 * API-DISCOVERY-001 맛집 목록 및 조건 검색 유스케이스다.
 */
public interface SearchRestaurantsUseCase {

    RestaurantSearchResult search(SearchRestaurantsCommand command);

    /** 공개·활성 맛집이 실제로 사용하는 지역·음식 종류 선택지를 조회한다. */
    RestaurantFilterOptions getFilterOptions();

    /**
     * 조회를 실행하지 않고 직접 지정 필터의 유효성만 검증한다.
     * 조회를 건너뛰는 경로에서도 BR-SEARCH-001~009 검증 결과가 같아야 하므로 분리했다.
     */
    void validateFilters(SearchRestaurantsCommand command);
}

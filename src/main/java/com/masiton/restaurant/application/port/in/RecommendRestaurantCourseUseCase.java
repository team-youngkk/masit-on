package com.masiton.restaurant.application.port.in;

public interface RecommendRestaurantCourseUseCase {

    RestaurantCourseResult recommend(RestaurantCourseCommand command);
}

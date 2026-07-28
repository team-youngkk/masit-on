package com.masiton.restaurant.application.port.out;

import java.util.UUID;

/**
 * district·category는 restaurant가 소유한 region·food_category 이름을 조인한 표시값이다.
 */
public record RestaurantSearchRow(UUID id, String name, String district, String category) {
}

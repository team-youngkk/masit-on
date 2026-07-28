package com.masiton.restaurant.application.port.out;

import java.util.List;

public record RestaurantSearchQueryResult(List<RestaurantSearchRow> rows, long totalElements) {
}

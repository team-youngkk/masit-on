package com.masiton.restaurant.application.port.in;

import java.util.List;
import java.util.UUID;

public record RestaurantCourseCommand(List<UUID> restaurantIds) {
}

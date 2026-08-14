package com.masiton.restaurant.application.port.out;

import java.math.BigDecimal;
import java.util.UUID;

public record CourseRouteWaypoint(UUID restaurantId, BigDecimal latitude, BigDecimal longitude) {
}

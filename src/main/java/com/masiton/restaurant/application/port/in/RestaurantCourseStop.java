package com.masiton.restaurant.application.port.in;

import java.util.UUID;

import com.masiton.restaurant.domain.course.CourseStopRole;

public record RestaurantCourseStop(int sequence, UUID restaurantId, String name, CourseStopRole role) {
}

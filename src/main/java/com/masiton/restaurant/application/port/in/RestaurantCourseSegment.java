package com.masiton.restaurant.application.port.in;

import java.util.List;
import java.util.UUID;

import com.masiton.restaurant.domain.course.CourseRouteShapeStatus;

/**
 * ADR-ROUTE-001 5.5절: {@code path}는 세그먼트당 최대 500개의 제공자 중립 WGS84 좌표를 담고,
 * 형상이 없으면 {@code shapeStatus}가 {@code MISSING}이며 {@code path}는 빈 목록이다.
 */
public record RestaurantCourseSegment(
        UUID fromRestaurantId,
        UUID toRestaurantId,
        int distanceMeters,
        int durationSeconds,
        CourseRouteShapeStatus shapeStatus,
        List<RestaurantCourseVertex> path) {

    public RestaurantCourseSegment {
        path = List.copyOf(path);
    }
}

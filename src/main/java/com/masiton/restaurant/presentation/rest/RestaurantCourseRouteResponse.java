package com.masiton.restaurant.presentation.rest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.masiton.restaurant.application.port.in.RestaurantCourseResult;
import com.masiton.restaurant.application.port.in.RestaurantCourseSegment;
import com.masiton.restaurant.application.port.in.RestaurantCourseStop;
import com.masiton.restaurant.application.port.in.RestaurantCourseVertex;

/**
 * API-DISCOVERY-COURSE-001 성공 응답. 필드명·순서는 API 문서 4절 Success Response를 그대로 따른다.
 * {@code restaurants[].coordinate}와 {@code segments[].path}는 지도 마커·실제 경로 선 표시를 위한
 * 의도적 예외로만 좌표를 노출하며, 그 밖의 좌표(Kakao 원문 응답, API Key, 지도 뷰포트 등)는 포함하지
 * 않는다(NFR-PRIVACY-006). 근거: docs/05-specs/api/discovery/restaurant-course-recommendation-api.md 4절
 */
public record RestaurantCourseRouteResponse(
        String status,
        List<Stop> restaurants,
        List<Segment> segments,
        int totalDistanceMeters,
        int totalDurationSeconds,
        OffsetDateTime generatedAt,
        OffsetDateTime expiresAt) {

    private static final String SUCCEEDED = "SUCCEEDED";

    public record Stop(int sequence, String restaurantId, String name, String role, Coordinate coordinate) {
    }

    public record Segment(
            String fromRestaurantId,
            String toRestaurantId,
            int distanceMeters,
            int durationSeconds,
            String shapeStatus,
            List<Coordinate> path) {
    }

    public record Coordinate(BigDecimal latitude, BigDecimal longitude) {
    }

    public static RestaurantCourseRouteResponse from(RestaurantCourseResult result) {
        List<Stop> restaurants = result.restaurants().stream()
                .map(RestaurantCourseRouteResponse::stop)
                .toList();
        List<Segment> segments = result.segments().stream()
                .map(RestaurantCourseRouteResponse::segment)
                .toList();
        return new RestaurantCourseRouteResponse(
                SUCCEEDED,
                restaurants,
                segments,
                result.totalDistanceMeters(),
                result.totalDurationSeconds(),
                result.generatedAt(),
                result.expiresAt());
    }

    private static Stop stop(RestaurantCourseStop stop) {
        return new Stop(
                stop.sequence(),
                stop.restaurantId().toString(),
                stop.name(),
                stop.role().name(),
                new Coordinate(stop.latitude(), stop.longitude()));
    }

    private static Segment segment(RestaurantCourseSegment segment) {
        List<Coordinate> path = segment.path().stream()
                .map(RestaurantCourseRouteResponse::coordinate)
                .toList();
        return new Segment(
                segment.fromRestaurantId().toString(),
                segment.toRestaurantId().toString(),
                segment.distanceMeters(),
                segment.durationSeconds(),
                segment.shapeStatus().name(),
                path);
    }

    private static Coordinate coordinate(RestaurantCourseVertex vertex) {
        return new Coordinate(BigDecimal.valueOf(vertex.latitude()), BigDecimal.valueOf(vertex.longitude()));
    }
}

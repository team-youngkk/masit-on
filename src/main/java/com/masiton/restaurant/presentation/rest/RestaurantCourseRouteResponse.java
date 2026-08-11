package com.masiton.restaurant.presentation.rest;

import java.time.OffsetDateTime;
import java.util.List;

import com.masiton.restaurant.application.port.in.RestaurantCourseResult;
import com.masiton.restaurant.application.port.in.RestaurantCourseSegment;
import com.masiton.restaurant.application.port.in.RestaurantCourseStop;

/**
 * API-DISCOVERY-COURSE-001 성공 응답. 필드명·순서는 API 문서 4절 Success Response를 그대로 따른다.
 * 좌표, Kakao 원문 응답, API Key는 포함하지 않는다(NFR-PRIVACY-006).
 * 근거: docs/05-specs/api/discovery/restaurant-course-recommendation-api.md 4절
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

    public record Stop(int sequence, String restaurantId, String name, String role) {
    }

    public record Segment(String fromRestaurantId, String toRestaurantId, int distanceMeters, int durationSeconds) {
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
        return new Stop(stop.sequence(), stop.restaurantId().toString(), stop.name(), stop.role().name());
    }

    private static Segment segment(RestaurantCourseSegment segment) {
        return new Segment(
                segment.fromRestaurantId().toString(),
                segment.toRestaurantId().toString(),
                segment.distanceMeters(),
                segment.durationSeconds());
    }
}

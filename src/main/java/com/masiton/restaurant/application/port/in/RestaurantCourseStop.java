package com.masiton.restaurant.application.port.in;

import java.math.BigDecimal;
import java.util.UUID;

import com.masiton.restaurant.domain.course.CourseStopRole;

/**
 * {@code latitude}·{@code longitude}는 지도 마커 표시를 위한 WGS84 좌표다(API-DISCOVERY-COURSE-001
 * 4절 {@code restaurants[].coordinate}). {@link RestaurantMapPointSummary}가 이미 쓰는 방식과 같이
 * 이 계층에서는 좌표를 평평한 필드로 두고, 중첩 객체 변환은 presentation DTO에서 한다.
 */
public record RestaurantCourseStop(
        int sequence, UUID restaurantId, String name, CourseStopRole role, BigDecimal latitude, BigDecimal longitude) {
}

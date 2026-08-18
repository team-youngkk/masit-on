package com.masiton.restaurant.application.port.out;

import java.util.List;

import com.masiton.restaurant.domain.course.CourseRouteShapeStatus;

/**
 * ADR-ROUTE-001 5.5절: 형상 좌표는 세그먼트(구간)당 최대 500개이며, 형상이 전혀 없으면
 * {@code shapeStatus}가 {@code MISSING}이고 {@code path}는 빈 목록이다.
 */
public record CourseRouteLeg(
        int distanceMeters, int durationSeconds, CourseRouteShapeStatus shapeStatus, List<CourseRouteVertex> path) {

    public CourseRouteLeg {
        path = List.copyOf(path);
    }

    /**
     * 형상 정보가 없는 leg를 위한 편의 생성자다. 형상 상한·저하와 무관한 기존 호출부(거리·시간만
     * 다루는 시나리오)를 그대로 유지하기 위해 {@code shapeStatus}를 {@code MISSING}, {@code path}를
     * 빈 목록으로 채운다.
     */
    public CourseRouteLeg(int distanceMeters, int durationSeconds) {
        this(distanceMeters, durationSeconds, CourseRouteShapeStatus.MISSING, List.of());
    }
}

package com.masiton.restaurant.domain.course;

/**
 * 코스 구간의 실제 경로 형상 제공 여부다.
 * 근거: ADR-ROUTE-001 5.5절, API-DISCOVERY-COURSE-001 "경로 형상 상한과 저하" 절.
 * 거리·시간 계산은 정상이지만 형상 좌표만 없는 경우 {@code MISSING}이며, 이때도 코스 결과 전체는
 * {@code SUCCEEDED}로 유지한다(구간 거리·시간 계산 자체 실패와는 별개다).
 */
public enum CourseRouteShapeStatus {
    AVAILABLE,
    MISSING
}

package com.masiton.restaurant.application.port.out;

/**
 * Route Provider Adapter가 보고하는 외부 경로 계산 실패 범주다.
 * 근거: ADR-ROUTE-001 5.2절, API-DISCOVERY-COURSE-001 6절.
 */
public enum CourseRouteFailureCategory {
    TIMEOUT,
    RATE_LIMIT,
    SERVICE_RATE_LIMIT,
    UPSTREAM,
    SCHEMA,
    PARTIAL,
    PROVIDER_BLOCKED
}

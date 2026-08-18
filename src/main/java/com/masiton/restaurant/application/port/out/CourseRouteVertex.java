package com.masiton.restaurant.application.port.out;

/**
 * Route Provider Adapter가 정규화한 제공자 중립 WGS84 좌표 한 점이다.
 * 근거: ADR-ROUTE-001 5.2절·5.5절. 좌표 순서는 항상 위도, 경도이며 Kakao 원문의 경도·위도 교차 배열
 * 순서를 그대로 노출하지 않는다.
 */
public record CourseRouteVertex(double latitude, double longitude) {
}

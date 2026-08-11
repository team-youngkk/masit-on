package com.masiton.restaurant.application.port.out;

import java.util.List;

/**
 * 방문 순서대로 담긴 경로 계산 요청이다.
 * 첫 원소가 출발지, 마지막이 도착지, 중간이 waypoints다.
 */
public record CourseRouteRequest(List<CourseRouteWaypoint> stops) {

    public CourseRouteRequest {
        stops = List.copyOf(stops);
    }
}

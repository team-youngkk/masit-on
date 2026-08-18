package com.masiton.restaurant.application.port.in;

/**
 * 코스 구간의 실제 경로 형상을 이루는 WGS84 좌표 한 점이다. {@code application.port.out}의
 * {@code CourseRouteVertex}와 형태는 같지만, Port 경계를 넘나드는 결합을 피하기 위해 별도 타입으로 둔다.
 */
public record RestaurantCourseVertex(double latitude, double longitude) {
}

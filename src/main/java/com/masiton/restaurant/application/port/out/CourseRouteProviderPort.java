package com.masiton.restaurant.application.port.out;

/**
 * 외부 자동차 경로 제공자(Kakao Mobility) 호출을 격리하는 출력 Port다.
 * ADR-ROUTE-001 5.2절: Application은 이 Port만 호출하고 SDK/HTTP client를 직접 사용하지 않는다.
 */
public interface CourseRouteProviderPort {

    /**
     * 실패 시 {@link CourseRouteProviderException}을 던진다. 구현체는 추정값을 반환하지 않는다.
     */
    CourseRouteResult calculate(CourseRouteRequest request);
}

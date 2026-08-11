package com.masiton.restaurant.application.port.out;

/** Redis 등 quota 저장소를 사용할 수 없어 외부 호출을 안전하게 중단할 때 사용한다. */
public final class CourseRouteQuotaUnavailableException extends RuntimeException {

    public CourseRouteQuotaUnavailableException(Throwable cause) {
        super("Course route quota store is unavailable", cause);
    }
}

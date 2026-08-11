package com.masiton.restaurant.application.port.out;

/**
 * 외부 경로 제공자 호출이 실패했을 때 Adapter가 던지는 예외다.
 * NFR-OBSERVABILITY-005: 메시지에는 category 이름만 담고, 좌표·외부 응답 본문·API Key를 포함하지 않는다.
 */
public class CourseRouteProviderException extends RuntimeException {

    private final CourseRouteFailureCategory category;

    public CourseRouteProviderException(CourseRouteFailureCategory category) {
        super(category.name());
        this.category = category;
    }

    public CourseRouteProviderException(CourseRouteFailureCategory category, Throwable cause) {
        super(category.name(), cause);
        this.category = category;
    }

    public CourseRouteFailureCategory category() {
        return category;
    }
}

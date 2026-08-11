package com.masiton.restaurant.application.course;

import org.springframework.http.HttpStatus;

import com.masiton.common.web.BusinessException;

/**
 * 근거: docs/05-specs/api/discovery/restaurant-course-recommendation-api.md 6절.
 * 메시지에는 맛집 이름·좌표·식별자를 담지 않는다.
 */
public final class RestaurantCourseException extends BusinessException {

    private RestaurantCourseException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    private RestaurantCourseException(HttpStatus status, String code, String message, Object details) {
        super(status, code, message, details);
    }

    public static RestaurantCourseException invalidCourseSize() {
        return new RestaurantCourseException(
                HttpStatus.BAD_REQUEST, "INVALID_COURSE_SIZE", "코스는 맛집을 2개 이상 5개 이하로 선택해야 합니다.");
    }

    public static RestaurantCourseException duplicateRestaurant() {
        return new RestaurantCourseException(
                HttpStatus.BAD_REQUEST, "DUPLICATE_RESTAURANT_IN_COURSE", "동일한 맛집을 코스에 중복해서 선택할 수 없습니다.");
    }

    public static RestaurantCourseException restaurantNotFound() {
        return new RestaurantCourseException(HttpStatus.NOT_FOUND, "RESTAURANT_NOT_FOUND", "선택한 맛집을 찾을 수 없습니다.");
    }

    public static RestaurantCourseException restaurantNotPublic(RestaurantCourseSelectionDetails details) {
        return new RestaurantCourseException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "RESTAURANT_NOT_PUBLIC",
                "공개 상태가 아닌 맛집은 코스에 포함할 수 없습니다.",
                details);
    }

    public static RestaurantCourseException coordinateRequired(RestaurantCourseSelectionDetails details) {
        return new RestaurantCourseException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "RESTAURANT_COORDINATE_REQUIRED",
                "좌표가 없거나 올바르지 않은 맛집은 코스에 포함할 수 없습니다.",
                details);
    }

    public static RestaurantCourseException distanceLimitExceeded() {
        return new RestaurantCourseException(
                HttpStatus.UNPROCESSABLE_ENTITY, "COURSE_DISTANCE_LIMIT_EXCEEDED", "코스 전체 이동 거리가 상한을 초과했습니다.");
    }

    public static RestaurantCourseException routePartialFailure(RestaurantCourseFailureDetails details) {
        return new RestaurantCourseException(
                HttpStatus.BAD_GATEWAY, "COURSE_ROUTE_PARTIAL_FAILURE", "일부 구간의 경로 계산에 실패했습니다.", details);
    }

    public static RestaurantCourseException routeProviderUnavailable(RestaurantCourseFailureDetails details) {
        return new RestaurantCourseException(
                HttpStatus.BAD_GATEWAY, "COURSE_ROUTE_PROVIDER_UNAVAILABLE", "경로 계산 서비스를 일시적으로 사용할 수 없습니다.", details);
    }

    public static RestaurantCourseException routeRateLimited(RestaurantCourseFailureDetails details) {
        return new RestaurantCourseException(
                HttpStatus.TOO_MANY_REQUESTS,
                "COURSE_ROUTE_RATE_LIMITED",
                "코스 경로 요청 제한에 도달했습니다.",
                details);
    }
}

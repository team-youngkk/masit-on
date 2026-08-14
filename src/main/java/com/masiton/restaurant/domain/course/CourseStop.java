package com.masiton.restaurant.domain.course;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 코스 순서 계산에 쓰이는 방문지 한 건이다.
 * ADR-ROUTE-001 5.3절: 좌표 직선거리 최근접 이웃 정렬의 입력 단위다.
 */
public record CourseStop(UUID restaurantId, String name, BigDecimal latitude, BigDecimal longitude) {

    public CourseStop {
        if (restaurantId == null) {
            throw new IllegalArgumentException("restaurantId는 null일 수 없습니다.");
        }
        if (name == null) {
            throw new IllegalArgumentException("name은 null일 수 없습니다.");
        }
        if (latitude == null) {
            throw new IllegalArgumentException("latitude는 null일 수 없습니다.");
        }
        if (longitude == null) {
            throw new IllegalArgumentException("longitude는 null일 수 없습니다.");
        }
    }
}

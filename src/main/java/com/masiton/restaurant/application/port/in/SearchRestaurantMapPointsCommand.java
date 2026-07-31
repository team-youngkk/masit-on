package com.masiton.restaurant.application.port.in;

import java.math.BigDecimal;

/**
 * 이미 형식이 유효한 좌표·클라이언트 주소만 전달한다. 범위·순서·필터 값 검증은 Application이 수행한다.
 */
public record SearchRestaurantMapPointsCommand(
        BigDecimal south,
        BigDecimal west,
        BigDecimal north,
        BigDecimal east,
        String query,
        String district,
        String category,
        String creatorId,
        String clientAddress) {
}

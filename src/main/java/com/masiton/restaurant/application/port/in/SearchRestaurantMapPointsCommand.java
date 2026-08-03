package com.masiton.restaurant.application.port.in;

/**
 * 이미 형식이 유효한 클라이언트 주소만 전달한다. 필터 값 검증은 Application이 수행한다.
 */
public record SearchRestaurantMapPointsCommand(
        String query,
        String district,
        String category,
        String creatorId,
        String clientAddress) {
}

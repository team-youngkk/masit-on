package com.masiton.orchestration.application.query;

import java.util.UUID;

/**
 * 공개 맛집 기본 정보 Projection이다. JPA Entity나 Domain Aggregate가 아니다.
 */
public record RestaurantDetailBase(
        UUID id,
        String name,
        String categoryName,
        String roadAddress,
        String detailAddress,
        String phoneNumber,
        String kakaoPlaceUrl
) {
}

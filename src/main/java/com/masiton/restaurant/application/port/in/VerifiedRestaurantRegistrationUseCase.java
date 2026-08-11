package com.masiton.restaurant.application.port.in;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 외부 검증과 자동 정책 검증을 끝낸 후보를 정식 Restaurant로 등록하는 내부 Port다.
 * 관리자 확인 Token을 사용하는 수동 등록 경로와 분리한다.
 */
public interface VerifiedRestaurantRegistrationUseCase {

    RegistrationResult register(VerifiedRestaurantCommand command);

    record VerifiedRestaurantCommand(
            UUID regionId,
            UUID foodCategoryId,
            String name,
            String kakaoPlaceId,
            String kakaoPlaceUrl,
            String roadAddress,
            String detailAddress,
            String phoneNumber,
            BigDecimal latitude,
            BigDecimal longitude) {
    }

    record RegistrationResult(UUID restaurantId, boolean created) {
    }
}

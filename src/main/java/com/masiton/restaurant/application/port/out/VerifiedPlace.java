package com.masiton.restaurant.application.port.out;

import java.math.BigDecimal;

/**
 * 외부 제공자 응답에서 등록에 필요한 정보만 추린 내부 장소 확인 결과다.
 * latitude·longitude는 제공자가 유효 좌표를 반환한 경우에만 값을 가지며, 둘 중 하나만 있을 수는 없다.
 */
public record VerifiedPlace(
        String identityKey,
        String name,
        String kakaoPlaceUrl,
        String roadAddress,
        String phoneNumber,
        BigDecimal latitude,
        BigDecimal longitude
) {
}

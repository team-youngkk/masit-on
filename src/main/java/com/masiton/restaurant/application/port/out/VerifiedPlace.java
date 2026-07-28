package com.masiton.restaurant.application.port.out;

/**
 * 외부 제공자 응답에서 등록에 필요한 정보만 추린 내부 장소 확인 결과다.
 */
public record VerifiedPlace(
        String identityKey,
        String name,
        String kakaoPlaceUrl,
        String roadAddress,
        String phoneNumber
) {
}

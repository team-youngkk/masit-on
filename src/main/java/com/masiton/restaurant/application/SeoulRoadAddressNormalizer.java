package com.masiton.restaurant.application;

/** Kakao 축약 시도명과 관리자 주소 힌트를 도메인의 서울특별시 표기로 맞춘다. */
public final class SeoulRoadAddressNormalizer {

    private SeoulRoadAddressNormalizer() {
    }

    public static String normalize(String roadAddress) {
        String normalized = roadAddress.strip();
        if (normalized.startsWith("서울특별시")) {
            return normalized;
        }
        if (normalized.startsWith("서울 ")) {
            return "서울특별시 " + normalized.substring("서울 ".length()).strip();
        }
        return normalized;
    }
}

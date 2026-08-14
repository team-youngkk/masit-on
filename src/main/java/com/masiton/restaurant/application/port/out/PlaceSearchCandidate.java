package com.masiton.restaurant.application.port.out;

/**
 * 상호명 검색 한 문서를 Adapter가 정리한 결과다. 도로명주소나 장소 링크가 없는 문서는
 * Adapter가 이 형태로 만들지 않고 걸러낸다. phoneNumber는 제공자 응답에 없으면 null이다.
 */
public record PlaceSearchCandidate(
        String placeName,
        String kakaoPlaceUrl,
        String roadAddress,
        String phoneNumber
) {
}

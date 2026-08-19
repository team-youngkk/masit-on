package com.masiton.restaurant.application.port.out;

/**
 * 상호명 검색 한 문서를 Adapter가 정리한 결과다. 도로명주소나 장소 링크가 없는 문서는
 * Adapter가 이 형태로 만들지 않고 걸러낸다. phoneNumber와 placeCategory는 제공자 응답에
 * 없으면 null이다. placeCategory는 Kakao 문서의 {@code category_name}(예: "음식점 > 한식 > 냉면")
 * 원문이며, {@code BR-AIEXTRACT-010} 1순위 근거로 {@code food_category_mapping}과 대조하는 데 쓴다.
 */
public record PlaceSearchCandidate(
        String placeName,
        String kakaoPlaceUrl,
        String roadAddress,
        String phoneNumber,
        String placeCategory
) {

    public PlaceSearchCandidate(String placeName, String kakaoPlaceUrl, String roadAddress, String phoneNumber) {
        this(placeName, kakaoPlaceUrl, roadAddress, phoneNumber, null);
    }
}

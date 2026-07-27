package com.masiton.restaurant.application.port.out;

import java.net.URI;
import java.util.Optional;

/**
 * 관리자가 제출한 장소 링크가 가리키는 장소를 외부 기준정보로 확인한다.
 * 제공자 HTTP 계약은 Infrastructure Adapter에만 둔다.
 */
public interface PlaceVerificationPort {

    Optional<VerifiedPlace> verify(String restaurantName, URI kakaoPlaceUrl);
}

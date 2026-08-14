package com.masiton.restaurant.application.port.in;

import java.util.List;

/**
 * 관리자가 상호명·주소 힌트로 카카오 장소 후보를 조회한다. 자원을 만들거나 확인 Token을
 * 발급하지 않는다.
 */
public interface SearchAdminPlaceCandidatesUseCase {

    List<PlaceCandidateResult> search(SearchAdminPlaceCandidatesCommand command);

    record SearchAdminPlaceCandidatesCommand(String name, String roadAddressHint) {
    }

    record PlaceCandidateResult(
            String placeName,
            String kakaoPlaceUrl,
            String roadAddress,
            String phoneNumber,
            String district) {
    }
}

package com.masiton.orchestration.application.port.in;

/**
 * {@code BR-AIEXTRACT-009} 장소 동일성 자동 확정을 orchestration이 소유하는 공개 계약이다.
 * AI가 만든 상호명·주소 후보만으로 Kakao 장소 동일성을 판정하며 관리자 사전 승인을 요구하지 않는다.
 */
public interface ResolvePlaceIdentityUseCase {

    PlaceIdentityResult resolve(PlaceIdentityCommand command);

    /**
     * {@code restaurantName}은 AI가 추출한 등록 단위의 상호명 후보, {@code candidateAddress}는
     * 같은 등록 단위의 주소 후보다. 두 값 모두 Kakao 장소 식별자를 전제하지 않는다.
     */
    record PlaceIdentityCommand(String restaurantName, String candidateAddress) {
    }

    record PlaceIdentityResult(PlaceIdentityStatus status, ConfirmedPlace confirmedPlace) {

        public PlaceIdentityResult {
            if ((status == PlaceIdentityStatus.CONFIRMED) != (confirmedPlace != null)) {
                throw new IllegalArgumentException(
                        "confirmedPlace must be present only when status is CONFIRMED.");
            }
        }

        public static PlaceIdentityResult confirmed(ConfirmedPlace confirmedPlace) {
            return new PlaceIdentityResult(PlaceIdentityStatus.CONFIRMED, confirmedPlace);
        }

        public static PlaceIdentityResult notFound() {
            return new PlaceIdentityResult(PlaceIdentityStatus.PLACE_NOT_FOUND, null);
        }

        public static PlaceIdentityResult ambiguous() {
            return new PlaceIdentityResult(PlaceIdentityStatus.PLACE_AMBIGUOUS, null);
        }

        public boolean isConfirmed() {
            return status == PlaceIdentityStatus.CONFIRMED;
        }
    }

    /** {@code ai_registration_unit.block_reason}의 장소 사유 두 값과 확정 상태를 함께 표현한다. */
    enum PlaceIdentityStatus {
        CONFIRMED,
        PLACE_NOT_FOUND,
        PLACE_AMBIGUOUS
    }

    /**
     * 자동 확정한 Kakao 장소다. {@code placeCategory}는 Kakao 원문 분류 표현이며
     * {@code BR-AIEXTRACT-010} 카테고리 판정의 1순위 입력으로 쓴다.
     */
    record ConfirmedPlace(String kakaoPlaceUrl, String roadAddress, String matchedBy, String placeCategory) {
    }
}

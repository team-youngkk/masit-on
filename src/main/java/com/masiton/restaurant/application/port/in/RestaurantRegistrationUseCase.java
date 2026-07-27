package com.masiton.restaurant.application.port.in;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 관리자 맛집 등록의 외부 검증 미리보기와 확인 Token 기반 확정을 제공한다.
 */
public interface RestaurantRegistrationUseCase {

    RestaurantPreviewResult preview(RestaurantPreviewCommand command);

    RestaurantCreationResult create(RestaurantCreateCommand command);

    record RestaurantPreviewCommand(
            UUID adminAccountId,
            String name,
            String kakaoPlaceUrl,
            String roadAddress,
            String detailAddress,
            String phoneNumber,
            String category) {
    }

    record RestaurantCreateCommand(UUID adminAccountId, String confirmationToken) {
    }

    record RestaurantPreviewResult(
            Decision decision,
            String confirmationToken,
            OffsetDateTime expiresAt,
            RestaurantCandidate candidate,
            ExistingRestaurant existingResource) {

        public enum Decision {
            READY,
            DUPLICATE,
            REVIEW_REQUIRED
        }
    }

    record RestaurantCreationResult(RestaurantCandidate restaurant, boolean created, boolean duplicate) {
    }

    record RestaurantCandidate(
            UUID id,
            String name,
            String district,
            String category,
            String roadAddress,
            String detailAddress,
            String phoneNumber,
            String kakaoPlaceUrl) {
    }

    record ExistingRestaurant(UUID id, String name, String roadAddress) {
    }
}

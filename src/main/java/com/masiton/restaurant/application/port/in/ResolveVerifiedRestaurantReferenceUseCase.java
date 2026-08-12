package com.masiton.restaurant.application.port.in;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;

/** AI 후보 검증에 공개하는 Restaurant의 외부 장소·기준정보 최소 Snapshot 계약이다. */
public interface ResolveVerifiedRestaurantReferenceUseCase {

    Optional<VerifiedRestaurantReference> resolve(String restaurantName, String candidateAddress,
                                                   URI kakaoPlaceUrl, String menuExpression);

    record VerifiedRestaurantReference(
            UUID regionId,
            UUID foodCategoryId,
            String name,
            String kakaoPlaceId,
            String kakaoPlaceUrl,
            String roadAddress,
            String phoneNumber,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }
}

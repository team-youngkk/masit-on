package com.masiton.orchestration.application.port.in;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 자동 검증을 모두 끝낸 영상 후보를 정식 콘텐츠 관계로 확정하는 내부 입력 Port다.
 * 관리자 사전 승인이나 확인 Token을 요구하지 않는다.
 */
public interface AutoRegisterVerifiedContentUseCase {

    RegistrationResult register(VerifiedContentCommand command);

    record VerifiedContentCommand(
            RestaurantCandidate restaurant,
            CreatorCandidate creator,
            VideoCandidate video,
            boolean visitEvidenceConfirmed) {
    }

    record RestaurantCandidate(
            UUID regionId,
            UUID foodCategoryId,
            String name,
            String kakaoPlaceId,
            String kakaoPlaceUrl,
            String roadAddress,
            String detailAddress,
            String phoneNumber,
            BigDecimal latitude,
            BigDecimal longitude) {
    }

    record CreatorCandidate(
            String externalChannelId,
            String channelName,
            String channelUrl) {
    }

    record VideoCandidate(
            String externalVideoId,
            String publisherExternalChannelId,
            String title,
            String sourceUrl,
            String thumbnailUrl,
            OffsetDateTime publishedAt,
            OffsetDateTime checkedAt) {
    }

    record RegistrationResult(
            UUID restaurantId,
            UUID creatorId,
            UUID videoId,
            UUID visitId,
            boolean restaurantCreated,
            boolean creatorCreated,
            boolean videoCreated,
            boolean visitCreated) {
    }
}

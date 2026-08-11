package com.masiton.orchestration.application.port.in;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** AI 후보의 외부 기준정보 검증 순서를 orchestration이 소유하는 공개 계약이다. */
public interface VerifyAiContentCandidateUseCase {

    Optional<VerifiedContent> verify(VerificationCommand command);

    record VerificationCommand(
            String channelId,
            String videoId,
            URI videoUrl,
            String restaurantName,
            String candidateAddress,
            URI kakaoPlaceUrl,
            String menuExpression
    ) {
    }

    record VerifiedContent(
            UUID regionId,
            UUID foodCategoryId,
            String restaurantName,
            String kakaoPlaceId,
            String kakaoPlaceUrl,
            String roadAddress,
            String phoneNumber,
            BigDecimal latitude,
            BigDecimal longitude,
            String channelId,
            String channelName,
            String channelUrl,
            String videoId,
            String videoTitle,
            String videoSourceUrl,
            String videoThumbnailUrl,
            OffsetDateTime publishedAt,
            OffsetDateTime checkedAt
    ) {
    }
}

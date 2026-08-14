package com.masiton.orchestration.application.port.in;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;

/** AI 후보의 외부 기준정보 검증 순서를 orchestration이 소유하는 공개 계약이다. */
public interface VerifyAiContentCandidateUseCase {

    VerificationResult verify(VerificationCommand command);

    record VerificationResult(VerifiedContent content, String failureReason) {

        public VerificationResult {
            if ((content == null) == (failureReason == null)) {
                throw new IllegalArgumentException("Verification result must contain either content or a failure reason.");
            }
        }

        public static VerificationResult verified(VerifiedContent content) {
            return new VerificationResult(content, null);
        }

        public static VerificationResult blocked(String failureReason) {
            return new VerificationResult(null, failureReason);
        }

        public boolean isVerified() {
            return content != null;
        }
    }

    record VerificationCommand(
            String channelId,
            String videoId,
            URI videoUrl,
            String restaurantName,
            String candidateAddress,
            URI kakaoPlaceUrl,
            String menuExpression,
            VisitEvidenceCandidate visitEvidence
    ) {
    }

    record VisitEvidenceCandidate(String value, double confidence, Evidence evidence) {
    }

    record Evidence(EvidenceType type, Long startMs, Long endMs, Long startOffset, Long endOffset,
                    String sourceHash) {
    }

    enum EvidenceType {
        TIMESTAMP,
        TEXT_RANGE,
        UNKNOWN
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

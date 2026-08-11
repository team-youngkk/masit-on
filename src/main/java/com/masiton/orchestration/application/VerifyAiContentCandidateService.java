package com.masiton.orchestration.application;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.masiton.orchestration.application.port.in.VerifyAiContentCandidateUseCase;
import com.masiton.restaurant.application.port.in.ResolveVerifiedRestaurantReferenceUseCase;
import com.masiton.video.application.port.in.ResolveVerifiedVideoUseCase;
import com.masiton.video.application.port.out.VerifiedVideo;

/** Kakao·YouTube 검증 순서와 교차 도메인 최소 Snapshot 조합을 소유한다. */
@Service
class VerifyAiContentCandidateService implements VerifyAiContentCandidateUseCase {

    /**
     * Candidate value is a short structured claim, not a source transcript. Requiring a complete
     * normalized claim prevents a positive substring from turning a negation or question into proof.
     */
    private static final Set<String> EXPLICIT_ACTUAL_VISIT_CLAIMS = Set.of(
            "방문함", "방문했음", "방문했다", "방문했습니다",
            "직접방문", "직접방문함", "직접방문했음", "직접방문했다", "직접방문했습니다",
            "제가방문했다", "제가방문했습니다", "제가직접방문했다", "제가직접방문했습니다",
            "저희가방문했다", "저희가방문했습니다", "다녀옴", "다녀왔다", "다녀왔습니다",
            "직접다녀옴", "직접다녀왔다", "직접다녀왔습니다", "찾아갔다", "찾아갔습니다",
            "직접찾아갔다", "직접찾아갔습니다", "들렀다", "들렀습니다", "직접들렀다",
            "직접들렀습니다", "먹어봤다", "먹어봤습니다", "visited", "ivisited",
            "directlyvisited", "atehere", "wentthere", "stoppedby");

    private final ResolveVerifiedRestaurantReferenceUseCase restaurantReference;
    private final ResolveVerifiedVideoUseCase videoVerification;

    VerifyAiContentCandidateService(ResolveVerifiedRestaurantReferenceUseCase restaurantReference,
                                    ResolveVerifiedVideoUseCase videoVerification) {
        this.restaurantReference = restaurantReference;
        this.videoVerification = videoVerification;
    }

    @Override
    public Optional<VerifiedContent> verify(VerificationCommand command) {
        Optional<ResolveVerifiedRestaurantReferenceUseCase.VerifiedRestaurantReference> restaurant =
                restaurantReference.resolve(command.restaurantName(), command.candidateAddress(),
                        command.kakaoPlaceUrl(), command.menuExpression());
        if (restaurant.isEmpty()) {
            return Optional.empty();
        }
        Optional<VerifiedVideo> video = videoVerification.resolve(command.videoUrl());
        if (video.isEmpty() || !matchesVideo(command, video.get())) {
            return Optional.empty();
        }
        if (!confirmsActualVisit(command.visitEvidence())) {
            return Optional.empty();
        }
        VerifiedVideo verifiedVideo = video.get();
        var verifiedRestaurant = restaurant.get();
        return Optional.of(new VerifiedContent(
                verifiedRestaurant.regionId(), verifiedRestaurant.foodCategoryId(), verifiedRestaurant.name(),
                verifiedRestaurant.kakaoPlaceId(), verifiedRestaurant.kakaoPlaceUrl(),
                verifiedRestaurant.roadAddress(), verifiedRestaurant.phoneNumber(),
                verifiedRestaurant.latitude(), verifiedRestaurant.longitude(),
                verifiedVideo.publisherExternalChannelId(), verifiedVideo.channelName(),
                "https://www.youtube.com/channel/" + verifiedVideo.publisherExternalChannelId(),
                verifiedVideo.externalVideoId(), verifiedVideo.title(), verifiedVideo.sourceUrl(),
                verifiedVideo.thumbnailUrl(), verifiedVideo.publishedAt(), verifiedVideo.checkedAt(), true));
    }

    private boolean confirmsActualVisit(VisitEvidenceCandidate candidate) {
        if (candidate == null || !nonBlank(candidate.value()) || !Double.isFinite(candidate.confidence())
                || candidate.confidence() < 0 || candidate.confidence() > 1) {
            return false;
        }
        Evidence evidence = candidate.evidence();
        if (!validEvidence(evidence)) {
            return false;
        }

        String normalized = candidate.value().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        return EXPLICIT_ACTUAL_VISIT_CLAIMS.contains(normalized);
    }

    private boolean validEvidence(Evidence evidence) {
        if (evidence == null || evidence.type() == EvidenceType.UNKNOWN) {
            return false;
        }
        if (evidence.type() == EvidenceType.TIMESTAMP) {
            return validRange(evidence.startMs(), evidence.endMs());
        }
        return validRange(evidence.startOffset(), evidence.endOffset()) && nonBlank(evidence.sourceHash());
    }

    private boolean validRange(Long start, Long end) {
        return start != null && end != null && start >= 0 && end >= start;
    }

    private boolean matchesVideo(VerificationCommand command, VerifiedVideo video) {
        return command.videoId().equals(video.externalVideoId())
                && command.channelId().equals(video.publisherExternalChannelId())
                && nonBlank(video.channelName()) && nonBlank(video.title())
                && nonBlank(video.sourceUrl()) && nonBlank(video.thumbnailUrl());
    }

    private boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }
}

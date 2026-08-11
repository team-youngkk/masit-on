package com.masiton.orchestration.application;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.masiton.orchestration.application.port.in.VerifyAiContentCandidateUseCase;
import com.masiton.restaurant.application.port.in.ResolveVerifiedRestaurantReferenceUseCase;
import com.masiton.video.application.port.in.ResolveVerifiedVideoUseCase;
import com.masiton.video.application.port.out.VerifiedVideo;

/** Kakao·YouTube 검증 순서와 교차 도메인 최소 Snapshot 조합을 소유한다. */
@Service
class VerifyAiContentCandidateService implements VerifyAiContentCandidateUseCase {

    private static final Pattern EXPLICIT_ACTUAL_VISIT = Pattern.compile(
            "(직접방문|직접다녀|직접찾아|직접들러|방문(함|했|했다|한|하여|해서)|다녀왔|찾아갔|들렀|먹어봤|먹었|"
                    + "visited|ateat|wentto|stoppedby)", Pattern.CASE_INSENSITIVE);
    private static final String[] AMBIGUOUS_OR_NEGATIVE = {
        "추천", "언급", "소개", "추정", "아마", "싶", "것같", "예정", "가능", "방문여부", "안갔", "못갔",
        "recommend", "mentioned", "suggest", "guess", "maybe", "planned", "notvisited"
    };

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
        if (candidate == null || blank(candidate.value()) || !Double.isFinite(candidate.confidence())
                || candidate.confidence() < 0 || candidate.confidence() > 1) {
            return false;
        }
        Evidence evidence = candidate.evidence();
        if (evidence == null || evidence.type() != EvidenceType.TIMESTAMP
                || evidence.startMs() == null || evidence.endMs() == null
                || evidence.startMs() < 0 || evidence.endMs() < evidence.startMs()) {
            return false;
        }

        String normalized = candidate.value().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        for (String ambiguous : AMBIGUOUS_OR_NEGATIVE) {
            if (normalized.contains(ambiguous.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return EXPLICIT_ACTUAL_VISIT.matcher(normalized).find();
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

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

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

    /**
     * Candidate value is a short structured claim, not a source transcript. Match the complete
     * normalized claim so a positive verb cannot turn a negation or question into proof.
     */
    private static final Pattern EXPLICIT_ACTUAL_VISIT_CLAIM = Pattern.compile(
            "^(?:직접방문|(?:[가-힣a-z0-9]+)*(?:직접)?(?:방문(?:함|했(?:음|다|습니다|어요|어))"
                    + "|다녀(?:옴|왔(?:음|다|습니다|어요|어))"
                    + "|찾아갔(?:다|습니다|어요|어)|들렀(?:다|습니다|어요|어)"
                    + "|먹어봤(?:다|습니다|어요|어)|visited|directlyvisited|atehere|wentthere|stoppedby))$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOCKING_VISIT_CONTEXT = Pattern.compile(
            "(?:방문|다녀|찾아|들러|먹어).*(?:않|안|못|아니|을까|을까요|나요|습니까|일까|일까요"
                    + "|것같|듯|추정|예정|계획|가능|추천|언급|소개|아마)");
    private static final Pattern VISIT_VERB = Pattern.compile(
            "방문|다녀|찾아갔|들렀|먹어봤|visited|atehere|wentthere|stoppedby");
    private static final Pattern FIRSTHAND_SUBJECT = Pattern.compile(
            "^(?:제가|저희가|저는|나는|내가|우리가|우리는|저희는|i|we)");
    private static final Pattern SUBJECT_PARTICLE = Pattern.compile("[가-힣]+(?:가|이|은|는)");
    private static final Pattern LOCATION_TARGET = Pattern.compile("[가-힣]+(?:을|를|에|에서)");

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

        String normalized = normalizeClaim(candidate.value());
        return !hasBlockingVisitContext(normalized) && EXPLICIT_ACTUAL_VISIT_CLAIM.matcher(normalized).matches();
    }

    private String normalizeClaim(String value) {
        return value.trim().replaceAll("\\s+", "").replaceFirst("[.。]+$", "").toLowerCase(Locale.ROOT);
    }

    private boolean hasBlockingVisitContext(String normalized) {
        return normalized.indexOf('?') >= 0 || normalized.indexOf('？') >= 0
                || normalized.indexOf('!') >= 0 || normalized.indexOf('！') >= 0
                || BLOCKING_VISIT_CONTEXT.matcher(normalized).find()
                || !hasFirsthandVisitContext(normalized);
    }

    private boolean hasFirsthandVisitContext(String normalized) {
        var visitVerb = VISIT_VERB.matcher(normalized);
        if (!visitVerb.find()) {
            return false;
        }
        String prefix = normalized.substring(0, visitVerb.start());
        var firsthandSubject = FIRSTHAND_SUBJECT.matcher(prefix);
        String subjectRemainder = firsthandSubject.lookingAt() ? prefix.substring(firsthandSubject.end()) : prefix;
        if (SUBJECT_PARTICLE.matcher(subjectRemainder).find()) {
            return false;
        }
        return firsthandSubject.lookingAt() || prefix.contains("직접") || prefix.contains("directly")
                || LOCATION_TARGET.matcher(prefix).find();
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

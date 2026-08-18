package com.masiton.orchestration.application;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
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
            "^.*(?:방문(?:함|했(?:음|다|습니다|어요|어))"
                    + "|다녀(?:옴|왔(?:음|다|습니다|어요|어))"
                    + "|찾아갔(?:다|습니다|어요|어)|들렀(?:다|습니다|어요|어)"
                    + "|먹어봤(?:다|습니다|어요|어)|visited|directlyvisited|atehere|wentthere|stoppedby)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PRE_VERB_NEGATION = Pattern.compile(
            "(?:안|못)(?=방문|다녀|찾아|들러|들렀|먹어)");
    private static final Pattern BLOCKING_VISIT_CONTEXT = Pattern.compile(
            "(?:방문|다녀|찾아|들러|들렀|먹어).*(?:않|안|못|아니|을까|을까요|나요|습니까|일까|일까요"
                    + "|것같|듯|추정|예정|계획|가능|추천|언급|소개|아마)");
    private static final Pattern VISIT_VERB = Pattern.compile(
            "방문|다녀|찾아갔|들렀|먹어봤|visited|atehere|wentthere|stoppedby");
    private static final Pattern FIRSTHAND_SUBJECT = Pattern.compile(
            "^(?:제가|저희가|저는|나는|내가|우리가|우리는|저희는|i(?=\\s|$)|we(?=\\s|$))\\s*");
    private static final Pattern SUBJECT_PARTICLE = Pattern.compile("[가-힣]+(?:가|이|은|는)");
    private static final Pattern SUBJECT_BEFORE_DIRECT = Pattern.compile(
            "(?:^|\\s)[가-힣]+(?:가|이|은|는)\\s+직접$");
    private static final Set<String> IMPLICIT_PLACE_PREFIXES = Set.of("", "이", "그", "저", "해당");

    private final ResolveVerifiedRestaurantReferenceUseCase restaurantReference;
    private final ResolveVerifiedVideoUseCase videoVerification;

    VerifyAiContentCandidateService(ResolveVerifiedRestaurantReferenceUseCase restaurantReference,
                                    ResolveVerifiedVideoUseCase videoVerification) {
        this.restaurantReference = restaurantReference;
        this.videoVerification = videoVerification;
    }

    @Override
    public VerificationResult verify(VerificationCommand command) {
        var restaurant = restaurantReference.resolve(command.restaurantName(), command.candidateAddress(),
                command.kakaoPlaceUrl(), command.menuExpression());
        if (restaurant.isEmpty()) {
            return VerificationResult.blocked("EXTERNAL_REFERENCE_MISMATCH");
        }
        var verifiedRestaurant = restaurant.get();
        var video = videoVerification.resolve(command.videoUrl());
        if (video.isEmpty() || !matchesVideo(command, video.get())) {
            return VerificationResult.blocked("EXTERNAL_REFERENCE_MISMATCH");
        }
        VerifiedVideo verifiedVideo = video.get();
        if (!confirmsActualVisit(command.visitEvidence(), verifiedRestaurant.name(), verifiedVideo.channelName())) {
            return VerificationResult.blocked("VISIT_EVIDENCE_REQUIRED");
        }
        return VerificationResult.verified(new VerifiedContent(
                verifiedRestaurant.regionId(), verifiedRestaurant.foodCategoryId(), verifiedRestaurant.name(),
                verifiedRestaurant.kakaoPlaceId(), verifiedRestaurant.kakaoPlaceUrl(),
                verifiedRestaurant.roadAddress(), verifiedRestaurant.phoneNumber(),
                verifiedRestaurant.latitude(), verifiedRestaurant.longitude(),
                verifiedVideo.publisherExternalChannelId(), verifiedVideo.channelName(),
                "https://www.youtube.com/channel/" + verifiedVideo.publisherExternalChannelId(),
                verifiedVideo.externalVideoId(), verifiedVideo.title(), verifiedVideo.sourceUrl(),
                verifiedVideo.thumbnailUrl(), verifiedVideo.publishedAt(), verifiedVideo.checkedAt()));
    }

    private boolean confirmsActualVisit(VisitEvidenceCandidate candidate, String verifiedRestaurantName,
                                        String verifiedChannelName) {
        if (candidate == null || !nonBlank(candidate.value()) || !Double.isFinite(candidate.confidence())
                || candidate.confidence() < 0 || candidate.confidence() > 1) {
            return false;
        }
        Evidence evidence = candidate.evidence();
        if (!validEvidence(evidence)) {
            return false;
        }

        String normalized = normalizeClaim(candidate.value());
        return !hasBlockingVisitContext(normalized)
                && EXPLICIT_ACTUAL_VISIT_CLAIM.matcher(normalized).matches()
                && hasFirsthandVisitContext(normalizePhrase(candidate.value()), verifiedRestaurantName,
                verifiedChannelName);
    }

    private String normalizeClaim(String value) {
        return normalizeText(value).replaceAll("\\s+", "").replaceFirst("[.。]+$", "");
    }

    private String normalizePhrase(String value) {
        return value.trim().replaceAll("\\s+", " ").replaceFirst("[.。]+$", "").toLowerCase(Locale.ROOT);
    }

    private boolean hasBlockingVisitContext(String normalized) {
        return normalized.indexOf('?') >= 0 || normalized.indexOf('？') >= 0
                || normalized.indexOf('!') >= 0 || normalized.indexOf('！') >= 0
                || PRE_VERB_NEGATION.matcher(normalized).find()
                || BLOCKING_VISIT_CONTEXT.matcher(normalized).find();
    }

    private boolean hasFirsthandVisitContext(String phrase, String verifiedRestaurantName, String verifiedChannelName) {
        var visitVerb = VISIT_VERB.matcher(phrase);
        if (!visitVerb.find()) {
            return false;
        }
        String prefix = phrase.substring(0, visitVerb.start()).trim();
        String target = normalizeClaim(verifiedRestaurantName);
        String compactPrefix = normalizeClaim(prefix);
        int targetIndex = compactPrefix.lastIndexOf(target);
        if (target.isBlank() || targetIndex < 0 || !hasTargetBoundary(compactPrefix, targetIndex, target)) {
            return false;
        }
        if (hasThirdPartySubjectBeforeTarget(compactPrefix, target, verifiedChannelName)) {
            return false;
        }
        var firsthandSubject = FIRSTHAND_SUBJECT.matcher(prefix);
        if (!firsthandSubject.lookingAt()) {
            return hasImplicitPlaceTarget(compactPrefix, target)
                    || isVerifiedChannelSubject(compactPrefix.substring(0, targetIndex), verifiedChannelName);
        }
        String subjectRemainder = prefix.substring(firsthandSubject.end()).trim();
        return !SUBJECT_BEFORE_DIRECT.matcher(subjectRemainder).find();
    }

    private boolean hasImplicitPlaceTarget(String compactPrefix, String target) {
        int targetIndex = compactPrefix.lastIndexOf(target);
        return IMPLICIT_PLACE_PREFIXES.contains(compactPrefix.substring(0, targetIndex));
    }

    private boolean hasTargetBoundary(String compactPrefix, int targetIndex, String target) {
        String beforeTarget = compactPrefix.substring(0, targetIndex);
        String afterTarget = compactPrefix.substring(targetIndex + target.length());
        if (beforeTarget.endsWith("아닌") || beforeTarget.endsWith("아니라") || beforeTarget.endsWith("제외")) {
            return false;
        }
        if (afterTarget.startsWith("이아닌") || afterTarget.startsWith("아닌")
                || afterTarget.startsWith("이아니라") || afterTarget.startsWith("아니라")
                || afterTarget.startsWith("을제외") || afterTarget.startsWith("를제외")
                || afterTarget.startsWith("제외")) {
            return false;
        }
        return afterTarget.matches(
                "(?:을|를|이|가|은|는|에|에서|으로|로|만|도|까지)?"
                        + "(?:직접|정말|진짜|바로|다시|오늘|어제|또|한번|한번더)?");
    }

    private boolean hasThirdPartySubjectBeforeTarget(String compactPrefix, String target, String verifiedChannelName) {
        int targetIndex = compactPrefix.lastIndexOf(target);
        if (targetIndex <= 0) {
            return false;
        }
        Matcher subject = SUBJECT_PARTICLE.matcher(compactPrefix.substring(0, targetIndex));
        String lastSubject = null;
        while (subject.find()) {
            lastSubject = subject.group();
        }
        return lastSubject != null && !isFirsthandSubject(lastSubject)
                && !isVerifiedChannelSubject(lastSubject, verifiedChannelName);
    }

    private boolean isVerifiedChannelSubject(String subject, String verifiedChannelName) {
        String baseSubject = subject.replaceFirst("(?:가|이|은|는)$", "");
        return !baseSubject.isBlank() && normalizeClaim(verifiedChannelName).equals(normalizeClaim(baseSubject));
    }

    private boolean isFirsthandSubject(String subject) {
        return switch (subject) {
            case "제가", "저희가", "저는", "나는", "내가", "우리가", "우리는", "저희는" -> true;
            default -> false;
        };
    }

    private boolean validEvidence(Evidence evidence) {
        return evidence != null
                && evidence.type() == EvidenceType.TIMESTAMP
                && validRange(evidence.startMs(), evidence.endMs());
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

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

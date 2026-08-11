package com.masiton.ai.application;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.masiton.ai.application.port.out.AiExtractionResultProcessor;
import com.masiton.ai.application.port.out.AiExtractionResultStore;
import com.masiton.ai.application.port.out.dto.AiVideoExtractionResult;
import com.masiton.orchestration.application.port.in.AutoRegisterVerifiedContentUseCase;
import com.masiton.restaurant.application.port.out.FoodCategoryRepositoryPort;
import com.masiton.restaurant.application.port.out.PlaceVerificationPort;
import com.masiton.restaurant.application.port.out.RegionRepositoryPort;
import com.masiton.restaurant.application.port.out.VerifiedPlace;
import com.masiton.video.application.port.in.ResolveVerifiedVideoUseCase;
import com.masiton.video.application.port.out.VerifiedVideo;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Provider 결과를 후보로 해석하고 외부 검증이 끝난 뒤 짧은 원자 트랜잭션으로 넘긴다.
 * 원본 응답·자막·보완 텍스트는 이 경계를 넘어 저장하지 않는다.
 */
@Service
class AiExtractionResultProcessorService implements AiExtractionResultProcessor {

    private static final Pattern DISTRICT = Pattern.compile("^서울특별시\\s+([^\\s]+구)\\s+.+$");
    private static final Pattern KAKAO_PLACE_URL = Pattern.compile(
            "https://place\\.map\\.kakao\\.com/[^/?#]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG_CODE = Pattern.compile("[A-Z0-9_]{1,64}");
    private static final Set<String> FORBIDDEN_TAG_WORDS = Set.of(
            "가격", "품질", "평점", "영업시간", "영업", "방문가능", "예약",
            "price", "rating", "hours", "availability");

    private final AiExtractionResultStore resultStore;
    private final AiExtractionResultCommitService commitService;
    private final PlaceVerificationPort placeVerification;
    private final ResolveVerifiedVideoUseCase videoVerification;
    private final RegionRepositoryPort regionRepository;
    private final FoodCategoryRepositoryPort foodCategoryRepository;
    private final ObjectMapper objectMapper;
    private final AiCandidateValidator candidateValidator = new AiCandidateValidator();

    AiExtractionResultProcessorService(
            AiExtractionResultStore resultStore,
            AiExtractionResultCommitService commitService,
            PlaceVerificationPort placeVerification,
            ResolveVerifiedVideoUseCase videoVerification,
            RegionRepositoryPort regionRepository,
            FoodCategoryRepositoryPort foodCategoryRepository,
            ObjectMapper objectMapper) {
        this.resultStore = resultStore;
        this.commitService = commitService;
        this.placeVerification = placeVerification;
        this.videoVerification = videoVerification;
        this.regionRepository = regionRepository;
        this.foodCategoryRepository = foodCategoryRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean process(UUID jobId, String workerId, int attemptNo, OffsetDateTime attemptStartedAt,
                           OffsetDateTime finishedAt, AiVideoExtractionResult result) {
        Optional<AiExtractionResultStore.ProcessingJob> job = resultStore.lockProcessingJob(
                jobId, workerId, attemptNo);
        if (job.isEmpty()) {
            return false;
        }

        ParsedCandidate candidate = parse(result.candidates());
        AiExtractionResultCommitService.ProcessCommand base = command(
                jobId, workerId, attemptNo, attemptStartedAt, finishedAt, result, candidate);
        if (candidate.blockReason() != null) {
            return persistBlocked(base, candidate);
        }
        if (!KAKAO_PLACE_URL.matcher(candidate.location().value()).matches()) {
            return persistBlocked(withReason(base, "KAKAO_LOCATION_REQUIRED"), candidate);
        }

        try {
            VerifiedVideo verifiedVideo = videoVerification.resolve(job.get().videoUrl())
                    .orElseThrow(() -> new CandidateBlockedException("YOUTUBE_VIDEO_NOT_FOUND"));
            validateVideo(job.get(), verifiedVideo);

            VerifiedPlace place = placeVerification.verify(
                            candidate.restaurantName().value(), URI.create(candidate.location().value()), null)
                    .orElseThrow(() -> new CandidateBlockedException("KAKAO_PLACE_NOT_FOUND"));
            validatePlace(candidate, place);

            String district = districtOf(place.roadAddress());
            var region = regionRepository.findByName(district)
                    .filter(value -> value.isActive())
                    .orElseThrow(() -> new CandidateBlockedException("REGION_NOT_FOUND"));
            var foodCategory = foodCategoryRepository.findByName(candidate.menu().value())
                    .filter(value -> value.isActive())
                    .orElseThrow(() -> new CandidateBlockedException("FOOD_CATEGORY_NOT_FOUND"));

            List<AiExtractionResultCommitService.AiTagCandidate> tags = resolveTags(candidate.tags());
            AutoRegisterVerifiedContentUseCase.VerifiedContentCommand registration = new AutoRegisterVerifiedContentUseCase.VerifiedContentCommand(
                    new AutoRegisterVerifiedContentUseCase.RestaurantCandidate(
                            region.getId(), foodCategory.getId(), place.name(), place.identityKey(),
                            place.kakaoPlaceUrl(), place.roadAddress(), null, place.phoneNumber(),
                            place.latitude(), place.longitude()),
                    new AutoRegisterVerifiedContentUseCase.CreatorCandidate(
                            verifiedVideo.publisherExternalChannelId(), verifiedVideo.channelName(),
                            youtubeChannelUrl(verifiedVideo.publisherExternalChannelId())),
                    new AutoRegisterVerifiedContentUseCase.VideoCandidate(
                            verifiedVideo.externalVideoId(), verifiedVideo.publisherExternalChannelId(),
                            verifiedVideo.title(), verifiedVideo.sourceUrl(), verifiedVideo.thumbnailUrl(),
                            verifiedVideo.publishedAt(), verifiedVideo.checkedAt()),
                    true);
            return commitService.persistConfirmed(withTags(base, tags), registration);
        } catch (CandidateBlockedException | IllegalArgumentException exception) {
            return persistBlocked(withReason(base, exception.getMessage()), candidate);
        } catch (RuntimeException exception) {
            // Database or other infrastructure failures must remain retryable and must not be
            // converted into a successful but blocked candidate.
            throw exception;
        }
    }

    private ParsedCandidate parse(JsonNode root) {
        AiCandidateValidationResult validation = candidateValidator.validate(root);
        ObjectNode fields = objectMapper.createObjectNode();
        ObjectNode confidences = objectMapper.createObjectNode();
        ObjectNode evidence = objectMapper.createObjectNode();
        ArrayNode tagsJson = objectMapper.createArrayNode();
        ArrayNode missing = objectMapper.createArrayNode();
        Map<String, ParsedField> parsedFields = new LinkedHashMap<>();

        validation.candidates().forEach((field, candidate) -> {
            fields.put(field, candidate.value());
            confidences.put(field, candidate.confidence());
            ObjectNode candidateEvidence = evidenceNode(candidate.evidence());
            evidence.set(field, candidateEvidence);
            parsedFields.put(field, new ParsedField(field, candidate.value(),
                    BigDecimal.valueOf(candidate.confidence()), candidateEvidence));
        });
        validation.missingFields().forEach(missing::add);

        List<ParsedTag> tags = new ArrayList<>();
        validation.allTags().forEach(tag -> {
            ObjectNode safe = objectMapper.createObjectNode();
            safe.put("field", "tag");
            safe.put("candidateTagId", tag.candidateTagId());
            safe.put("tagType", tag.tagType());
            safe.put("rawLabel", tag.rawLabel());
            safe.put("normalizedCode", tag.normalizedCode());
            safe.put("label", tag.label());
            safe.put("confidence", tag.confidence());
            safe.set("evidence", evidenceNode(tag.evidence()));
            tagsJson.add(safe);
            tags.add(new ParsedTag(tag.candidateTagId(), tag.tagType(), tag.rawLabel(), tag.normalizedCode(),
                    tag.label(), BigDecimal.valueOf(tag.confidence()), evidenceNode(tag.evidence())));
        });

        String reason = validation.isAutoConfirmable() && validation.foodCategoryName() != null
                ? null : validation.reasonCodes().stream().findFirst().orElse("FOOD_CATEGORY_REQUIRED");
        return new ParsedCandidate(root.path("resultCompleteness").asText("PARTIAL"), fields, tagsJson,
                confidences, evidence, missing, tags, parsedFields.get("restaurantName"), parsedFields.get("menu"),
                parsedFields.get("address"), parsedFields.get("location"), reason,
                validation.isAutoRejected() ? "AUTO_REJECTED" : "AUTO_BLOCKED");
    }

    private List<AiExtractionResultCommitService.AiTagCandidate> resolveTags(List<ParsedTag> parsedTags) {
        List<AiExtractionResultCommitService.AiTagCandidate> result = new ArrayList<>();
        for (ParsedTag tag : parsedTags) {
            if (!isTagAutoConnectable(tag)) {
                result.add(toTag(tag, "AUTO_REJECT", "TAG_POLICY", false, null));
                continue;
            }
            Optional<AiExtractionResultStore.TagDefinition> existing = resultStore.findTag(tag.normalizedCode());
            if (existing.isPresent()) {
                AiExtractionResultStore.TagDefinition definition = existing.get();
                if (!"ACTIVE".equals(definition.status()) || !tag.tagType().equals(definition.tagType())
                        || !AiTagPolicy.matchesApprovedLabel(tag.label(), definition, objectMapper)) {
                    result.add(toTag(tag, "AUTO_REJECT", "TAG_POLICY", false, definition.id()));
                    continue;
                }
                result.add(toTag(tag, "AUTO_MERGE", null, true, definition.id()));
                continue;
            }
            if (!AiTagPolicy.isNewTagCandidate(tag.tagType(), tag.rawLabel(), tag.label(), tag.normalizedCode())) {
                result.add(toTag(tag, "AUTO_REJECT", "TAG_POLICY", false, null));
                continue;
            }
            result.add(toTag(tag, "AUTO_ACCEPT", null, true, null));
        }
        return result;
    }

    private boolean persistBlocked(AiExtractionResultCommitService.ProcessCommand command,
                                   ParsedCandidate candidate) {
        return commitService.persistBlocked(withTags(command, resolveTags(candidate.tags()), command.reviewStatus()));
    }

    private boolean isTagAutoConnectable(ParsedTag tag) {
        return !"UNKNOWN".equals(tag.evidence().path("type").asText())
                && TAG_CODE.matcher(tag.normalizedCode()).matches()
                && tag.normalizedCode().startsWith(tag.tagType() + "_")
                && FORBIDDEN_TAG_WORDS.stream().noneMatch(word -> containsIgnoreCase(
                        tag.rawLabel() + " " + tag.label() + " " + tag.normalizedCode(), word));
    }

    private AiExtractionResultCommitService.AiTagCandidate toTag(ParsedTag tag, String decision, String reason,
                                                                   boolean autoConnectable, UUID existingId) {
        return new AiExtractionResultCommitService.AiTagCandidate(
                tag.candidateTagId(), tag.tagType(), tag.normalizedCode(), tag.label(), tag.confidence(),
                json(tag.evidence()), aliases(tag.rawLabel(), tag.label()),
                AiExtractionContract.MODEL_VERSION + "/" + AiExtractionContract.PROMPT_VERSION + "/"
                        + AiExtractionContract.SCHEMA_VERSION,
                decision, reason, autoConnectable, existingId);
    }

    private void validateVideo(AiExtractionResultStore.ProcessingJob job, VerifiedVideo video) {
        if (!job.videoId().equals(video.externalVideoId())
                || !job.channelId().equals(video.publisherExternalChannelId())
                || blank(video.channelName()) || blank(video.title()) || blank(video.sourceUrl())
                || blank(video.thumbnailUrl())) {
            throw new CandidateBlockedException("YOUTUBE_METADATA_MISMATCH");
        }
    }

    private void validatePlace(ParsedCandidate candidate, VerifiedPlace place) {
        if (blank(place.identityKey()) || blank(place.name()) || blank(place.kakaoPlaceUrl())
                || blank(place.roadAddress()) || blank(place.phoneNumber())
                || !sameText(candidate.restaurantName().value(), place.name())
                || !sameAddress(candidate.address().value(), place.roadAddress())) {
            throw new CandidateBlockedException("KAKAO_PLACE_MISMATCH");
        }
    }

    private AiExtractionResultCommitService.ProcessCommand command(
            UUID jobId, String workerId, int attemptNo, OffsetDateTime attemptStartedAt,
            OffsetDateTime finishedAt, AiVideoExtractionResult result, ParsedCandidate candidate) {
        List<AiExtractionResultCommitService.AiTagCandidate> basicTags = candidate.tags().stream()
                .map(tag -> {
                    boolean connectable = isTagAutoConnectable(tag);
                    return toTag(tag, connectable ? "AUTO_ACCEPT" : "AUTO_REJECT",
                            connectable ? null : "TAG_POLICY", connectable, null);
                })
                .toList();
        return new AiExtractionResultCommitService.ProcessCommand(jobId, workerId, attemptNo, attemptStartedAt,
                finishedAt, result.providerRequestId(), candidate.completeness(), json(candidate.fields()),
                json(candidate.tagsJson()), json(candidate.confidences()), json(candidate.evidence()),
                json(candidate.missing()), candidate.blockReason(), candidate.reviewStatus(), basicTags);
    }

    private AiExtractionResultCommitService.ProcessCommand withTags(
            AiExtractionResultCommitService.ProcessCommand command,
            List<AiExtractionResultCommitService.AiTagCandidate> tags) {
        return withTags(command, tags, "AUTO_CONFIRMED");
    }

    private AiExtractionResultCommitService.ProcessCommand withTags(
            AiExtractionResultCommitService.ProcessCommand command,
            List<AiExtractionResultCommitService.AiTagCandidate> tags,
            String reviewStatus) {
        return new AiExtractionResultCommitService.ProcessCommand(command.jobId(), command.workerId(), command.attemptNo(),
                command.attemptStartedAt(), command.finishedAt(), command.providerRequestId(), command.resultCompleteness(),
                command.candidateFields(), command.candidateTags(), command.fieldConfidences(), command.evidence(),
                command.missingFields(), command.blockReason(), reviewStatus, tags);
    }

    private AiExtractionResultCommitService.ProcessCommand withReason(
            AiExtractionResultCommitService.ProcessCommand command, String reason) {
        return new AiExtractionResultCommitService.ProcessCommand(command.jobId(), command.workerId(), command.attemptNo(),
                command.attemptStartedAt(), command.finishedAt(), command.providerRequestId(), command.resultCompleteness(),
                command.candidateFields(), command.candidateTags(), command.fieldConfidences(), command.evidence(),
                command.missingFields(), reason == null ? "VALIDATION_FAILED" : reason, "AUTO_BLOCKED", command.tags());
    }

    private ObjectNode evidenceNode(AiCandidateValidationResult.Evidence source) {
        ObjectNode target = objectMapper.createObjectNode();
        target.put("type", source.type().name());
        if (source.type() == AiCandidateValidationResult.EvidenceType.TIMESTAMP) {
            target.put("startMs", source.startMs());
            target.put("endMs", source.endMs());
        } else if (source.type() == AiCandidateValidationResult.EvidenceType.TEXT_RANGE) {
            target.put("startOffset", source.startOffset());
            target.put("endOffset", source.endOffset());
            target.put("sourceHash", source.sourceHash());
        }
        return target;
    }

    private String districtOf(String roadAddress) {
        Matcher matcher = DISTRICT.matcher(roadAddress == null ? "" : roadAddress.trim());
        if (!matcher.matches()) throw new CandidateBlockedException("REGION_NOT_FOUND");
        return matcher.group(1);
    }

    private boolean sameText(String left, String right) {
        return containsEither(normalize(left), normalize(right));
    }

    private boolean sameAddress(String left, String right) {
        return containsEither(normalize(left), normalize(right));
    }

    private boolean containsEither(String left, String right) {
        return !left.isBlank() && !right.isBlank() && (left.equals(right)
                || left.contains(right) || right.contains(left));
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String youtubeChannelUrl(String channelId) {
        return "https://www.youtube.com/channel/" + channelId;
    }

    private boolean containsIgnoreCase(String value, String word) {
        return value.toLowerCase(Locale.ROOT).contains(word.toLowerCase(Locale.ROOT));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String json(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JacksonException exception) {
            throw new IllegalStateException("AI candidate could not be serialized.", exception);
        }
    }

    private String aliases(String rawLabel, String label) {
        ArrayNode aliases = objectMapper.createArrayNode();
        aliases.add(label);
        return json(aliases);
    }

    private record ParsedField(String field, String value, BigDecimal confidence, JsonNode evidence) {
    }

    private record ParsedTag(String candidateTagId, String tagType, String rawLabel, String normalizedCode,
                             String label, BigDecimal confidence, JsonNode evidence) {
    }

    private record ParsedCandidate(
            String completeness,
            ObjectNode fields,
            ArrayNode tagsJson,
            ObjectNode confidences,
            ObjectNode evidence,
            ArrayNode missing,
            List<ParsedTag> tags,
            ParsedField restaurantName,
            ParsedField menu,
            ParsedField address,
            ParsedField location,
            String blockReason,
            String reviewStatus) {
    }

    private static final class CandidateBlockedException extends RuntimeException {
        CandidateBlockedException(String message) {
            super(message);
        }
    }
}

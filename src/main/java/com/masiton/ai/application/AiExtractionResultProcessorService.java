package com.masiton.ai.application;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.Clock;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.masiton.ai.application.port.out.AiExtractionResultProcessor;
import com.masiton.ai.application.port.out.AiExtractionResultStore;
import com.masiton.ai.application.port.out.AiRegistrationUnitStore;
import com.masiton.ai.application.port.out.AiRegistrationUnitRetryStore;
import com.masiton.ai.application.port.out.TemporaryInputCipher;
import com.masiton.ai.application.port.out.TemporaryInputDecryptionException;
import com.masiton.ai.application.port.out.TemporaryInputCipher.EncryptedInput;
import com.masiton.ai.application.port.out.AiExtractionResultProcessor.SupplementProvider;
import com.masiton.ai.application.port.out.AiExtractionResultProcessor.SupplementResult;
import com.masiton.ai.application.port.out.dto.AiVideoExtractionResult;
import com.masiton.video.application.port.in.ResolveVerifiedVideoUseCase;
import com.masiton.video.application.port.out.VerifiedVideo;
import com.masiton.orchestration.application.port.in.AutoRegisterVerifiedContentUseCase;
import com.masiton.orchestration.application.port.in.VerifyAiContentCandidateUseCase;

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

    private static final Pattern KAKAO_PLACE_URL = Pattern.compile(
            "https://place\\.map\\.kakao\\.com/[^/?#]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG_CODE = Pattern.compile(
            "(?:MENU|TASTE|OCCASION|ATMOSPHERE)_[A-Z0-9]+(?:_[A-Z0-9]+)*");
    private static final Set<String> FORBIDDEN_TAG_WORDS = Set.of(
            "가격", "품질", "평점", "영업시간", "영업", "방문가능", "예약",
            "price", "rating", "hours", "availability");

    private final AiExtractionResultStore resultStore;
    private final AiExtractionResultCommitService commitService;
    private final VerifyAiContentCandidateUseCase contentVerification;
    private final RegistrationUnitAutoExecutionService registrationUnitAutoExecution;
    private final ObjectMapper objectMapper;
    private final AiRegistrationUnitStore registrationUnitStore;
    private final AiRegistrationUnitRetryStore retryStore;
    private final TemporaryInputCipher temporaryInputCipher;
    private final ResolveVerifiedVideoUseCase videoResolver;
    private final Clock clock;
    private final AiCandidateValidator candidateValidator = new AiCandidateValidator();

    @Autowired
    AiExtractionResultProcessorService(
            AiExtractionResultStore resultStore,
            AiExtractionResultCommitService commitService,
            VerifyAiContentCandidateUseCase contentVerification,
            RegistrationUnitAutoExecutionService registrationUnitAutoExecution,
            ObjectMapper objectMapper,
            AiRegistrationUnitStore registrationUnitStore,
            AiRegistrationUnitRetryStore retryStore,
            TemporaryInputCipher temporaryInputCipher,
            ResolveVerifiedVideoUseCase videoResolver,
            @Qualifier("aiWorkerClock") Clock aiWorkerClock) {
        this.resultStore = resultStore;
        this.commitService = commitService;
        this.contentVerification = contentVerification;
        this.registrationUnitAutoExecution = registrationUnitAutoExecution;
        this.objectMapper = objectMapper;
        this.registrationUnitStore = registrationUnitStore;
        this.retryStore = retryStore;
        this.temporaryInputCipher = temporaryInputCipher;
        this.videoResolver = videoResolver;
        this.clock = aiWorkerClock;
    }

    AiExtractionResultProcessorService(AiExtractionResultStore resultStore,
                                       AiExtractionResultCommitService commitService,
                                       VerifyAiContentCandidateUseCase contentVerification,
                                       RegistrationUnitAutoExecutionService registrationUnitAutoExecution,
                                       ObjectMapper objectMapper) {
        this(resultStore, commitService, contentVerification, registrationUnitAutoExecution, objectMapper,
                null, null, null, null, Clock.systemUTC());
    }

    @Override
    public boolean process(UUID jobId, String workerId, int attemptNo, OffsetDateTime attemptStartedAt,
                           OffsetDateTime finishedAt, AiVideoExtractionResult result) {
        return processInternal(jobId, workerId, attemptNo, attemptStartedAt, finishedAt, result, null);
    }

    @Override
    public boolean process(UUID jobId, String workerId, int attemptNo, OffsetDateTime attemptStartedAt,
                           OffsetDateTime finishedAt, AiVideoExtractionResult result,
                           SupplementProvider supplementProvider) {
        return processInternal(jobId, workerId, attemptNo, attemptStartedAt, finishedAt, result, supplementProvider);
    }

    @Override
    public Optional<Boolean> resumePending(UUID jobId, String workerId, int attemptNo,
                                           OffsetDateTime attemptStartedAt, OffsetDateTime finishedAt,
                                           SupplementProvider supplementProvider) {
        if (retryStore == null || registrationUnitStore == null || temporaryInputCipher == null) {
            return Optional.empty();
        }
        Optional<AiExtractionResultStore.ProcessingJob> job = resultStore.lockProcessingJob(jobId, workerId, attemptNo);
        Optional<AiExtractionResultStore.SnapshotCandidate> latest = resultStore.latestSnapshotCandidate(jobId);
        if (job.isEmpty() || latest.isEmpty()) return Optional.empty();
        AiExtractionResultStore.SnapshotCandidate snapshot = latest.get();
        JsonNode root;
        try {
            root = objectMapper.readTree(snapshot.candidateFields());
        } catch (JacksonException exception) {
            return Optional.empty();
        }
        JsonNode units = root.path("registrationUnits");
        if (!units.isArray()) return Optional.empty();
        List<AiRegistrationUnitStore.RegistrationUnitRow> rows = registrationUnitStore.findBySnapshotId(snapshot.id());
        Map<Integer, AiRegistrationUnitStore.RegistrationUnitRow> rowsByIndex = new LinkedHashMap<>();
        rows.forEach(row -> rowsByIndex.put(row.unitIndex(), row));
        List<RegistrationUnitOutcome> outcomes = rows.stream().map(this::toOutcome).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Map<UUID, Integer> pending = new LinkedHashMap<>();
        boolean remediationCheckpoint = "AUTO_BLOCKED".equals(snapshot.reviewStatus()) && !rows.isEmpty();
        int finalAttemptNo = attemptNo;
        OffsetDateTime finalStartedAt = attemptStartedAt;
        OffsetDateTime finalFinishedAt = finishedAt;
        AiVideoExtractionResult finalResult = new AiVideoExtractionResult(root, null);
        ArrayNode mutableUnits = (ArrayNode) units.deepCopy();
        boolean found = false;
        for (int index = 0; index < units.size(); index++) {
            String candidateUnitId = units.path(index).path("candidateUnitId").asText("");
            Optional<AiRegistrationUnitRetryStore.RetryClaim> existing = retryStore
                    .findByJobAndCandidateUnitId(jobId, candidateUnitId);
            if (existing.isEmpty() || !"PENDING".equals(existing.get().outcome())
                    || !snapshot.id().equals(existing.get().sourceSnapshotId())) continue;
            AiRegistrationUnitRetryStore.RetryClaim claim = existing.get();
            int unitIndex = index + 1;
            AiRegistrationUnitStore.RegistrationUnitRow sourceRow = rowsByIndex.get(unitIndex);
            if (sourceRow == null || !sourceRow.id().equals(claim.sourceRegistrationUnitId())) continue;
            found = true;
            pending.put(claim.id(), unitIndex);
            AiVideoExtractionResult recoveredResult = null;
            if (claim.providerResultCiphertext() != null && claim.providerResultKeyId() != null) {
                try {
                    String saved = temporaryInputCipher.decrypt(new EncryptedInput(
                            claim.providerResultCiphertext(), claim.providerResultKeyId()));
                    JsonNode savedPayload = objectMapper.readTree(saved);
                    recoveredResult = new AiVideoExtractionResult(savedPayload.path("candidates"),
                            savedPayload.path("providerRequestId").isNull() ? null
                                    : savedPayload.path("providerRequestId").asText());
                } catch (TemporaryInputDecryptionException | JacksonException exception) {
                    // The HTTP response exists but cannot be recovered; preserve the source unit and block.
                }
            }
            boolean priorAttemptFailed = "FAILED".equals(claim.providerAttemptOutcome());
            if (recoveredResult == null && (claim.providerAttemptNo() != null && !priorAttemptFailed
                    || claim.ciphertext() == null || claim.encryptionKeyId() == null)) continue;
            try {
                SupplementResult supplied;
                if (recoveredResult != null) {
                    OffsetDateTime recoveredAt = claim.providerResultAt() == null ? finishedAt : claim.providerResultAt();
                    supplied = new SupplementResult(claim.providerAttemptNo() == null ? attemptNo : claim.providerAttemptNo(), recoveredAt, recoveredAt,
                            recoveredResult, true);
                } else {
                    String approvedInput = temporaryInputCipher.decrypt(new EncryptedInput(
                            claim.ciphertext(), claim.encryptionKeyId()));
                    Optional<SupplementResult> response = supplementProvider.extract(jobId, claim.id(), approvedInput);
                    if (response.isEmpty()) continue;
                    supplied = response.get();
                }
                finalAttemptNo = supplied.attemptNo();
                finalStartedAt = supplied.attemptStartedAt();
                finalFinishedAt = supplied.finishedAt();
                finalResult = supplied.result();
                JsonNode replacement = supplied.providerSucceeded()
                        ? validatedSupplementUnit(supplied.result().candidates(), candidateUnitId) : null;
                if (replacement == null) continue;
                mutableUnits.set(index, replacement.deepCopy());
                ParsedCandidate changed = parse(withRegistrationUnits(root, mutableUnits));
                List<RegistrationUnitOutcome> unitOutcome = registrationUnitAutoExecution.execute(changed.fields(),
                        changed.confidences(), changed.evidence(), job.get().channelId(), job.get().videoId(),
                        job.get().videoUrl(), unitIndex);
                if (!unitOutcome.isEmpty()) replaceOutcome(outcomes, unitOutcome.get(0));
            } catch (TemporaryInputDecryptionException exception) {
                // Expired or unavailable ciphertext is terminal for this one remediation.
            }
        }
        if (!found && !remediationCheckpoint) return Optional.empty();
        JsonNode merged = withRegistrationUnits(root, mutableUnits);
        ParsedCandidate candidate = parse(merged);
        List<ParsedTag> parsedTags = candidate.tags();
        String status = !outcomes.isEmpty() && outcomes.stream().allMatch(o -> "AUTO_CONFIRMED".equals(o.reviewStatus()))
                ? "AUTO_CONFIRMED" : "AUTO_BLOCKED";
        AiExtractionResultCommitService.ProcessCommand finalCommand = withTags(command(jobId, workerId,
                finalAttemptNo, finalStartedAt, finalFinishedAt, finalResult, candidate, outcomes),
                resolveTags(parsedTags), status);
        boolean committed = commitService.persistRemediationResult(finalCommand, retryStatuses(pending, outcomes));
        return Optional.of(committed);
    }

    private JsonNode withRegistrationUnits(JsonNode original, ArrayNode units) {
        ObjectNode copy = (ObjectNode) original.deepCopy();
        copy.set("registrationUnits", units.deepCopy());
        return copy;
    }

    private void replaceOutcome(List<RegistrationUnitOutcome> outcomes, RegistrationUnitOutcome replacement) {
        for (int i = 0; i < outcomes.size(); i++) {
            if (outcomes.get(i).unitIndex() == replacement.unitIndex()) {
                outcomes.set(i, replacement);
                return;
            }
        }
    }

    private RegistrationUnitOutcome toOutcome(AiRegistrationUnitStore.RegistrationUnitRow row) {
        if ("AUTO_CONFIRMED".equals(row.reviewStatus())) {
            return RegistrationUnitOutcome.confirmed(row.unitIndex(), row.restaurantName(), row.placeDecisionJson(),
                    row.categoryDecisionJson(), row.registeredRestaurantId(), row.registeredCreatorId(),
                    row.registeredVideoId(), row.registeredVisitId(), json(objectMapper.valueToTree(row.reusedResources())));
        }
        return RegistrationUnitOutcome.blocked(row.unitIndex(), row.restaurantName(), row.blockReason());
    }

    private boolean processInternal(UUID jobId, String workerId, int attemptNo, OffsetDateTime attemptStartedAt,
                                    OffsetDateTime finishedAt, AiVideoExtractionResult result,
                                    SupplementProvider supplementProvider) {
        Optional<AiExtractionResultStore.ProcessingJob> job = resultStore.lockProcessingJob(
                jobId, workerId, attemptNo);
        if (job.isEmpty()) {
            return false;
        }

        ParsedCandidate candidate = parse(result.candidates());
        if (result.candidates().has("registrationUnits")) {
            AiCandidateValidationResult validation = candidateValidator.validateS3(result.candidates());
            List<RegistrationUnitOutcome> outcomes = validation.isAutoRejected() ? List.of()
                    : registrationUnitAutoExecution.execute(candidate.fields(), candidate.confidences(), candidate.evidence(),
                            job.get().channelId(), job.get().videoId(), job.get().videoUrl());
            AiExtractionResultCommitService.ProcessCommand unitCommand = command(jobId, workerId, attemptNo,
                    attemptStartedAt, finishedAt, result, candidate, outcomes);
            String reviewStatus = validation.isAutoRejected() ? "AUTO_REJECTED"
                    : !outcomes.isEmpty() && outcomes.stream().allMatch(outcome -> "AUTO_CONFIRMED".equals(outcome.reviewStatus()))
                    ? "AUTO_CONFIRMED" : "AUTO_BLOCKED";
            if (supplementProvider != null && !validation.isAutoRejected()) {
                List<RegistrationUnitOutcome> eligible = outcomes.stream().filter(this::isRemediable).toList();
                if (!eligible.isEmpty()) {
                    return remediateRegistrationUnits(job.get(), jobId, workerId, attemptNo, attemptStartedAt,
                            finishedAt, result, candidate, outcomes, eligible, supplementProvider);
                }
            }
            return commitService.persistBlocked(withTags(unitCommand, resolveTags(candidate.tags()), reviewStatus));
        }
        // BR-AIEXTRACT-001: registration units are judged independently of the job-level candidate
        // outcome below, so a video with multiple places can still auto-register the places that
        // clear steps 1-2 even when the legacy single-candidate pipeline blocks the job itself.
        List<RegistrationUnitOutcome> registrationUnitOutcomes = registrationUnitAutoExecution.execute(
                candidate.fields(), candidate.confidences(), candidate.evidence(),
                job.get().channelId(), job.get().videoId(), job.get().videoUrl());
        AiExtractionResultCommitService.ProcessCommand base = command(
                jobId, workerId, attemptNo, attemptStartedAt, finishedAt, result, candidate, registrationUnitOutcomes);
        if (candidate.blockReason() != null) {
            return persistBlocked(base, candidate);
        }
        if (!KAKAO_PLACE_URL.matcher(candidate.location().value()).matches()) {
            return persistBlocked(withReason(base, "KAKAO_LOCATION_REQUIRED"), candidate);
        }

        URI kakaoPlaceUrl;
        try {
            kakaoPlaceUrl = URI.create(candidate.location().value());
        } catch (IllegalArgumentException exception) {
            return persistBlocked(withReason(base, "KAKAO_LOCATION_REQUIRED"), candidate);
        }

        try {
            VerifyAiContentCandidateUseCase.VerificationResult verification = contentVerification.verify(
                    new VerifyAiContentCandidateUseCase.VerificationCommand(
                            job.get().channelId(), job.get().videoId(), job.get().videoUrl(),
                            candidate.restaurantName().value(), candidate.address().value(),
                            kakaoPlaceUrl, candidate.menu().value(), visitEvidence(candidate.visitEvidence())));
            if (!verification.isVerified()) {
                throw new CandidateBlockedException(verification.failureReason());
            }
            VerifyAiContentCandidateUseCase.VerifiedContent verified = verification.content();
            List<AiExtractionResultCommitService.AiTagCandidate> tags = resolveTags(candidate.tags());
            AutoRegisterVerifiedContentUseCase.VerifiedContentCommand registration = new AutoRegisterVerifiedContentUseCase.VerifiedContentCommand(
                    new AutoRegisterVerifiedContentUseCase.RestaurantCandidate(
                            verified.regionId(), verified.foodCategoryId(), verified.restaurantName(),
                            verified.kakaoPlaceId(), verified.kakaoPlaceUrl(), verified.roadAddress(), null,
                            verified.phoneNumber(), verified.latitude(), verified.longitude()),
                    new AutoRegisterVerifiedContentUseCase.CreatorCandidate(
                            verified.channelId(), verified.channelName(), verified.channelUrl()),
                    new AutoRegisterVerifiedContentUseCase.VideoCandidate(
                            verified.videoId(), verified.channelId(), verified.videoTitle(), verified.videoSourceUrl(),
                            verified.videoThumbnailUrl(), verified.publishedAt(), verified.checkedAt()),
                    true);
            // Registration defects and infrastructure failures must not be mistaken for a blocked candidate.
            return commitService.persistConfirmed(withTags(base, tags), registration);
        } catch (CandidateBlockedException exception) {
            return persistBlocked(withReason(base, exception.getMessage()), candidate);
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

        validation.candidates().forEach((field, fieldCandidates) -> {
            if (fieldCandidates.size() > 1) {
                // BR-AIEXTRACT-001: multiple candidates stay blocked but must still be preserved for admin review.
                ArrayNode candidatesArray = objectMapper.createArrayNode();
                for (AiCandidateValidationResult.Candidate candidate : fieldCandidates) {
                    ObjectNode candidateNode = objectMapper.createObjectNode();
                    candidateNode.put("value", candidate.value());
                    candidateNode.put("confidence", candidate.confidence());
                    candidateNode.set("evidence", evidenceNode(candidate.evidence()));
                    candidatesArray.add(candidateNode);
                }
                fields.set(field, candidatesArray);
                return;
            }
            AiCandidateValidationResult.Candidate candidate = fieldCandidates.get(0);
            fields.put(field, candidate.value());
            confidences.put(field, candidate.confidence());
            ObjectNode candidateEvidence = evidenceNode(candidate.evidence());
            evidence.set(field, candidateEvidence);
            parsedFields.put(field, new ParsedField(field, candidate.value(),
                    BigDecimal.valueOf(candidate.confidence()), candidateEvidence));
        });
        if (root.path("registrationUnits").isArray()) {
            // S3 keeps the provider-declared grouping as raw Snapshot evidence.  The decomposition
            // service rejects malformed or missing restaurant values rather than inferring a relation.
            fields.set("registrationUnits", root.path("registrationUnits").deepCopy());
        }
        validation.missingFields().forEach(missing::add);
        boolean candidateTruncated = validation.candidateTruncated();

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

        String reason = validation.isAutoConfirmable() && (root.has("registrationUnits") || validation.foodCategoryName() != null)
                ? null : validation.reasonCodes().stream().findFirst().orElse("FOOD_CATEGORY_REQUIRED");
        String rawCompleteness = root.path("resultCompleteness").asText();
        String completeness = validation.resultCompleteness(rawCompleteness);
        return new ParsedCandidate(completeness, fields, tagsJson,
                confidences, evidence, missing, candidateTruncated, tags, parsedFields.get("restaurantName"),
                parsedFields.get("menu"), parsedFields.get("address"), parsedFields.get("location"),
                parsedFields.get("visitEvidence"), reason,
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

    private boolean remediateRegistrationUnits(AiExtractionResultStore.ProcessingJob job, UUID jobId, String workerId,
                                                int attemptNo, OffsetDateTime attemptStartedAt,
                                                OffsetDateTime finishedAt, AiVideoExtractionResult initialResult,
                                                ParsedCandidate initialCandidate,
                                                List<RegistrationUnitOutcome> initialOutcomes,
                                                List<RegistrationUnitOutcome> eligible,
                                                SupplementProvider supplementProvider) {
        if (registrationUnitStore == null || retryStore == null || temporaryInputCipher == null || videoResolver == null) {
            return commitService.persistBlocked(withTags(command(jobId, workerId, attemptNo, attemptStartedAt,
                    finishedAt, initialResult, initialCandidate, initialOutcomes), resolveTags(initialCandidate.tags()),
                    "AUTO_BLOCKED"));
        }
        AiExtractionResultCommitService.ProcessCommand checkpoint = withTags(command(jobId, workerId, attemptNo,
                attemptStartedAt, finishedAt, initialResult, initialCandidate, initialOutcomes),
                resolveTags(initialCandidate.tags()), "AUTO_BLOCKED");
        UUID sourceSnapshotId = commitService.persistRemediationCheckpoint(checkpoint);
        if (sourceSnapshotId == null) {
            return false;
        }
        List<AiRegistrationUnitStore.RegistrationUnitRow> sourceRows = registrationUnitStore.findBySnapshotId(sourceSnapshotId);
        Map<Integer, AiRegistrationUnitStore.RegistrationUnitRow> rowsByIndex = new LinkedHashMap<>();
        sourceRows.forEach(row -> rowsByIndex.put(row.unitIndex(), row));

        VerifiedVideo metadata;
        try {
            metadata = videoResolver.resolve(job.videoUrl()).orElse(null);
        } catch (RuntimeException exception) {
            metadata = null;
        }
        JsonNode mergedRoot = initialResult.candidates().deepCopy();
        List<RegistrationUnitOutcome> mergedOutcomes = new ArrayList<>(initialOutcomes);
        Map<UUID, Integer> claimedRetries = new LinkedHashMap<>();
        int finalAttemptNo = attemptNo;
        OffsetDateTime finalStartedAt = attemptStartedAt;
        OffsetDateTime finalFinishedAt = finishedAt;
        AiVideoExtractionResult finalResult = initialResult;

        for (RegistrationUnitOutcome failed : eligible) {
            if (metadata == null) {
                break;
            }
            int index = failed.unitIndex() - 1;
            JsonNode sourceUnit = mergedRoot.path("registrationUnits").path(index);
            String candidateUnitId = sourceUnit.path("candidateUnitId").asText("");
            String locationExpression = sourceUnit.path("location").path("value").asText("");
            AiRegistrationUnitStore.RegistrationUnitRow sourceRow = rowsByIndex.get(failed.unitIndex());
            if (candidateUnitId.isBlank() || locationExpression.isBlank() || sourceRow == null
                    || !candidateUnitId.equals(sourceUnit.path("candidateUnitId").asText())) {
                continue;
            }
            String supplementText = "YouTube title: " + nullToEmpty(metadata.title())
                    + "\nYouTube description: " + nullToEmpty(metadata.description())
                    + "\nApproved location expression for candidateUnitId " + candidateUnitId + ": " + locationExpression
                    + "\nFailure reason: " + failed.blockReason()
                    + "\nSupplement only this registration unit. Preserve candidateUnitId exactly as " + candidateUnitId + ".";
            EncryptedInput encrypted = temporaryInputCipher.encrypt(supplementText);
            OffsetDateTime claimTime = OffsetDateTime.now(clock);
            UUID retryId = UUID.randomUUID();
            ArrayNode approvedIdentifiers = objectMapper.createArrayNode();
            approvedIdentifiers.add("videoId=" + job.videoId());
            approvedIdentifiers.add("candidateUnitId=" + candidateUnitId);
            approvedIdentifiers.add("sourceRegistrationUnitId=" + sourceRow.id());
            Optional<AiRegistrationUnitRetryStore.RetryClaim> claim = retryStore.claim(
                    new AiRegistrationUnitRetryStore.RetryClaimRequest(retryId, jobId, sourceSnapshotId,
                            sourceRow.id(), candidateUnitId, failed.blockReason(), json(approvedIdentifiers), sha256(supplementText),
                            encrypted.ciphertext(), encrypted.keyId(), claimTime.plusHours(24), claimTime));
            if (claim.isEmpty()) {
                continue;
            }
            claimedRetries.put(retryId, failed.unitIndex());
            Optional<SupplementResult> response = supplementProvider.extract(jobId, retryId, supplementText);
            if (response.isEmpty()) {
                continue;
            }
            SupplementResult supplied = response.get();
            finalAttemptNo = supplied.attemptNo();
            finalStartedAt = supplied.attemptStartedAt();
            finalFinishedAt = supplied.finishedAt();
            finalResult = supplied.result();
            JsonNode replacement = validatedSupplementUnit(supplied.result().candidates(), candidateUnitId);
            if (replacement == null) {
                continue;
            }
            ((ArrayNode) mergedRoot.path("registrationUnits")).set(index, replacement.deepCopy());
            ParsedCandidate updatedCandidate = parse(mergedRoot);
            List<RegistrationUnitOutcome> updated = registrationUnitAutoExecution.execute(
                    updatedCandidate.fields(), updatedCandidate.confidences(), updatedCandidate.evidence(),
                    job.channelId(), job.videoId(), job.videoUrl(), failed.unitIndex());
            updated.stream().findFirst().ifPresent(outcome -> {
                for (int i = 0; i < mergedOutcomes.size(); i++) {
                    if (mergedOutcomes.get(i).unitIndex() == failed.unitIndex()) {
                        mergedOutcomes.set(i, outcome);
                        break;
                    }
                }
            });
        }

        ParsedCandidate finalCandidate = parse(mergedRoot);
        String status = !mergedOutcomes.isEmpty() && mergedOutcomes.stream()
                .allMatch(outcome -> "AUTO_CONFIRMED".equals(outcome.reviewStatus())) ? "AUTO_CONFIRMED" : "AUTO_BLOCKED";
        AiExtractionResultCommitService.ProcessCommand finalCommand = withTags(command(jobId, workerId,
                finalAttemptNo, finalStartedAt, finalFinishedAt, finalResult, finalCandidate, mergedOutcomes),
                resolveTags(finalCandidate.tags()), status);
        boolean committed = commitService.persistRemediationResult(finalCommand, retryStatuses(claimedRetries, mergedOutcomes));
        return committed;
    }

    private Map<UUID, String> retryStatuses(Map<UUID, Integer> retries, List<RegistrationUnitOutcome> outcomes) {
        Map<UUID, String> statuses = new LinkedHashMap<>();
        retries.forEach((retryId, unitIndex) -> {
            RegistrationUnitOutcome outcome = outcomes.stream()
                    .filter(candidate -> candidate.unitIndex() == unitIndex).findFirst().orElse(null);
            statuses.put(retryId, outcome != null && "AUTO_CONFIRMED".equals(outcome.reviewStatus())
                    ? "SUCCEEDED" : "BLOCKED");
        });
        return statuses;
    }

    private boolean isRemediable(RegistrationUnitOutcome outcome) {
        return "AUTO_BLOCKED".equals(outcome.reviewStatus())
                && Set.of("MISSING_REQUIRED_FIELD", "PLACE_NOT_FOUND", "PLACE_AMBIGUOUS", "VISIT_EVIDENCE_REQUIRED")
                .contains(outcome.blockReason());
    }

    private JsonNode findUnit(JsonNode result, String candidateUnitId) {
        JsonNode units = result == null ? null : result.path("registrationUnits");
        if (units == null || !units.isArray()) {
            return null;
        }
        JsonNode found = null;
        for (JsonNode unit : units) {
            if (candidateUnitId.equals(unit.path("candidateUnitId").asText())) {
                if (found != null) {
                    return null;
                }
                found = unit;
            }
        }
        return found;
    }

    private JsonNode validatedSupplementUnit(JsonNode result, String candidateUnitId) {
        AiCandidateValidationResult validation = candidateValidator.validateS3(result);
        JsonNode units = result == null ? null : result.path("registrationUnits");
        if (validation.isAutoRejected() || units == null || !units.isArray() || units.size() != 1
                || !candidateUnitId.equals(units.path(0).path("candidateUnitId").asText())) {
            return null;
        }
        JsonNode unit = units.path(0);
        return unit.path("location").hasNonNull("candidateUnitId")
                && !candidateUnitId.equals(unit.path("location").path("candidateUnitId").asText()) ? null : unit;
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available.", exception);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
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
                json(tag.evidence()), emptyAliases(),
                AiExtractionContract.MODEL_VERSION + "/" + AiExtractionContract.PROMPT_VERSION + "/"
                        + AiExtractionContract.SCHEMA_VERSION,
                decision, reason, autoConnectable, existingId);
    }

    private AiExtractionResultCommitService.ProcessCommand command(
            UUID jobId, String workerId, int attemptNo, OffsetDateTime attemptStartedAt,
            OffsetDateTime finishedAt, AiVideoExtractionResult result, ParsedCandidate candidate,
            List<RegistrationUnitOutcome> registrationUnitOutcomes) {
        return new AiExtractionResultCommitService.ProcessCommand(jobId, workerId, attemptNo, attemptStartedAt,
                finishedAt, result.providerRequestId(), candidate.completeness(), json(candidate.fields()),
                json(candidate.tagsJson()), json(candidate.confidences()), json(candidate.evidence()),
                json(candidate.missing()), candidate.candidateTruncated(), candidate.blockReason(),
                candidate.reviewStatus(), List.of(), registrationUnitOutcomes);
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
                command.missingFields(), command.candidateTruncated(), command.blockReason(), reviewStatus, tags,
                command.registrationUnitOutcomes());
    }

    private AiExtractionResultCommitService.ProcessCommand withReason(
            AiExtractionResultCommitService.ProcessCommand command, String reason) {
        return new AiExtractionResultCommitService.ProcessCommand(command.jobId(), command.workerId(), command.attemptNo(),
                command.attemptStartedAt(), command.finishedAt(), command.providerRequestId(), command.resultCompleteness(),
                command.candidateFields(), command.candidateTags(), command.fieldConfidences(), command.evidence(),
                command.missingFields(), command.candidateTruncated(), reason == null ? "VALIDATION_FAILED" : reason,
                "AUTO_BLOCKED", command.tags(), command.registrationUnitOutcomes());
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

    private VerifyAiContentCandidateUseCase.VisitEvidenceCandidate visitEvidence(ParsedField field) {
        if (field == null) {
            return null;
        }
        AiCandidateValidationResult.Evidence source = candidateEvidence(field.evidence());
        return new VerifyAiContentCandidateUseCase.VisitEvidenceCandidate(
                field.value(), field.confidence().doubleValue(), new VerifyAiContentCandidateUseCase.Evidence(
                        source.type() == AiCandidateValidationResult.EvidenceType.TIMESTAMP
                                ? VerifyAiContentCandidateUseCase.EvidenceType.TIMESTAMP
                                : source.type() == AiCandidateValidationResult.EvidenceType.TEXT_RANGE
                                ? VerifyAiContentCandidateUseCase.EvidenceType.TEXT_RANGE
                                : VerifyAiContentCandidateUseCase.EvidenceType.UNKNOWN,
                        source.startMs(), source.endMs(), source.startOffset(), source.endOffset(), source.sourceHash()));
    }

    private AiCandidateValidationResult.Evidence candidateEvidence(JsonNode evidence) {
        String type = evidence.path("type").asText();
        return switch (type) {
            case "TIMESTAMP" -> AiCandidateValidationResult.Evidence.timestamp(
                    evidence.path("startMs").asLong(), evidence.path("endMs").asLong());
            case "TEXT_RANGE" -> AiCandidateValidationResult.Evidence.textRange(
                    evidence.path("startOffset").asLong(), evidence.path("endOffset").asLong(),
                    evidence.path("sourceHash").asText());
            default -> AiCandidateValidationResult.Evidence.unknown();
        };
    }

    private boolean containsIgnoreCase(String value, String word) {
        return value.toLowerCase(Locale.ROOT).contains(word.toLowerCase(Locale.ROOT));
    }

    private String json(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JacksonException exception) {
            throw new IllegalStateException("AI candidate could not be serialized.", exception);
        }
    }

    private String emptyAliases() {
        return json(objectMapper.createArrayNode());
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
            boolean candidateTruncated,
            List<ParsedTag> tags,
            ParsedField restaurantName,
            ParsedField menu,
            ParsedField address,
            ParsedField location,
            ParsedField visitEvidence,
            String blockReason,
            String reviewStatus) {
    }

    private static final class CandidateBlockedException extends RuntimeException {
        CandidateBlockedException(String message) {
            super(message);
        }
    }
}

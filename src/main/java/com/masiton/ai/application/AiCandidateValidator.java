package com.masiton.ai.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.masiton.ai.application.AiCandidateValidationResult.Candidate;
import com.masiton.ai.application.AiCandidateValidationResult.Decision;
import com.masiton.ai.application.AiCandidateValidationResult.Evidence;
import com.masiton.ai.application.AiCandidateValidationResult.EvidenceType;
import com.masiton.ai.application.AiCandidateValidationResult.TagCandidate;
import com.masiton.ai.application.AiCandidateValidationResult.TagDecision;
import com.masiton.ai.application.AiCandidateValidationResult.ValidationIssue;

import tools.jackson.databind.JsonNode;

/**
 * Pure S1 candidate validator. External place, video, duplicate, and registration checks belong to
 * the orchestration layer and are intentionally outside this class.
 */
public final class AiCandidateValidator {

    private static final List<String> REQUIRED_FIELDS = List.of(
            "restaurantName", "address", "location", "visitEvidence");
    private static final Set<String> FIELD_NAMES = Set.of(
            "restaurantName", "menu", "address", "location", "visitEvidence");
    private static final Set<String> MISSING_FIELD_NAMES = Set.of(
            "restaurantName", "menu", "address", "location", "visitEvidence", "tag");
    private static final Set<String> COMMON_CANDIDATE_FIELDS = Set.of(
            "field", "value", "confidence", "evidence");
    private static final Set<String> TAG_CANDIDATE_FIELDS = Set.of(
            "field", "candidateTagId", "tagType", "rawLabel", "normalizedCode", "label",
            "confidence", "evidence");
    private static final Set<String> TAG_TYPES = Set.of(
            "MENU", "TASTE", "OCCASION", "ATMOSPHERE");
    private static final Set<String> ROOT_FIELDS = Set.of(
            "resultCompleteness", "candidates", "missingFields");
    private static final Set<String> EVIDENCE_TYPES = Set.of("TIMESTAMP", "TEXT_RANGE", "UNKNOWN");

    public AiCandidateValidationResult validate(JsonNode payload) {
        if (payload == null || !payload.isObject() || !hasOnlyFields(payload, ROOT_FIELDS)) {
            return rejected(Map.of(), null, List.of(), List.of(), List.of(),
                    issue("INVALID_PAYLOAD", null));
        }

        String resultCompleteness = textValue(payload.get("resultCompleteness"));
        if (!"COMPLETE".equals(resultCompleteness) && !"PARTIAL".equals(resultCompleteness)) {
            return rejected(Map.of(), null, List.of(), List.of(), List.of(),
                    issue("INVALID_RESULT_COMPLETENESS", null));
        }

        JsonNode candidatesNode = payload.get("candidates");
        JsonNode missingFieldsNode = payload.get("missingFields");
        if (candidatesNode == null || !candidatesNode.isArray()
                || missingFieldsNode == null || !missingFieldsNode.isArray()) {
            return rejected(Map.of(), null, List.of(), List.of(), List.of(),
                    issue("INVALID_PAYLOAD", null));
        }

        List<ValidationIssue> issues = new ArrayList<>();
        LinkedHashSet<String> declaredMissing = new LinkedHashSet<>();
        boolean structurallyInvalid = false;
        for (JsonNode missingField : missingFieldsNode) {
            String field = textValue(missingField);
            if (field == null || !MISSING_FIELD_NAMES.contains(field) || !declaredMissing.add(field)) {
                issues.add(issue("INVALID_MISSING_FIELD", field));
                structurallyInvalid = true;
            }
        }

        Map<String, List<Candidate>> candidatesByField = new LinkedHashMap<>();
        List<TagCandidate> connectableTags = new ArrayList<>();
        List<TagCandidate> rejectedTags = new ArrayList<>();
        boolean blocked = declaredMissing.stream()
                .anyMatch(field -> REQUIRED_FIELDS.contains(field) || "menu".equals(field));
        if ("PARTIAL".equals(resultCompleteness)) {
            issues.add(issue("PARTIAL_RESULT", null));
        }

        for (JsonNode candidateNode : candidatesNode) {
            if (candidateNode == null || !candidateNode.isObject()) {
                issues.add(issue("INVALID_CANDIDATE", null));
                structurallyInvalid = true;
                continue;
            }

            String field = textValue(candidateNode.get("field"));
            if (field == null || (!FIELD_NAMES.contains(field) && !"tag".equals(field))) {
                issues.add(issue("INVALID_CANDIDATE_FIELD", field));
                structurallyInvalid = true;
                continue;
            }

            if ("tag".equals(field)) {
                TagParseResult tag = parseTag(candidateNode, issues);
                if (tag.structurallyInvalid()) {
                    structurallyInvalid = true;
                }
                if (tag.blocked()) {
                    blocked = true;
                }
                if (tag.candidate() != null) {
                    if (tag.candidate().decision() == TagDecision.AUTO_CONNECTABLE) {
                        connectableTags.add(tag.candidate());
                    } else {
                        rejectedTags.add(tag.candidate());
                    }
                }
                continue;
            }

            List<Candidate> fieldCandidates = candidatesByField.computeIfAbsent(field, ignored -> new ArrayList<>());
            CandidateParseResult parsed = parseCandidate(candidateNode, field, issues);
            if (parsed.structurallyInvalid()) {
                structurallyInvalid = true;
            }
            if (parsed.blocked()) {
                blocked = true;
            }
            if (parsed.candidate() != null) {
                fieldCandidates.add(parsed.candidate());
            }
        }

        Map<String, List<Candidate>> selectedCandidates = new LinkedHashMap<>();
        LinkedHashSet<String> missingFields = new LinkedHashSet<>(declaredMissing);
        for (String requiredField : REQUIRED_FIELDS) {
            List<Candidate> fieldCandidates = candidatesByField.getOrDefault(requiredField, List.of());
            if (fieldCandidates.isEmpty()) {
                missingFields.add(requiredField);
                issues.add(issue("MISSING_REQUIRED_FIELD", requiredField));
                blocked = true;
            } else if (fieldCandidates.size() > 1) {
                issues.add(issue("MULTIPLE_CANDIDATES", requiredField));
                blocked = true;
                selectedCandidates.put(requiredField, fieldCandidates);
            } else {
                Candidate candidate = fieldCandidates.get(0);
                selectedCandidates.put(requiredField, fieldCandidates);
                if (candidate.evidence().type() == EvidenceType.UNKNOWN) {
                    issues.add(issue("UNKNOWN_EVIDENCE", requiredField));
                    blocked = true;
                }
                if ("visitEvidence".equals(requiredField)
                        && candidate.evidence().type() == EvidenceType.UNKNOWN) {
                    issues.add(issue("VISIT_EVIDENCE_REQUIRED", requiredField));
                    blocked = true;
                }
            }
        }

        List<Candidate> menuCandidates = candidatesByField.getOrDefault("menu", List.of());
        String foodCategoryName = null;
        if (menuCandidates.size() > 1) {
            issues.add(issue("MULTIPLE_CANDIDATES", "menu"));
            blocked = true;
            selectedCandidates.put("menu", menuCandidates);
        } else if (menuCandidates.size() == 1 && menuCandidates.get(0).evidence().type() != EvidenceType.UNKNOWN) {
            // This is only the extracted menu expression. No food-category mapping is performed here.
            foodCategoryName = menuCandidates.get(0).value();
            selectedCandidates.put("menu", menuCandidates);
        } else if (menuCandidates.size() == 1) {
            issues.add(issue("UNKNOWN_EVIDENCE", "menu"));
            blocked = true;
            selectedCandidates.put("menu", menuCandidates);
        }

        Decision decision;
        if (structurallyInvalid) {
            decision = Decision.AUTO_REJECTED;
        } else if (blocked) {
            decision = Decision.AUTO_BLOCKED;
        } else {
            decision = Decision.AUTO_CONFIRMED;
        }

        return new AiCandidateValidationResult(decision, selectedCandidates, foodCategoryName,
                connectableTags, rejectedTags, new ArrayList<>(missingFields), issues);
    }

    private CandidateParseResult parseCandidate(JsonNode node, String field, List<ValidationIssue> issues) {
        if (!hasOnlyFields(node, COMMON_CANDIDATE_FIELDS)) {
            issues.add(issue("INVALID_CANDIDATE", field));
            return CandidateParseResult.rejected();
        }

        boolean blocked = false;
        String value = textValue(node.get("value"));
        if (value == null || value.isBlank()) {
            issues.add(issue("MISSING_CANDIDATE_VALUE", field));
            blocked = true;
        }

        JsonNode confidenceNode = node.get("confidence");
        if (confidenceNode == null || !confidenceNode.isNumber()
                || !Double.isFinite(confidenceNode.doubleValue())
                || confidenceNode.doubleValue() < 0 || confidenceNode.doubleValue() > 1) {
            issues.add(issue("INVALID_CONFIDENCE", field));
            return CandidateParseResult.rejected();
        }

        EvidenceParseResult evidence = parseEvidence(node.get("evidence"), field, issues);
        if (evidence.structurallyInvalid()) {
            return CandidateParseResult.rejected();
        }
        if (evidence.blocked()) {
            blocked = true;
        }
        if (value == null || value.isBlank() || evidence.evidence() == null) {
            return new CandidateParseResult(null, blocked, false);
        }
        return new CandidateParseResult(new Candidate(field, value.trim(), confidenceNode.doubleValue(),
                evidence.evidence()), blocked, false);
    }

    private TagParseResult parseTag(JsonNode node, List<ValidationIssue> issues) {
        if (!hasOnlyFields(node, TAG_CANDIDATE_FIELDS)) {
            issues.add(issue("INVALID_TAG", "tag"));
            return TagParseResult.rejected();
        }

        String candidateTagId = textValue(node.get("candidateTagId"));
        String tagType = textValue(node.get("tagType"));
        String rawLabel = textValue(node.get("rawLabel"));
        String normalizedCode = textValue(node.get("normalizedCode"));
        String label = textValue(node.get("label"));
        if (isBlank(candidateTagId) || isBlank(tagType) || !TAG_TYPES.contains(tagType)
                || isBlank(rawLabel) || isBlank(normalizedCode) || isBlank(label)) {
            issues.add(issue("INVALID_TAG", "tag"));
            return TagParseResult.rejected();
        }

        JsonNode confidenceNode = node.get("confidence");
        if (confidenceNode == null || !confidenceNode.isNumber()
                || !Double.isFinite(confidenceNode.doubleValue())
                || confidenceNode.doubleValue() < 0 || confidenceNode.doubleValue() > 1) {
            issues.add(issue("INVALID_CONFIDENCE", "tag"));
            return TagParseResult.rejected();
        }

        EvidenceParseResult evidence = parseEvidence(node.get("evidence"), "tag", issues);
        if (evidence.structurallyInvalid()) {
            return TagParseResult.rejected();
        }
        if (evidence.evidence() == null) {
            return new TagParseResult(null, evidence.blocked(), false);
        }

        if (evidence.evidence().type() == EvidenceType.UNKNOWN) {
            issues.add(issue("UNKNOWN_TAG_EVIDENCE", "tag"));
            return new TagParseResult(new TagCandidate(
                    candidateTagId.trim(), tagType, rawLabel.trim(), normalizedCode.trim(), label.trim(),
                    confidenceNode.doubleValue(), evidence.evidence(), TagDecision.AUTO_REJECTED,
                    "UNKNOWN_EVIDENCE"), false, false);
        }
        return new TagParseResult(new TagCandidate(
                candidateTagId.trim(), tagType, rawLabel.trim(), normalizedCode.trim(), label.trim(),
                confidenceNode.doubleValue(), evidence.evidence(), TagDecision.AUTO_CONNECTABLE, null),
                evidence.blocked(), false);
    }

    private EvidenceParseResult parseEvidence(JsonNode node, String field, List<ValidationIssue> issues) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            issues.add(issue("MISSING_EVIDENCE", field));
            return new EvidenceParseResult(null, true, false);
        }
        if (!node.isObject()) {
            issues.add(issue("INVALID_EVIDENCE", field));
            return new EvidenceParseResult(null, false, true);
        }

        String type = textValue(node.get("type"));
        if (type == null) {
            issues.add(issue("MISSING_EVIDENCE", field));
            return new EvidenceParseResult(null, true, false);
        }
        if (!EVIDENCE_TYPES.contains(type)) {
            issues.add(issue("INVALID_EVIDENCE", field));
            return new EvidenceParseResult(null, false, true);
        }

        return switch (type) {
            case "TIMESTAMP" -> parseTimestamp(node, field, issues);
            case "TEXT_RANGE" -> parseTextRange(node, field, issues);
            case "UNKNOWN" -> parseUnknown(node, field, issues);
            default -> new EvidenceParseResult(null, false, true);
        };
    }

    private EvidenceParseResult parseTimestamp(JsonNode node, String field, List<ValidationIssue> issues) {
        if (!hasOnlyFields(node, Set.of("type", "startMs", "endMs"))) {
            issues.add(issue("INVALID_EVIDENCE", field));
            return new EvidenceParseResult(null, false, true);
        }
        JsonNode start = node.get("startMs");
        JsonNode end = node.get("endMs");
        if (!validRange(start, end)) {
            issues.add(issue("INCOMPLETE_EVIDENCE", field));
            return new EvidenceParseResult(null, true, false);
        }
        return new EvidenceParseResult(Evidence.timestamp(start.longValue(), end.longValue()), false, false);
    }

    private EvidenceParseResult parseTextRange(JsonNode node, String field, List<ValidationIssue> issues) {
        if (!hasOnlyFields(node, Set.of("type", "startOffset", "endOffset", "sourceHash"))) {
            issues.add(issue("INVALID_EVIDENCE", field));
            return new EvidenceParseResult(null, false, true);
        }
        JsonNode start = node.get("startOffset");
        JsonNode end = node.get("endOffset");
        String sourceHash = textValue(node.get("sourceHash"));
        if (!validRange(start, end) || isBlank(sourceHash)) {
            issues.add(issue("INCOMPLETE_EVIDENCE", field));
            return new EvidenceParseResult(null, true, false);
        }
        return new EvidenceParseResult(
                Evidence.textRange(start.longValue(), end.longValue(), sourceHash.trim()), false, false);
    }

    private EvidenceParseResult parseUnknown(JsonNode node, String field, List<ValidationIssue> issues) {
        if (!hasOnlyFields(node, Set.of("type"))) {
            issues.add(issue("INVALID_EVIDENCE", field));
            return new EvidenceParseResult(null, false, true);
        }
        return new EvidenceParseResult(Evidence.unknown(), false, false);
    }

    private boolean validRange(JsonNode start, JsonNode end) {
        return start != null && end != null
                && start.isIntegralNumber() && end.isIntegralNumber()
                && start.longValue() >= 0 && end.longValue() >= start.longValue();
    }

    private AiCandidateValidationResult rejected(
            Map<String, List<Candidate>> candidates,
            String foodCategoryName,
            List<TagCandidate> tags,
            List<TagCandidate> rejectedTags,
            List<String> missingFields,
            ValidationIssue issue
    ) {
        return new AiCandidateValidationResult(Decision.AUTO_REJECTED, candidates, foodCategoryName,
                tags, rejectedTags, missingFields, List.of(issue));
    }

    private ValidationIssue issue(String code, String field) {
        return new ValidationIssue(code, field);
    }

    private boolean hasOnlyFields(JsonNode object, Set<String> allowedFields) {
        for (String field : object.propertyNames()) {
            if (!allowedFields.contains(field)) {
                return false;
            }
        }
        return true;
    }

    private String textValue(JsonNode node) {
        return node != null && node.isTextual() ? node.textValue() : null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record CandidateParseResult(Candidate candidate, boolean blocked, boolean structurallyInvalid) {
        private static CandidateParseResult rejected() {
            return new CandidateParseResult(null, false, true);
        }
    }

    private record TagParseResult(TagCandidate candidate, boolean blocked, boolean structurallyInvalid) {
        private static TagParseResult rejected() {
            return new TagParseResult(null, false, true);
        }
    }

    private record EvidenceParseResult(Evidence evidence, boolean blocked, boolean structurallyInvalid) {
    }
}

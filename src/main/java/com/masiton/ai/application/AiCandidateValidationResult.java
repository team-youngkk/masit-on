package com.masiton.ai.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * S1 payload validation output. It contains only validated candidate fields and evidence metadata;
 * the provider payload itself is deliberately not retained.
 */
public record AiCandidateValidationResult(
        Decision decision,
        Map<String, Candidate> candidates,
        String foodCategoryName,
        List<TagCandidate> tags,
        List<TagCandidate> rejectedTags,
        List<String> missingFields,
        List<ValidationIssue> issues
) {

    public AiCandidateValidationResult {
        Objects.requireNonNull(decision, "decision must not be null");
        candidates = immutableMap(candidates);
        tags = immutableList(tags);
        rejectedTags = immutableList(rejectedTags);
        missingFields = immutableList(missingFields);
        issues = immutableList(issues);
    }

    public enum Decision {
        AUTO_CONFIRMED,
        AUTO_BLOCKED,
        AUTO_REJECTED
    }

    public enum EvidenceType {
        TIMESTAMP,
        TEXT_RANGE,
        UNKNOWN
    }

    public enum TagDecision {
        AUTO_CONNECTABLE,
        AUTO_REJECTED
    }

    public record Candidate(String field, String value, double confidence, Evidence evidence) {
        public Candidate {
            Objects.requireNonNull(field, "field must not be null");
            Objects.requireNonNull(value, "value must not be null");
            Objects.requireNonNull(evidence, "evidence must not be null");
        }
    }

    /** Location metadata only; TEXT_RANGE never contains the source text. */
    public record Evidence(
            EvidenceType type,
            Long startMs,
            Long endMs,
            Long startOffset,
            Long endOffset,
            String sourceHash
    ) {
        public Evidence {
            Objects.requireNonNull(type, "type must not be null");
        }

        public static Evidence timestamp(long startMs, long endMs) {
            return new Evidence(EvidenceType.TIMESTAMP, startMs, endMs, null, null, null);
        }

        public static Evidence textRange(long startOffset, long endOffset, String sourceHash) {
            return new Evidence(EvidenceType.TEXT_RANGE, null, null, startOffset, endOffset, sourceHash);
        }

        public static Evidence unknown() {
            return new Evidence(EvidenceType.UNKNOWN, null, null, null, null, null);
        }
    }

    public record TagCandidate(
            String candidateTagId,
            String tagType,
            String rawLabel,
            String normalizedCode,
            String label,
            double confidence,
            Evidence evidence,
            TagDecision decision,
            String rejectionReason
    ) {
        public TagCandidate {
            Objects.requireNonNull(candidateTagId, "candidateTagId must not be null");
            Objects.requireNonNull(tagType, "tagType must not be null");
            Objects.requireNonNull(rawLabel, "rawLabel must not be null");
            Objects.requireNonNull(normalizedCode, "normalizedCode must not be null");
            Objects.requireNonNull(label, "label must not be null");
            Objects.requireNonNull(evidence, "evidence must not be null");
            Objects.requireNonNull(decision, "decision must not be null");
        }

        public boolean isAutoConnectable() {
            return decision == TagDecision.AUTO_CONNECTABLE;
        }
    }

    public record ValidationIssue(String code, String field) {
        public ValidationIssue {
            Objects.requireNonNull(code, "code must not be null");
        }
    }

    public boolean isAutoConfirmable() {
        return decision == Decision.AUTO_CONFIRMED;
    }

    public boolean canAutoConfirm() {
        return isAutoConfirmable();
    }

    public boolean isAutoBlocked() {
        return decision == Decision.AUTO_BLOCKED;
    }

    public boolean isAutoRejected() {
        return decision == Decision.AUTO_REJECTED;
    }

    public Decision status() {
        return decision;
    }

    public Decision validationStatus() {
        return decision;
    }

    public Map<String, Candidate> fieldCandidates() {
        return candidates;
    }

    public Optional<Candidate> candidate(String field) {
        return Optional.ofNullable(candidates.get(field));
    }

    public Optional<String> foodCategory() {
        return Optional.ofNullable(foodCategoryName);
    }

    public boolean hasFoodCategoryName() {
        return foodCategoryName != null;
    }

    public boolean isFoodCategoryMissing() {
        return foodCategoryName == null;
    }

    public List<TagCandidate> connectableTags() {
        return tags;
    }

    public List<TagCandidate> allTags() {
        List<TagCandidate> all = new ArrayList<>(tags.size() + rejectedTags.size());
        all.addAll(tags);
        all.addAll(rejectedTags);
        return List.copyOf(all);
    }

    public List<String> reasonCodes() {
        return issues.stream().map(ValidationIssue::code).toList();
    }

    private static <T> List<T> immutableList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}

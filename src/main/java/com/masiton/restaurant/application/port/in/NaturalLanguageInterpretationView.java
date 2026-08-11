package com.masiton.restaurant.application.port.in;

import java.util.List;

/** API 응답으로 내보낼 안전한 자연어 해석 요약이다. */
public record NaturalLanguageInterpretationView(
        Status status,
        AppliedConditions appliedConditions,
        List<IgnoredCondition> ignoredConditions,
        List<Conflict> conflicts,
        String parserVersion
) {

    public enum Status {
        APPLIED,
        PARTIAL,
        FAILED
    }

    public record AppliedConditions(
            String query,
            String district,
            String category,
            String creatorId,
            List<String> tags
    ) {
    }

    public record IgnoredCondition(String type, String text, String reason) {
    }

    public record Conflict(String field, String resolution) {
    }
}

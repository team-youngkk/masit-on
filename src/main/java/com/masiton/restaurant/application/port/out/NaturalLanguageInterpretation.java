package com.masiton.restaurant.application.port.out;

import java.util.List;

/** 해석기가 반환하는 제한된 구조화 결과다. 자유 형식 답변이나 검색 결과를 포함하지 않는다. */
public record NaturalLanguageInterpretation(
        Status status,
        AppliedConditions appliedConditions,
        List<IgnoredCondition> ignoredConditions,
        List<Conflict> conflicts,
        String parserVersion
) {

    public NaturalLanguageInterpretation {
        status = status == null ? Status.FAILED : status;
        appliedConditions = appliedConditions == null ? AppliedConditions.empty() : appliedConditions;
        ignoredConditions = ignoredConditions == null ? List.of() : List.copyOf(ignoredConditions);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }

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

        public AppliedConditions {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }

        public static AppliedConditions empty() {
            return new AppliedConditions(null, null, null, null, List.of());
        }

        public boolean hasAny() {
            return query != null || district != null || category != null || creatorId != null || !tags.isEmpty();
        }
    }

    public record IgnoredCondition(Kind type, String text, String reason) {

        public enum Kind {
            UNSUPPORTED,
            UNRESOLVED,
            CONFLICT
        }
    }

    public record Conflict(Field field, Resolution resolution) {

        public enum Field {
            query,
            district,
            category,
            creatorId,
            tags
        }

        public enum Resolution {
            DIRECT_FILTER_WON
        }
    }
}

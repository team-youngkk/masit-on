package com.masiton.restaurant.application.naturallanguage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** 기존 맛집 목록 Query에 전달할 수 있는, 외부 의존성이 없는 구조화 조건이다. */
public record NaturalLanguageFilters(
        String query,
        String district,
        String category,
        String creatorId,
        List<String> tags) {

    public NaturalLanguageFilters {
        query = trimToNull(query);
        district = trimToNull(district);
        category = trimToNull(category);
        creatorId = trimToNull(creatorId);
        tags = normalizeTags(tags);
    }

    public static NaturalLanguageFilters empty() {
        return new NaturalLanguageFilters(null, null, null, null, List.of());
    }

    public boolean has(ConditionField field) {
        return switch (Objects.requireNonNull(field)) {
            case QUERY -> query != null;
            case DISTRICT -> district != null;
            case CATEGORY -> category != null;
            case CREATOR_ID -> creatorId != null;
            case TAGS -> !tags.isEmpty();
        };
    }

    public Object value(ConditionField field) {
        return switch (Objects.requireNonNull(field)) {
            case QUERY -> query;
            case DISTRICT -> district;
            case CATEGORY -> category;
            case CREATOR_ID -> creatorId;
            case TAGS -> tags;
        };
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static List<String> normalizeTags(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                normalized.add(trimmed);
            }
        }
        return List.copyOf(new ArrayList<>(normalized));
    }
}

package com.masiton.restaurant.application.naturallanguage;

import java.util.Objects;

/** 직접 지정 필터가 자연어 조건을 대체한 필드 요약이다. */
public record NaturalLanguageConflict(
        ConditionField field,
        ConflictResolution resolution) {

    public NaturalLanguageConflict {
        field = Objects.requireNonNull(field);
        resolution = Objects.requireNonNull(resolution);
    }
}

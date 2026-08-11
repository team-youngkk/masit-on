package com.masiton.restaurant.application.naturallanguage;

import java.util.Objects;

/** 적용하지 않은 자연어 조건의 안전한 요약이다. 원문 전체를 보존하지 않는다. */
public record IgnoredCondition(
        IgnoredConditionType type,
        String text,
        String reason) {

    public IgnoredCondition {
        type = Objects.requireNonNull(type);
        text = Objects.requireNonNull(text);
        reason = Objects.requireNonNull(reason);
    }
}

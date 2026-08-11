package com.masiton.restaurant.application.naturallanguage;

import java.util.List;
import java.util.Objects;

/** P1 parser가 만든 해석 상태와 실제 적용 조건이다. */
public record NaturalLanguageInterpretation(
        InterpretationStatus status,
        NaturalLanguageFilters parsedConditions,
        NaturalLanguageFilters appliedConditions,
        List<IgnoredCondition> ignoredConditions,
        List<NaturalLanguageConflict> conflicts,
        String parserVersion) {

    public NaturalLanguageInterpretation {
        status = Objects.requireNonNull(status);
        parsedConditions = Objects.requireNonNull(parsedConditions);
        appliedConditions = Objects.requireNonNull(appliedConditions);
        ignoredConditions = List.copyOf(Objects.requireNonNull(ignoredConditions));
        conflicts = List.copyOf(Objects.requireNonNull(conflicts));
        parserVersion = Objects.requireNonNull(parserVersion);
    }
}

package com.masiton.restaurant.application.naturallanguage;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** P1 parser가 만든 해석 상태와 실제 적용 조건이다. */
public record NaturalLanguageInterpretation(
        InterpretationStatus status,
        NaturalLanguageFilters parsedConditions,
        NaturalLanguageFilters appliedConditions,
        List<IgnoredCondition> ignoredConditions,
        List<NaturalLanguageConflict> conflicts,
        Set<ConditionField> unresolvedFields,
        String parserVersion) {

    public NaturalLanguageInterpretation {
        status = Objects.requireNonNull(status);
        parsedConditions = Objects.requireNonNull(parsedConditions);
        appliedConditions = Objects.requireNonNull(appliedConditions);
        ignoredConditions = List.copyOf(Objects.requireNonNull(ignoredConditions));
        conflicts = List.copyOf(Objects.requireNonNull(conflicts));
        unresolvedFields = Set.copyOf(Objects.requireNonNull(unresolvedFields));
        parserVersion = Objects.requireNonNull(parserVersion);
    }

    public NaturalLanguageInterpretation(
            InterpretationStatus status,
            NaturalLanguageFilters parsedConditions,
            NaturalLanguageFilters appliedConditions,
            List<IgnoredCondition> ignoredConditions,
            List<NaturalLanguageConflict> conflicts,
            String parserVersion) {
        this(status, parsedConditions, appliedConditions, ignoredConditions, conflicts, Set.of(), parserVersion);
    }
}

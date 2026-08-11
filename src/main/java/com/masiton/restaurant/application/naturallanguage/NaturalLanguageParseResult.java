package com.masiton.restaurant.application.naturallanguage;

import java.util.Objects;

public record NaturalLanguageParseResult(NaturalLanguageInterpretation interpretation) {

    public NaturalLanguageParseResult {
        interpretation = Objects.requireNonNull(interpretation);
    }

    public InterpretationStatus status() {
        return interpretation.status();
    }

    public NaturalLanguageFilters appliedConditions() {
        return interpretation.appliedConditions();
    }
}

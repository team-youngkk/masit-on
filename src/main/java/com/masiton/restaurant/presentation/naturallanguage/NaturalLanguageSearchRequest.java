package com.masiton.restaurant.presentation.naturallanguage;

import java.util.List;

public record NaturalLanguageSearchRequest(
        String sentence,
        Filters filters,
        Integer page,
        Integer size
) {

    public record Filters(
            String query,
            String district,
            String category,
            String creatorId,
            List<String> tags
    ) {
    }
}

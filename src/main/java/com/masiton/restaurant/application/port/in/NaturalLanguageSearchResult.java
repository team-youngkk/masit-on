package com.masiton.restaurant.application.port.in;

public record NaturalLanguageSearchResult(
        NaturalLanguageInterpretationView interpretation,
        RestaurantSearchResult results
) {
}

package com.masiton.restaurant.presentation.naturallanguage;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAnySetter;

public record NaturalLanguageSearchRequest(
        String sentence,
        Filters filters,
        Integer page,
        Integer size
) {

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("지원하지 않는 요청 필드입니다: " + fieldName);
    }

    public record Filters(
            String query,
            String district,
            String category,
            String creatorId,
            List<String> tags
    ) {

        @JsonAnySetter
        public void rejectUnknownField(String fieldName, Object value) {
            throw new IllegalArgumentException("지원하지 않는 filters 필드입니다: " + fieldName);
        }
    }
}

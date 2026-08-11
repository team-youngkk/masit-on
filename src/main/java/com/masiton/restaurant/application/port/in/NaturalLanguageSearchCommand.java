package com.masiton.restaurant.application.port.in;

import java.util.List;

/** 자연어 검색 Application 입력이다. */
public record NaturalLanguageSearchCommand(
        String sentence,
        String query,
        String district,
        String category,
        String creatorId,
        List<String> tags,
        int page,
        int size,
        String clientAddress
) {

    public NaturalLanguageSearchCommand {
        tags = tags == null ? List.of() : List.copyOf(tags);
        clientAddress = clientAddress == null || clientAddress.isBlank() ? "unknown" : clientAddress;
    }

    public NaturalLanguageSearchCommand(
            String sentence,
            String query,
            String district,
            String category,
            String creatorId,
            List<String> tags,
            int page,
            int size) {
        this(sentence, query, district, category, creatorId, tags, page, size, "unknown");
    }
}

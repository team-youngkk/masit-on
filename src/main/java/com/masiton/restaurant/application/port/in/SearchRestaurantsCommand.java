package com.masiton.restaurant.application.port.in;

import java.util.List;

/**
 * Presentation이 정규화하지 않은 원본 값을 그대로 전달한다.
 * trim, 존재 확인과 식별자 파싱은 Application이 수행한다. page·size는 Presentation이
 * 맛집 탐색 페이지네이션 계약(1 이상, 10·20·21·50)으로 이미 검증한 값이다.
 */
public record SearchRestaurantsCommand(
        String query,
        String district,
        String category,
        String creatorId,
        List<String> tags,
        int page,
        int size) {

    public SearchRestaurantsCommand(
            String query,
            String district,
            String category,
            String creatorId,
            int page,
            int size
    ) {
        this(query, district, category, creatorId, List.of(), page, size);
    }

    public SearchRestaurantsCommand {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}

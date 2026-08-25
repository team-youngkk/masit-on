package com.masiton.security.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RestaurantPathClassifierTest {

    @Test
    @DisplayName("공개 리터럴 경로 세그먼트는 맛집 상세 조회가 아니다")
    void 리터럴공개경로_상세조회아님() {
        assertThat(RestaurantPathClassifier.isRestaurantDetailPath("/api/restaurants/popular")).isFalse();
        assertThat(RestaurantPathClassifier.isRestaurantDetailPath("/api/restaurants/map-points")).isFalse();
        assertThat(RestaurantPathClassifier.isRestaurantDetailPath("/api/restaurants/natural-language-search")).isFalse();
        assertThat(RestaurantPathClassifier.isRestaurantDetailPath("/api/restaurants/filter-options")).isFalse();
        assertThat(RestaurantPathClassifier.isNonIdentifierPublicPath(
                "/api/restaurants/natural-language-search")).isTrue();
        assertThat(RestaurantPathClassifier.isNonIdentifierPublicPath(
                "/api/restaurants/filter-options")).isTrue();
    }

    @Test
    @DisplayName("맛집 식별자 UUID 경로는 맛집 상세 조회다")
    void 맛집식별자경로_상세조회임() {
        assertThat(RestaurantPathClassifier.isRestaurantDetailPath("/api/restaurants/10000000-0000-4000-8000-000000000001")).isTrue();
    }

    @Test
    @DisplayName("기타 하위 경로나 접두사가 다른 경로는 상세 조회가 아니다")
    void 기타경로_상세조회아님() {
        assertThat(RestaurantPathClassifier.isRestaurantDetailPath("/api/restaurants")).isFalse();
        assertThat(RestaurantPathClassifier.isRestaurantDetailPath("/api/restaurants/")).isFalse();
        assertThat(RestaurantPathClassifier.isRestaurantDetailPath("/api/restaurants/x/y")).isFalse();
        assertThat(RestaurantPathClassifier.isRestaurantDetailPath("/api/creators")).isFalse();
    }
}

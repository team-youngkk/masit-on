package com.masiton.restaurant.presentation.rest;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API-POPULAR-001 인기 맛집 조회의 Controller-PostgreSQL 인수 테스트다.
 * 근거: docs/05-specs/api/discovery/popular-restaurant-api.md
 */
@SpringBootTest
@com.masiton.test.TestProfile
@AutoConfigureMockMvc
@DisplayName("인기 맛집 API")
class PopularRestaurantApiTest extends com.masiton.test.FullContextIntegrationTest {

    private static final UUID SEED_REGION_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID SEED_FOOD_CATEGORY_ID =
            UUID.fromString("20000000-0000-4000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearAggregationSources() {
        jdbcTemplate.update("DELETE FROM favorite");
        jdbcTemplate.update("DELETE FROM restaurant");
        jdbcTemplate.update("DELETE FROM member_account");
    }

    @Test
    @DisplayName("정상 요청은 인증 없이 200과 계약이 정의한 응답 스키마를 반환한다")
    void findPopularRestaurants_정상요청_인증없이200과응답스키마를반환한다() throws Exception {
        // given
        UUID firstMemberId = UUID.randomUUID();
        UUID secondMemberId = UUID.randomUUID();
        insertMember(firstMemberId);
        insertMember(secondMemberId);
        UUID popularRestaurantId = UUID.randomUUID();
        UUID lessPopularRestaurantId = UUID.randomUUID();
        insertRestaurant(popularRestaurantId, "인기 맛집");
        insertRestaurant(lessPopularRestaurantId, "덜 인기 있는 맛집");
        OffsetDateTime favoritedAt = OffsetDateTime.parse("2026-07-01T00:00:00Z");
        insertFavorite(firstMemberId, popularRestaurantId, favoritedAt);
        insertFavorite(secondMemberId, popularRestaurantId, favoritedAt);
        insertFavorite(firstMemberId, lessPopularRestaurantId, favoritedAt);

        // when & then
        mockMvc.perform(get("/api/restaurants/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].rank").value(1))
                .andExpect(jsonPath("$.items[0].restaurantId").value(popularRestaurantId.toString()))
                .andExpect(jsonPath("$.items[0].name").value("인기 맛집"))
                .andExpect(jsonPath("$.items[0].roadAddress").value("서울특별시 종로구 테스트로 1"))
                .andExpect(jsonPath("$.items[0].category").value("한식"))
                .andExpect(jsonPath("$.items[0].favoriteCount").value(2))
                .andExpect(jsonPath("$.items[1].rank").value(2))
                .andExpect(jsonPath("$.items[1].restaurantId").value(lessPopularRestaurantId.toString()))
                .andExpect(jsonPath("$.items[1].favoriteCount").value(1));
    }

    @Test
    @DisplayName("조건에 맞는 맛집이 없으면 200과 빈 목록을 반환한다")
    void findPopularRestaurants_결과없음_200과빈목록을반환한다() throws Exception {
        mockMvc.perform(get("/api/restaurants/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("지원하지 않는 쿼리 파라미터가 있으면 400 INVALID_REQUEST를 반환한다")
    void findPopularRestaurants_정의되지않은파라미터_400INVALID_REQUEST를반환한다() throws Exception {
        mockMvc.perform(get("/api/restaurants/popular").param("page", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private void insertMember(UUID memberId) {
        jdbcTemplate.update("""
                INSERT INTO member_account
                    (id, email, password_hash, email_verified_at, status)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, 'ACTIVE')
                """, memberId, memberId + "@example.com", "password-hash");
    }

    private void insertRestaurant(UUID restaurantId, String name) {
        jdbcTemplate.update(
                "INSERT INTO restaurant "
                        + "(id, region_id, food_category_id, name, kakao_place_id, kakao_place_url, "
                        + "road_address, phone_number) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                restaurantId, SEED_REGION_ID, SEED_FOOD_CATEGORY_ID, name, "KAKAO-" + restaurantId,
                "https://example.com/place/" + restaurantId, "서울특별시 종로구 테스트로 1", "02-1234-5678");
    }

    private void insertFavorite(UUID memberId, UUID restaurantId, OffsetDateTime favoritedAt) {
        jdbcTemplate.update("""
                INSERT INTO favorite (member_id, restaurant_id, favorited_at)
                VALUES (?, ?, ?)
                """, memberId, restaurantId, favoritedAt);
    }
}

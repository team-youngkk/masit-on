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
 * API-DISCOVERY-001 맛집 목록 및 조건 검색의 Controller-PostgreSQL 인수 테스트다.
 * 근거: docs/05-specs/api/discovery/restaurant-discovery-api.md
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("맛집 검색 API")
class RestaurantSearchApiTest {

    private static final UUID MAPO_REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000014");
    private static final UUID KOREAN_CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17.10-alpine")
                    .withDatabaseName("masiton")
                    .withUsername("masiton")
                    .withPassword("masiton_local");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUpTransactionalTables() {
        jdbcTemplate.execute("TRUNCATE TABLE visit, video, creator, restaurant CASCADE");
    }

    @Test
    @DisplayName("정상 요청은 200과 계약이 정의한 응답 스키마를 반환한다")
    void search_정상요청_200과응답스키마를반환한다() throws Exception {
        // given
        UUID restaurantId = insertRestaurant("마포 맛집", MAPO_REGION_ID, KOREAN_CATEGORY_ID);
        UUID creatorId = insertCreator("테스트 채널");
        String channelId = "UC-" + creatorId;
        UUID videoId = insertVideo(creatorId, channelId);
        insertVisit(restaurantId, creatorId, videoId);

        // when & then
        mockMvc.perform(get("/api/restaurants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(restaurantId.toString()))
                .andExpect(jsonPath("$.items[0].name").value("마포 맛집"))
                .andExpect(jsonPath("$.items[0].district").value("마포구"))
                .andExpect(jsonPath("$.items[0].category").value("한식"))
                .andExpect(jsonPath("$.items[0].visitedBy[0].id").value(creatorId.toString()))
                .andExpect(jsonPath("$.items[0].visitedBy[0].channelName").value("테스트 채널"))
                .andExpect(jsonPath("$.items[0].remainingVisitedByCount").value(0))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.page.hasNext").value(false));
    }

    @Test
    @DisplayName("조건을 만족하는 맛집이 없으면 200과 빈 목록을 반환한다")
    void search_결과없음_200과빈목록을반환한다() throws Exception {
        mockMvc.perform(get("/api/restaurants").param("query", "존재하지않는이름"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.page.totalPages").value(0))
                .andExpect(jsonPath("$.page.hasNext").value(false));
    }

    @Test
    @DisplayName("page가 0이면 400 INVALID_FIELD_VALUE(page)를 반환한다")
    void search_page가0_400INVALID_FIELD_VALUE를반환한다() throws Exception {
        mockMvc.perform(get("/api/restaurants").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("page"));
    }

    @Test
    @DisplayName("size가 허용되지 않은 값이면 400 INVALID_FIELD_VALUE(size)를 반환한다")
    void search_size허용되지않은값_400INVALID_FIELD_VALUE를반환한다() throws Exception {
        mockMvc.perform(get("/api/restaurants").param("size", "15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    @DisplayName("district가 서울 자치구가 아니면 400 INVALID_FIELD_VALUE(district)를 반환한다")
    void search_district가자치구가아님_400INVALID_FIELD_VALUE를반환한다() throws Exception {
        mockMvc.perform(get("/api/restaurants").param("district", "없는구"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("district"));
    }

    @Test
    @DisplayName("category가 사전 정의된 값이 아니면 400 INVALID_FIELD_VALUE(category)를 반환한다")
    void search_category가사전정의값아님_400INVALID_FIELD_VALUE를반환한다() throws Exception {
        mockMvc.perform(get("/api/restaurants").param("category", "없는음식"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("category"));
    }

    @Test
    @DisplayName("creatorId가 UUID 형식이 아니면 400 INVALID_IDENTIFIER를 반환한다")
    void search_creatorId형식오류_400INVALID_IDENTIFIER를반환한다() throws Exception {
        mockMvc.perform(get("/api/restaurants").param("creatorId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDENTIFIER"))
                .andExpect(jsonPath("$.errors[0].field").value("creatorId"));
    }

    @Test
    @DisplayName("정의되지 않은 쿼리 파라미터는 400 INVALID_REQUEST를 반환한다")
    void search_정의되지않은파라미터_400INVALID_REQUEST를반환한다() throws Exception {
        mockMvc.perform(get("/api/restaurants").param("sort", "asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("같은 파라미터를 반복하면 400 INVALID_FIELD_VALUE(해당 field)를 반환한다")
    void search_반복파라미터_400INVALID_FIELD_VALUE를반환한다() throws Exception {
        mockMvc.perform(get("/api/restaurants").param("district", "마포구", "강남구"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("district"));
    }

    @Test
    @DisplayName("size를 지정하지 않으면 기본값 20이 적용된다")
    void search_size미지정_기본값20이적용된다() throws Exception {
        mockMvc.perform(get("/api/restaurants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(20));
    }

    @Test
    @DisplayName("허용된 페이지 크기 10, 20, 50은 그대로 응답 page.size에 반영된다")
    void search_허용된페이지크기_응답에반영된다() throws Exception {
        for (int size : new int[] {10, 20, 50}) {
            mockMvc.perform(get("/api/restaurants").param("size", String.valueOf(size)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page.size").value(size));
        }
    }

    private UUID insertRestaurant(String name, UUID regionId, UUID foodCategoryId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO restaurant "
                        + "(id, region_id, food_category_id, name, kakao_place_id, kakao_place_url, "
                        + "road_address, phone_number) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, regionId, foodCategoryId, name, "KAKAO-" + UUID.randomUUID(),
                "https://example.com/place/" + id, "서울특별시 테스트로 1", "02-1234-5678");
        return id;
    }

    private UUID insertCreator(String channelName) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO creator (id, external_channel_id, channel_name, channel_url, external_status_checked_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                id, "UC-" + id, channelName, "https://example.com/channel/" + id, OffsetDateTime.now());
        return id;
    }

    private UUID insertVideo(UUID creatorId, String publisherExternalChannelId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO video "
                        + "(id, creator_id, external_video_id, publisher_external_channel_id, title, "
                        + "source_url, thumbnail_url, external_status_checked_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, creatorId, "VID-" + UUID.randomUUID().toString().substring(0, 20), publisherExternalChannelId,
                "테스트 영상", "https://example.com/video/" + id, "https://example.com/thumbnail/" + id,
                OffsetDateTime.now());
        return id;
    }

    private UUID insertVisit(UUID restaurantId, UUID creatorId, UUID videoId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO visit (id, restaurant_id, creator_id, video_id) VALUES (?, ?, ?, ?)",
                id, restaurantId, creatorId, videoId);
        return id;
    }
}

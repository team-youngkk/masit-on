package com.masiton.restaurant.presentation.rest;

import java.math.BigDecimal;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API-MAP-001 지도 영역 맛집 조회의 Controller-PostgreSQL-Redis 인수 테스트다.
 * 근거: docs/05-specs/api/discovery/map-discovery-api.md
 */
@SpringBootTest
@com.masiton.test.TestProfile
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("지도 영역 맛집 조회 API")
class RestaurantMapPointsApiTest {

    private static final UUID MAPO_REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000014");
    private static final UUID KOREAN_CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17.10-alpine")
                    .withDatabaseName("masiton")
                    .withUsername("masiton")
                    .withPassword("masiton_local");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.8-alpine"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUpState() throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE visit, video, creator, restaurant CASCADE");
        REDIS.execInContainer("redis-cli", "FLUSHALL");
    }

    @Test
    @DisplayName("정상 요청은 200과 계약이 정의한 응답 스키마를 반환한다")
    void mapPoints_정상요청_200과응답스키마를반환한다() throws Exception {
        // given
        UUID restaurantId = insertRestaurant("마포 맛집", "37.5665", "126.9780");

        // when & then
        mockMvc.perform(get("/api/restaurants/map-points")
                        .param("south", "37.5").param("west", "126.9")
                        .param("north", "37.6").param("east", "127.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.limit").value(200))
                .andExpect(jsonPath("$.items[0].id").value(restaurantId.toString()))
                .andExpect(jsonPath("$.items[0].name").value("마포 맛집"))
                .andExpect(jsonPath("$.items[0].category").value("한식"))
                .andExpect(jsonPath("$.items[0].addressSummary").value("서울특별시 테스트로 1"))
                .andExpect(jsonPath("$.items[0].coordinate.latitude").value(37.5665))
                .andExpect(jsonPath("$.items[0].coordinate.longitude").value(126.978));
    }

    @Test
    @DisplayName("영역 밖 좌표와 좌표 없는 맛집은 결과에서 제외한다")
    void mapPoints_영역밖과좌표없음_결과에서제외한다() throws Exception {
        // given
        insertRestaurant("영역 밖 맛집", "37.9", "127.5");
        insertRestaurant("좌표 없는 맛집", null, null);

        // when & then
        mockMvc.perform(get("/api/restaurants/map-points")
                        .param("south", "37.5").param("west", "126.9")
                        .param("north", "37.6").param("east", "127.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("영역 값이 하나라도 없으면 400 MISSING_REQUIRED_FIELD를 반환한다")
    void mapPoints_영역값누락_400MISSING_REQUIRED_FIELD를반환한다() throws Exception {
        mockMvc.perform(get("/api/restaurants/map-points")
                        .param("west", "126.9").param("north", "37.6").param("east", "127.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUIRED_FIELD"))
                .andExpect(jsonPath("$.errors[0].field").value("south"));
    }

    @Test
    @DisplayName("영역 값이 decimal 형식이 아니면 400 INVALID_FIELD_VALUE를 반환한다")
    void mapPoints_영역값형식오류_400INVALID_FIELD_VALUE를반환한다() throws Exception {
        mockMvc.perform(get("/api/restaurants/map-points")
                        .param("south", "not-a-number").param("west", "126.9")
                        .param("north", "37.6").param("east", "127.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("south"));
    }

    @Test
    @DisplayName("south가 north보다 크거나 같으면 400 INVALID_FIELD_VALUE(north)를 반환한다")
    void mapPoints_south가north이상_400INVALID_FIELD_VALUE를반환한다() throws Exception {
        mockMvc.perform(get("/api/restaurants/map-points")
                        .param("south", "37.7").param("west", "126.9")
                        .param("north", "37.6").param("east", "127.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("north"));
    }

    @Test
    @DisplayName("creatorId가 UUID 형식이 아니면 400 INVALID_IDENTIFIER를 반환한다")
    void mapPoints_creatorId형식오류_400INVALID_IDENTIFIER를반환한다() throws Exception {
        mockMvc.perform(get("/api/restaurants/map-points")
                        .param("south", "37.5").param("west", "126.9")
                        .param("north", "37.6").param("east", "127.0")
                        .param("creatorId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDENTIFIER"))
                .andExpect(jsonPath("$.errors[0].field").value("creatorId"));
    }

    @Test
    @DisplayName("정의되지 않은 쿼리 파라미터는 400 INVALID_REQUEST를 반환한다")
    void mapPoints_정의되지않은파라미터_400INVALID_REQUEST를반환한다() throws Exception {
        mockMvc.perform(get("/api/restaurants/map-points")
                        .param("south", "37.5").param("west", "126.9")
                        .param("north", "37.6").param("east", "127.0")
                        .param("sort", "asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("결과가 정확히 200건이면 AVAILABLE과 200개 전체를 반환한다")
    void mapPoints_결과200건_AVAILABLE과200개전체를반환한다() throws Exception {
        // given
        for (int index = 0; index < 200; index++) {
            insertRestaurant(String.format("맛집 %03d", index), "37.5665", "126.9780");
        }

        // when & then
        mockMvc.perform(get("/api/restaurants/map-points")
                        .param("south", "37.5").param("west", "126.9")
                        .param("north", "37.6").param("east", "127.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.limit").value(200))
                .andExpect(jsonPath("$.items.length()").value(200));
    }

    @Test
    @DisplayName("결과가 201건이면 200개 상한을 넘겨 TOO_MANY_RESULTS와 빈 items를 반환한다")
    void mapPoints_결과201건_TOO_MANY_RESULTS와빈items를반환한다() throws Exception {
        // given
        for (int index = 0; index < 201; index++) {
            insertRestaurant(String.format("맛집 %03d", index), "37.5665", "126.9780");
        }

        // when & then
        mockMvc.perform(get("/api/restaurants/map-points")
                        .param("south", "37.5").param("west", "126.9")
                        .param("north", "37.6").param("east", "127.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultStatus").value("TOO_MANY_RESULTS"))
                .andExpect(jsonPath("$.limit").value(200))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("클라이언트 출처당 초당 4회를 초과하면 429와 Retry-After를 반환한다")
    void mapPoints_초당4회초과_429와RetryAfter를반환한다() throws Exception {
        for (int attempt = 0; attempt < 4; attempt++) {
            mockMvc.perform(get("/api/restaurants/map-points")
                            .param("south", "37.5").param("west", "126.9")
                            .param("north", "37.6").param("east", "127.0"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/restaurants/map-points")
                        .param("south", "37.5").param("west", "126.9")
                        .param("north", "37.6").param("east", "127.0"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(header().string("Retry-After", "1"));
    }

    private UUID insertRestaurant(String name, String latitude, String longitude) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO restaurant "
                        + "(id, region_id, food_category_id, name, kakao_place_id, kakao_place_url, "
                        + "road_address, phone_number, latitude, longitude) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, MAPO_REGION_ID, KOREAN_CATEGORY_ID, name, "KAKAO-" + UUID.randomUUID(),
                "https://example.com/place/" + id, "서울특별시 테스트로 1", "02-1234-5678",
                latitude == null ? null : new BigDecimal(latitude),
                longitude == null ? null : new BigDecimal(longitude));
        return id;
    }
}

package com.masiton.restaurant.presentation.rest;

import static com.masiton.test.IntegrationTestFixtures.courseRequestJson;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.masiton.restaurant.application.port.out.CourseRouteFailureCategory;
import com.masiton.restaurant.application.port.out.CourseRouteLeg;
import com.masiton.restaurant.application.port.out.CourseRouteProviderException;
import com.masiton.restaurant.application.port.out.CourseRouteProviderPort;
import com.masiton.restaurant.application.port.out.CourseRouteResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.masiton.test.FullContextIntegrationTest;

/**
 * 코스 추천 외부 실패 격리와 만료 경계 인수 테스트다.
 * 근거: docs/05-specs/api/discovery/restaurant-course-recommendation-api.md,
 * docs/08-planning/third-expansion-test-matrix.md, docs/troubleshooting/pr-171-course-route-review.md
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("맛집 코스 추천 외부 실패 격리와 만료 경계")
class CourseRouteFailureIsolationApiTest extends FullContextIntegrationTest {

    private static final String COURSE_ROUTES_PATH = "/api/restaurants/course-routes";
    private static final UUID MAPO_REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000014");
    private static final UUID KOREAN_CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CourseRouteProviderPort courseRouteProviderPort;

    @BeforeEach
    void cleanUpState() throws Exception {
        cleanupTransactionalState(jdbcTemplate);
        REDIS.execInContainer("redis-cli", "FLUSHALL");
    }

    @Test
    @DisplayName("코스 경로가 TIMEOUT으로 502 COURSE_ROUTE_PROVIDER_UNAVAILABLE을 반환하는 동안에도 기존 공개 탐색 API 3종은 정상 200을 반환하고 실패 응답에 거리·시간 추정값이 없다")
    void courseRoute_ProviderTIMEOUT실패동안_기존공개탐색API3종은정상200을반환한다() throws Exception {
        // given
        UUID startId = insertPublicRestaurant("출발 맛집", "37.5665", "126.9780");
        UUID destinationId = insertPublicRestaurant("도착 맛집", "37.5700", "126.9820");
        UUID creatorId = insertCreator("탐색 채널");
        UUID videoId = insertVideo(creatorId, "UC-" + creatorId);
        insertVisit(startId, creatorId, videoId);
        given(courseRouteProviderPort.calculate(any()))
                .willThrow(new CourseRouteProviderException(CourseRouteFailureCategory.TIMEOUT));

        // when
        mockMvc.perform(post(COURSE_ROUTES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJson(startId, destinationId)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("COURSE_ROUTE_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())))
                .andExpect(jsonPath("$.totalDistanceMeters").doesNotExist())
                .andExpect(jsonPath("$.totalDurationSeconds").doesNotExist())
                .andExpect(jsonPath("$.segments").doesNotExist());

        // then
        assertPublicDiscoveryIsolated(startId, creatorId);
    }

    @Test
    @DisplayName("코스 경로가 provider RATE_LIMIT(429)로 502 COURSE_ROUTE_PROVIDER_UNAVAILABLE을 반환한 뒤에도 기존 공개 탐색 API 3종은 정상 200을 반환하고 실패 응답에 거리·시간 추정값이 없다")
    void courseRoute_ProviderRATE_LIMIT실패후_기존공개탐색API3종은정상200을반환한다() throws Exception {
        // given
        UUID startId = insertPublicRestaurant("출발 맛집", "37.5665", "126.9780");
        UUID destinationId = insertPublicRestaurant("도착 맛집", "37.5700", "126.9820");
        UUID creatorId = insertCreator("탐색 채널");
        UUID videoId = insertVideo(creatorId, "UC-" + creatorId);
        insertVisit(startId, creatorId, videoId);
        given(courseRouteProviderPort.calculate(any()))
                .willThrow(new CourseRouteProviderException(CourseRouteFailureCategory.RATE_LIMIT));

        // when
        mockMvc.perform(post(COURSE_ROUTES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJson(startId, destinationId)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("COURSE_ROUTE_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())))
                .andExpect(jsonPath("$.totalDistanceMeters").doesNotExist())
                .andExpect(jsonPath("$.totalDurationSeconds").doesNotExist())
                .andExpect(jsonPath("$.segments").doesNotExist());

        // then
        assertPublicDiscoveryIsolated(startId, creatorId);
    }

    @Test
    @DisplayName("코스 경로가 PARTIAL 실패로 502 COURSE_ROUTE_PARTIAL_FAILURE를 반환한 뒤에도 기존 공개 탐색 API 3종은 정상 200을 반환하고 실패 응답에 거리·시간 추정값이 없다")
    void courseRoute_ProviderPARTIAL실패후_기존공개탐색API3종은정상200을반환한다() throws Exception {
        // given
        UUID startId = insertPublicRestaurant("출발 맛집", "37.5665", "126.9780");
        UUID destinationId = insertPublicRestaurant("도착 맛집", "37.5700", "126.9820");
        UUID creatorId = insertCreator("탐색 채널");
        UUID videoId = insertVideo(creatorId, "UC-" + creatorId);
        insertVisit(startId, creatorId, videoId);
        given(courseRouteProviderPort.calculate(any()))
                .willThrow(new CourseRouteProviderException(CourseRouteFailureCategory.PARTIAL));

        // when
        mockMvc.perform(post(COURSE_ROUTES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJson(startId, destinationId)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("COURSE_ROUTE_PARTIAL_FAILURE"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())))
                .andExpect(jsonPath("$.totalDistanceMeters").doesNotExist())
                .andExpect(jsonPath("$.totalDurationSeconds").doesNotExist())
                .andExpect(jsonPath("$.segments").doesNotExist());

        // then
        assertPublicDiscoveryIsolated(startId, creatorId);
    }

    @Test
    @DisplayName("코스 경로가 주입한 provider 차단(PROVIDER_BLOCKED) 실패의 매핑으로 502를 반환한 뒤에도 기존 공개 탐색은 정상이고 실패 응답에 거리·시간 추정값이 없다")
    void courseRoute_ProviderBLOCKED실패후_기존공개탐색정상이고추정값필드가없다() throws Exception {
        // given
        UUID startId = insertPublicRestaurant("출발 맛집", "37.5665", "126.9780");
        UUID destinationId = insertPublicRestaurant("도착 맛집", "37.5700", "126.9820");
        UUID creatorId = insertCreator("탐색 채널");
        UUID videoId = insertVideo(creatorId, "UC-" + creatorId);
        insertVisit(startId, creatorId, videoId);
        given(courseRouteProviderPort.calculate(any()))
                .willThrow(new CourseRouteProviderException(CourseRouteFailureCategory.PROVIDER_BLOCKED));

        // when
        mockMvc.perform(post(COURSE_ROUTES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJson(startId, destinationId)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("COURSE_ROUTE_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())))
                .andExpect(jsonPath("$.totalDistanceMeters").doesNotExist())
                .andExpect(jsonPath("$.totalDurationSeconds").doesNotExist())
                .andExpect(jsonPath("$.segments").doesNotExist());

        // then
        assertPublicDiscoveryIsolated(startId, creatorId);
    }

    @Test
    @DisplayName("성공 응답의 expiresAt은 generatedAt에서 5분 뒤이고, 동일 요청을 두 번 보내면 두 번 모두 외부 경로 계산을 호출하며 generatedAt이 갱신된다")
    void courseRoute_동일요청두번요청_외부호출두번모두발생하고generatedAt이갱신된다() throws Exception {
        // given
        UUID startId = insertPublicRestaurant("출발 맛집", "37.5665", "126.9780");
        UUID destinationId = insertPublicRestaurant("도착 맛집", "37.5700", "126.9820");
        given(courseRouteProviderPort.calculate(any())).willReturn(singleLegResult(1200, 300));
        String requestBody = courseRequestJson(startId, destinationId);

        // when
        MvcResult firstResult = mockMvc.perform(post(COURSE_ROUTES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult secondResult = mockMvc.perform(post(COURSE_ROUTES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        // then
        JsonNode firstBody = objectMapper.readTree(firstResult.getResponse().getContentAsString());
        JsonNode secondBody = objectMapper.readTree(secondResult.getResponse().getContentAsString());

        OffsetDateTime firstGeneratedAt = OffsetDateTime.parse(firstBody.get("generatedAt").asText());
        OffsetDateTime firstExpiresAt = OffsetDateTime.parse(firstBody.get("expiresAt").asText());
        assertThat(Duration.between(firstGeneratedAt, firstExpiresAt)).isEqualTo(Duration.ofMinutes(5));

        OffsetDateTime secondGeneratedAt = OffsetDateTime.parse(secondBody.get("generatedAt").asText());
        OffsetDateTime secondExpiresAt = OffsetDateTime.parse(secondBody.get("expiresAt").asText());
        assertThat(Duration.between(secondGeneratedAt, secondExpiresAt)).isEqualTo(Duration.ofMinutes(5));

        assertThat(secondGeneratedAt).isAfterOrEqualTo(firstGeneratedAt);
        assertThat(secondGeneratedAt)
                .describedAs("서버가 만료 결과를 재사용하지 않고 매 요청 시점으로 generatedAt을 갱신해야 한다")
                .isNotEqualTo(firstGeneratedAt);

        verify(courseRouteProviderPort, times(2)).calculate(any());
    }

    @Test
    @DisplayName("실패 응답 본문에 좌표·Kakao 원문·API Key가 없고 details.selectedRestaurants가 입력 순서를 그대로 유지한다")
    void courseRoute_실패응답본문_좌표Kakao원문APIKey없이입력순서를유지한다() throws Exception {
        // given: 입력 순서와 좌표상 최근접 순서가 달라지도록 세 맛집을 배치한다.
        UUID startId = insertPublicRestaurant("출발 맛집", "37.5665", "126.9780");
        UUID farId = insertPublicRestaurant("먼 맛집", "37.6000", "127.0500");
        UUID nearId = insertPublicRestaurant("가까운 맛집", "37.5670", "126.9790");
        given(courseRouteProviderPort.calculate(any()))
                .willThrow(new CourseRouteProviderException(CourseRouteFailureCategory.TIMEOUT));

        // when
        String body = mockMvc.perform(post(COURSE_ROUTES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJson(startId, farId, nearId)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("COURSE_ROUTE_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.details.selectedRestaurants[0].restaurantId").value(startId.toString()))
                .andExpect(jsonPath("$.details.selectedRestaurants[0].inputOrder").value(1))
                .andExpect(jsonPath("$.details.selectedRestaurants[1].restaurantId").value(farId.toString()))
                .andExpect(jsonPath("$.details.selectedRestaurants[1].inputOrder").value(2))
                .andExpect(jsonPath("$.details.selectedRestaurants[2].restaurantId").value(nearId.toString()))
                .andExpect(jsonPath("$.details.selectedRestaurants[2].inputOrder").value(3))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // then
        assertThat(body).doesNotContain(
                "latitude", "longitude", "KakaoAK",
                "37.5665", "126.9780", "37.6000", "127.0500", "37.5670", "126.9790");
    }

    private void assertPublicDiscoveryIsolated(UUID restaurantId, UUID creatorId) throws Exception {
        mockMvc.perform(get("/api/restaurants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + restaurantId + "')]").isNotEmpty());

        mockMvc.perform(get("/api/restaurants/{restaurantId}", restaurantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(restaurantId.toString()));

        mockMvc.perform(get("/api/creators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + creatorId + "')]").isNotEmpty());
    }

    private CourseRouteResult singleLegResult(int distanceMeters, int durationSeconds) {
        return new CourseRouteResult(List.of(new CourseRouteLeg(distanceMeters, durationSeconds)));
    }

    private UUID insertPublicRestaurant(String name, String latitude, String longitude) {
        return insertRestaurant(name, latitude, longitude);
    }

    private UUID insertRestaurant(String name, String latitude, String longitude) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO restaurant "
                        + "(id, region_id, food_category_id, name, kakao_place_id, kakao_place_url, "
                        + "road_address, phone_number, latitude, longitude, publication_status, lifecycle_status, "
                        + "deleted_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, MAPO_REGION_ID, KOREAN_CATEGORY_ID, name, "KAKAO-" + id,
                "https://example.com/place/" + id, "서울특별시 테스트로 1", "02-1234-5678",
                latitude == null ? null : new BigDecimal(latitude),
                longitude == null ? null : new BigDecimal(longitude),
                "PUBLIC", "ACTIVE",
                null);
        return id;
    }

    private UUID insertCreator(String channelName) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO creator "
                        + "(id, external_channel_id, channel_name, channel_url, publication_status, "
                        + "external_status_checked_at) VALUES (?, ?, ?, ?, ?, ?)",
                id,
                "UC-" + id,
                channelName,
                "https://example.com/channel/" + id,
                "PUBLIC",
                OffsetDateTime.now());
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

    private void insertVisit(UUID restaurantId, UUID creatorId, UUID videoId) {
        jdbcTemplate.update(
                "INSERT INTO visit (id, restaurant_id, creator_id, video_id, publication_status) "
                        + "VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), restaurantId, creatorId, videoId, "PUBLIC");
    }
}

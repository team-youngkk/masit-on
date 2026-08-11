package com.masiton.restaurant.presentation.rest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API-DISCOVERY-COURSE-001 맛집 코스 추천의 API 계약·보안 경계 인수 테스트다.
 * 외부 Kakao Mobility 호출은 {@link CourseRouteProviderPort}를 {@code @MockitoBean}으로 대체해 격리한다.
 * Adapter의 실제 HTTP 계약은 별도 WireMock 테스트가 검증한다.
 * 근거: docs/05-specs/api/discovery/restaurant-course-recommendation-api.md
 */
@SpringBootTest
@com.masiton.test.TestProfile
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("맛집 코스 추천 API")
class RestaurantCourseRouteApiTest {

    private static final String COURSE_ROUTES_PATH = "/api/restaurants/course-routes";
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

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CourseRouteProviderPort courseRouteProviderPort;

    @BeforeEach
    void cleanUpState() throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE visit, video, creator, restaurant CASCADE");
        REDIS.execInContainer("redis-cli", "FLUSHALL");
    }

    @Test
    @DisplayName("인증 헤더 없이 정상 요청하면 200과 계약이 정의한 응답 스키마를 반환하고 외부 호출은 1회뿐이다")
    void courseRoute_인증헤더없이정상요청_200과응답스키마를반환한다() throws Exception {
        // given
        UUID startId = insertPublicRestaurant("출발 맛집", "37.5665", "126.9780");
        UUID destinationId = insertPublicRestaurant("도착 맛집", "37.5700", "126.9820");
        given(courseRouteProviderPort.calculate(any())).willReturn(singleLegResult(1200, 300));
        int restaurantCountBefore = restaurantCount();

        // when
        MvcResult result = mockMvc.perform(post(COURSE_ROUTES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJson(startId, destinationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.restaurants.length()").value(2))
                .andExpect(jsonPath("$.restaurants[0].sequence").value(1))
                .andExpect(jsonPath("$.restaurants[0].role").value("START"))
                .andExpect(jsonPath("$.restaurants[0].restaurantId").value(startId.toString()))
                .andExpect(jsonPath("$.restaurants[1].sequence").value(2))
                .andExpect(jsonPath("$.restaurants[1].role").value("DESTINATION"))
                .andExpect(jsonPath("$.restaurants[1].restaurantId").value(destinationId.toString()))
                .andExpect(jsonPath("$.segments.length()").value(1))
                .andExpect(jsonPath("$.segments[0].fromRestaurantId").value(startId.toString()))
                .andExpect(jsonPath("$.segments[0].toRestaurantId").value(destinationId.toString()))
                .andExpect(jsonPath("$.segments[0].distanceMeters").value(1200))
                .andExpect(jsonPath("$.segments[0].durationSeconds").value(300))
                .andExpect(jsonPath("$.totalDistanceMeters").value(1200))
                .andExpect(jsonPath("$.totalDurationSeconds").value(300))
                .andExpect(jsonPath("$.generatedAt").exists())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn();

        // then
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("latitude", "longitude", "KakaoAK");

        JsonNode root = objectMapper.readTree(body);
        assertThat(root.get("restaurants").get(0).get("restaurantId").isTextual()).isTrue();
        assertThat(root.get("restaurants").get(1).get("restaurantId").isTextual()).isTrue();

        OffsetDateTime generatedAt = OffsetDateTime.parse(root.get("generatedAt").asText());
        OffsetDateTime expiresAt = OffsetDateTime.parse(root.get("expiresAt").asText());
        assertThat(Duration.between(generatedAt, expiresAt)).isEqualTo(Duration.ofMinutes(5));

        verify(courseRouteProviderPort, times(1)).calculate(any());
        assertThat(restaurantCount()).isEqualTo(restaurantCountBefore);
    }

    @Test
    @DisplayName("맛집을 1개만 선택하면 400 INVALID_COURSE_SIZE를 반환하고 외부를 호출하지 않는다")
    void courseRoute_맛집1개선택_400INVALID_COURSE_SIZE를반환한다() throws Exception {
        mockMvc.perform(post(COURSE_ROUTES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJson(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COURSE_SIZE"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));

        verifyNoInteractions(courseRouteProviderPort);
    }

    @Test
    @DisplayName("맛집을 6개 선택하면 400 INVALID_COURSE_SIZE를 반환하고 외부를 호출하지 않는다")
    void courseRoute_맛집6개선택_400INVALID_COURSE_SIZE를반환한다() throws Exception {
        UUID[] ids = new UUID[6];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = UUID.randomUUID();
        }

        mockMvc.perform(post(COURSE_ROUTES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJson(ids)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COURSE_SIZE"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));

        verifyNoInteractions(courseRouteProviderPort);
    }

    @Test
    @DisplayName("빈 JSON 본문은 400 INVALID_COURSE_SIZE를 반환하고 외부를 호출하지 않는다")
    void courseRoute_빈JSON본문_400INVALID_COURSE_SIZE를반환한다() throws Exception {
        mockMvc.perform(post(COURSE_ROUTES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COURSE_SIZE"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));

        verifyNoInteractions(courseRouteProviderPort);
    }

    @Test
    @DisplayName("동일 식별자를 반복 선택하면 400 DUPLICATE_RESTAURANT_IN_COURSE를 반환하고 외부를 호출하지 않는다")
    void courseRoute_동일식별자반복선택_400DUPLICATE_RESTAURANT_IN_COURSE를반환한다() throws Exception {
        UUID duplicateId = UUID.randomUUID();

        mockMvc.perform(post(COURSE_ROUTES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJson(duplicateId, duplicateId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESTAURANT_IN_COURSE"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));

        verifyNoInteractions(courseRouteProviderPort);
    }

    @Test
    @DisplayName("UUID 형식이 아닌 식별자는 400 INVALID_IDENTIFIER를 반환하고 외부를 호출하지 않는다")
    void courseRoute_UUID형식아님_400INVALID_IDENTIFIER를반환한다() throws Exception {
        mockMvc.perform(post(COURSE_ROUTES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJsonRaw("not-a-uuid", UUID.randomUUID().toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDENTIFIER"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));

        verifyNoInteractions(courseRouteProviderPort);
    }

    @Test
    @DisplayName("빈 문자열 식별자는 400 INVALID_IDENTIFIER를 반환하고 외부를 호출하지 않는다")
    void courseRoute_빈문자열식별자_400INVALID_IDENTIFIER를반환한다() throws Exception {
        mockMvc.perform(post(COURSE_ROUTES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJsonRaw("", UUID.randomUUID().toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDENTIFIER"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));

        verifyNoInteractions(courseRouteProviderPort);
    }

    @Test
    @DisplayName("파싱할 수 없는 JSON 본문은 400 INVALID_REQUEST를 반환하고 외부를 호출하지 않는다")
    void courseRoute_파싱불가JSON본문_400INVALID_REQUEST를반환한다() throws Exception {
        mockMvc.perform(post(COURSE_ROUTES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));

        verifyNoInteractions(courseRouteProviderPort);
    }

    @Test
    @DisplayName("존재하지 않는 맛집을 선택하면 404 RESTAURANT_NOT_FOUND를 반환하고 외부를 호출하지 않는다")
    void courseRoute_존재하지않는맛집선택_404RESTAURANT_NOT_FOUND를반환한다() throws Exception {
        mockMvc.perform(post(COURSE_ROUTES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJson(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESTAURANT_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));

        verifyNoInteractions(courseRouteProviderPort);
    }

    @Test
    @DisplayName("PRIVATE 맛집을 포함하면 422 RESTAURANT_NOT_PUBLIC을 반환하고 외부를 호출하지 않는다")
    void courseRoute_PRIVATE맛집포함_422RESTAURANT_NOT_PUBLIC을반환한다() throws Exception {
        UUID publicId = insertPublicRestaurant("공개 맛집", "37.5665", "126.9780");
        UUID privateId = insertRestaurant("비공개 맛집", "37.5700", "126.9820", "PRIVATE", "ACTIVE");

        mockMvc.perform(post(COURSE_ROUTES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJson(publicId, privateId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RESTAURANT_NOT_PUBLIC"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));

        verifyNoInteractions(courseRouteProviderPort);
    }

    @Test
    @DisplayName("DELETED 맛집을 포함하면 422 RESTAURANT_NOT_PUBLIC을 반환하고 외부를 호출하지 않는다")
    void courseRoute_DELETED맛집포함_422RESTAURANT_NOT_PUBLIC을반환한다() throws Exception {
        UUID publicId = insertPublicRestaurant("공개 맛집", "37.5665", "126.9780");
        UUID deletedId = insertRestaurant("삭제된 맛집", "37.5700", "126.9820", "PRIVATE", "DELETED");

        mockMvc.perform(post(COURSE_ROUTES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJson(publicId, deletedId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RESTAURANT_NOT_PUBLIC"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));

        verifyNoInteractions(courseRouteProviderPort);
    }

    @Test
    @DisplayName("좌표 없는 맛집을 포함하면 422 RESTAURANT_COORDINATE_REQUIRED를 반환하고 외부를 호출하지 않는다")
    void courseRoute_좌표없는맛집포함_422RESTAURANT_COORDINATE_REQUIRED를반환한다() throws Exception {
        UUID withCoordinateId = insertPublicRestaurant("좌표 있는 맛집", "37.5665", "126.9780");
        UUID withoutCoordinateId = insertRestaurant("좌표 없는 맛집", null, null, "PUBLIC", "ACTIVE");

        mockMvc.perform(post(COURSE_ROUTES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJson(withCoordinateId, withoutCoordinateId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RESTAURANT_COORDINATE_REQUIRED"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));

        verifyNoInteractions(courseRouteProviderPort);
    }

    @Test
    @DisplayName("경로 합계가 30,000m을 초과하면 422 COURSE_DISTANCE_LIMIT_EXCEEDED를 반환하고 추정값을 포함하지 않는다")
    void courseRoute_경로합계30001미터_422COURSE_DISTANCE_LIMIT_EXCEEDED를반환한다() throws Exception {
        UUID startId = insertPublicRestaurant("출발 맛집", "37.5665", "126.9780");
        UUID destinationId = insertPublicRestaurant("도착 맛집", "37.5700", "126.9820");
        given(courseRouteProviderPort.calculate(any())).willReturn(singleLegResult(30_001, 3_600));

        mockMvc.perform(post(COURSE_ROUTES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJson(startId, destinationId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COURSE_DISTANCE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())))
                .andExpect(jsonPath("$.totalDistanceMeters").doesNotExist())
                .andExpect(jsonPath("$.totalDurationSeconds").doesNotExist())
                .andExpect(jsonPath("$.segments").doesNotExist());

        verify(courseRouteProviderPort, times(1)).calculate(any());
    }

    @Test
    @DisplayName("경로 합계가 정확히 30,000m이면 상한 경계를 포함해 200을 반환한다")
    void courseRoute_경로합계30000미터_200경계포함() throws Exception {
        UUID startId = insertPublicRestaurant("출발 맛집", "37.5665", "126.9780");
        UUID destinationId = insertPublicRestaurant("도착 맛집", "37.5700", "126.9820");
        given(courseRouteProviderPort.calculate(any())).willReturn(singleLegResult(30_000, 3_600));

        mockMvc.perform(post(COURSE_ROUTES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJson(startId, destinationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.totalDistanceMeters").value(30_000));

        verify(courseRouteProviderPort, times(1)).calculate(any());
    }

    @Test
    @DisplayName("Provider가 PARTIAL 실패를 보고하면 502 COURSE_ROUTE_PARTIAL_FAILURE를 반환하고 추정값을 포함하지 않는다")
    void courseRoute_Provider부분실패_502COURSE_ROUTE_PARTIAL_FAILURE를반환한다() throws Exception {
        UUID startId = insertPublicRestaurant("출발 맛집", "37.5665", "126.9780");
        UUID destinationId = insertPublicRestaurant("도착 맛집", "37.5700", "126.9820");
        given(courseRouteProviderPort.calculate(any()))
                .willThrow(new CourseRouteProviderException(CourseRouteFailureCategory.PARTIAL));

        mockMvc.perform(post(COURSE_ROUTES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJson(startId, destinationId)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("COURSE_ROUTE_PARTIAL_FAILURE"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())))
                .andExpect(jsonPath("$.resource").doesNotExist())
                .andExpect(jsonPath("$.details.selectedRestaurants[0].restaurantId").value(startId.toString()))
                .andExpect(jsonPath("$.details.selectedRestaurants[0].name").value("출발 맛집"))
                .andExpect(jsonPath("$.details.selectedRestaurants[0].inputOrder").value(1))
                .andExpect(jsonPath("$.details.selectedRestaurants[1].restaurantId").value(destinationId.toString()))
                .andExpect(jsonPath("$.details.selectedRestaurants[1].inputOrder").value(2))
                .andExpect(jsonPath("$.details.failureCategory").value("PARTIAL"))
                .andExpect(jsonPath("$.details.retryGuidance.action").value("RESELECT_OR_RETRY"))
                .andExpect(jsonPath("$.details.retryGuidance.message").isNotEmpty())
                .andExpect(jsonPath("$.totalDistanceMeters").doesNotExist())
                .andExpect(jsonPath("$.totalDurationSeconds").doesNotExist())
                .andExpect(jsonPath("$.segments").doesNotExist());

        verify(courseRouteProviderPort, times(1)).calculate(any());
    }

    @Test
    @DisplayName("Provider가 TIMEOUT으로 실패하면 502 COURSE_ROUTE_PROVIDER_UNAVAILABLE을 반환하고 추정값을 포함하지 않는다")
    void courseRoute_ProviderTIMEOUT실패_502COURSE_ROUTE_PROVIDER_UNAVAILABLE을반환한다() throws Exception {
        UUID startId = insertPublicRestaurant("출발 맛집", "37.5665", "126.9780");
        UUID destinationId = insertPublicRestaurant("도착 맛집", "37.5700", "126.9820");
        given(courseRouteProviderPort.calculate(any()))
                .willThrow(new CourseRouteProviderException(CourseRouteFailureCategory.TIMEOUT));

        mockMvc.perform(post(COURSE_ROUTES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJson(startId, destinationId)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("COURSE_ROUTE_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())))
                .andExpect(jsonPath("$.details.failureCategory").value("PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.details.selectedRestaurants[0].inputOrder").value(1))
                .andExpect(jsonPath("$.details.selectedRestaurants[1].inputOrder").value(2))
                .andExpect(jsonPath("$.totalDistanceMeters").doesNotExist())
                .andExpect(jsonPath("$.totalDurationSeconds").doesNotExist())
                .andExpect(jsonPath("$.segments").doesNotExist());

        verify(courseRouteProviderPort, times(1)).calculate(any());
    }

    @Test
    @DisplayName("Provider가 PROVIDER_BLOCKED로 실패하면 502 COURSE_ROUTE_PROVIDER_UNAVAILABLE을 반환하고 추정값을 포함하지 않는다")
    void courseRoute_ProviderBLOCKED실패_502COURSE_ROUTE_PROVIDER_UNAVAILABLE을반환한다() throws Exception {
        UUID startId = insertPublicRestaurant("출발 맛집", "37.5665", "126.9780");
        UUID destinationId = insertPublicRestaurant("도착 맛집", "37.5700", "126.9820");
        given(courseRouteProviderPort.calculate(any()))
                .willThrow(new CourseRouteProviderException(CourseRouteFailureCategory.PROVIDER_BLOCKED));

        mockMvc.perform(post(COURSE_ROUTES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJson(startId, destinationId)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("COURSE_ROUTE_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())))
                .andExpect(jsonPath("$.totalDistanceMeters").doesNotExist())
                .andExpect(jsonPath("$.totalDurationSeconds").doesNotExist())
                .andExpect(jsonPath("$.segments").doesNotExist());

        verify(courseRouteProviderPort, times(1)).calculate(any());
    }

    @Test
    @DisplayName("서비스 자체 제한이면 429 COURSE_ROUTE_RATE_LIMITED와 실패 상세를 반환한다")
    void courseRoute_서비스자체제한_429와실패상세를반환한다() throws Exception {
        UUID startId = insertPublicRestaurant("출발 맛집", "37.5665", "126.9780");
        UUID destinationId = insertPublicRestaurant("도착 맛집", "37.5700", "126.9820");
        given(courseRouteProviderPort.calculate(any()))
                .willThrow(new CourseRouteProviderException(CourseRouteFailureCategory.SERVICE_RATE_LIMIT));

        String body = mockMvc.perform(post(COURSE_ROUTES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJson(startId, destinationId)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("COURSE_ROUTE_RATE_LIMITED"))
                .andExpect(jsonPath("$.resource").doesNotExist())
                .andExpect(jsonPath("$.details.failureCategory").value("SERVICE_RATE_LIMIT"))
                .andExpect(jsonPath("$.details.selectedRestaurants[0].inputOrder").value(1))
                .andExpect(jsonPath("$.details.selectedRestaurants[1].inputOrder").value(2))
                .andExpect(jsonPath("$.details.retryGuidance.action").value("RESELECT_OR_RETRY"))
                .andExpect(jsonPath("$.details.retryGuidance.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").value(not(emptyString())))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("latitude", "longitude", "KakaoAK", "SERVICE_RATE_LIMITED");
        verify(courseRouteProviderPort, times(1)).calculate(any());
    }

    private CourseRouteResult singleLegResult(int distanceMeters, int durationSeconds) {
        return new CourseRouteResult(List.of(new CourseRouteLeg(distanceMeters, durationSeconds)));
    }

    private String courseRequestJson(UUID... restaurantIds) {
        String[] rawIds = Arrays.stream(restaurantIds).map(UUID::toString).toArray(String[]::new);
        return courseRequestJsonRaw(rawIds);
    }

    private String courseRequestJsonRaw(String... restaurantIds) {
        String ids = Arrays.stream(restaurantIds)
                .map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(","));
        return "{\"restaurantIds\":[" + ids + "]}";
    }

    private int restaurantCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM restaurant", Integer.class);
        return count == null ? 0 : count;
    }

    private UUID insertPublicRestaurant(String name, String latitude, String longitude) {
        return insertRestaurant(name, latitude, longitude, "PUBLIC", "ACTIVE");
    }

    private UUID insertRestaurant(
            String name, String latitude, String longitude, String publicationStatus, String lifecycleStatus) {
        UUID id = UUID.randomUUID();
        boolean deleted = "DELETED".equals(lifecycleStatus);
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
                publicationStatus, lifecycleStatus,
                deleted ? OffsetDateTime.now() : null);
        return id;
    }
}

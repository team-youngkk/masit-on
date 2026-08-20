package com.masiton.orchestration.presentation.detail;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BR-CREATOR-011·creator-detail-api.md 3절: 사용자 조회 중 YouTube API를 호출하지 않는다.
 * {@code masiton.integration.youtube.base-url}을 실제 WireMock 컨테이너로 연결해 두 목록 API를
 * 호출한 뒤 WireMock 관리 API({@code /__admin/requests/count})로 수신한 요청이 0건인지 검증한다.
 *
 * <p>두 호출의 200을 함께 단정한다. 상태를 확인하지 않으면 매핑이나 공개 경로 허용이 깨져 조회가
 * 실패하는 상태에서도 "YouTube 요청 0건"은 참이 되어 테스트가 통과한다.
 */
@SpringBootTest
@com.masiton.test.TestProfile
@AutoConfigureMockMvc
@DisplayName("유튜버 방문 맛집·근거 영상 조회 중 YouTube 미호출")
class CreatorVisitContentYoutubeCallIntegrationTest extends com.masiton.test.FullContextIntegrationTest {

    private static final UUID SEED_REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID SEED_FOOD_CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final int WIREMOCK_PORT = 8080;

    static final GenericContainer<?> WIREMOCK =
            new GenericContainer<>("wiremock/wiremock:3.13.2-alpine")
                    .withExposedPorts(WIREMOCK_PORT)
                    .waitingFor(Wait.forHttp("/__admin/health").forPort(WIREMOCK_PORT).forStatusCode(200));

    static {
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void registerYoutubeProperties(DynamicPropertyRegistry registry) {
        registry.add("masiton.integration.youtube.base-url",
                CreatorVisitContentYoutubeCallIntegrationTest::wireMockUrl);
        registry.add("masiton.integration.youtube.api-key", () -> "wiremock-only-key");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("방문 맛집·근거 영상을 조회해도 YouTube base-url로 향한 요청은 0건이다")
    void 두목록조회_YouTube요청0건이다() throws Exception {
        // given
        UUID creatorId = insertCreator("PUBLIC", "ACTIVE", "AVAILABLE");
        UUID restaurantId = insertRestaurant("호출검증맛집", "PUBLIC", "ACTIVE");
        UUID videoId = insertVideo(creatorId, "호출검증영상", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(restaurantId, creatorId, videoId, "PUBLIC", "ACTIVE", OffsetDateTime.now());

        // when
        mockMvc.perform(get("/api/creators/{creatorId}/restaurants", creatorId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/creators/{creatorId}/videos", creatorId))
                .andExpect(status().isOk());

        // then
        assertThat(requestCountReceivedByWireMock()).isZero();
    }

    private long requestCountReceivedByWireMock() throws Exception {
        URI uri = URI.create("http://%s:%d/__admin/requests/count".formatted(
                WIREMOCK.getHost(), WIREMOCK.getMappedPort(WIREMOCK_PORT)));
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(uri)
                            .timeout(Duration.ofSeconds(5))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode body = new ObjectMapper().readTree(response.body());
            return body.get("count").asLong();
        }
    }

    private static String wireMockUrl() {
        return "http://%s:%d".formatted(WIREMOCK.getHost(), WIREMOCK.getMappedPort(WIREMOCK_PORT));
    }

    private UUID insertRestaurant(String name, String publicationStatus, String lifecycleStatus) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO restaurant "
                        + "(id, region_id, food_category_id, name, kakao_place_id, kakao_place_url, "
                        + "road_address, phone_number, publication_status, lifecycle_status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, SEED_REGION_ID, SEED_FOOD_CATEGORY_ID, name, "KAKAO-" + id,
                "https://example.com/place/" + id, "서울특별시 종로구 테스트로 1", "02-1234-5678",
                publicationStatus, lifecycleStatus);
        return id;
    }

    private UUID insertCreator(String publicationStatus, String lifecycleStatus, String externalAvailabilityStatus) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO creator "
                        + "(id, external_channel_id, channel_name, channel_url, publication_status, "
                        + "lifecycle_status, external_availability_status, external_status_checked_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, "UC-" + id, "테스트 채널", "https://www.youtube.com/channel/" + id,
                publicationStatus, lifecycleStatus, externalAvailabilityStatus, OffsetDateTime.now());
        return id;
    }

    private UUID insertVideo(
            UUID creatorId, String title, String publicationStatus, String lifecycleStatus,
            String externalAvailabilityStatus) {
        UUID id = UUID.randomUUID();
        String publisherExternalChannelId = jdbcTemplate.queryForObject(
                "SELECT external_channel_id FROM creator WHERE id = ?", String.class, creatorId);
        jdbcTemplate.update(
                "INSERT INTO video "
                        + "(id, creator_id, external_video_id, publisher_external_channel_id, title, "
                        + "source_url, thumbnail_url, publication_status, lifecycle_status, "
                        + "external_availability_status, external_status_checked_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, creatorId, shortId("VID-"), publisherExternalChannelId, title,
                "https://www.youtube.com/watch?v=" + id, "https://i.ytimg.com/" + id + ".jpg",
                publicationStatus, lifecycleStatus, externalAvailabilityStatus, OffsetDateTime.now());
        return id;
    }

    private void insertVisit(
            UUID restaurantId, UUID creatorId, UUID videoId, String publicationStatus, String lifecycleStatus,
            OffsetDateTime createdAt) {
        jdbcTemplate.update(
                "INSERT INTO visit "
                        + "(id, restaurant_id, creator_id, video_id, publication_status, lifecycle_status, "
                        + "created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), restaurantId, creatorId, videoId, publicationStatus, lifecycleStatus, createdAt);
    }

    /** varchar(32) 컬럼(external_video_id)에 맞도록 UUID를 잘라 짧은 식별자를 만든다. */
    private String shortId(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 20);
    }
}

package com.masiton.acceptance;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("관리자 데이터 등록 사용자 여정 인수")
class AdminRegistrationJourneyAcceptanceTest {

    private static final UUID ADMIN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID REGION_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CATEGORY_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final String PASSWORD = "acceptance-password";
    private static final int WIREMOCK_PORT = 8080;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17.10-alpine"))
            .withDatabaseName("masiton").withUsername("masiton").withPassword("masiton_local");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.8-alpine"))
            .withExposedPorts(6379).waitingFor(Wait.forListeningPort());

    @Container
    static final GenericContainer<?> WIREMOCK = new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.13.2-alpine"))
            .withCopyFileToContainer(MountableFile.forHostPath("docker/wiremock/mappings"), "/home/wiremock/mappings")
            .withCopyFileToContainer(MountableFile.forHostPath("docker/wiremock/__files"), "/home/wiremock/__files")
            .withExposedPorts(WIREMOCK_PORT)
            .waitingFor(Wait.forHttp("/__admin/health").forPort(WIREMOCK_PORT).forStatusCode(200));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("masiton.integration.kakao.base-url", AdminRegistrationJourneyAcceptanceTest::wireMockUrl);
        registry.add("masiton.integration.kakao.rest-api-key", () -> "wiremock-only-key");
        registry.add("masiton.integration.youtube.base-url", AdminRegistrationJourneyAcceptanceTest::wireMockUrl);
        registry.add("masiton.integration.youtube.api-key", () -> "wiremock-only-key");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute("truncate table visit, video, creator, restaurant, confirmation_token, admin_account, food_category, region cascade");
        jdbcTemplate.update("insert into region (id, code, name, sort_order, active) values (?, 'MAPO', '마포구', 1, true)", REGION_ID);
        jdbcTemplate.update("insert into food_category (id, code, name, sort_order, active) values (?, 'KOREAN', '한식', 1, true)", CATEGORY_ID);
        jdbcTemplate.update("insert into admin_account (id, login_id, password_hash, role, active) values (?, 'acceptance-admin', ?, 'ADMIN', true)",
                ADMIN_ID, new BCryptPasswordEncoder().encode(PASSWORD));
        REDIS.execInContainer("redis-cli", "FLUSHALL");
    }

    @Test
    @DisplayName("관리자가 로그인해 세 기준정보와 방문 관계를 등록하면 모든 공개 조회에 즉시 반영된다")
    void 관리자등록_전체흐름_공개조회에반영된다() throws Exception {
        String accessToken = login();

        String restaurantId = confirm(accessToken, "/api/admin/restaurant-registration-previews", """
                {"name":"fixture-place-normal","kakaoPlaceUrl":"https://place.map.kakao.com/fixture-place-normal",
                 "roadAddress":"서울특별시 마포구 월드컵로 1","detailAddress":"1층","phoneNumber":"02-000-0000","category":"한식"}
                """, "/api/admin/restaurants");
        String creatorId = confirm(accessToken, "/api/admin/creator-registration-previews", """
                {"channelUrl":"https://www.youtube.com/channel/UCfixtureNormalChannel01"}
                """, "/api/admin/creators");
        String videoId = confirm(accessToken, "/api/admin/video-registration-previews", """
                {"sourceUrl":"https://www.youtube.com/watch?v=fixtureVid1"}
                """, "/api/admin/videos");

        mockMvc.perform(post("/api/admin/visit-relationships")
                        .header("Authorization", "Bearer " + accessToken).contentType(APPLICATION_JSON)
                        .content("""
                                {"restaurantId":"%s","creatorId":"%s","videoId":"%s","visitEvidenceConfirmed":true}
                                """.formatted(restaurantId, creatorId, videoId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.restaurantId").value(restaurantId));

        mockMvc.perform(get("/api/restaurants"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(restaurantId));
        mockMvc.perform(get("/api/restaurants").param("creatorId", creatorId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(restaurantId));
        mockMvc.perform(get("/api/restaurants/{id}", restaurantId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.visitedBy[0].id").value(creatorId))
                .andExpect(jsonPath("$.videos[0].id").value(videoId));
        mockMvc.perform(get("/api/creators"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(creatorId));
    }

    @Test
    @DisplayName("인증 없이 관리자 API를 호출하면 저장 없이 거부되고 공개 목록은 비어 있다")
    void 권한우회_인증없음_저장없이거부된다() throws Exception {
        mockMvc.perform(post("/api/admin/restaurant-registration-previews").contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/restaurants"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        assertThat(jdbcTemplate.queryForObject("select count(*) from restaurant", Integer.class)).isZero();
    }

    @Test
    @DisplayName("외부 검증 실패와 중복 방문 요청은 부분 저장을 만들지 않는다")
    void 실패요청_외부검증과중복_부분저장되지않는다() throws Exception {
        String accessToken = login();

        mockMvc.perform(post("/api/admin/creator-registration-previews")
                        .header("Authorization", "Bearer " + accessToken).contentType(APPLICATION_JSON)
                        .content("{\"channelUrl\":\"https://www.youtube.com/channel/UCfixtureMissingChannel1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from creator", Integer.class)).isZero();

        String restaurantId = confirm(accessToken, "/api/admin/restaurant-registration-previews", """
                {"name":"fixture-place-normal","kakaoPlaceUrl":"https://place.map.kakao.com/fixture-place-normal","roadAddress":"서울특별시 마포구 월드컵로 1","detailAddress":"1층","phoneNumber":"02-000-0000","category":"한식"}
                """, "/api/admin/restaurants");
        String creatorId = confirm(accessToken, "/api/admin/creator-registration-previews", "{\"channelUrl\":\"https://www.youtube.com/channel/UCfixtureNormalChannel01\"}", "/api/admin/creators");
        String videoId = confirm(accessToken, "/api/admin/video-registration-previews", "{\"sourceUrl\":\"https://youtu.be/fixtureVid1\"}", "/api/admin/videos");
        String request = """
                {"restaurantId":"%s","creatorId":"%s","videoId":"%s","visitEvidenceConfirmed":true}
                """.formatted(restaurantId, creatorId, videoId);
        String insufficientEvidenceRequest = """
                {"restaurantId":"%s","creatorId":"%s","videoId":"%s","visitEvidenceConfirmed":false}
                """.formatted(restaurantId, creatorId, videoId);
        mockMvc.perform(post("/api/admin/visit-relationships").header("Authorization", "Bearer " + accessToken)
                        .contentType(APPLICATION_JSON).content(insufficientEvidenceRequest))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VISIT_EVIDENCE_INSUFFICIENT"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from visit", Integer.class)).isZero();

        mockMvc.perform(post("/api/admin/visit-relationships").header("Authorization", "Bearer " + accessToken)
                        .contentType(APPLICATION_JSON).content(request)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/admin/visit-relationships").header("Authorization", "Bearer " + accessToken)
                        .contentType(APPLICATION_JSON).content(request)).andExpect(status().isConflict());

        assertThat(jdbcTemplate.queryForObject("select count(*) from visit", Integer.class)).isEqualTo(1);
    }

    private String login() throws Exception {
        String response = mockMvc.perform(post("/api/admin/auth/tokens").contentType(APPLICATION_JSON)
                        .content("{\"loginId\":\"acceptance-admin\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private String confirm(String accessToken, String previewPath, String previewBody, String confirmPath) throws Exception {
        String preview = mockMvc.perform(post(previewPath).header("Authorization", "Bearer " + accessToken)
                        .contentType(APPLICATION_JSON).content(previewBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.decision").value("READY"))
                .andReturn().getResponse().getContentAsString();
        JsonNode previewJson = objectMapper.readTree(preview);
        String confirmationToken = previewJson.get("confirmationToken").asText();
        String confirmed = mockMvc.perform(post(confirmPath).header("Authorization", "Bearer " + accessToken)
                        .contentType(APPLICATION_JSON).content("{\"confirmationToken\":\"" + confirmationToken + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(confirmed).get("id").asText();
    }

    private static String wireMockUrl() {
        return "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(WIREMOCK_PORT);
    }
}

package com.masiton.creator.presentation.rest;

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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API-CREATOR-DETAIL-001 유튜버 기본 상세 조회를 실제 PostgreSQL과 MockMvc로 검증한다.
 */
@SpringBootTest
@com.masiton.test.TestProfile
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("유튜버 기본 상세 조회 API")
class CreatorDetailApiTest {

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

    @Test
    @DisplayName("공개·활성·이용 가능한 유튜버는 저장된 표시 정보를 계약대로 반환한다")
    void 상세조회_공개활성이용가능_저장된표시정보를반환한다() throws Exception {
        // given
        UUID creatorId = UUID.randomUUID();
        String externalChannelId = "UC-" + UUID.randomUUID();
        insertCreator(
                creatorId, externalChannelId, "테스트 채널",
                "https://i.ytimg.com/vi/fixtureVid1/hqdefault.jpg", "채널 소개", "@masiton-fixture",
                "PUBLIC", "ACTIVE", "AVAILABLE", null);

        // when & then
        mockMvc.perform(get("/api/creators/{creatorId}", creatorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(creatorId.toString()))
                .andExpect(jsonPath("$.channelName").value("테스트 채널"))
                .andExpect(jsonPath("$.profileImageUrl").value("https://i.ytimg.com/vi/fixtureVid1/hqdefault.jpg"))
                .andExpect(jsonPath("$.description").value("채널 소개"))
                .andExpect(jsonPath("$.handle").value("@masiton-fixture"))
                .andExpect(jsonPath("$.channelUrl").value("https://www.youtube.com/channel/" + externalChannelId));
    }

    @Test
    @DisplayName("선택 표시 정보가 미등록이면 명시적 null로 응답한다")
    void 상세조회_선택표시정보미등록_명시적null로응답한다() throws Exception {
        // given
        UUID creatorId = UUID.randomUUID();
        insertCreator(
                creatorId, "UC-" + UUID.randomUUID(), "표시정보없는 채널",
                null, null, null,
                "PUBLIC", "ACTIVE", "AVAILABLE", null);

        // when & then
        mockMvc.perform(get("/api/creators/{creatorId}", creatorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl").value(nullValue()))
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.handle").value(nullValue()));
    }

    @Test
    @DisplayName("존재하지 않는 식별자는 404 CREATOR_NOT_FOUND를 반환한다")
    void 상세조회_존재하지않는식별자_404CREATOR_NOT_FOUND를반환한다() throws Exception {
        mockMvc.perform(get("/api/creators/{creatorId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));
    }

    @Test
    @DisplayName("비공개(PRIVATE) 유튜버는 존재 여부를 누설하지 않고 404 CREATOR_NOT_FOUND를 반환한다")
    void 상세조회_비공개유튜버_404CREATOR_NOT_FOUND를반환한다() throws Exception {
        // given
        UUID creatorId = UUID.randomUUID();
        insertCreator(
                creatorId, "UC-" + UUID.randomUUID(), "비공개 채널",
                null, null, null,
                "PRIVATE", "ACTIVE", "AVAILABLE", null);

        // when & then
        mockMvc.perform(get("/api/creators/{creatorId}", creatorId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"));
    }

    @Test
    @DisplayName("삭제(DELETED) 유튜버는 존재 여부를 누설하지 않고 404 CREATOR_NOT_FOUND를 반환한다")
    void 상세조회_삭제된유튜버_404CREATOR_NOT_FOUND를반환한다() throws Exception {
        // given
        UUID creatorId = UUID.randomUUID();
        insertCreator(
                creatorId, "UC-" + UUID.randomUUID(), "삭제된 채널",
                null, null, null,
                "PRIVATE", "DELETED", "AVAILABLE", OffsetDateTime.now());

        // when & then
        mockMvc.perform(get("/api/creators/{creatorId}", creatorId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"));
    }

    @Test
    @DisplayName("외부 이용 불가(UNAVAILABLE) 유튜버는 404 CREATOR_NOT_FOUND를 반환한다")
    void 상세조회_외부이용불가유튜버_404CREATOR_NOT_FOUND를반환한다() throws Exception {
        // given: ck_creator__external_unavailable_private가 UNAVAILABLE을 PRIVATE와만 허용하므로
        // 외부 이용 불가 Creator는 PUBLIC 상태로 존재할 수 없다.
        UUID creatorId = UUID.randomUUID();
        insertCreator(
                creatorId, "UC-" + UUID.randomUUID(), "이용불가 채널",
                null, null, null,
                "PRIVATE", "ACTIVE", "UNAVAILABLE", null);

        // when & then
        mockMvc.perform(get("/api/creators/{creatorId}", creatorId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"));
    }

    @Test
    @DisplayName("UUID 형식이 아닌 식별자는 400 INVALID_IDENTIFIER를 반환한다")
    void 상세조회_UUID형식아닌식별자_400INVALID_IDENTIFIER를반환한다() throws Exception {
        mockMvc.perform(get("/api/creators/{creatorId}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDENTIFIER"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));
    }

    private void insertCreator(
            UUID id,
            String externalChannelId,
            String channelName,
            String profileImageUrl,
            String description,
            String handle,
            String publicationStatus,
            String lifecycleStatus,
            String externalAvailabilityStatus,
            OffsetDateTime deletedAt) {
        jdbcTemplate.update(
                "INSERT INTO creator "
                        + "(id, external_channel_id, channel_name, channel_url, profile_image_url, "
                        + "description, handle, publication_status, lifecycle_status, "
                        + "external_availability_status, external_status_checked_at, deleted_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                externalChannelId,
                channelName,
                "https://www.youtube.com/channel/" + externalChannelId,
                profileImageUrl,
                description,
                handle,
                publicationStatus,
                lifecycleStatus,
                externalAvailabilityStatus,
                OffsetDateTime.now(),
                deletedAt);
    }
}

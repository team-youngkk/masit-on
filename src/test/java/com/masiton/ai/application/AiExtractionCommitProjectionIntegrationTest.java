package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.masiton.test.IntegrationTestFixtures.sha256;

import java.math.BigDecimal;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.masiton.orchestration.application.port.in.AutoRegisterVerifiedContentUseCase;
import com.masiton.restaurant.application.port.out.NaturalLanguageRateLimitPort;
import com.masiton.test.FullContextIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("AI 확정 커밋과 자연어 공개 조회 통합")
class AiExtractionCommitProjectionIntegrationTest extends FullContextIntegrationTest {

    private static final UUID REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000014");
    private static final UUID CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID TAG_ID = UUID.fromString("30000000-0000-4000-8000-000000000007");

    @Autowired
    private AiExtractionResultCommitService commitService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AutoRegisterVerifiedContentUseCase autoRegister;

    @MockitoBean
    private NaturalLanguageRateLimitPort naturalLanguageRateLimitPort;

    @BeforeEach
    void cleanUpState() {
        cleanupTransactionalState(jdbcTemplate);
        given(naturalLanguageRateLimitPort.tryAcquire(any())).willReturn(true);
    }

    @Test
    @DisplayName("AI 확정 커밋은 Snapshot provenance와 VisitTag를 만들고 자연어 공개 검색에 연결한다")
    void ai확정커밋_SnapshotProvenance와VisitTag를생성하고_자연어공개검색에연결한다() throws Exception {
        // given
        Fixture fixture = insertExistingPublicContent();
        OffsetDateTime finishedAt = OffsetDateTime.now();
        OffsetDateTime startedAt = finishedAt.minusSeconds(2);
        UUID jobId = insertRunningJob(startedAt);
        given(autoRegister.register(any())).willReturn(new AutoRegisterVerifiedContentUseCase.RegistrationResult(
                fixture.restaurantId(), fixture.creatorId(), fixture.videoId(), fixture.visitId(),
                false, false, false, false));
        AiExtractionResultCommitService.ProcessCommand command = new AiExtractionResultCommitService.ProcessCommand(
                jobId, "worker-1", 1, startedAt, finishedAt, "provider-request-1", "COMPLETE",
                "{}", "[]", "{}", "{}", "[]", false, null, "AUTO_CONFIRMED",
                List.of(new AiExtractionResultCommitService.AiTagCandidate(
                        "candidate-spicy", "TASTE", "TASTE_SPICY", "매운맛", BigDecimal.valueOf(0.95),
                        "{\"type\":\"TIMESTAMP\",\"startMs\":1000,\"endMs\":2000}",
                        "[]", "P1/S1", "AUTO_ACCEPT", null, true, null)),
                List.of());
        AutoRegisterVerifiedContentUseCase.VerifiedContentCommand registration =
                new AutoRegisterVerifiedContentUseCase.VerifiedContentCommand(
                        new AutoRegisterVerifiedContentUseCase.RestaurantCandidate(
                                REGION_ID, CATEGORY_ID, "AI 확정 맛집", "kakao-existing-" + fixture.restaurantId(),
                                "https://example.com/place/" + fixture.restaurantId(), "서울특별시 마포구 테스트로 1",
                                null, "02-1234-5678", BigDecimal.valueOf(37.5665), BigDecimal.valueOf(126.9780)),
                        new AutoRegisterVerifiedContentUseCase.CreatorCandidate(
                                "UC-existing-" + fixture.creatorId(), "AI 확정 채널",
                                "https://example.com/channel/" + fixture.creatorId()),
                        new AutoRegisterVerifiedContentUseCase.VideoCandidate(
                                "VID-existing-" + fixture.videoId(), "UC-existing-" + fixture.creatorId(),
                                "AI 확정 영상", "https://example.com/video/" + fixture.videoId(),
                                "https://example.com/thumbnail/" + fixture.videoId(), finishedAt, finishedAt), true);

        // when
        assertThat(commitService.persistConfirmed(command, registration)).isTrue();

        // then
        UUID snapshotId = jdbcTemplate.queryForObject(
                "SELECT created_from_snapshot_id FROM visit_tag WHERE visit_id = ? AND tag_definition_id = ?",
                UUID.class, fixture.visitId(), TAG_ID);
        assertThat(snapshotId).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT registered_visit_id FROM ai_candidate_snapshot WHERE id = ?", UUID.class, snapshotId))
                .isEqualTo(fixture.visitId());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT execution_status FROM ai_extraction_job WHERE id = ?", String.class, jobId))
                .isEqualTo("SUCCEEDED");

        mockMvc.perform(post("/api/restaurants/natural-language-search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sentence\":\"매운맛 맛집\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interpretation.status").value("APPLIED"))
                .andExpect(jsonPath("$.results.items[0].id").value(fixture.restaurantId().toString()));
    }

    private Fixture insertExistingPublicContent() {
        UUID restaurantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO restaurant (id, region_id, food_category_id, name, kakao_place_id, kakao_place_url, "
                        + "road_address, phone_number, latitude, longitude) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                restaurantId, REGION_ID, CATEGORY_ID, "기존 AI 맛집", "kakao-existing-" + restaurantId,
                "https://example.com/place/" + restaurantId, "서울특별시 마포구 테스트로 1", "02-1234-5678",
                BigDecimal.valueOf(37.5665), BigDecimal.valueOf(126.978));
        UUID creatorId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO creator (id, external_channel_id, channel_name, channel_url, "
                        + "external_status_checked_at) VALUES (?, ?, ?, ?, ?)",
                creatorId, "UC-existing-" + creatorId, "기존 AI 채널", "https://example.com/channel/" + creatorId,
                OffsetDateTime.now());
        UUID videoId = UUID.randomUUID();
        String externalVideoId = "VID-" + videoId.toString().substring(0, 20);
        jdbcTemplate.update(
                "INSERT INTO video (id, creator_id, external_video_id, publisher_external_channel_id, title, "
                        + "source_url, thumbnail_url, external_status_checked_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                videoId, creatorId, externalVideoId, "UC-existing-" + creatorId, "기존 AI 영상",
                "https://example.com/video/" + videoId, "https://example.com/thumbnail/" + videoId,
                OffsetDateTime.now());
        UUID visitId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO visit (id, restaurant_id, creator_id, video_id) VALUES (?, ?, ?, ?)",
                visitId, restaurantId, creatorId, videoId);
        return new Fixture(restaurantId, creatorId, videoId, visitId);
    }

    private UUID insertRunningJob(OffsetDateTime startedAt) {
        UUID jobId = UUID.randomUUID();
        String videoId = "ai-commit-" + jobId.toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO ai_extraction_job (
                    id, source, priority, youtube_channel_id, youtube_video_id, video_url,
                    input_mode, input_hash, provider, model_version, prompt_version, schema_version,
                    execution_status, attempt_count, lease_owner, lease_expires_at, created_at, started_at
                ) VALUES (?, 'ADMIN', 'REALTIME', 'ai-commit-channel', ?, ?, 'ADMIN_TEXT', ?,
                          'GOOGLE_GEMINI', 'gemini-3.5-flash-lite', 'P1', 'S1',
                          'RUNNING', 1, 'worker-1', ?, ?, ?)
                """, jobId, videoId, "https://www.youtube.com/watch?v=" + videoId, sha256(jobId.toString()),
                startedAt.plusMinutes(5), startedAt.minusSeconds(1), startedAt);
        return jobId;
    }

    private record Fixture(UUID restaurantId, UUID creatorId, UUID videoId, UUID visitId) {
    }
}

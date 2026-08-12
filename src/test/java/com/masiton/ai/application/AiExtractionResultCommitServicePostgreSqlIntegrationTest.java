package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static com.masiton.test.IntegrationTestFixtures.sha256;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.masiton.orchestration.application.port.in.AutoRegisterVerifiedContentUseCase;
import com.masiton.test.FullContextIntegrationTest;
import com.masiton.visit.application.port.in.RegisterVisitUseCase;

@DisplayName("AI 추출 결과 커밋 PostgreSQL 원자성")
@SpringBootTest
class AiExtractionResultCommitServicePostgreSqlIntegrationTest extends FullContextIntegrationTest {

    private static final UUID REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID FOOD_CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");

    @Autowired
    private AiExtractionResultCommitService commitService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private RegisterVisitUseCase visitRegistration;

    private UUID jobId;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;

    @BeforeEach
    void setUp() {
        jobId = UUID.randomUUID();
        startedAt = OffsetDateTime.now().minusSeconds(5);
        finishedAt = OffsetDateTime.now();
        String channelId = "channel-" + suffix();
        String videoId = "video-" + suffix();
        jdbcTemplate.update("""
                INSERT INTO ai_extraction_job (
                    id, source, priority, youtube_channel_id, youtube_video_id, video_url,
                    input_mode, input_hash, provider, model_version, prompt_version, schema_version,
                    execution_status, attempt_count, lease_owner, lease_expires_at, created_at, started_at
                ) VALUES (?, 'ADMIN', 'REALTIME', ?, ?, ?, 'ADMIN_TEXT', ?,
                          'GOOGLE_GEMINI', 'gemini-3-flash-preview', 'P1', 'S1',
                          'RUNNING', 1, 'worker-1', ?, ?, ?)
                """, jobId, channelId, videoId, "https://www.youtube.com/watch?v=" + videoId,
                sha256(jobId.toString()), OffsetDateTime.now().plusMinutes(5), startedAt.minusSeconds(1), startedAt);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM ai_extraction_job WHERE id = ?", jobId);
        jdbcTemplate.update("DELETE FROM visit_tag WHERE visit_id IN (SELECT id FROM visit WHERE creator_id IN (SELECT id FROM creator WHERE external_channel_id LIKE ?))", "channel-" + suffix() + "%");
        jdbcTemplate.update("DELETE FROM visit WHERE creator_id IN (SELECT id FROM creator WHERE external_channel_id LIKE ?)", "channel-" + suffix() + "%");
        jdbcTemplate.update("DELETE FROM video WHERE external_video_id = ?", "video-" + suffix());
        jdbcTemplate.update("DELETE FROM restaurant WHERE kakao_place_id = ?", "kakao-" + suffix());
        jdbcTemplate.update("DELETE FROM creator WHERE external_channel_id = ?", "channel-" + suffix());
    }

    @Test
    @DisplayName("정식 등록 후 단계에서 실패하면 후보·정식 Entity·VisitTag·시도 완료가 모두 롤백된다")
    void persistConfirmed_Visit등록실패_모든쓰기와작업완료를롤백한다() {
        // Given
        willThrow(new IllegalStateException("injected visit failure"))
                .given(visitRegistration).register(any());
        String channelId = "channel-" + suffix();
        String videoId = "video-" + suffix();
        String kakaoPlaceId = "kakao-" + suffix();
        AutoRegisterVerifiedContentUseCase.VerifiedContentCommand registration =
                new AutoRegisterVerifiedContentUseCase.VerifiedContentCommand(
                        new AutoRegisterVerifiedContentUseCase.RestaurantCandidate(
                                REGION_ID, FOOD_CATEGORY_ID, "원자성 맛집", kakaoPlaceId,
                                "https://place.map.kakao.com/" + jobId, "서울특별시 마포구 월드컵로 1", null,
                                "02-1234-5678", BigDecimal.valueOf(37.5), BigDecimal.valueOf(126.9)),
                        new AutoRegisterVerifiedContentUseCase.CreatorCandidate(
                                channelId, "원자성 채널", "https://www.youtube.com/channel/" + channelId),
                        new AutoRegisterVerifiedContentUseCase.VideoCandidate(
                                videoId, channelId, "원자성 영상", "https://www.youtube.com/watch?v=" + videoId,
                                "https://img.youtube.com/vi/atomic/0.jpg", finishedAt, finishedAt), true);
        AiExtractionResultCommitService.ProcessCommand command = new AiExtractionResultCommitService.ProcessCommand(
                jobId, "worker-1", 1, startedAt, finishedAt, "provider-request-atomic", "COMPLETE",
                "{}", "[]", "{}", "{}", "[]", null, "AUTO_CONFIRMED", List.of());

        // When / Then
        assertThatThrownBy(() -> commitService.persistConfirmed(command, registration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("injected visit failure");
        assertThat(count("ai_candidate_snapshot", "job_id = ?", jobId)).isZero();
        assertThat(count("creator", "external_channel_id = ?", channelId)).isZero();
        assertThat(count("restaurant", "kakao_place_id = ?", kakaoPlaceId)).isZero();
        assertThat(count("video", "external_video_id = ?", videoId)).isZero();
        assertThat(count("visit", "creator_id IN (SELECT id FROM creator WHERE external_channel_id = ?)", channelId)).isZero();
        assertThat(count("visit_tag", "visit_id NOT IN (SELECT id FROM visit)")).isZero();
        assertThat(count("ai_extraction_attempt", "job_id = ?", jobId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT execution_status FROM ai_extraction_job WHERE id = ?", String.class, jobId))
                .isEqualTo("RUNNING");
    }

    private int count(String table, String predicate, Object... args) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table + " WHERE " + predicate,
                Integer.class, args);
    }

    private String suffix() {
        return jobId.toString().substring(0, 8);
    }
}

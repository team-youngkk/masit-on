package com.masiton.ai.application;

import static com.masiton.test.IntegrationTestFixtures.sha256;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

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

import com.masiton.ai.application.port.out.AiRegistrationUnitReviewStore;
import com.masiton.ai.application.port.out.AiRegistrationUnitStore;
import com.masiton.test.FullContextIntegrationTest;

@DisplayName("등록 단위 CONFIRM 커밋 PostgreSQL 원자성")
@SpringBootTest
class RegistrationUnitConfirmCommitServicePostgreSqlIntegrationTest extends FullContextIntegrationTest {

    private static final UUID REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID FOOD_CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");

    @Autowired
    private RegistrationUnitConfirmCommitService commitService;

    @Autowired
    private AiRegistrationUnitStore registrationUnitStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private AiRegistrationUnitReviewStore registrationUnitReviewStore;

    private UUID jobId;
    private UUID snapshotId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        cleanupTransactionalState(jdbcTemplate);
        jobId = UUID.randomUUID();
        snapshotId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO ai_extraction_job (
                    id, source, priority, youtube_channel_id, youtube_video_id, video_url,
                    input_mode, input_hash, provider, model_version, prompt_version, schema_version,
                    execution_status, attempt_count, created_at
                ) VALUES (?, 'ADMIN', 'REALTIME', ?, ?, ?, 'ADMIN_TEXT', ?, 'GOOGLE_GEMINI',
                          'gemini-3.5-flash-lite', 'P8', 'S2', 'QUEUED', 0, ?)
                """, jobId, "channel-" + suffix(), "video-" + suffix(),
                "https://www.youtube.com/watch?v=video-" + suffix(), sha256(jobId.toString()), now);
        jdbcTemplate.update("""
                INSERT INTO ai_candidate_snapshot (
                    id, job_id, snapshot_version, candidate_fields, candidate_tags, field_confidences,
                    evidence, missing_fields, review_status, reviewed_at, created_at
                ) VALUES (?, ?, 1, '{}'::jsonb, '[]'::jsonb, '{}'::jsonb, '{}'::jsonb, '[]'::jsonb,
                          'AUTO_BLOCKED', ?, ?)
                """, snapshotId, jobId, now, now);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM ai_extraction_job WHERE id = ?", jobId);
        jdbcTemplate.update("DELETE FROM visit WHERE creator_id IN (SELECT id FROM creator WHERE external_channel_id = ?)",
                "channel-" + suffix());
        jdbcTemplate.update("DELETE FROM video WHERE external_video_id = ?", "video-" + suffix());
        jdbcTemplate.update("DELETE FROM restaurant WHERE kakao_place_id = ?", "kakao-" + suffix());
        jdbcTemplate.update("DELETE FROM creator WHERE external_channel_id = ?", "channel-" + suffix());
    }

    @Test
    @DisplayName("검토 감사 이력 삽입이 실패하면 이미 반영한 등록 단위 상태 전이도 함께 롤백된다")
    void commit_감사이력삽입실패_등록단위상태전이도롤백된다() {
        // Given
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        registerContentFixtures(restaurantId, creatorId, videoId, visitId);
        UUID unitId = registrationUnitStore.insert(new AiRegistrationUnitStore.RegistrationUnitInsert(
                snapshotId, 1, "행복식당", "AUTO_BLOCKED", "PLACE_NOT_FOUND", null, null,
                null, null, null, null, null, "WORKER", OffsetDateTime.now()));
        AiRegistrationUnitStore.RegisteredResult registered = new AiRegistrationUnitStore.RegisteredResult(
                restaurantId, creatorId, videoId, visitId, "[]",
                "{\"kakaoPlaceUrl\":\"https://place.map.kakao.com/1\",\"roadAddress\":\"서울특별시 마포구 월드컵로 1\","
                        + "\"matchedBy\":\"MANUAL_OVERRIDE\"}",
                "{\"foodCategoryName\":\"한식\",\"resolvedBy\":\"KAKAO_PLACE_CATEGORY\"}", null);
        willThrow(new IllegalStateException("injected review audit failure"))
                .given(registrationUnitReviewStore).insert(any());

        // When / Then
        assertThatThrownBy(() -> commitService.commit(unitId, "AUTO_BLOCKED", registered, snapshotId, visitId,
                List.of(), adminId, "보충 입력 확인 완료", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("injected review audit failure");

        AiRegistrationUnitStore.RegistrationUnitRow row = registrationUnitStore.findBySnapshotId(snapshotId).get(0);
        assertThat(row.reviewStatus()).isEqualTo("AUTO_BLOCKED");
        assertThat(row.blockReason()).isEqualTo("PLACE_NOT_FOUND");
        assertThat(row.registeredRestaurantId()).isNull();
    }

    @Test
    @DisplayName("expectedReviewStatus가 어긋나면 아무것도 반영하지 않고 false를 반환한다")
    void commit_expectedReviewStatus불일치_아무것도반영하지않고false를반환한다() {
        // Given
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        registerContentFixtures(restaurantId, creatorId, videoId, visitId);
        UUID unitId = registrationUnitStore.insert(new AiRegistrationUnitStore.RegistrationUnitInsert(
                snapshotId, 1, "행복식당", "AUTO_CONFIRMED", null,
                "{\"kakaoPlaceUrl\":\"https://place.map.kakao.com/1\",\"roadAddress\":\"서울특별시 마포구 월드컵로 1\","
                        + "\"matchedBy\":\"NAME_AND_DISTRICT\"}",
                "{\"foodCategoryName\":\"한식\",\"resolvedBy\":\"KAKAO_PLACE_CATEGORY\"}",
                restaurantId, creatorId, videoId, visitId, "[]", "WORKER", OffsetDateTime.now()));
        AiRegistrationUnitStore.RegisteredResult registered = new AiRegistrationUnitStore.RegisteredResult(
                restaurantId, creatorId, videoId, visitId, "[]", "{}", "{}", null);

        // When
        boolean committed = commitService.commit(unitId, "AUTO_BLOCKED", registered, snapshotId, visitId,
                List.of(), adminId, "보충 입력 확인 완료", null);

        // Then
        assertThat(committed).isFalse();
        verify(registrationUnitReviewStore, org.mockito.Mockito.never()).insert(any());
        AiRegistrationUnitStore.RegistrationUnitRow row = registrationUnitStore.findBySnapshotId(snapshotId).get(0);
        assertThat(row.reviewStatus()).isEqualTo("AUTO_CONFIRMED");
    }

    @Test
    @DisplayName("정상 반영은 등록 단위 상태 전이와 검토 감사 이력 삽입을 함께 커밋한다")
    void commit_정상반영_상태전이와감사이력을함께커밋한다() {
        // Given
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        registerContentFixtures(restaurantId, creatorId, videoId, visitId);
        UUID unitId = registrationUnitStore.insert(new AiRegistrationUnitStore.RegistrationUnitInsert(
                snapshotId, 1, "행복식당", "AUTO_BLOCKED", "PLACE_NOT_FOUND", null, null,
                null, null, null, null, null, "WORKER", OffsetDateTime.now()));
        AiRegistrationUnitStore.RegisteredResult registered = new AiRegistrationUnitStore.RegisteredResult(
                restaurantId, creatorId, videoId, visitId, "[]",
                "{\"kakaoPlaceUrl\":\"https://place.map.kakao.com/1\",\"roadAddress\":\"서울특별시 마포구 월드컵로 1\","
                        + "\"matchedBy\":\"MANUAL_OVERRIDE\"}",
                "{\"foodCategoryName\":\"한식\",\"resolvedBy\":\"KAKAO_PLACE_CATEGORY\"}", null);
        given(registrationUnitReviewStore.insert(any())).willReturn(UUID.randomUUID());

        // When
        boolean committed = commitService.commit(unitId, "AUTO_BLOCKED", registered, snapshotId, visitId,
                List.of(), adminId, "보충 입력 확인 완료", "{\"kakaoPlaceUrl\":\"https://place.map.kakao.com/1\"}");

        // Then
        assertThat(committed).isTrue();
        AiRegistrationUnitStore.RegistrationUnitRow row = registrationUnitStore.findBySnapshotId(snapshotId).get(0);
        assertThat(row.reviewStatus()).isEqualTo("MANUAL_OVERRIDE");
        assertThat(row.registeredRestaurantId()).isEqualTo(restaurantId);
        verify(registrationUnitReviewStore).insert(new AiRegistrationUnitReviewStore.RegistrationUnitReviewInsert(
                unitId, "CONFIRM", "보충 입력 확인 완료", "{\"kakaoPlaceUrl\":\"https://place.map.kakao.com/1\"}", null, null,
                adminId));
    }

    private void registerContentFixtures(UUID restaurantId, UUID creatorId, UUID videoId, UUID visitId) {
        jdbcTemplate.update("""
                INSERT INTO creator (id, external_channel_id, channel_name, channel_url, external_status_checked_at)
                VALUES (?, ?, '테스트 채널', 'https://example.com/channel', now())
                """, creatorId, "channel-" + suffix());
        jdbcTemplate.update("""
                INSERT INTO restaurant (id, region_id, food_category_id, name, kakao_place_id, kakao_place_url,
                    road_address, phone_number)
                VALUES (?, ?, ?, '행복식당', ?, 'https://place.map.kakao.com/1', '서울특별시 마포구 월드컵로 1', '02-1234-5678')
                """, restaurantId, REGION_ID, FOOD_CATEGORY_ID, "kakao-" + suffix());
        jdbcTemplate.update("""
                INSERT INTO video (id, creator_id, external_video_id, publisher_external_channel_id, title,
                    source_url, thumbnail_url, external_status_checked_at)
                VALUES (?, ?, ?, ?, '테스트 영상', 'https://example.com/video', 'https://example.com/thumb', now())
                """, videoId, creatorId, "video-" + suffix(), "channel-" + suffix());
        jdbcTemplate.update("INSERT INTO visit (id, restaurant_id, creator_id, video_id) VALUES (?, ?, ?, ?)",
                visitId, restaurantId, creatorId, videoId);
    }

    private String suffix() {
        return jobId.toString().substring(0, 8);
    }
}

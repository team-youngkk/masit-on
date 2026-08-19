package com.masiton.ai.infrastructure.persistence;

import static com.masiton.test.IntegrationTestFixtures.sha256;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

import com.masiton.ai.application.port.out.AiRegistrationUnitStore;
import com.masiton.test.FullContextIntegrationTest;

@DisplayName("ai_registration_unit JDBC 저장소")
@SpringBootTest
class JdbcAiRegistrationUnitStorePostgreSqlIntegrationTest extends FullContextIntegrationTest {

    private static final UUID REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID FOOD_CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");

    @Autowired
    private AiRegistrationUnitStore store;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID jobId;
    private UUID snapshotId;

    @BeforeEach
    void setUp() {
        jobId = UUID.randomUUID();
        snapshotId = UUID.randomUUID();
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
    @DisplayName("등록 완료로 갱신하면 등록 결과 4종과 재사용 자원·판정 근거가 모두 저장된다")
    void markRegistered_등록완료로갱신하면_등록결과와판정근거가저장된다() {
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
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
        UUID unitId = store.insert(new AiRegistrationUnitStore.RegistrationUnitInsert(
                snapshotId, 1, "행복식당", "AUTO_BLOCKED", "PLACE_NOT_FOUND", null, null,
                null, null, null, null, null, "WORKER", OffsetDateTime.now()));

        boolean updated = store.markRegistered(unitId, "AUTO_BLOCKED", new AiRegistrationUnitStore.RegisteredResult(
                restaurantId, creatorId, videoId, visitId, "[\"creator\",\"video\"]",
                "{\"kakaoPlaceUrl\":\"https://place.map.kakao.com/1\",\"roadAddress\":\"서울특별시 마포구 월드컵로 1\","
                        + "\"matchedBy\":\"NAME_AND_DISTRICT\"}",
                "{\"foodCategoryName\":\"한식\",\"resolvedBy\":\"KAKAO_PLACE_CATEGORY\"}", "WORKER"));

        assertThat(updated).isTrue();
        AiRegistrationUnitStore.RegistrationUnitRow row = store.findBySnapshotId(snapshotId).get(0);
        assertThat(row.reviewStatus()).isEqualTo("AUTO_CONFIRMED");
        assertThat(row.blockReason()).isNull();
        assertThat(row.registeredRestaurantId()).isEqualTo(restaurantId);
        assertThat(row.registeredCreatorId()).isEqualTo(creatorId);
        assertThat(row.registeredVideoId()).isEqualTo(videoId);
        assertThat(row.registeredVisitId()).isEqualTo(visitId);
        assertThat(row.reusedResources()).containsExactly("creator", "video");
        assertThat(row.placeDecisionJson()).contains("NAME_AND_DISTRICT");
        assertThat(row.categoryDecisionJson()).contains("한식");
    }

    @Test
    @DisplayName("차단 상태로 삽입한 등록 단위를 Snapshot 기준으로 다시 조회할 수 있다")
    void insert_차단상태_조회로다시읽을수있다() {
        OffsetDateTime decidedAt = OffsetDateTime.now();

        UUID unitId = store.insert(new AiRegistrationUnitStore.RegistrationUnitInsert(
                snapshotId, 1, "행복식당", "AUTO_BLOCKED", "PLACE_NOT_FOUND", null, null,
                null, null, null, null, null, "WORKER", decidedAt));

        List<AiRegistrationUnitStore.RegistrationUnitRow> rows = store.findBySnapshotId(snapshotId);
        assertThat(rows).hasSize(1);
        AiRegistrationUnitStore.RegistrationUnitRow row = rows.get(0);
        assertThat(row.id()).isEqualTo(unitId);
        assertThat(row.snapshotId()).isEqualTo(snapshotId);
        assertThat(row.unitIndex()).isEqualTo(1);
        assertThat(row.restaurantName()).isEqualTo("행복식당");
        assertThat(row.reviewStatus()).isEqualTo("AUTO_BLOCKED");
        assertThat(row.blockReason()).isEqualTo("PLACE_NOT_FOUND");
        assertThat(row.placeDecisionJson()).isNull();
        assertThat(row.categoryDecisionJson()).isNull();
        assertThat(row.registeredRestaurantId()).isNull();
        assertThat(row.reusedResources()).isEmpty();
        assertThat(row.executedBy()).isEqualTo("WORKER");
        assertThat(row.rolledBackAt()).isNull();
        assertThat(row.discardedAt()).isNull();
    }

    @Test
    @DisplayName("두 등록 단위를 unit_index 오름차순으로 조회한다")
    void findBySnapshotId_복수등록단위_unit_index순으로반환한다() {
        OffsetDateTime decidedAt = OffsetDateTime.now();
        store.insert(new AiRegistrationUnitStore.RegistrationUnitInsert(
                snapshotId, 2, "둘째 맛집", "AUTO_BLOCKED", "PLACE_AMBIGUOUS", null, null,
                null, null, null, null, null, "WORKER", decidedAt));
        store.insert(new AiRegistrationUnitStore.RegistrationUnitInsert(
                snapshotId, 1, "첫 맛집", "AUTO_BLOCKED", "CATEGORY_UNRESOLVED", null, null,
                null, null, null, null, null, "WORKER", decidedAt));

        List<AiRegistrationUnitStore.RegistrationUnitRow> rows = store.findBySnapshotId(snapshotId);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).unitIndex()).isEqualTo(1);
        assertThat(rows.get(0).restaurantName()).isEqualTo("첫 맛집");
        assertThat(rows.get(1).unitIndex()).isEqualTo(2);
        assertThat(rows.get(1).restaurantName()).isEqualTo("둘째 맛집");
    }

    @Test
    @DisplayName("존재하지 않는 등록 단위를 등록 완료로 갱신하면 아무것도 바꾸지 않고 false를 반환한다")
    void markRegistered_존재하지않는단위_false를반환한다() {
        boolean updated = store.markRegistered(UUID.randomUUID(), "AUTO_BLOCKED",
                new AiRegistrationUnitStore.RegisteredResult(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        "[\"creator\",\"video\"]", "{\"kakaoPlaceUrl\":\"https://place.map.kakao.com/1\"}",
                        "{\"foodCategoryName\":\"한식\"}", "WORKER"));

        assertThat(updated).isFalse();
    }

    @Test
    @DisplayName("이미 다른 요청이 상태를 바꾼 등록 단위는 expectedReviewStatus가 어긋나 false를 반환하고 아무것도 갱신하지 않는다")
    void markRegistered_동시요청으로상태가바뀐뒤_false를반환하고갱신하지않는다() {
        UUID firstRestaurantId = UUID.randomUUID();
        UUID firstCreatorId = UUID.randomUUID();
        UUID firstVideoId = UUID.randomUUID();
        UUID firstVisitId = UUID.randomUUID();
        registerContentFixtures(firstRestaurantId, firstCreatorId, firstVideoId, firstVisitId);
        UUID unitId = store.insert(new AiRegistrationUnitStore.RegistrationUnitInsert(
                snapshotId, 1, "행복식당", "AUTO_BLOCKED", "PLACE_NOT_FOUND", null, null,
                null, null, null, null, null, "WORKER", OffsetDateTime.now()));
        boolean firstRequestWon = store.markRegistered(unitId, "AUTO_BLOCKED", new AiRegistrationUnitStore.RegisteredResult(
                firstRestaurantId, firstCreatorId, firstVideoId, firstVisitId, "[]",
                "{\"kakaoPlaceUrl\":\"https://place.map.kakao.com/1\"}", "{\"foodCategoryName\":\"한식\"}", "ADMIN"));

        // second request still observes the pre-write "AUTO_BLOCKED" snapshot (its own lockByJobAndUnitId
        // read happened before the first request committed), so its expectedReviewStatus is stale by the
        // time it writes; the WHERE review_status = ? guard rejects it instead of overwriting the winner.
        boolean secondRequestWon = store.markRegistered(unitId, "AUTO_BLOCKED",
                new AiRegistrationUnitStore.RegisteredResult(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "[]",
                        "{\"kakaoPlaceUrl\":\"https://place.map.kakao.com/1\"}", "{\"foodCategoryName\":\"한식\"}", "ADMIN"));

        assertThat(firstRequestWon).isTrue();
        assertThat(secondRequestWon).isFalse();
        AiRegistrationUnitStore.RegistrationUnitRow row = store.findBySnapshotId(snapshotId).get(0);
        assertThat(row.registeredRestaurantId()).isEqualTo(firstRestaurantId);
    }

    @Test
    @DisplayName("작업 ID로 조회하면 최신 Snapshot의 등록 단위를 unit_index 오름차순으로 반환한다")
    void findByJobId_최신Snapshot의등록단위를순서대로반환한다() {
        store.insert(new AiRegistrationUnitStore.RegistrationUnitInsert(
                snapshotId, 2, "둘째 맛집", "AUTO_BLOCKED", "PLACE_AMBIGUOUS", null, null,
                null, null, null, null, null, "WORKER", OffsetDateTime.now()));
        store.insert(new AiRegistrationUnitStore.RegistrationUnitInsert(
                snapshotId, 1, "첫 맛집", "AUTO_BLOCKED", "CATEGORY_UNRESOLVED", null, null,
                null, null, null, null, null, "WORKER", OffsetDateTime.now()));

        List<AiRegistrationUnitStore.RegistrationUnitRow> rows = store.findByJobId(jobId);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).unitIndex()).isEqualTo(1);
        assertThat(rows.get(1).unitIndex()).isEqualTo(2);
    }

    @Test
    @DisplayName("존재하지 않는 등록 단위를 잠그려 하면 빈 값을 반환한다")
    void lockByJobAndUnitId_존재하지않음_빈값을반환한다() {
        assertThat(store.lockByJobAndUnitId(jobId, UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("다른 트랜잭션이 잠근 등록 단위에 동시 요청하면 동시성 충돌 예외를 던진다")
    void lockByJobAndUnitId_다른트랜잭션이잠금_동시성충돌예외를던진다() throws Exception {
        UUID unitId = store.insert(new AiRegistrationUnitStore.RegistrationUnitInsert(
                snapshotId, 1, "행복식당", "AUTO_BLOCKED", "PLACE_NOT_FOUND", null, null,
                null, null, null, null, null, "WORKER", OffsetDateTime.now()));

        try (java.sql.Connection lockingConnection = jdbcTemplate.getDataSource().getConnection()) {
            lockingConnection.setAutoCommit(false);
            try (java.sql.PreparedStatement statement = lockingConnection.prepareStatement(
                    "SELECT id FROM ai_registration_unit WHERE id = ? FOR UPDATE")) {
                statement.setObject(1, unitId);
                statement.executeQuery();
            }

            assertThatThrownBy(() -> store.lockByJobAndUnitId(jobId, unitId))
                    .isInstanceOf(com.masiton.ai.application.port.out.AiRegistrationUnitConcurrentAccessException.class);

            lockingConnection.rollback();
        }
    }

    @Test
    @DisplayName("보충 입력 확정은 MANUAL_OVERRIDE로 전환하고 executed_by는 바꾸지 않는다")
    void confirmWithSupplement_MANUAL_OVERRIDE로전환하고executedBy는유지한다() {
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        registerContentFixtures(restaurantId, creatorId, videoId, visitId);
        UUID unitId = store.insert(new AiRegistrationUnitStore.RegistrationUnitInsert(
                snapshotId, 1, "행복식당", "AUTO_BLOCKED", "PLACE_NOT_FOUND", null, null,
                null, null, null, null, null, "WORKER", OffsetDateTime.now()));

        boolean updated = store.confirmWithSupplement(unitId, "AUTO_BLOCKED", new AiRegistrationUnitStore.RegisteredResult(
                restaurantId, creatorId, videoId, visitId, "[]",
                "{\"kakaoPlaceUrl\":\"https://place.map.kakao.com/1\",\"roadAddress\":\"서울특별시 마포구 월드컵로 1\","
                        + "\"matchedBy\":\"MANUAL_OVERRIDE\"}",
                "{\"foodCategoryName\":\"한식\",\"resolvedBy\":\"KAKAO_PLACE_CATEGORY\"}", null));

        assertThat(updated).isTrue();
        AiRegistrationUnitStore.RegistrationUnitRow row = store.findBySnapshotId(snapshotId).get(0);
        assertThat(row.reviewStatus()).isEqualTo("MANUAL_OVERRIDE");
        assertThat(row.manualOverrideType()).isNull();
        assertThat(row.executedBy()).isEqualTo("WORKER");
        assertThat(row.registeredRestaurantId()).isEqualTo(restaurantId);
        assertThat(row.isRegistered()).isTrue();
    }

    @Test
    @DisplayName("expectedReviewStatus가 더 이상 일치하지 않으면 보충 입력 확정은 아무것도 바꾸지 않고 false를 반환한다")
    void confirmWithSupplement_expectedReviewStatus불일치_false를반환하고갱신하지않는다() {
        UUID unitId = store.insert(new AiRegistrationUnitStore.RegistrationUnitInsert(
                snapshotId, 1, "행복식당", "AUTO_BLOCKED", "PLACE_NOT_FOUND", null, null,
                null, null, null, null, null, "WORKER", OffsetDateTime.now()));

        boolean updated = store.confirmWithSupplement(unitId, "AUTO_CONFIRMED",
                new AiRegistrationUnitStore.RegisteredResult(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "[]",
                        "{\"kakaoPlaceUrl\":\"https://place.map.kakao.com/1\"}", "{\"foodCategoryName\":\"한식\"}", null));

        assertThat(updated).isFalse();
        AiRegistrationUnitStore.RegistrationUnitRow row = store.findBySnapshotId(snapshotId).get(0);
        assertThat(row.reviewStatus()).isEqualTo("AUTO_BLOCKED");
        assertThat(row.registeredRestaurantId()).isNull();
    }

    @Test
    @DisplayName("롤백은 등록 결과를 지우고 rolled_back_at을 채운다")
    void rollback_등록결과를지우고rolledBackAt을채운다() {
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        registerContentFixtures(restaurantId, creatorId, videoId, visitId);
        UUID unitId = store.insert(new AiRegistrationUnitStore.RegistrationUnitInsert(
                snapshotId, 1, "행복식당", "AUTO_CONFIRMED", null,
                "{\"kakaoPlaceUrl\":\"https://place.map.kakao.com/1\",\"roadAddress\":\"서울특별시 마포구 월드컵로 1\","
                        + "\"matchedBy\":\"NAME_AND_DISTRICT\"}",
                "{\"foodCategoryName\":\"한식\",\"resolvedBy\":\"KAKAO_PLACE_CATEGORY\"}",
                restaurantId, creatorId, videoId, visitId, "[]", "WORKER", OffsetDateTime.now()));

        store.rollback(unitId, OffsetDateTime.now());

        AiRegistrationUnitStore.RegistrationUnitRow row = store.findBySnapshotId(snapshotId).get(0);
        assertThat(row.reviewStatus()).isEqualTo("MANUAL_OVERRIDE");
        assertThat(row.manualOverrideType()).isEqualTo("ROLLED_BACK");
        assertThat(row.registeredRestaurantId()).isNull();
        assertThat(row.placeDecisionJson()).isNull();
        assertThat(row.categoryDecisionJson()).isNull();
        assertThat(row.isRegistered()).isFalse();
    }

    @Test
    @DisplayName("폐기는 discarded_at을 채우고 block_reason을 지운다")
    void discard_discardedAt을채우고blockReason을지운다() {
        UUID unitId = store.insert(new AiRegistrationUnitStore.RegistrationUnitInsert(
                snapshotId, 1, "행복식당", "AUTO_BLOCKED", "PLACE_NOT_FOUND", null, null,
                null, null, null, null, null, "WORKER", OffsetDateTime.now()));

        store.discard(unitId, OffsetDateTime.now());

        AiRegistrationUnitStore.RegistrationUnitRow row = store.findBySnapshotId(snapshotId).get(0);
        assertThat(row.reviewStatus()).isEqualTo("MANUAL_OVERRIDE");
        assertThat(row.manualOverrideType()).isEqualTo("DISCARDED");
        assertThat(row.blockReason()).isNull();
        assertThat(row.registeredRestaurantId()).isNull();
    }

    @Test
    @DisplayName("카테고리 보정은 category_decision만 바꾸고 등록 결과는 유지한다")
    void adjustCategory_categoryDecision만바꾸고등록결과는유지한다() {
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        registerContentFixtures(restaurantId, creatorId, videoId, visitId);
        UUID unitId = store.insert(new AiRegistrationUnitStore.RegistrationUnitInsert(
                snapshotId, 1, "행복식당", "AUTO_CONFIRMED", null,
                "{\"kakaoPlaceUrl\":\"https://place.map.kakao.com/1\",\"roadAddress\":\"서울특별시 마포구 월드컵로 1\","
                        + "\"matchedBy\":\"NAME_AND_DISTRICT\"}",
                "{\"foodCategoryName\":\"한식\",\"resolvedBy\":\"KAKAO_PLACE_CATEGORY\"}",
                restaurantId, creatorId, videoId, visitId, "[]", "WORKER", OffsetDateTime.now()));

        store.adjustCategory(unitId, "{\"foodCategoryName\":\"일식\",\"resolvedBy\":\"MANUAL_OVERRIDE\"}");

        AiRegistrationUnitStore.RegistrationUnitRow row = store.findBySnapshotId(snapshotId).get(0);
        assertThat(row.reviewStatus()).isEqualTo("MANUAL_OVERRIDE");
        assertThat(row.categoryDecisionJson()).contains("일식", "MANUAL_OVERRIDE");
        assertThat(row.registeredRestaurantId()).isEqualTo(restaurantId);
        assertThat(row.isRegistered()).isTrue();
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

package com.masiton.ai.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static com.masiton.test.IntegrationTestFixtures.sha256;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.masiton.ai.application.port.out.AiExtractionStatisticsPort;
import com.masiton.test.FullContextIntegrationTest;

@DisplayName("AI 추출 통계 PostgreSQL 집계")
class JdbcAiExtractionStatisticsAdapterIntegrationTest extends FullContextIntegrationTest {
    private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-10-01T00:00:00Z");
    private static final OffsetDateTime TO = FROM.plusDays(1);
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AiExtractionStatisticsPort port;

    @Test
    @DisplayName("6개 확정과 4개 차단은 고유 작업 한 건과 차단율 0.4로 집계한다")
    void 집계_6확정4차단_작업중복없이04를반환한다() {
        // Given
        UUID job = job("ADMIN", "gemini-3.5-flash-lite", FROM);
        UUID snapshot = snapshot(job, 1);
        UUID[] resources = resources();
        for (int i = 1; i <= 6; i++) unit(snapshot, i, "AUTO_CONFIRMED", null, resources, null);
        for (int i = 7; i <= 10; i++) unit(snapshot, i, "AUTO_BLOCKED", "PLACE_NOT_FOUND", null, null);
        // When
        var result = port.query(filter(null, null));
        // Then
        assertThat(result.totals().totalJobs()).isEqualTo(1);
        assertThat(result.totals().jobStatuses()).containsEntry("QUEUED", 1L).containsEntry("FAILED", 0L);
        assertThat(result.totals().totalUnits()).isEqualTo(10);
        assertThat(result.totals().evaluatedUnits()).isEqualTo(10);
        assertThat(result.totals().registeredUnits()).isEqualTo(6);
        assertThat(result.totals().blockedRate()).isEqualTo(0.4);
        assertThat(result.totals().blockReasons().get("PLACE_NOT_FOUND").blockedShare()).isEqualTo(1.0);
        assertThat(result.groups()).hasSize(1);
    }

    @Test
    @DisplayName("최신 Snapshot만 집계하고 수동 등록과 롤백 폐기를 구분한다")
    void 집계_다중Snapshot수동상태_최신단위와현재등록만반환한다() {
        // Given
        UUID job = job("ADMIN", "gemini-3.5-flash-lite", FROM);
        unit(snapshot(job, 1), 1, "AUTO_BLOCKED", "PLACE_NOT_FOUND", null, null);
        UUID latest = snapshot(job, 2);
        unit(latest, 1, "MANUAL_OVERRIDE", null, resources(), null);
        unit(latest, 2, "MANUAL_OVERRIDE", null, null, "rolled_back_at");
        unit(latest, 3, "MANUAL_OVERRIDE", null, null, "discarded_at");
        // When
        var counts = port.query(filter(null, null)).totals();
        // Then
        assertThat(counts.totalJobs()).isEqualTo(1);
        assertThat(counts.totalUnits()).isEqualTo(3);
        assertThat(counts.manualOverrideUnits()).isEqualTo(3);
        assertThat(counts.registeredUnits()).isEqualTo(1);
        assertThat(counts.evaluatedUnits()).isZero();
        assertThat(counts.blockedRate()).isNull();
        assertThat(counts.blockReasons()).isEmpty();
    }

    @Test
    @DisplayName("기간은 시작 포함 끝 제외이며 유입 경로 모델과 버전 그룹을 분리한다")
    void 집계_기간과차원필터_일치작업만반환한다() {
        // Given
        job("ADMIN", "gemini-3.5-flash-lite", FROM.minusNanos(1000));
        job("ADMIN", "gemini-3.5-flash-lite", TO);
        job("ADMIN", "gemini-3.5-flash-lite", FROM);
        UUID webhook = job("WEBHOOK", "gemini-3.5-flash-lite", FROM.plusHours(1));
        jdbc.update("UPDATE ai_extraction_job SET prompt_version = 'P9', schema_version = 'S3' WHERE id = ?", webhook);
        // When / Then
        assertThat(port.query(filter(null, null)).totals().totalJobs()).isEqualTo(2);
        assertThat(port.query(filter(null, null)).groups()).hasSize(2);
        assertThat(port.query(new AiExtractionStatisticsPort.Filter(FROM, TO, null, null, "P9", "S3"))
                .totals().totalJobs()).isEqualTo(1);
        assertThat(port.query(filter("WEBHOOK", "gemini-3.5-flash-lite")).totals().totalJobs()).isEqualTo(1);
        assertThat(port.query(new AiExtractionStatisticsPort.Filter(FROM, TO, null, null, "absent", null))
                .totals().totalJobs()).isZero();
        assertThat(port.query(new AiExtractionStatisticsPort.Filter(FROM, TO, null, null, null, "absent"))
                .totals().totalJobs()).isZero();
    }

    @Test
    @DisplayName("전체 확정 전체 차단과 빈 집계는 각각 0 1 null 비율을 반환한다")
    void 집계_확정차단빈분모_비율경계를반환한다() {
        // Given
        UUID a = snapshot(job("ADMIN", "gemini-3.5-flash-lite", FROM), 1);
        unit(a, 1, "AUTO_CONFIRMED", null, resources(), null);
        UUID b = snapshot(job("WEBHOOK", "gemini-3.5-flash-lite", FROM), 1);
        unit(b, 1, "AUTO_BLOCKED", "PLACE_NOT_FOUND", null, null);
        unit(b, 2, "AUTO_BLOCKED", "PLACE_AMBIGUOUS", null, null);
        // When / Then
        assertThat(port.query(filter("ADMIN", null)).totals().blockedRate()).isEqualTo(0.0);
        var blocked = port.query(filter("WEBHOOK", null)).totals();
        assertThat(blocked.blockedRate()).isEqualTo(1.0);
        assertThat(blocked.blockReasons().get("PLACE_AMBIGUOUS").blockedShare()).isEqualTo(0.5);
        var empty = port.query(filter(null, "absent"));
        assertThat(empty.totals().totalJobs()).isZero();
        assertThat(empty.totals().blockedRate()).isNull();
        assertThat(empty.groups()).isEmpty();
    }

    @Test
    @DisplayName("자동 거절은 판정 분모에 포함하고 차단 사유 분모에서는 제외한다")
    void 집계_자동거절_판정분모와차단사유를구분한다() {
        // Given
        UUID s = snapshot(job("ADMIN", "gemini-3.5-flash-lite", FROM), 1);
        unit(s, 1, "AUTO_BLOCKED", "PLACE_NOT_FOUND", null, null);
        unit(s, 2, "AUTO_REJECTED", "VISIT_EVIDENCE_REQUIRED", null, null);
        // When
        var counts = port.query(filter(null, null)).totals();
        // Then
        assertThat(counts.evaluatedUnits()).isEqualTo(2);
        assertThat(counts.rejectedUnits()).isEqualTo(1);
        assertThat(counts.blockedRate()).isEqualTo(0.5);
        assertThat(counts.blockReasons()).containsOnlyKeys("PLACE_NOT_FOUND");
        assertThat(counts.blockReasons().get("PLACE_NOT_FOUND").blockedShare()).isEqualTo(1.0);
    }
    private AiExtractionStatisticsPort.Filter filter(String source, String model) {
        return new AiExtractionStatisticsPort.Filter(FROM, TO, source, model, null, null);
    }

    private UUID job(String source, String model, OffsetDateTime time) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_extraction_job (id, source, priority, youtube_channel_id, youtube_video_id,
                video_url, input_mode, input_hash, provider, model_version, prompt_version, schema_version, created_at)
                VALUES (?, ?, 'REALTIME', ?, ?, 'https://www.youtube.com/watch?v=test', 'GEMINI_VIDEO_URL', ?,
                'GOOGLE_GEMINI', ?, 'P8', 'S2', ?)
                """, id, source, "c-" + id, "v-" + id, sha256(id.toString()), model, time);
        return id;
    }

    private UUID snapshot(UUID job, int version) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_candidate_snapshot (id, job_id, snapshot_version, candidate_fields, candidate_tags,
                field_confidences, evidence, missing_fields, review_status, reviewed_at)
                VALUES (?, ?, ?, '{}'::jsonb, '[]'::jsonb, '{}'::jsonb, '{}'::jsonb, '[]'::jsonb, 'AUTO_BLOCKED', now())
                """, id, job, version);
        return id;
    }

    private void unit(UUID snapshot, int index, String status, String reason, UUID[] ids, String lifecycle) {
        jdbc.update("""
                INSERT INTO ai_registration_unit (id, snapshot_id, unit_index, restaurant_name, review_status,
                block_reason, registered_restaurant_id, registered_creator_id, registered_video_id, registered_visit_id,
                place_decision, category_decision, executed_by, rolled_back_at, discarded_at)
                VALUES (?, ?, ?, '식당', ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, 'WORKER', ?, ?)
                """, UUID.randomUUID(), snapshot, index, status, reason,
                ids == null ? null : ids[0], ids == null ? null : ids[1], ids == null ? null : ids[2], ids == null ? null : ids[3],
                ids == null ? null : "{}", ids == null ? null : "{}",
                "rolled_back_at".equals(lifecycle) ? OffsetDateTime.now().plusSeconds(1) : null,
                "discarded_at".equals(lifecycle) ? OffsetDateTime.now().plusSeconds(1) : null);
    }

    private UUID[] resources() {
        UUID r = UUID.randomUUID(), c = UUID.randomUUID(), v = UUID.randomUUID(), visit = UUID.randomUUID();
        jdbc.update("INSERT INTO creator (id, external_channel_id, channel_name, channel_url, external_status_checked_at) "
                + "VALUES (?, ?, '채널', 'https://example.com/channel', now())", c, "c-" + c);
        jdbc.update("""
                INSERT INTO restaurant (id, region_id, food_category_id, name, kakao_place_id, kakao_place_url,
                road_address, phone_number) VALUES (?, '10000000-0000-4000-8000-000000000001',
                '20000000-0000-4000-8000-000000000001', '식당', ?, 'https://place.map.kakao.com/1', '서울', '02-1234-5678')
                """, r, "p-" + r.toString().substring(0, 18));
        jdbc.update("""
                INSERT INTO video (id, creator_id, external_video_id, publisher_external_channel_id, title,
                source_url, thumbnail_url, external_status_checked_at)
                VALUES (?, ?, ?, ?, '영상', 'https://example.com/video', 'https://example.com/thumb', now())
                """, v, c, "v-" + v.toString().substring(0, 18), "c-" + c);
        jdbc.update("INSERT INTO visit (id, restaurant_id, creator_id, video_id) VALUES (?, ?, ?, ?)", visit, r, c, v);
        return new UUID[]{r, c, v, visit};
    }
}

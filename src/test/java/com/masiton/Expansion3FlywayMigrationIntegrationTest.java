package com.masiton;

import java.util.List;
import java.util.UUID;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masiton.ai.application.AiExtractionContract;
import com.masiton.ai.application.port.out.AiExtractionJobStore;
import com.masiton.ai.infrastructure.persistence.JdbcAiExtractionJobStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DisplayName("3차 확장 AI Flyway 마이그레이션")
class Expansion3FlywayMigrationIntegrationTest {

    private static final UUID REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000014");
    private static final UUID CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID SEEDED_TAG_ID = UUID.fromString("30000000-0000-4000-8000-000000000001");
    private static final AtomicInteger SCHEMA_SEQUENCE = new AtomicInteger();

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17.10-alpine")
                    .withDatabaseName("masiton")
                    .withUsername("masiton")
                    .withPassword("masiton_local");

    @Test
    @DisplayName("V3 데이터와 스키마를 보존하면서 V4 AI 후보 스키마와 18개 통제 태그를 전진 적용한다")
    void V4적용_V3스키마존재_AI후보스키마와통제태그를전진적용한다() {
        // given
        SchemaDatabase database = createSchemaDatabase();
        migrate(database.dataSource(), database.schema(), MigrationVersion.fromVersion("3"));
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database.dataSource());
        UUID existingAdminId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO admin_account (id, login_id, password_hash) VALUES (?, ?, 'password-hash')",
                existingAdminId, "admin-" + existingAdminId);

        // when
        migrate(database.dataSource(), database.schema(), null);

        // then
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank",
                String.class);
        Integer aiTableCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = ? AND table_name IN "
                        + "('ai_extraction_job', 'ai_extraction_temporary_input', 'ai_candidate_snapshot', "
                        + "'ai_candidate_tag_review', 'tag_definition', 'visit_tag', 'ai_extraction_attempt', "
                        + "'youtube_channel_watch')",
                Integer.class,
                database.schema());
        Integer seededTagCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM tag_definition WHERE source = 'SEED' AND status = 'ACTIVE'", Integer.class);
        Integer indexCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = current_schema() AND indexname IN "
                        + "('ix_ai_job__claim', 'ix_ai_job__expired_lease_claim', 'ix_ai_job__review', "
                        + "'ix_ai_snapshot__review', 'ix_ai_tag_review__candidate', 'ix_visit_tag__tag_lookup', "
                        + "'ix_ai_job__video_input_versions', 'ix_ai_job__video_mode_versions', "
                        + "'ix_ai_temporary_input__expires_at')",
                Integer.class);

        assertThat(versions).containsExactly("1", "2", "3", "4", "5");
        assertThat(aiTableCount).isEqualTo(8);
        assertThat(seededTagCount).isEqualTo(18);
        assertThat(indexCount).isEqualTo(9);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM admin_account WHERE id = ?", Integer.class, existingAdminId)).isEqualTo(1);
    }

    @Test
    @DisplayName("작업 멱등성·상태 lease·관리자 임시 입력 제약을 강제한다")
    void 작업제약_멱등성과lease와임시입력_잘못된상태를거부한다() {
        // given
        JdbcTemplate jdbcTemplate = migratedJdbcTemplate();
        UUID adminJobId = insertJob(jdbcTemplate, "ADMIN", "ADMIN_TEXT", "video-a", new byte[32]);

        // when & then
        assertThatThrownBy(() -> insertJob(jdbcTemplate, "ADMIN", "ADMIN_TEXT", "video-a", new byte[32]))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE ai_extraction_job SET execution_status = 'RUNNING', started_at = now() WHERE id = ?",
                adminJobId))
                .isInstanceOf(DataAccessException.class);

        jdbcTemplate.update(
                "INSERT INTO ai_extraction_temporary_input (job_id, ciphertext, encryption_key_id, expires_at) "
                        + "VALUES (?, ?, 'key-2026-08', now() + interval '1 hour')",
                adminJobId, new byte[] {1, 2, 3});

        UUID completedJobId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO ai_extraction_job (id, source, priority, youtube_channel_id, youtube_video_id, video_url, "
                        + "input_mode, input_hash, provider, model_version, prompt_version, schema_version, "
                        + "execution_status, started_at, finished_at, result_completeness, created_at) "
                        + "VALUES (?, 'ADMIN', 'REALTIME', 'channel-completed', 'video-completed', "
                        + "'https://www.youtube.com/watch?v=video-completed', 'ADMIN_TEXT', ?, 'GOOGLE_GEMINI', "
                        + "'gemini-3-flash-preview', 'P1', 'S1', 'SUCCEEDED', now() - interval '2 hours', now(), 'COMPLETE', "
                        + "now() - interval '3 hours')",
                completedJobId, bytes(3));
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO ai_extraction_temporary_input (job_id, ciphertext, encryption_key_id, expires_at) "
                        + "VALUES (?, ?, 'key-2026-08', now() - interval '1 hour')",
                completedJobId, new byte[] {4, 5, 6}))
                .isInstanceOf(DataAccessException.class);

        UUID webhookJobId = insertJob(jdbcTemplate, "WEBHOOK", "GEMINI_VIDEO_URL", "video-b", bytes(1));
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO ai_extraction_temporary_input (job_id, ciphertext, encryption_key_id, expires_at) "
                        + "VALUES (?, ?, 'key-2026-08', now() + interval '1 hour')",
                webhookJobId, new byte[] {1, 2, 3}))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("후보 검수 이력과 확정 Visit 태그의 근거·중복 제약을 강제한다")
    void 후보와태그제약_이력수정과UNKNOWN자동근거와중복을거부한다() {
        // given
        JdbcTemplate jdbcTemplate = migratedJdbcTemplate();
        Fixture fixture = insertFixture(jdbcTemplate);
        UUID jobId = insertJob(jdbcTemplate, "ADMIN", "ADMIN_TEXT", "video-c", bytes(2));
        UUID snapshotId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO ai_candidate_snapshot (id, job_id, snapshot_version, candidate_fields, candidate_tags, "
                        + "field_confidences, evidence, missing_fields, review_status, reviewed_at) "
                        + "VALUES (?, ?, 1, '{}'::jsonb, '[]'::jsonb, '{}'::jsonb, '{}'::jsonb, '[]'::jsonb, "
                        + "'AUTO_BLOCKED', now())",
                snapshotId, jobId);
        UUID reviewId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO ai_candidate_tag_review (id, snapshot_id, candidate_tag_id, decision, "
                        + "replacement_tag_definition_id, decision_source) VALUES (?, ?, 'tag-1', 'AUTO_MERGE', ?, 'SYSTEM')",
                reviewId, snapshotId, SEEDED_TAG_ID);

        // when & then
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE ai_candidate_tag_review SET reason = '수정' WHERE id = ?", reviewId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO tag_definition (id, tag_code, tag_type, display_name, aliases, source) "
                        + "VALUES (?, 'MENU_DUPLICATE_ALIAS', 'MENU', '중복 별칭', "
                        + "'[\"동의어\", \"동의어\"]'::jsonb, 'MANUAL_OVERRIDE')",
                UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO visit_tag (id, visit_id, tag_definition_id, source, confidence, evidence, extractor_version) "
                        + "VALUES (?, ?, ?, 'AI_AUTO_CONFIRMED', 0.9000, '{\"type\":\"UNKNOWN\"}'::jsonb, 'P1/S1')",
                UUID.randomUUID(), fixture.visitId(), SEEDED_TAG_ID))
                .isInstanceOf(DataAccessException.class);

        jdbcTemplate.update(
                "INSERT INTO visit_tag (id, visit_id, tag_definition_id, source, confidence, evidence, extractor_version) "
                        + "VALUES (?, ?, ?, 'AI_AUTO_CONFIRMED', 0.9000, "
                        + "'{\"type\":\"TIMESTAMP\",\"startMs\":42000,\"endMs\":49000}'::jsonb, 'P1/S1')",
                UUID.randomUUID(), fixture.visitId(), SEEDED_TAG_ID);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO visit_tag (id, visit_id, tag_definition_id, source, evidence) "
                        + "VALUES (?, ?, ?, 'ADMIN_OVERRIDE', '{}'::jsonb)",
                UUID.randomUUID(), fixture.visitId(), SEEDED_TAG_ID))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("AI 작업 저장소는 PostgreSQL unique 계약으로 동일 입력을 한 건으로 수렴한다")
    void 작업저장소_동일입력_유일작업으로수렴한다() {
        JdbcTemplate jdbcTemplate = migratedJdbcTemplate();
        JdbcAiExtractionJobStore store = new JdbcAiExtractionJobStore(jdbcTemplate);
        byte[] inputHash = bytes(19);
        URI videoUrl = URI.create("https://www.youtube.com/watch?v=store-video");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AiExtractionJobStore.AiExtractionJobDraft first = new AiExtractionJobStore.AiExtractionJobDraft(
                UUID.randomUUID(), "WEBHOOK", "REALTIME", "store-channel", "store-video", videoUrl,
                "GEMINI_VIDEO_URL", inputHash, AiExtractionContract.PROVIDER, AiExtractionContract.MODEL_VERSION,
                AiExtractionContract.PROMPT_VERSION, AiExtractionContract.SCHEMA_VERSION, now);

        assertThat(store.insert(first)).isPresent();
        AiExtractionJobStore.AiExtractionJobDraft duplicate = new AiExtractionJobStore.AiExtractionJobDraft(
                UUID.randomUUID(), first.source(), first.priority(), first.channelId(), first.videoId(), videoUrl,
                first.inputMode(), inputHash, first.provider(), first.modelVersion(), first.promptVersion(),
                first.schemaVersion(), now.plusSeconds(1));

        assertThat(store.insert(duplicate)).isEmpty();
        assertThat(store.find(first.channelId(), first.videoId(), inputHash, first.provider(), first.modelVersion(),
                first.promptVersion(), first.schemaVersion())).isPresent()
                .get().extracting("jobId").isEqualTo(first.jobId());
    }

    @Test
    @DisplayName("지연된 작업은 완료 시 임시 입력 보존 기간을 다시 계산하고 비종료 입력은 조기 삭제하지 않는다")
    void 임시입력_지연완료_보존기간재계산과비종료삭제보호() {
        JdbcTemplate jdbcTemplate = migratedJdbcTemplate();
        JdbcAiExtractionJobStore store = new JdbcAiExtractionJobStore(jdbcTemplate);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID queuedJobId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ai_extraction_job (id, source, priority, youtube_channel_id, youtube_video_id,
                    video_url, input_mode, input_hash, provider, model_version, prompt_version, schema_version,
                    created_at) VALUES (?, 'ADMIN', 'BACKFILL', 'late-channel', 'late-video',
                    'https://www.youtube.com/watch?v=late-video', 'ADMIN_TEXT', ?, 'GOOGLE_GEMINI',
                    'gemini-3-flash-preview', 'P1', 'S1', ?)
                """, queuedJobId, bytes(21), now.minusDays(2));
        jdbcTemplate.update("""
                INSERT INTO ai_extraction_temporary_input (job_id, ciphertext, encryption_key_id, expires_at, created_at)
                VALUES (?, ?, 'key-2026-08', ?, ?)
                """, queuedJobId, new byte[] {1, 2, 3}, now.minusHours(1), now.minusDays(2));

        assertThat(store.deleteExpiredTemporaryInputs(now)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ai_extraction_temporary_input WHERE job_id = ?", Integer.class, queuedJobId))
                .isEqualTo(1);

        jdbcTemplate.update("""
                UPDATE ai_extraction_job
                   SET execution_status = 'RUNNING', started_at = ?, lease_owner = 'worker-1',
                       lease_expires_at = ?
                 WHERE id = ?
                """, now.minusHours(2), now.plusHours(1), queuedJobId);
        jdbcTemplate.update("""
                UPDATE ai_extraction_job
                   SET execution_status = 'SUCCEEDED', finished_at = ?, result_completeness = 'COMPLETE',
                       lease_owner = NULL, lease_expires_at = NULL
                 WHERE id = ?
                """, now, queuedJobId);

        OffsetDateTime expiresAt = jdbcTemplate.queryForObject(
                "SELECT expires_at FROM ai_extraction_temporary_input WHERE job_id = ?", OffsetDateTime.class, queuedJobId);
        assertThat(expiresAt).isAfterOrEqualTo(now.plusHours(23)).isBeforeOrEqualTo(now.plusHours(24).plusMinutes(1));
        assertThat(store.deleteExpiredTemporaryInputs(now.plusHours(25))).isEqualTo(1);
    }

    private JdbcTemplate migratedJdbcTemplate() {
        SchemaDatabase database = createSchemaDatabase();
        migrate(database.dataSource(), database.schema(), null);
        return new JdbcTemplate(database.dataSource());
    }

    private SchemaDatabase createSchemaDatabase() {
        String schema = "expansion3_" + SCHEMA_SEQUENCE.incrementAndGet();
        JdbcTemplate adminJdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        adminJdbcTemplate.execute("CREATE SCHEMA " + schema);

        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        DataSource schemaDataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        return new SchemaDatabase(schema, schemaDataSource);
    }

    private void migrate(DataSource dataSource, String schema, MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema);
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private UUID insertJob(
            JdbcTemplate jdbcTemplate, String source, String inputMode, String videoId, byte[] inputHash) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO ai_extraction_job (id, source, priority, youtube_channel_id, youtube_video_id, video_url, "
                        + "input_mode, input_hash, provider, model_version, prompt_version, schema_version) "
                        + "VALUES (?, ?, 'REALTIME', 'channel-1', ?, ?, ?, ?, 'GOOGLE_GEMINI', "
                        + "'gemini-3-flash-preview', 'P1', 'S1')",
                id, source, videoId, "https://www.youtube.com/watch?v=" + videoId, inputMode, inputHash);
        return id;
    }

    private Fixture insertFixture(JdbcTemplate jdbcTemplate) {
        UUID creatorId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO creator (id, external_channel_id, channel_name, channel_url, external_status_checked_at) "
                        + "VALUES (?, ?, '테스트 채널', 'https://example.com/channel', now())",
                creatorId, "channel-" + creatorId);
        jdbcTemplate.update(
                "INSERT INTO restaurant (id, region_id, food_category_id, name, kakao_place_id, kakao_place_url, "
                        + "road_address, phone_number) VALUES (?, ?, ?, '테스트 맛집', ?, 'https://example.com/place', "
                        + "'서울특별시 마포구', '02-1234-5678')",
                restaurantId, REGION_ID, CATEGORY_ID, "place-" + restaurantId);
        jdbcTemplate.update(
                "INSERT INTO video (id, creator_id, external_video_id, publisher_external_channel_id, title, source_url, "
                        + "thumbnail_url, external_status_checked_at) VALUES (?, ?, ?, ?, '테스트 영상', "
                        + "'https://example.com/video', 'https://example.com/thumb', now())",
                videoId, creatorId, "video-" + videoId.toString().substring(0, 8), "channel-" + creatorId);
        jdbcTemplate.update(
                "INSERT INTO visit (id, restaurant_id, creator_id, video_id) VALUES (?, ?, ?, ?)",
                visitId, restaurantId, creatorId, videoId);
        return new Fixture(visitId);
    }

    private byte[] bytes(int lastByte) {
        byte[] value = new byte[32];
        value[31] = (byte) lastByte;
        return value;
    }

    private record SchemaDatabase(String schema, DataSource dataSource) {
    }

    private record Fixture(UUID visitId) {
    }
}

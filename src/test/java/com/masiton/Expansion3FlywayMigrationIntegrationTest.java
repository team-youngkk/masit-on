package com.masiton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import com.masiton.ai.infrastructure.persistence.JdbcAiExtractionAdminQueryAdapter;
import com.masiton.orchestration.infrastructure.rollback.JdbcAiRegisteredContentStore;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DisplayName("3차 확장 AI Flyway 마이그레이션")
class Expansion3FlywayMigrationIntegrationTest {

    private static final UUID REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000014");
    private static final UUID CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID SEEDED_TAG_ID = UUID.fromString("30000000-0000-4000-8000-000000000001");
    private static final AtomicInteger SCHEMA_SEQUENCE = new AtomicInteger();
    private static final List<String> EXPECTED_TAG_CODES = List.of(
            "MENU_NAENGMYEON", "MENU_GUKBAP", "MENU_RAMEN", "MENU_SUSHI", "MENU_PIZZA", "MENU_SAMGYEOPSAL",
            "TASTE_SPICY", "TASTE_SWEET", "TASTE_SAVORY", "TASTE_LIGHT", "OCCASION_SOLO", "OCCASION_DATE",
            "OCCASION_GROUP", "OCCASION_LATE_NIGHT", "ATMOSPHERE_CASUAL", "ATMOSPHERE_QUIET",
            "ATMOSPHERE_LIVELY", "ATMOSPHERE_BAR");

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
        migrate(database.dataSource(), database.schema(), MigrationVersion.fromVersion("4"));

        // then
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank",
                String.class);
        assertThat(versions).containsExactly("1", "2", "3", "4");
        assertAiSchemaAndContracts(jdbcTemplate, database.schema());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname = current_schema() "
                + "AND indexname IN ('ix_ai_job__video_input_versions', 'ix_ai_job__video_mode_versions', "
                + "'ix_ai_temporary_input__expires_at')", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM admin_account WHERE id = ?", Integer.class, existingAdminId)).isEqualTo(1);
    }

    @Test
    @DisplayName("빈 데이터베이스를 최신 버전까지 마이그레이션하면 V4~V7 AI 관리 스키마가 모두 적용된다")
    void 빈데이터베이스_최신버전마이그레이션_V4부터V7관리스키마적용() {
        // given
        SchemaDatabase database = createSchemaDatabase();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database.dataSource());

        // when
        migrate(database.dataSource(), database.schema(), null);

        // then
        assertThat(jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank", String.class))
                .containsExactly("1", "2", "3", "4", "5", "6", "7");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname = current_schema() "
                + "AND indexname IN ('ix_ai_job__video_input_versions', 'ix_ai_job__video_mode_versions', "
                + "'ix_ai_temporary_input__expires_at', 'ix_visit_tag__created_from_snapshot')", Integer.class)).isEqualTo(4);
        assertAiSchemaAndContracts(jdbcTemplate, database.schema());
        assertManualReviewSchema(jdbcTemplate, database.schema());
    }

    @Test
    @DisplayName("AI 관리자 목록 조회는 상세 Snapshot JSON 컬럼 없이도 PostgreSQL에서 실행된다")
    void 관리자목록조회_상세Snapshot컬럼없이_PostgreSQL에서실행된다() {
        JdbcTemplate jdbcTemplate = migratedJdbcTemplate();
        insertJob(jdbcTemplate, "ADMIN", "ADMIN_TEXT", "list-video", bytes(31));

        JdbcAiExtractionAdminQueryAdapter adapter = new JdbcAiExtractionAdminQueryAdapter(jdbcTemplate, new ObjectMapper());

        assertThat(adapter.list(null, null, null, 0, 20).items()).hasSize(1);
    }

    @Test
    @DisplayName("AI 롤백은 대상 Snapshot의 VisitTag만 제거하고 기존 태그는 남긴다")
    void AI롤백_대상Snapshot태그만제거하고_기존태그는남긴다() {
        JdbcTemplate jdbcTemplate = migratedJdbcTemplate();
        Fixture fixture = insertFixture(jdbcTemplate);
        UUID jobId = insertJob(jdbcTemplate, "ADMIN", "ADMIN_TEXT", "rollback-video", bytes(32));
        UUID snapshotId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO ai_candidate_snapshot (id, job_id, snapshot_version, candidate_fields, candidate_tags, "
                        + "field_confidences, evidence, missing_fields, review_status, reviewed_at) "
                        + "VALUES (?, ?, 1, '{}'::jsonb, '[]'::jsonb, '{}'::jsonb, '{}'::jsonb, '[]'::jsonb, "
                        + "'AUTO_CONFIRMED', now())", snapshotId, jobId);
        jdbcTemplate.update(
                "INSERT INTO visit_tag (id, visit_id, tag_definition_id, source, confidence, evidence, extractor_version, created_from_snapshot_id) "
                        + "VALUES (?, ?, ?, 'AI_AUTO_CONFIRMED', 0.9000, '{\"type\":\"TIMESTAMP\",\"startMs\":1,\"endMs\":2}'::jsonb, 'P1/S1', ?)",
                UUID.randomUUID(), fixture.visitId(), SEEDED_TAG_ID, snapshotId);
        jdbcTemplate.update(
                "INSERT INTO visit_tag (id, visit_id, tag_definition_id, source, evidence) "
                        + "VALUES (?, ?, (SELECT id FROM tag_definition WHERE tag_code='MENU_GUKBAP'), 'ADMIN_OVERRIDE', '{}'::jsonb)",
                UUID.randomUUID(), fixture.visitId());

        new JdbcAiRegisteredContentStore(jdbcTemplate).makePrivateIfCreated(snapshotId,
                null, false, null, false, null, false, fixture.visitId(), true);

        assertThat(jdbcTemplate.queryForObject("SELECT publication_status FROM visit WHERE id=?", String.class, fixture.visitId()))
                .isEqualTo("PRIVATE");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM visit_tag WHERE visit_id=? AND created_from_snapshot_id=?",
                Integer.class, fixture.visitId(), snapshotId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM visit_tag WHERE visit_id=? AND created_from_snapshot_id IS NULL",
                Integer.class, fixture.visitId())).isEqualTo(1);
    }

    private void assertManualReviewSchema(JdbcTemplate jdbcTemplate, String schema) {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema=? AND table_name='ai_extraction_manual_review'", Integer.class, schema)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema=? AND table_name='ai_candidate_snapshot' "
                + "AND column_name LIKE 'registered_%' OR table_schema=? AND table_name='ai_candidate_snapshot' "
                + "AND column_name LIKE '%_created' ORDER BY column_name", String.class, schema, schema))
                .containsExactlyInAnyOrder("registered_creator_id", "registered_restaurant_id", "registered_video_id",
                        "registered_visit_id", "creator_created", "restaurant_created", "video_created", "visit_created");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM information_schema.columns "
                + "WHERE table_schema=? AND table_name='ai_candidate_tag_review' AND column_name='manual_tag_code'",
                Integer.class, schema)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM pg_constraint "
                + "WHERE connamespace=current_schema()::regnamespace AND conname='ck_ai_snapshot__registration_flags'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM pg_constraint "
                + "WHERE connamespace=current_schema()::regnamespace AND conname='ck_ai_candidate_tag_review__manual_tag_code'",
                Integer.class)).isEqualTo(1);
    }

    private void assertAiSchemaAndContracts(JdbcTemplate jdbcTemplate, String schema) {
        boolean includesV6 = jdbcTemplate.queryForObject("SELECT count(*) FROM information_schema.columns "
                + "WHERE table_schema=? AND table_name='ai_candidate_snapshot' "
                + "AND column_name='registered_restaurant_id'", Integer.class, schema) == 1;
        boolean includesV7 = jdbcTemplate.queryForObject("SELECT count(*) FROM information_schema.columns "
                + "WHERE table_schema=? AND table_name='ai_extraction_job' AND column_name='retry_reason'",
                Integer.class, schema) == 1;
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema = ? "
                + "AND table_name IN ('ai_extraction_job','ai_extraction_temporary_input','ai_candidate_snapshot',"
                + "'ai_candidate_tag_review','tag_definition','visit_tag','ai_extraction_attempt','youtube_channel_watch')",
                Integer.class, schema)).isEqualTo(8);
        assertThat(jdbcTemplate.queryForList("SELECT tag_code FROM tag_definition WHERE source='SEED' AND status='ACTIVE' "
                + "ORDER BY tag_code", String.class)).containsExactlyInAnyOrderElementsOf(EXPECTED_TAG_CODES);
        assertThat(jdbcTemplate.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname=current_schema() "
                + "AND indexname IN ('ix_ai_job__claim','ix_ai_job__expired_lease_claim','ix_ai_job__review',"
                + "'ix_ai_snapshot__review','ix_ai_tag_review__candidate','ix_visit_tag__tag_lookup')",
                String.class)).containsExactlyInAnyOrder(
                        "ix_ai_job__claim", "ix_ai_job__expired_lease_claim", "ix_ai_job__review",
                        "ix_ai_snapshot__review", "ix_ai_tag_review__candidate", "ix_visit_tag__tag_lookup");
        String aiTables = "('ai_extraction_job'::regclass,'ai_extraction_temporary_input'::regclass,'ai_candidate_snapshot'::regclass,"
                + "'ai_candidate_tag_review'::regclass,'tag_definition'::regclass,'visit_tag'::regclass,'ai_extraction_attempt'::regclass,'youtube_channel_watch'::regclass)";
        assertThat(jdbcTemplate.queryForList("SELECT conname FROM pg_constraint WHERE connamespace=current_schema()::regnamespace "
                + "AND contype='p' AND conrelid IN " + aiTables + " ORDER BY conname", String.class)).containsExactlyInAnyOrder(
                "pk_ai_extraction_job", "pk_ai_extraction_temporary_input", "pk_ai_candidate_snapshot",
                "pk_ai_candidate_tag_review", "pk_tag_definition", "pk_visit_tag", "pk_ai_extraction_attempt",
                "pk_youtube_channel_watch");
        assertThat(jdbcTemplate.queryForList("SELECT conname FROM pg_constraint WHERE connamespace=current_schema()::regnamespace "
                + "AND contype='u' AND conrelid IN " + aiTables + " ORDER BY conname", String.class)).containsExactlyInAnyOrder(
                "ux_ai_job__idempotency", "ux_ai_snapshot__job_version", "ux_tag_definition__code",
                "ux_visit_tag__visit_tag", "ux_ai_attempt__job_no", "ux_channel_watch__creator",
                "ux_channel_watch__youtube_channel");
        Map<String, String> foreignKeys = jdbcTemplate.query(
                "SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint "
                        + "WHERE connamespace=current_schema()::regnamespace AND contype='f' AND conrelid IN "
                        + aiTables,
                rs -> {
                    Map<String, String> values = new HashMap<>();
                    while (rs.next()) {
                        values.put(rs.getString(1), rs.getString(2));
                    }
                    return values;
                });
        Map<String, List<String>> expectedForeignKeys = new HashMap<>(Map.ofEntries(
                Map.entry("fk_ai_extraction_temporary_input__job",
                        List.of("FOREIGN KEY (job_id)", "REFERENCES ai_extraction_job(id)",
                                "ON UPDATE RESTRICT", "ON DELETE CASCADE")),
                Map.entry("fk_ai_candidate_snapshot__job",
                        List.of("FOREIGN KEY (job_id)", "REFERENCES ai_extraction_job(id)",
                                "ON UPDATE RESTRICT", "ON DELETE CASCADE")),
                Map.entry("fk_ai_candidate_snapshot__reviewed_by",
                        List.of("FOREIGN KEY (reviewed_by)", "REFERENCES admin_account(id)",
                                "ON UPDATE RESTRICT", "ON DELETE RESTRICT")),
                Map.entry("fk_tag_definition__created_from_snapshot",
                        List.of("FOREIGN KEY (created_from_snapshot_id)", "REFERENCES ai_candidate_snapshot(id)",
                                "ON UPDATE RESTRICT", "ON DELETE RESTRICT")),
                Map.entry("fk_ai_candidate_tag_review__snapshot",
                        List.of("FOREIGN KEY (snapshot_id)", "REFERENCES ai_candidate_snapshot(id)",
                                "ON UPDATE RESTRICT", "ON DELETE CASCADE")),
                Map.entry("fk_ai_candidate_tag_review__replacement_tag_definition",
                        List.of("FOREIGN KEY (replacement_tag_definition_id)", "REFERENCES tag_definition(id)",
                                "ON UPDATE RESTRICT", "ON DELETE RESTRICT")),
                Map.entry("fk_ai_candidate_tag_review__reviewed_by",
                        List.of("FOREIGN KEY (reviewed_by)", "REFERENCES admin_account(id)",
                                "ON UPDATE RESTRICT", "ON DELETE RESTRICT")),
                Map.entry("fk_ai_snapshot__registered_restaurant",
                        List.of("FOREIGN KEY (registered_restaurant_id)", "REFERENCES restaurant(id)",
                                "ON UPDATE RESTRICT", "ON DELETE RESTRICT")),
                Map.entry("fk_ai_snapshot__registered_creator",
                        List.of("FOREIGN KEY (registered_creator_id)", "REFERENCES creator(id)",
                                "ON UPDATE RESTRICT", "ON DELETE RESTRICT")),
                Map.entry("fk_ai_snapshot__registered_video",
                        List.of("FOREIGN KEY (registered_video_id)", "REFERENCES video(id)",
                                "ON UPDATE RESTRICT", "ON DELETE RESTRICT")),
                Map.entry("fk_ai_snapshot__registered_visit",
                        List.of("FOREIGN KEY (registered_visit_id)", "REFERENCES visit(id)",
                                "ON UPDATE RESTRICT", "ON DELETE RESTRICT")),
                Map.entry("fk_visit_tag__visit",
                        List.of("FOREIGN KEY (visit_id)", "REFERENCES visit(id)",
                                "ON UPDATE RESTRICT", "ON DELETE RESTRICT")),
                Map.entry("fk_visit_tag__tag_definition",
                        List.of("FOREIGN KEY (tag_definition_id)", "REFERENCES tag_definition(id)",
                                "ON UPDATE RESTRICT", "ON DELETE RESTRICT")),
                Map.entry("fk_visit_tag__created_from_snapshot",
                        List.of("FOREIGN KEY (created_from_snapshot_id)", "REFERENCES ai_candidate_snapshot(id)",
                                "ON UPDATE RESTRICT", "ON DELETE RESTRICT")),
                Map.entry("fk_ai_extraction_attempt__job",
                        List.of("FOREIGN KEY (job_id)", "REFERENCES ai_extraction_job(id)",
                                "ON UPDATE RESTRICT", "ON DELETE CASCADE")),
                Map.entry("fk_youtube_channel_watch__creator",
                        List.of("FOREIGN KEY (creator_id)", "REFERENCES creator(id)",
                                "ON UPDATE RESTRICT", "ON DELETE RESTRICT"))));
        if (!includesV7) {
            expectedForeignKeys.remove("fk_visit_tag__created_from_snapshot");
        }
        if (!includesV6) {
            expectedForeignKeys.remove("fk_ai_snapshot__registered_restaurant");
            expectedForeignKeys.remove("fk_ai_snapshot__registered_creator");
            expectedForeignKeys.remove("fk_ai_snapshot__registered_video");
            expectedForeignKeys.remove("fk_ai_snapshot__registered_visit");
        }
        assertThat(foreignKeys).containsOnlyKeys(expectedForeignKeys.keySet());
        expectedForeignKeys.forEach((name, fragments) -> assertThat(foreignKeys.get(name))
                .as(name)
                .contains(fragments.toArray(String[]::new)));

        Map<String, List<String>> expectedChecks = new HashMap<>(Map.of(
                "ai_extraction_job", List.of(
                        "ck_ai_extraction_job__source", "ck_ai_extraction_job__priority",
                        "ck_ai_extraction_job__youtube_channel_id_not_blank",
                        "ck_ai_extraction_job__youtube_video_id_not_blank", "ck_ai_extraction_job__video_url",
                        "ck_ai_extraction_job__input_mode", "ck_ai_extraction_job__source_input_mode",
                        "ck_ai_extraction_job__input_hash_length", "ck_ai_extraction_job__provider",
                        "ck_ai_extraction_job__model_version", "ck_ai_extraction_job__prompt_version_not_blank",
                        "ck_ai_extraction_job__schema_version_not_blank", "ck_ai_extraction_job__execution_status",
                        "ck_ai_extraction_job__result_completeness", "ck_ai_extraction_job__attempt_count",
                        "ck_ai_extraction_job__lease_pair", "ck_ai_extraction_job__state_timestamps",
                        "ck_ai_extraction_job__started_after_created", "ck_ai_extraction_job__retry_reason"),
                "ai_extraction_temporary_input", List.of(
                        "ck_ai_extraction_temporary_input__ciphertext_not_empty",
                        "ck_ai_extraction_temporary_input__key_id_not_blank",
                        "ck_ai_extraction_temporary_input__expires_after_created"),
                "ai_candidate_snapshot", List.of(
                        "ck_ai_candidate_snapshot__version", "ck_ai_candidate_snapshot__candidate_fields_object",
                        "ck_ai_candidate_snapshot__candidate_tags_array",
                        "ck_ai_candidate_snapshot__field_confidences_object",
                        "ck_ai_candidate_snapshot__evidence_object", "ck_ai_candidate_snapshot__missing_fields_array",
                        "ck_ai_candidate_snapshot__review_status", "ck_ai_candidate_snapshot__review_state",
                        "ck_ai_candidate_snapshot__reviewed_after_created"),
                "ai_candidate_tag_review", List.of(
                         "ck_ai_candidate_tag_review__candidate_tag_id_not_blank",
                         "ck_ai_candidate_tag_review__decision", "ck_ai_candidate_tag_review__decision_source",
                         "ck_ai_candidate_tag_review__decision_pair", "ck_ai_candidate_tag_review__decision_actor",
                         "ck_ai_candidate_tag_review__manual_tag_code"),
                "tag_definition", List.of(
                        "ck_tag_definition__code_not_blank", "ck_tag_definition__type",
                        "ck_tag_definition__display_name_not_blank", "ck_tag_definition__aliases_array",
                        "ck_tag_definition__aliases_text_unique", "ck_tag_definition__status",
                        "ck_tag_definition__source", "ck_tag_definition__snapshot_source",
                        "ck_tag_definition__updated_after_created"),
                "visit_tag", List.of(
                        "ck_visit_tag__source", "ck_visit_tag__confidence", "ck_visit_tag__evidence_object",
                        "ck_visit_tag__ai_evidence"),
                "ai_extraction_attempt", List.of(
                        "ck_ai_extraction_attempt__number", "ck_ai_extraction_attempt__provider_request_id_not_blank",
                        "ck_ai_extraction_attempt__timestamps", "ck_ai_extraction_attempt__outcome",
                        "ck_ai_extraction_attempt__error", "ck_ai_extraction_attempt__input_tokens",
                        "ck_ai_extraction_attempt__output_tokens", "ck_ai_extraction_attempt__estimated_cost"),
                "youtube_channel_watch", List.of(
                        "ck_youtube_channel_watch__channel_id_not_blank",
                        "ck_youtube_channel_watch__subscription_status",
                        "ck_youtube_channel_watch__token_hash_not_empty",
                        "ck_youtube_channel_watch__last_error_not_blank",
                        "ck_youtube_channel_watch__updated_after_created")));
        if (!includesV7) {
            expectedChecks.put("ai_extraction_job", expectedChecks.get("ai_extraction_job").subList(0,
                    expectedChecks.get("ai_extraction_job").size() - 1));
        }
        if (!includesV6) {
            expectedChecks.put("ai_candidate_tag_review", expectedChecks.get("ai_candidate_tag_review").subList(0,
                    expectedChecks.get("ai_candidate_tag_review").size() - 1));
        }
        expectedChecks.forEach((table, names) -> assertThat(jdbcTemplate.queryForList(
                "SELECT conname FROM pg_constraint WHERE connamespace=current_schema()::regnamespace "
                        + "AND contype='c' AND conrelid=?::regclass",
                String.class, table)).as(table).containsExactlyInAnyOrderElementsOf(names));

        Map<String, List<String>> expectedColumns = new HashMap<>(Map.of(
                "ai_extraction_job", List.of(
                        "id", "source", "priority", "youtube_channel_id", "youtube_video_id", "video_url",
                        "input_mode", "input_hash", "provider", "model_version", "prompt_version", "schema_version",
                        "execution_status", "result_completeness", "attempt_count", "lease_owner",
                        "lease_expires_at", "error_category", "created_at", "started_at", "finished_at", "retry_reason"),
                "ai_extraction_temporary_input", List.of(
                        "job_id", "ciphertext", "encryption_key_id", "expires_at", "created_at"),
                "ai_candidate_snapshot", List.of(
                        "id", "job_id", "snapshot_version", "candidate_fields", "candidate_tags",
                        "field_confidences", "evidence", "missing_fields", "review_status", "reviewed_by",
                        "review_reason", "reviewed_at", "created_at"),
                "ai_candidate_tag_review", List.of(
                        "id", "snapshot_id", "candidate_tag_id", "decision", "replacement_tag_definition_id",
                        "reason", "decision_source", "reviewed_by", "reviewed_at"),
                "tag_definition", List.of(
                        "id", "tag_code", "tag_type", "display_name", "aliases", "status", "source",
                        "created_from_snapshot_id", "created_at", "updated_at"),
                "visit_tag", List.of(
                        "id", "visit_id", "tag_definition_id", "source", "confidence", "evidence",
                        "extractor_version", "created_at", "created_from_snapshot_id"),
                "ai_extraction_attempt", List.of(
                        "id", "job_id", "attempt_no", "provider_request_id", "started_at", "finished_at",
                        "outcome", "error_category", "input_tokens", "output_tokens", "estimated_cost_minor"),
                "youtube_channel_watch", List.of(
                        "id", "creator_id", "youtube_channel_id", "enabled", "subscription_status",
                        "subscription_token_hash", "last_notification_at", "last_renewed_at", "last_error_category",
                        "created_at", "updated_at")));
        if (!includesV7) {
            expectedColumns.put("ai_extraction_job", expectedColumns.get("ai_extraction_job").subList(0,
                    expectedColumns.get("ai_extraction_job").size() - 1));
            expectedColumns.put("visit_tag", expectedColumns.get("visit_tag").subList(0,
                    expectedColumns.get("visit_tag").size() - 1));
        }
        expectedColumns.forEach((table, columns) -> assertThat(jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema=? AND table_name=? "
                        + "ORDER BY ordinal_position",
                String.class, schema, table)).as(table).containsExactlyElementsOf(columns));
        assertThat(jdbcTemplate.queryForObject("SELECT data_type FROM information_schema.columns WHERE table_schema=? "
                + "AND table_name='ai_extraction_temporary_input' AND column_name='ciphertext'", String.class, schema))
                .isEqualTo("bytea");
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

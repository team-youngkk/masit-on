package com.masiton.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@DisplayName("태그 정의 용어 마이그레이션")
class TagDefinitionMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.10-alpine");

    @Test
    @DisplayName("V9의 기존 정의를 보존하고 V10에서 표시명과 별칭 용어를 역적재한다")
    void 마이그레이션_V9에서V10_정의보존과용어역적재() {
        String schema = "tag_term_success";
        Flyway v9 = flyway(schema, "9");
        v9.migrate();
        JdbcTemplate jdbc = jdbc(schema);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tag_definition(id, tag_code, tag_type, display_name, aliases, status, source)
                VALUES (?, 'OCCASION_FAMILY', 'OCCASION', 'ＡＢＣ 가족', '["가족　외식"]'::jsonb,
                        'DEPRECATED', 'MANUAL_OVERRIDE')
                """, id);
        UUID jobId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID aiTagId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_extraction_job(
                    id, source, priority, youtube_channel_id, youtube_video_id, video_url, input_mode, input_hash,
                    provider, model_version, prompt_version, schema_version, execution_status, attempt_count)
                VALUES (?, 'ADMIN', 'REALTIME', 'channel', ?, ?, 'ADMIN_TEXT', decode(repeat('00', 32), 'hex'),
                        'GOOGLE_GEMINI', 'gemini-3.5-flash-lite', 'P1', 'S1', 'QUEUED', 0)
                """, jobId, "video-" + jobId, "https://www.youtube.com/watch?v=" + jobId);
        jdbc.update("""
                INSERT INTO ai_candidate_snapshot(
                    id, job_id, snapshot_version, candidate_fields, candidate_tags, field_confidences, evidence,
                    missing_fields, candidate_truncated, review_status, reviewed_at)
                VALUES (?, ?, 1, '{}'::jsonb, '[]'::jsonb, '{}'::jsonb, '{}'::jsonb, '[]'::jsonb,
                        false, 'AUTO_CONFIRMED', now())
                """, snapshotId, jobId);
        jdbc.update("""
                INSERT INTO tag_definition(
                    id, tag_code, tag_type, display_name, aliases, status, source, created_from_snapshot_id)
                VALUES (?, 'MENU__KIMBAP_', 'MENU', '김밥', '["김밥"]'::jsonb, 'ACTIVE', 'AI_AUTO', ?)
                """, aiTagId, snapshotId);

        int migrated = flyway(schema, "10").migrate().migrationsExecuted;

        assertThat(migrated).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT normalized_term FROM tag_definition_term WHERE tag_definition_id = ? "
                + "ORDER BY term_kind DESC", String.class, id)).containsExactly("abc 가족", "가족 외식");
        assertThat(jdbc.queryForObject("SELECT status FROM tag_definition WHERE id = ?", String.class, id))
                .isEqualTo("DEPRECATED");
        assertThat(jdbc.queryForObject("SELECT aliases::text FROM tag_definition WHERE id = ?", String.class, aiTagId))
                .isEqualTo("[]");
        assertThat(jdbc.queryForObject("SELECT tag_code FROM tag_definition WHERE id = ?", String.class, aiTagId))
                .isEqualTo("MENU_KIMBAP");
        assertThat(jdbc.queryForList("SELECT normalized_term FROM tag_definition_term WHERE tag_definition_id = ?",
                String.class, aiTagId)).containsExactly("김밥");
    }

    @Test
    @DisplayName("V9에 정규화 용어 충돌이 있으면 V10 전체를 실패시키고 자동 병합하지 않는다")
    void 마이그레이션_V9용어충돌_V10전체실패() {
        String schema = "tag_term_collision";
        flyway(schema, "9").migrate();
        JdbcTemplate jdbc = jdbc(schema);
        jdbc.update("""
                INSERT INTO tag_definition(id, tag_code, tag_type, display_name, aliases, status, source)
                VALUES (?, 'MENU_COLD_NOODLES', 'MENU', ' 냉면 ', '[]'::jsonb, 'ACTIVE', 'MANUAL_OVERRIDE')
                """, UUID.randomUUID());

        assertThatThrownBy(() -> flyway(schema, "10").migrate()).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version = '10' AND success",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tag_definition", Integer.class)).isEqualTo(19);
    }

    @Test
    @DisplayName("V10에 seed 별칭과 충돌하는 용어가 있으면 V11 전체를 롤백한다")
    void 마이그레이션_V10seed별칭충돌_V11전체롤백() {
        String schema = "tag_alias_collision";
        flyway(schema, "10").migrate();
        JdbcTemplate jdbc = jdbc(schema);
        UUID definitionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tag_definition(id, tag_code, tag_type, display_name, aliases, status, source)
                VALUES (?, 'MENU_COLD_NOODLES', 'MENU', '찬 국수', '["물냉면"]'::jsonb,
                        'ACTIVE', 'MANUAL_OVERRIDE')
                """, definitionId);
        jdbc.update("""
                INSERT INTO tag_definition_term(id, tag_definition_id, term_kind, normalized_term)
                VALUES (?, ?, 'DISPLAY_NAME', '찬 국수'), (?, ?, 'ALIAS', '물냉면')
                """, UUID.randomUUID(), definitionId, UUID.randomUUID(), definitionId);

        assertThatThrownBy(() -> flyway(schema, "11").migrate()).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '11' AND success", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT aliases::text FROM tag_definition WHERE tag_code = 'MENU_NAENGMYEON'", String.class))
                .isEqualTo("[]");
        assertThat(jdbc.queryForList(
                "SELECT normalized_term FROM tag_definition_term WHERE tag_definition_id = "
                        + "(SELECT id FROM tag_definition WHERE tag_code = 'MENU_NAENGMYEON')",
                String.class)).containsExactly("냉면");
    }

    @Test
    @DisplayName("V11 정의와 정규화 용어를 보존하며 V12 버전과 감사 구조를 전진 추가한다")
    void 마이그레이션_V11에서V12_정의와용어를보존한다() {
        String schema = "tag_lifecycle_success";
        flyway(schema, "11").migrate();
        JdbcTemplate jdbc = jdbc(schema);
        UUID definitionId = jdbc.queryForObject(
                "SELECT id FROM tag_definition WHERE tag_code = 'MENU_NAENGMYEON'", UUID.class);
        var terms = jdbc.queryForList(
                "SELECT term_kind || ':' || normalized_term FROM tag_definition_term WHERE tag_definition_id = ? ORDER BY term_kind, normalized_term",
                String.class, definitionId);

        int migrated = flyway(schema, "12").migrate().migrationsExecuted;

        assertThat(migrated).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT id FROM tag_definition WHERE tag_code = 'MENU_NAENGMYEON'", UUID.class))
                .isEqualTo(definitionId);
        assertThat(jdbc.queryForList(
                "SELECT term_kind || ':' || normalized_term FROM tag_definition_term WHERE tag_definition_id = ? ORDER BY term_kind, normalized_term",
                String.class, definitionId)).containsExactlyElementsOf(terms);
        assertThat(jdbc.queryForObject("SELECT version FROM tag_definition WHERE id = ?", Long.class, definitionId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tag_definition_audit", Integer.class)).isZero();
    }

    private Flyway flyway(String schema, String target) {
        return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).createSchemas(true).target(target).load();
    }

    private JdbcTemplate jdbc(String schema) {
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(), POSTGRES.getPassword());
        return new JdbcTemplate(dataSource);
    }
}

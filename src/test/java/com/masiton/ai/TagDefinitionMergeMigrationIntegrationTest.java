package com.masiton.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@DisplayName("태그 정의 병합 감사 마이그레이션")
class TagDefinitionMergeMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.10-alpine");

    @Test
    @DisplayName("V12 데이터를 보존하고 V13 병합과 VisitTag provenance를 append-only로 추가한다")
    void 마이그레이션_V12에서V13_기존데이터보존과감사불변성을강제한다() {
        String schema = "tag_merge_success";
        flyway(schema, "12").migrate();
        JdbcTemplate jdbc = jdbc(schema);
        UUID sourceId = definitionId(jdbc, "MENU_GUKBAP");
        UUID targetId = definitionId(jdbc, "MENU_NAENGMYEON");
        int definitionCount = jdbc.queryForObject("SELECT count(*) FROM tag_definition", Integer.class);

        assertThat(flyway(schema, "13").migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tag_definition", Integer.class))
                .isEqualTo(definitionCount);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='13' AND success",
                Integer.class)).isOne();

        UUID mergeId = UUID.randomUUID();
        insertMerge(jdbc, mergeId, sourceId, targetId, 1, 1, 0);
        UUID visitId = insertVisit(jdbc);
        UUID visitTagId = UUID.randomUUID();
        jdbc.update("UPDATE tag_definition SET status='DEPRECATED' WHERE id=?", sourceId);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO visit_tag(id, visit_id, tag_definition_id, source, evidence)
                VALUES (?, ?, ?, 'ADMIN_OVERRIDE', '{"type":"UNKNOWN"}'::jsonb)
                """, UUID.randomUUID(), visitId, sourceId)).isInstanceOf(DataAccessException.class);
        jdbc.update("""
                INSERT INTO visit_tag_merge_provenance(
                    id, tag_definition_merge_id, visit_id, visit_tag_id, snapshot_role, outcome,
                    visit_tag_snapshot)
                VALUES (?, ?, ?, ?, 'SOURCE', 'MOVED', '{"source":"ADMIN_OVERRIDE"}'::jsonb)
                """, UUID.randomUUID(), mergeId, visitId, visitTagId);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE tag_definition_merge SET reason='변조' WHERE id=?", mergeId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM visit_tag_merge_provenance WHERE tag_definition_merge_id=?", mergeId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("병합 원본은 한 번만 기록하고 자기 병합과 영향 건수 불일치를 거부한다")
    void 병합감사_원본유일성과자기병합과건수제약을강제한다() {
        String schema = "tag_merge_constraints";
        flyway(schema, "13").migrate();
        JdbcTemplate jdbc = jdbc(schema);
        UUID sourceId = definitionId(jdbc, "MENU_GUKBAP");
        UUID targetId = definitionId(jdbc, "MENU_NAENGMYEON");
        insertMerge(jdbc, UUID.randomUUID(), sourceId, targetId, 2, 1, 1);

        assertThatThrownBy(() -> insertMerge(jdbc, UUID.randomUUID(), sourceId,
                definitionId(jdbc, "MENU_RAMEN"), 0, 0, 0))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertMerge(jdbc, UUID.randomUUID(), targetId, targetId, 0, 0, 0))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertMerge(jdbc, UUID.randomUUID(), targetId, sourceId, 2, 1, 0))
                .isInstanceOf(DataAccessException.class);
    }

    private void insertMerge(JdbcTemplate jdbc, UUID id, UUID sourceId, UUID targetId,
            long affected, long moved, long deduplicated) {
        jdbc.update("""
                INSERT INTO tag_definition_merge(
                    id, source_tag_definition_id, target_tag_definition_id,
                    source_before_snapshot, source_after_snapshot, target_snapshot,
                    source_version, target_version, preview_fingerprint, reason,
                    affected_visit_count, moved_visit_tag_count, deduplicated_visit_tag_count)
                VALUES (?, ?, ?, '{"code":"SOURCE","status":"ACTIVE"}'::jsonb,
                        '{"code":"SOURCE","status":"DEPRECATED"}'::jsonb,
                        '{"code":"TARGET"}'::jsonb,
                        0, 0, repeat('a', 64), '중복 태그 정리', ?, ?, ?)
                """, id, sourceId, targetId, affected, moved, deduplicated);
    }

    private UUID definitionId(JdbcTemplate jdbc, String code) {
        return jdbc.queryForObject("SELECT id FROM tag_definition WHERE tag_code=?", UUID.class, code);
    }

    private UUID insertVisit(JdbcTemplate jdbc) {
        UUID creatorId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        UUID regionId = jdbc.queryForObject("SELECT id FROM region ORDER BY sort_order LIMIT 1", UUID.class);
        UUID categoryId = jdbc.queryForObject("SELECT id FROM food_category ORDER BY sort_order LIMIT 1", UUID.class);
        String suffix = visitId.toString().substring(0, 12);
        jdbc.update("INSERT INTO creator(id, external_channel_id, channel_name, channel_url, "
                        + "external_status_checked_at) VALUES (?, ?, '채널', 'https://example.com/channel', now())",
                creatorId, "channel-" + suffix);
        jdbc.update("INSERT INTO restaurant(id, region_id, food_category_id, name, kakao_place_id, kakao_place_url, "
                        + "road_address, phone_number) VALUES (?, ?, ?, '맛집', ?, 'https://example.com/place', "
                        + "'서울특별시', '02-0000-0000')",
                restaurantId, regionId, categoryId, "place-" + suffix);
        jdbc.update("INSERT INTO video(id, creator_id, external_video_id, publisher_external_channel_id, title, "
                        + "source_url, thumbnail_url, external_status_checked_at) VALUES (?, ?, ?, ?, '영상', "
                        + "'https://example.com/video', 'https://example.com/thumb', now())",
                videoId, creatorId, "video-" + suffix, "channel-" + suffix);
        jdbc.update("INSERT INTO visit(id, restaurant_id, creator_id, video_id) VALUES (?, ?, ?, ?)",
                visitId, restaurantId, creatorId, videoId);
        return visitId;
    }

    private Flyway flyway(String schema, String target) {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .target(target)
                .load();
    }

    private JdbcTemplate jdbc(String schema) {
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        return new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(), POSTGRES.getPassword()));
    }
}

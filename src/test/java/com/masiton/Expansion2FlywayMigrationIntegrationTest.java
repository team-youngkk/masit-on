package com.masiton;

import java.util.List;
import java.util.UUID;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DisplayName("2차 확장 Flyway 마이그레이션")
class Expansion2FlywayMigrationIntegrationTest {

    private static final UUID REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000014");
    private static final UUID CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final AtomicInteger SCHEMA_SEQUENCE = new AtomicInteger();

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17.10-alpine")
                    .withDatabaseName("masiton")
                    .withUsername("masiton")
                    .withPassword("masiton_local");

    @Test
    @DisplayName("V2 스키마와 기존 행을 보존하면서 V3를 전진 적용한다")
    void V3적용_V2스키마와기존행존재_기존행을보존하고전진적용한다() {
        // given
        SchemaDatabase database = createSchemaDatabase();
        migrate(database.dataSource(), database.schema(), MigrationVersion.fromVersion("2"));
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database.dataSource());
        Fixture fixture = insertFixture(jdbcTemplate);
        jdbcTemplate.update(
                "INSERT INTO favorite (member_id, restaurant_id) VALUES (?, ?)",
                fixture.memberId(), fixture.restaurantId());

        // when
        migrate(database.dataSource(), database.schema(), null);

        // then
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank",
                String.class);
        Integer favoriteCount = jdbcTemplate.queryForObject("SELECT count(*) FROM favorite", Integer.class);
        Integer expansionTableCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = ? AND table_name IN "
                        + "('personal_collection', 'collection_restaurant', 'curation', 'curation_restaurant', "
                        + "'submission', 'report', 'moderation_history', 'notification', 'idempotency_record')",
                Integer.class,
                database.schema());

        assertThat(versions).containsExactly("1", "2", "3");
        assertThat(favoriteCount).isEqualTo(1);
        assertThat(expansionTableCount).isEqualTo(9);
    }

    @Test
    @DisplayName("빈 데이터베이스에 V1부터 V3까지 순서대로 적용한다")
    void 마이그레이션적용_빈데이터베이스_V1부터V3까지성공한다() {
        // given
        SchemaDatabase database = createSchemaDatabase();

        // when
        migrate(database.dataSource(), database.schema(), null);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database.dataSource());

        // then
        List<AppliedMigration> migrations = jdbcTemplate.query(
                "SELECT version, script, success FROM flyway_schema_history "
                        + "WHERE version IS NOT NULL ORDER BY installed_rank",
                (resultSet, rowNum) -> new AppliedMigration(
                        resultSet.getString("version"),
                        resultSet.getString("script"),
                        resultSet.getBoolean("success")));

        assertThat(migrations).containsExactly(
                new AppliedMigration("1", "V1__create_initial_schema.sql", true),
                new AppliedMigration("2", "V2__add_expansion_1_schema.sql", true),
                new AppliedMigration("3", "V3__add_expansion_2_schema.sql", true));
    }

    @Test
    @DisplayName("핵심 CHECK와 partial unique 제약이 잘못된 상태와 열린 요청 중복을 거부한다")
    void V3제약_잘못된상태와열린요청중복_삽입을거부한다() {
        // given
        JdbcTemplate jdbcTemplate = migratedJdbcTemplate();
        Fixture fixture = insertFixture(jdbcTemplate);
        byte[] fingerprint = new byte[32];
        UUID submissionId = insertSubmission(jdbcTemplate, fixture.memberId(), fingerprint, "RECEIVED");

        // when & then
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO personal_collection (id, member_id, name) VALUES (?, ?, '   ')",
                UUID.randomUUID(), fixture.memberId()))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO submission (id, member_id, target_type, candidate, target_fingerprint, description) "
                        + "VALUES (?, ?, 'RESTAURANT', '{}'::jsonb, ?, '열 글자 이상인 중복 제보 설명')",
                UUID.randomUUID(), fixture.memberId(), fingerprint))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE submission SET member_id = NULL WHERE id = ?",
                submissionId))
                .isInstanceOf(DataAccessException.class);

        jdbcTemplate.update(
                "UPDATE submission SET status = 'REJECTED', member_reason = '반려', terminal_at = now() WHERE id = ?",
                submissionId);
        UUID reopenedId = insertSubmission(jdbcTemplate, fixture.memberId(), fingerprint, "RECEIVED");

        assertThat(reopenedId).isNotNull();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO curation (id, title, publication_status, main_position, created_by, updated_by) "
                        + "VALUES (?, '잘못된 초안', 'DRAFT', 1, ?, ?)",
                UUID.randomUUID(), fixture.adminId(), fixture.adminId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("부모 삭제 정책은 CASCADE, SET NULL, RESTRICT 계약을 지킨다")
    void V3삭제정책_부모삭제_CASCADE와SETNULL과RESTRICT를적용한다() {
        // given
        JdbcTemplate jdbcTemplate = migratedJdbcTemplate();
        Fixture fixture = insertFixture(jdbcTemplate);
        UUID collectionId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO personal_collection (id, member_id, name) VALUES (?, ?, '갈 곳')",
                collectionId, fixture.memberId());
        jdbcTemplate.update(
                "INSERT INTO collection_restaurant (collection_id, restaurant_id) VALUES (?, ?)",
                collectionId, fixture.restaurantId());
        UUID submissionId = insertSubmission(jdbcTemplate, fixture.memberId(), new byte[32], "RECEIVED");
        UUID reportId = insertReport(jdbcTemplate, fixture.memberId(), fixture.restaurantId());

        // when & then: 맛집은 관계가 먼저 정리되기 전에는 삭제할 수 없다.
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM restaurant WHERE id = ?", fixture.restaurantId()))
                .isInstanceOf(DataAccessException.class);

        // when: 회원 삭제는 컬렉션을 지우고 제보의 식별 연결을 제거한다.
        jdbcTemplate.update("DELETE FROM member_account WHERE id = ?", fixture.memberId());

        // then
        Integer collectionCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM personal_collection WHERE id = ?", Integer.class, collectionId);
        Boolean submissionUnlinkedWithTimestamp = jdbcTemplate.queryForObject(
                "SELECT member_id IS NULL AND member_unlinked_at IS NOT NULL FROM submission WHERE id = ?",
                Boolean.class,
                submissionId);
        Boolean reportUnlinkedWithTimestamp = jdbcTemplate.queryForObject(
                "SELECT member_id IS NULL AND member_unlinked_at IS NOT NULL FROM report WHERE id = ?",
                Boolean.class,
                reportId);
        assertThat(collectionCount).isZero();
        assertThat(submissionUnlinkedWithTimestamp).isTrue();
        assertThat(reportUnlinkedWithTimestamp).isTrue();
    }

    @Test
    @DisplayName("큐레이션 순서 고유 제약은 지연 가능하고 계약된 partial 및 역방향 인덱스가 존재한다")
    void V3인덱스_큐레이션순서와partial인덱스_계약된정의를가진다() {
        // given
        JdbcTemplate jdbcTemplate = migratedJdbcTemplate();

        // when
        Boolean deferrable = jdbcTemplate.queryForObject(
                "SELECT constraint_record.condeferrable FROM pg_constraint constraint_record "
                        + "JOIN pg_namespace namespace_record "
                        + "ON namespace_record.oid = constraint_record.connamespace "
                        + "WHERE namespace_record.nspname = current_schema() "
                        + "AND constraint_record.conname = 'uq_curation__status_main_position'",
                Boolean.class);
        List<String> indexDefinitions = jdbcTemplate.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = current_schema() AND indexname IN ("
                        + "'ix_favorite__restaurant_member', 'ux_submission__open_member_target', "
                        + "'ux_report__open_member_target_type', 'ux_notification__submission_status', "
                        + "'ix_notification__member_unread')",
                String.class);

        // then
        assertThat(deferrable).isTrue();
        assertThat(indexDefinitions).hasSize(5);
        assertThat(indexDefinitions).anySatisfy(definition ->
                assertThat(definition).contains("favorite", "restaurant_id", "member_id"));
        assertThat(indexDefinitions.stream().filter(definition -> definition.contains(" WHERE ")))
                .hasSize(4);
    }

    @Test
    @DisplayName("요청 이력과 알림은 정확히 한 요청만 참조하고 상태별 중복 생성을 막는다")
    void V3요청연결_이력과알림_정확히한요청과상태고유성을강제한다() {
        // given
        JdbcTemplate jdbcTemplate = migratedJdbcTemplate();
        Fixture fixture = insertFixture(jdbcTemplate);
        UUID submissionId = insertSubmission(jdbcTemplate, fixture.memberId(), new byte[32], "RECEIVED");

        // when & then
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO moderation_history (id, admin_account_id, from_status, to_status, trace_id) "
                        + "VALUES (?, ?, 'RECEIVED', 'IN_REVIEW', 'trace')",
                UUID.randomUUID(), fixture.adminId()))
                .isInstanceOf(DataAccessException.class);

        jdbcTemplate.update(
                "INSERT INTO notification (id, member_id, submission_id, status, title, message) "
                        + "VALUES (?, ?, ?, 'IN_REVIEW', '검토 시작', '검토를 시작했습니다.')",
                UUID.randomUUID(), fixture.memberId(), submissionId);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO notification (id, member_id, submission_id, status, title, message) "
                        + "VALUES (?, ?, ?, 'IN_REVIEW', '중복', '중복 알림')",
                UUID.randomUUID(), fixture.memberId(), submissionId))
                .isInstanceOf(DataAccessException.class);
    }

    private JdbcTemplate migratedJdbcTemplate() {
        SchemaDatabase database = createSchemaDatabase();
        migrate(database.dataSource(), database.schema(), null);
        return new JdbcTemplate(database.dataSource());
    }

    private SchemaDatabase createSchemaDatabase() {
        String schema = "expansion2_" + SCHEMA_SEQUENCE.incrementAndGet();
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

    private Fixture insertFixture(JdbcTemplate jdbcTemplate) {
        UUID memberId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO member_account (id, email, password_hash) VALUES (?, ?, 'password-hash')",
                memberId, memberId + "@example.com");
        jdbcTemplate.update(
                "INSERT INTO admin_account (id, login_id, password_hash) VALUES (?, ?, 'password-hash')",
                adminId, "admin-" + adminId);
        jdbcTemplate.update(
                "INSERT INTO restaurant (id, region_id, food_category_id, name, kakao_place_id, "
                        + "kakao_place_url, road_address, phone_number) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                restaurantId, REGION_ID, CATEGORY_ID, "테스트 맛집", "place-" + restaurantId,
                "https://example.com/" + restaurantId, "서울특별시 마포구", "02-1234-5678");
        return new Fixture(memberId, adminId, restaurantId);
    }

    private UUID insertSubmission(
            JdbcTemplate jdbcTemplate, UUID memberId, byte[] fingerprint, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO submission (id, member_id, target_type, candidate, target_fingerprint, "
                        + "description, status) VALUES (?, ?, 'RESTAURANT', '{}'::jsonb, ?, "
                        + "'열 글자 이상인 제보 설명입니다', ?)",
                id, memberId, fingerprint, status);
        return id;
    }

    private UUID insertReport(JdbcTemplate jdbcTemplate, UUID memberId, UUID targetId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO report (id, member_id, target_type, target_id, report_type, description) "
                        + "VALUES (?, ?, 'RESTAURANT', ?, 'ERROR', '열 글자 이상인 신고 설명입니다')",
                id, memberId, targetId);
        return id;
    }

    private record SchemaDatabase(String schema, DataSource dataSource) {
    }

    private record Fixture(UUID memberId, UUID adminId, UUID restaurantId) {
    }

    private record AppliedMigration(String version, String script, boolean success) {
    }
}

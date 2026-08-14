package com.masiton;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masiton.member.application.MemberSessionRevocation;
import com.masiton.member.application.port.out.MemberSessionRevocationStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 빈 PostgreSQL에 V1 baseline, V2(1차 확장 통합 스키마), V3(2차 확장 스키마),
 * V4(통합 3차 확장 AI 후보 스키마와 Lite 단일 모델 제약)가
 * 순서대로 성공적으로 적용되고,
 * ddl-auto=validate로 컨텍스트가 기동하며, Region·FoodCategory 기준 데이터가
 * seed-data-plan.md 2~4·6절 기준과 일치하는지 확인한다.
 *
 * <p>컨텍스트가 정상 기동하면 이미 Flyway 적용과 JPA validate가 통과한 것이므로,
 * 이 테스트는 flyway_schema_history를 직접 조회해 적용 파일과 순서가 migration-plan.md
 * 8~9절의 계약과 일치하는지 추가로 단언한다.
 */
@SpringBootTest
@com.masiton.test.TestProfile
@Testcontainers
    @DisplayName("Flyway 마이그레이션과 기준 데이터")
class FlywayMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17.10-alpine")
                    .withDatabaseName("masiton")
                    .withUsername("masiton")
                    .withPassword("masiton_local");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MemberSessionRevocationStore memberSessionRevocationStore;

    @Test
    @DisplayName("빈 데이터베이스에 V1부터 V4까지 계약된 순서와 파일명으로 성공 기록된다")
    void 마이그레이션적용_빈데이터베이스_V1부터V4까지계약된순서와파일명으로성공기록된다() {
        // given: 컨텍스트 기동 시점에 Flyway가 V1부터 V4 변경을 적용했다.

        // when
        List<AppliedMigration> appliedMigrations = jdbcTemplate.query(
                "SELECT version, description, type, script, success FROM flyway_schema_history "
                        + "WHERE version IS NOT NULL ORDER BY installed_rank",
                (resultSet, rowNum) -> new AppliedMigration(
                        resultSet.getString("version"),
                        resultSet.getString("description"),
                        resultSet.getString("type"),
                        resultSet.getString("script"),
                        resultSet.getBoolean("success")
                ));

        // then
        assertThat(appliedMigrations).containsExactly(
                new AppliedMigration("1", "create initial schema", "SQL", "V1__create_initial_schema.sql", true),
                new AppliedMigration("2", "add expansion 1 schema", "SQL",
                        "V2__add_expansion_1_schema.sql", true),
                new AppliedMigration("3", "add expansion 2 schema", "SQL",
                        "V3__add_expansion_2_schema.sql", true),
                new AppliedMigration("4", "create third expansion ai schema", "SQL",
                        "V4__create_third_expansion_ai_schema.sql", true)
        );
    }

    @Test
    @DisplayName("Region 기준 데이터는 정확히 25건이다")
    void Region조회_기준데이터적용후_정확히25건이다() {
        // given: baseline이 region 25건을 적재했다.

        // when
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM region", Integer.class);

        // then
        assertThat(count).isEqualTo(25);
    }

    @Test
    @DisplayName("FoodCategory 기준 데이터는 정확히 10건이고 code가 OTHER인 행은 정확히 1건이다")
    void FoodCategory조회_기준데이터적용후_정확히10건이고OTHER가1건이다() {
        // given: baseline이 food_category 10건을 적재했다.

        // when
        Integer total =
                jdbcTemplate.queryForObject("SELECT count(*) FROM food_category", Integer.class);
        Integer otherCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM food_category WHERE code = 'OTHER'", Integer.class);

        // then
        assertThat(total).isEqualTo(10);
        assertThat(otherCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Region의 code, name, sort_order가 각각 유일하다")
    void Region조회_기준데이터적용후_codeName정렬순서가모두유일하다() {
        assertColumnValuesAreUnique("region", "code");
        assertColumnValuesAreUnique("region", "name");
        assertColumnValuesAreUnique("region", "sort_order");
    }

    @Test
    @DisplayName("FoodCategory의 code, name, sort_order가 각각 유일하다")
    void FoodCategory조회_기준데이터적용후_codeName정렬순서가모두유일하다() {
        assertColumnValuesAreUnique("food_category", "code");
        assertColumnValuesAreUnique("food_category", "name");
        assertColumnValuesAreUnique("food_category", "sort_order");
    }

    @Test
    @DisplayName("V2 회원 보안 기반은 회원 계정에만 인증 Token을 의존시키고 sid 폐기 표식은 독립시킨다")
    void V2_회원보안기반_회원계정의존성과sid폐기표식독립성() {
        Integer memberAccountTables = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' "
                        + "AND table_name IN ('member_account', 'member_action_token', 'member_session_revocation')",
                Integer.class);

        assertThat(memberAccountTables).isEqualTo(3);
        assertForeignKey("fk_member_action_token__member_account", "pk_member_account", "RESTRICT");
        assertForeignKeyCount("member_session_revocation", 0);
        assertIndexCount("ux_member_action_token__active_member_purpose", "ix_member_session_revocation__expires_at");
    }

    @Test
    @DisplayName("V3 회원 인증 하드닝 작업 테이블을 전진 적용한다")
    void V3_회원인증하드닝_작업테이블생성() {
        Integer hardeningTables = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' "
                        + "AND table_name IN ('member_action_mail_outbox', 'member_deletion_job', "
                        + "'member_session_revocation_recovery')",
                Integer.class);

        assertThat(hardeningTables).isEqualTo(3);

        assertForeignKey("fk_member_action_mail_outbox__member_action_token", "pk_member_action_token", "CASCADE");
        assertForeignKeyCount("member_deletion_job", 0);
        assertForeignKeyCount("member_session_revocation_recovery", 0);
        assertIndexCount(
                "ix_member_action_mail_outbox__dispatch",
                "ix_member_deletion_job__next_attempt",
                "ix_member_session_revocation_recovery__next_attempt"
        );
        assertOutboxDoesNotDuplicatePersonalData();
    }

    @Test
    @DisplayName("V2 회원 개인화 관계 테이블을 전진 적용한다")
    void V2_회원개인화관계_테이블생성() {
        Integer personalTables = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' "
                        + "AND table_name IN ('favorite', 'recent_restaurant_view')", Integer.class);
        Integer personalIndexes = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public' "
                        + "AND indexname IN ('ix_favorite__member_favorited', "
                        + "'ix_recent_restaurant_view__member_viewed', "
                        + "'ix_recent_restaurant_view__cleanup_viewed')", Integer.class);

        assertThat(personalTables).isEqualTo(2);
        assertThat(personalIndexes).isEqualTo(3);
        assertPrimaryKeyColumns("favorite", "member_id", "restaurant_id");
        assertPrimaryKeyColumns("recent_restaurant_view", "member_id", "restaurant_id");
        assertForeignKey("fk_favorite__member_account", "pk_member_account", "CASCADE");
        assertForeignKey("fk_favorite__restaurant", "pk_restaurant", "RESTRICT");
        assertForeignKey("fk_recent_restaurant_view__member_account", "pk_member_account", "CASCADE");
        assertForeignKey("fk_recent_restaurant_view__restaurant", "pk_restaurant", "RESTRICT");
    }

    @Test
    @DisplayName("V5 맛집 좌표 열은 nullable이고 범위·null 쌍 CHECK와 bounds partial index를 강제한다")
    void V5_맛집좌표_nullable범위쌍CHECK와partialIndex강제() {
        assertIndexCount("ix_restaurant__public_coordinate_bounds");

        UUID mapoRegionId = UUID.fromString("10000000-0000-4000-8000-000000000014");
        UUID koreanCategoryId = UUID.fromString("20000000-0000-4000-8000-000000000001");

        UUID coordinateFreeRestaurantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO restaurant (id, region_id, food_category_id, name, kakao_place_id, "
                        + "kakao_place_url, road_address, phone_number) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                coordinateFreeRestaurantId, mapoRegionId, koreanCategoryId, "좌표 없는 맛집",
                "KAKAO-" + coordinateFreeRestaurantId, "https://example.com/place/" + coordinateFreeRestaurantId,
                "서울특별시 마포구 월드컵로 1", "02-0000-0000");

        UUID coordinatedRestaurantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO restaurant (id, region_id, food_category_id, name, kakao_place_id, "
                        + "kakao_place_url, road_address, phone_number, latitude, longitude) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                coordinatedRestaurantId, mapoRegionId, koreanCategoryId, "좌표 있는 맛집",
                "KAKAO-" + coordinatedRestaurantId, "https://example.com/place/" + coordinatedRestaurantId,
                "서울특별시 마포구 월드컵로 2", "02-0000-0001",
                new java.math.BigDecimal("37.5665"), new java.math.BigDecimal("126.9780"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO restaurant (id, region_id, food_category_id, name, kakao_place_id, "
                        + "kakao_place_url, road_address, phone_number, latitude) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), mapoRegionId, koreanCategoryId, "짝없는 위도 맛집",
                "KAKAO-" + UUID.randomUUID(), "https://example.com/place/pair", "서울특별시 마포구 월드컵로 3",
                "02-0000-0002", new java.math.BigDecimal("37.5665")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO restaurant (id, region_id, food_category_id, name, kakao_place_id, "
                        + "kakao_place_url, road_address, phone_number, latitude, longitude) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), mapoRegionId, koreanCategoryId, "범위 밖 위도 맛집",
                "KAKAO-" + UUID.randomUUID(), "https://example.com/place/range", "서울특별시 마포구 월드컵로 4",
                "02-0000-0003", new java.math.BigDecimal("91"), new java.math.BigDecimal("126.9780")))
                .isInstanceOf(DataIntegrityViolationException.class);

        Integer boundsRowCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM restaurant WHERE latitude BETWEEN 37 AND 38 AND longitude BETWEEN 126 AND 127 "
                        + "AND publication_status = 'PUBLIC' AND lifecycle_status = 'ACTIVE'",
                Integer.class);
        assertThat(boundsRowCount).isEqualTo(1);
    }

    @Test
    @DisplayName("V6 Creator 상세 표시 열은 nullable이고 기존 Creator 행의 값은 NULL로 남는다")
    void V6_Creator상세표시열_nullable이고기존행값은NULL로남는다() {
        // given: V1 baseline이 표시 열 없이 Creator 한 행을 이미 적재했다고 가정한 상태를 재현한다.
        UUID creatorId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO creator (id, external_channel_id, channel_name, channel_url, "
                        + "external_status_checked_at) VALUES (?, ?, ?, ?, ?)",
                creatorId, "UC-" + creatorId, "기존 채널", "https://example.com/channel/" + creatorId,
                OffsetDateTime.now());

        // when
        List<String> nullableColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'creator' "
                        + "AND column_name IN ('profile_image_url', 'description', 'handle') "
                        + "AND is_nullable = 'YES'",
                String.class);

        Integer nullValueCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM creator WHERE id = ? "
                        + "AND profile_image_url IS NULL AND description IS NULL AND handle IS NULL",
                Integer.class,
                creatorId);

        // then
        assertThat(nullableColumns)
                .containsExactlyInAnyOrder("profile_image_url", "description", "handle");
        assertThat(nullValueCount).isEqualTo(1);
    }

    @Test
    @DisplayName("동일 sid 폐기 기록은 가장 이른 폐기 시각과 가장 늦은 만료 시각을 유지한다")
    void memberSessionRevocation_동일sid_시각병합() {
        UUID sessionId = UUID.randomUUID();
        Instant firstRevokedAt = Instant.parse("2026-07-29T09:00:00Z");
        Instant firstExpiresAt = Instant.parse("2026-07-29T09:30:00Z");
        memberSessionRevocationStore.record(new MemberSessionRevocation(sessionId, firstRevokedAt, firstExpiresAt));
        memberSessionRevocationStore.record(new MemberSessionRevocation(
                sessionId,
                firstRevokedAt.plusSeconds(60),
                firstExpiresAt.plusSeconds(60)
        ));

        Boolean merged = jdbcTemplate.queryForObject(
                "SELECT revoked_at = ? AND expires_at = ? "
                        + "FROM member_session_revocation WHERE session_id = ?",
                Boolean.class,
                java.sql.Timestamp.from(firstRevokedAt),
                java.sql.Timestamp.from(firstExpiresAt.plusSeconds(60)),
                sessionId
        );

        assertThat(merged).isTrue();
    }

    private void assertColumnValuesAreUnique(String table, String column) {
        Integer distinctCount = jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT " + column + ") FROM " + table, Integer.class);
        Integer totalCount =
                jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);

        assertThat(distinctCount).isEqualTo(totalCount);
    }

    private void assertForeignKey(String constraintName, String targetConstraintName, String deleteRule) {
        ForeignKeyContract foreignKey = jdbcTemplate.queryForObject(
                "SELECT unique_constraint_name, update_rule, delete_rule "
                        + "FROM information_schema.referential_constraints "
                        + "WHERE constraint_schema = 'public' AND constraint_name = ?",
                (resultSet, rowNum) -> new ForeignKeyContract(
                        resultSet.getString("unique_constraint_name"),
                        resultSet.getString("update_rule"),
                        resultSet.getString("delete_rule")
                ),
                constraintName
        );

        assertThat(foreignKey).isEqualTo(new ForeignKeyContract(targetConstraintName, "RESTRICT", deleteRule));
    }

    private void assertForeignKeyCount(String tableName, int expectedCount) {
        Integer foreignKeyCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.table_constraints "
                        + "WHERE table_schema = 'public' AND table_name = ? AND constraint_type = 'FOREIGN KEY'",
                Integer.class,
                tableName
        );

        assertThat(foreignKeyCount).isEqualTo(expectedCount);
    }

    private void assertIndexCount(String... indexNames) {
        String placeholders = String.join(", ", Collections.nCopies(indexNames.length, "?"));
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname IN (" + placeholders + ")",
                Integer.class,
                indexNames
        );

        assertThat(count).isEqualTo(indexNames.length);
    }

    private void assertOutboxDoesNotDuplicatePersonalData() {
        List<String> columnNames = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'member_action_mail_outbox'",
                String.class
        );

        assertThat(columnNames)
                .contains("member_action_token_id", "encrypted_token", "encryption_nonce", "encryption_key_id")
                .doesNotContain("member_id", "email", "client_address", "token");
    }

    private void assertPrimaryKeyColumns(String tableName, String... expectedColumns) {
        List<String> primaryKeyColumns = jdbcTemplate.queryForList(
                "SELECT key_column_usage.column_name "
                        + "FROM information_schema.table_constraints "
                        + "JOIN information_schema.key_column_usage "
                        + "ON table_constraints.constraint_schema = key_column_usage.constraint_schema "
                        + "AND table_constraints.constraint_name = key_column_usage.constraint_name "
                        + "WHERE table_constraints.table_schema = 'public' "
                        + "AND table_constraints.table_name = ? "
                        + "AND table_constraints.constraint_type = 'PRIMARY KEY' "
                        + "ORDER BY key_column_usage.ordinal_position",
                String.class,
                tableName
        );

        assertThat(primaryKeyColumns).containsExactly(expectedColumns);
    }

    private record AppliedMigration(String version, String description, String type, String script, boolean success) {
    }

    private record ForeignKeyContract(String targetConstraintName, String updateRule, String deleteRule) {
    }
}

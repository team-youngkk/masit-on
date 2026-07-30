package com.masiton;

import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masiton.member.application.MemberSessionRevocation;
import com.masiton.member.application.port.out.MemberSessionRevocationStore;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-03 완료 조건을 검증한다: 빈 PostgreSQL에 Flyway 초기 스키마 baseline이 성공적으로
 * 적용되고, ddl-auto=validate로 컨텍스트가 기동하며, Region·FoodCategory 기준 데이터가
 * seed-data-plan.md 2~4·6절 기준과 일치하는지 확인한다.
 *
 * <p>컨텍스트가 정상 기동하면 이미 Flyway 적용과 JPA validate가 통과한 것이므로,
 * 이 테스트는 flyway_schema_history를 직접 조회해 baseline이 성공으로 기록됐는지
 * 추가로 단언한다. 초기 스키마는 migration-plan.md 2.1절에 따라 단일 파일이므로
 * 기록되는 버전도 하나다.
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
    @DisplayName("빈 데이터베이스에 초기 스키마 baseline이 성공으로 기록된다")
    void 마이그레이션적용_빈데이터베이스_초기스키마baseline이성공으로기록된다() {
        // given: 컨텍스트 기동 시점에 Flyway가 이미 초기 스키마 baseline을 적용했다.

        // when
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT version, success FROM flyway_schema_history "
                        + "WHERE version IS NOT NULL ORDER BY installed_rank");

        // then
        assertThat(rows)
                .extracting(row -> row.get("version"))
                .containsExactly("1", "2", "3", "4");
        assertThat(rows)
                .allSatisfy(row -> assertThat(row.get("success")).isEqualTo(Boolean.TRUE));
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
    @DisplayName("V2 회원 계정과 인증 기반 테이블을 전진 적용한다")
    void V2_회원보안기반_테이블생성() {
        Integer memberAccountTables = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' "
                        + "AND table_name IN ('member_account', 'member_action_token', 'member_session_revocation')",
                Integer.class);

        assertThat(memberAccountTables).isEqualTo(3);
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

        Integer cascadingOutboxForeignKeys = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.referential_constraints "
                        + "WHERE constraint_schema = 'public' "
                        + "AND constraint_name = 'fk_member_action_mail_outbox__member_action_token' "
                        + "AND delete_rule = 'CASCADE'",
                Integer.class);
        Integer deletionJobForeignKeys = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.table_constraints "
                        + "WHERE table_schema = 'public' AND table_name = 'member_deletion_job' "
                        + "AND constraint_type = 'FOREIGN KEY'",
                Integer.class);
        Integer dispatchIndexes = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public' "
                        + "AND indexname IN ('ix_member_action_mail_outbox__dispatch', "
                        + "'ix_member_deletion_job__next_attempt', "
                        + "'ix_member_session_revocation_recovery__next_attempt')",
                Integer.class);

        assertThat(cascadingOutboxForeignKeys).isEqualTo(1);
        assertThat(deletionJobForeignKeys).isZero();
        assertThat(dispatchIndexes).isEqualTo(3);
    }

    @Test
    @DisplayName("V4 회원 개인화 관계 테이블을 전진 적용한다")
    void V4_회원개인화관계_테이블생성() {
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
}

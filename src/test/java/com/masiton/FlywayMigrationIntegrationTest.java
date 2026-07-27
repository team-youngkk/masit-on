package com.masiton;

import java.util.List;
import java.util.Map;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-03 완료 조건을 검증한다: 빈 PostgreSQL에 Flyway V1~V5가 전부 성공적으로 적용되고,
 * ddl-auto=validate로 컨텍스트가 기동하며, Region·FoodCategory 기준 데이터가
 * seed-data-plan.md 2~4·6절 기준과 일치하는지 확인한다.
 *
 * <p>컨텍스트가 정상 기동하면 이미 Flyway 적용과 JPA validate가 통과한 것이므로,
 * 이 테스트는 flyway_schema_history를 직접 조회해 다섯 버전이 모두 성공으로 기록됐는지
 * 추가로 단언한다.
 */
@SpringBootTest
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

    @Test
    @DisplayName("빈 데이터베이스에 V1부터 V5까지 마이그레이션이 모두 성공으로 기록된다")
    void 마이그레이션적용_빈데이터베이스_V1부터V5까지모두성공으로기록된다() {
        // given: 컨텍스트 기동 시점에 Flyway가 이미 V1~V5를 적용했다.

        // when
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT version, success FROM flyway_schema_history "
                        + "WHERE version IS NOT NULL ORDER BY installed_rank");

        // then
        assertThat(rows)
                .extracting(row -> row.get("version"))
                .containsExactly("1", "2", "3", "4", "5");
        assertThat(rows)
                .allSatisfy(row -> assertThat(row.get("success")).isEqualTo(Boolean.TRUE));
    }

    @Test
    @DisplayName("Region 기준 데이터는 정확히 25건이다")
    void Region조회_V5시드적용후_정확히25건이다() {
        // given: V5가 region 25건을 적재했다.

        // when
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM region", Integer.class);

        // then
        assertThat(count).isEqualTo(25);
    }

    @Test
    @DisplayName("FoodCategory 기준 데이터는 정확히 10건이고 code가 OTHER인 행은 정확히 1건이다")
    void FoodCategory조회_V5시드적용후_정확히10건이고OTHER가1건이다() {
        // given: V5가 food_category 10건을 적재했다.

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
    void Region조회_V5시드적용후_codeName정렬순서가모두유일하다() {
        assertColumnValuesAreUnique("region", "code");
        assertColumnValuesAreUnique("region", "name");
        assertColumnValuesAreUnique("region", "sort_order");
    }

    @Test
    @DisplayName("FoodCategory의 code, name, sort_order가 각각 유일하다")
    void FoodCategory조회_V5시드적용후_codeName정렬순서가모두유일하다() {
        assertColumnValuesAreUnique("food_category", "code");
        assertColumnValuesAreUnique("food_category", "name");
        assertColumnValuesAreUnique("food_category", "sort_order");
    }

    private void assertColumnValuesAreUnique(String table, String column) {
        Integer distinctCount = jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT " + column + ") FROM " + table, Integer.class);
        Integer totalCount =
                jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);

        assertThat(distinctCount).isEqualTo(totalCount);
    }
}

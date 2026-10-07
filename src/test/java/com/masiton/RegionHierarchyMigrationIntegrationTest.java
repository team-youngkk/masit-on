package com.masiton;

import java.util.UUID;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.masiton.test.FullContextIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("V20 지역 계층 Flyway 마이그레이션")
class RegionHierarchyMigrationIntegrationTest {

    private static final UUID MAPO_ID = UUID.fromString("10000000-0000-4000-8000-000000000014");
    private static final UUID CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final AtomicInteger SCHEMA_SEQUENCE = new AtomicInteger();

    @Test
    @DisplayName("V19에서 V20으로 전진할 때 서울 25개 ID·code와 기존 맛집 FK를 보존한다")
    void V20전진적용_V19서울기준값과기존맛집FK를보존한다() {
        SchemaDatabase database = createSchemaDatabase();
        migrate(database, MigrationVersion.fromVersion("19"));
        JdbcTemplate jdbc = new JdbcTemplate(database.dataSource());
        UUID restaurantId = UUID.randomUUID();
        jdbc.update("INSERT INTO restaurant (id, region_id, food_category_id, name, kakao_place_id, "
                        + "kakao_place_url, road_address, phone_number) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                restaurantId, MAPO_ID, CATEGORY_ID, "기존 맛집", "kakao-" + restaurantId,
                "https://example.com/place/" + restaurantId, "서울특별시 마포구 월드컵로 1", "02-0000-0000");
        List<RegionSnapshot> seoulBefore = jdbc.query(
                "SELECT id, code, name, sort_order, active FROM region ORDER BY sort_order",
                (rs, rowNum) -> new RegionSnapshot(rs.getObject("id", UUID.class), rs.getString("code"),
                        rs.getString("name"), rs.getShort("sort_order"), rs.getBoolean("active")));
        assertThat(seoulBefore).hasSize(25);

        migrate(database, null);

        List<RegionSnapshot> seoulAfter = jdbc.query(
                "SELECT id, code, name, sort_order, active FROM region "
                        + "WHERE administrative_code LIKE '11%' AND parent_id IS NOT NULL ORDER BY sort_order",
                (rs, rowNum) -> new RegionSnapshot(rs.getObject("id", UUID.class), rs.getString("code"),
                        rs.getString("name"), rs.getShort("sort_order"), rs.getBoolean("active")));
        assertThat(seoulAfter).containsExactlyElementsOf(seoulBefore);
        assertThat(jdbc.queryForObject("SELECT region_id FROM restaurant WHERE id = ?", UUID.class, restaurantId))
                .isEqualTo(MAPO_ID);
        assertThat(jdbc.queryForObject("SELECT administrative_code FROM region WHERE id = ?", String.class, MAPO_ID))
                .isEqualTo("1144000000");
    }

    @Test
    @DisplayName("V20 시드에는 공식 시도 16건과 시군구 229건이 계층 제약에 맞게 적재된다")
    void V20시드적용_전국245건과세종제주특수케이스를검증한다() {
        JdbcTemplate jdbc = migratedJdbcTemplate();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM region", Integer.class)).isEqualTo(245);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM region WHERE parent_id IS NULL", Integer.class))
                .isEqualTo(16);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM region WHERE parent_id IS NOT NULL", Integer.class))
                .isEqualTo(229);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM region WHERE administrative_code = '3611000000' "
                + "AND parent_id IS NULL AND name = '세종특별자치시'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM region WHERE administrative_code IN "
                + "('5011000000', '5013000000') AND parent_id = "
                + "(SELECT id FROM region WHERE administrative_code = '5000000000')", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM region WHERE administrative_code = '1200000000' "
                + "AND name = '전남광주통합특별시' AND parent_id IS NULL", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM region WHERE administrative_code !~ '^[0-9]{10}$' "
                + "OR administrative_code !~ '^[0-9]{5}00000$'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("V20 지역 제약은 코드·형태·같은 부모의 이름과 정렬 순서 중복을 거부한다")
    void V20제약조건_잘못된코드와형제중복을거부한다() {
        JdbcTemplate jdbc = migratedJdbcTemplate();
        UUID seoulId = jdbc.queryForObject("SELECT id FROM region WHERE administrative_code = '1100000000'", UUID.class);
        UUID mapoId = jdbc.queryForObject("SELECT id FROM region WHERE administrative_code = '1144000000'", UUID.class);
        UUID busanId = jdbc.queryForObject("SELECT id FROM region WHERE administrative_code = '2600000000'", UUID.class);

        jdbc.update("INSERT INTO region (id, code, name, sort_order, active, administrative_code) "
                + "VALUES (?, 'UNIQUE-CODE-PROBE', '코드Probe', 99, true, '9900000000')", UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update("INSERT INTO region "
                + "(id, code, name, sort_order, active, administrative_code, parent_id) "
                + "VALUES (?, ?, ?, 98, true, ?, ?)", UUID.randomUUID(), "UNIQUE-CODE-PROBE-2", "코드Probe2",
                "9900000000", null))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO region "
                + "(id, code, name, sort_order, active, administrative_code, parent_id) "
                + "VALUES (?, ?, ?, 98, true, ?, ?)", UUID.randomUUID(), "BAD-SHAPE", "잘못된코드", "9900000001", null))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE region SET parent_id = ? WHERE id = ?", busanId, mapoId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO region "
                + "(id, code, name, sort_order, active, administrative_code, parent_id) "
                + "VALUES (?, ?, ?, 99, true, ?, ?)", UUID.randomUUID(), "DUP-NAME-PROBE", "마포구", "1199000000", seoulId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO region "
                + "(id, code, name, sort_order, active, administrative_code, parent_id) "
                + "VALUES (?, ?, ?, 14, true, ?, ?)", UUID.randomUUID(), "DUP-SORT-PROBE", "정렬Probe", "1198000000", seoulId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO region "
                + "(id, code, name, sort_order, active, administrative_code, parent_id) "
                + "VALUES (?, ?, ?, 99, true, ?, ?)", UUID.randomUUID(), "THIRD-LEVEL-PROBE", "3단계Probe",
                "1197000000", mapoId))
                .isInstanceOf(DataAccessException.class);
    }

    private JdbcTemplate migratedJdbcTemplate() {
        SchemaDatabase database = createSchemaDatabase();
        migrate(database, null);
        return new JdbcTemplate(database.dataSource());
    }

    private SchemaDatabase createSchemaDatabase() {
        String schema = "region_v20_" + SCHEMA_SEQUENCE.incrementAndGet();
        JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(
                FullContextIntegrationTest.POSTGRES.getJdbcUrl(), FullContextIntegrationTest.POSTGRES.getUsername(),
                FullContextIntegrationTest.POSTGRES.getPassword()));
        admin.execute("CREATE SCHEMA " + schema);
        String separator = FullContextIntegrationTest.POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        DataSource dataSource = new DriverManagerDataSource(
                FullContextIntegrationTest.POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                FullContextIntegrationTest.POSTGRES.getUsername(), FullContextIntegrationTest.POSTGRES.getPassword());
        return new SchemaDatabase(schema, dataSource);
    }

    private void migrate(SchemaDatabase database, MigrationVersion target) {
        var configuration = Flyway.configure().dataSource(database.dataSource()).schemas(database.schema())
                .defaultSchema(database.schema());
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private record SchemaDatabase(String schema, DataSource dataSource) {
    }

    private record RegionSnapshot(UUID id, String code, String name, short sortOrder, boolean active) {
    }
}

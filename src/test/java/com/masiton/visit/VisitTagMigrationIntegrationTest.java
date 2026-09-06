package com.masiton.visit;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DisplayName("방문 태그 감사 마이그레이션")
class VisitTagMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.10-alpine");

    @Test
    @DisplayName("V8 데이터베이스를 V9로 올려도 기존 태그 정의를 보존한다")
    void 마이그레이션_V8에서V9_기존데이터를보존한다() {
        // Given
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("8").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        String before = jdbc.queryForObject("SELECT jsonb_agg(to_jsonb(t) ORDER BY id)::text FROM tag_definition t", String.class);
        // When
        var result = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load().migrate();
        // Then
        assertThat(result.migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT jsonb_agg(to_jsonb(t) ORDER BY id)::text FROM tag_definition t", String.class))
                .isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM visit_tag_revision", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns WHERE table_name = 'visit_tag_revision' "
                + "AND column_name = 'changed_by_member_id'", String.class)).isEqualTo("YES");
    }
}

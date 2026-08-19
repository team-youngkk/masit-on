package com.masiton;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
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
@DisplayName("통합 계정 전환 Flyway 마이그레이션")
class UnifiedAccountMigrationIntegrationTest {

    private static final String BCRYPT_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private static final AtomicInteger SCHEMA_SEQUENCE = new AtomicInteger();

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17.10-alpine")
                    .withDatabaseName("masiton")
                    .withUsername("masiton")
                    .withPassword("masiton_local");

    @Test
    @DisplayName("V5 회원 행을 보존하면서 V6 역할 기본값과 전환 staging을 전진 적용한다")
    void V6적용_V5회원행존재_역할기본값과전환staging을전진적용한다() {
        // given
        SchemaDatabase database = createSchemaDatabase();
        migrate(database, MigrationVersion.fromVersion("5"));
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database.dataSource());
        UUID existingMemberId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO member_account (id, email, password_hash) VALUES (?, ?, 'password-hash')",
                existingMemberId, "existing-member@example.test");

        // when
        migrate(database, MigrationVersion.fromVersion("6"));

        // then
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank",
                String.class);
        String role = jdbcTemplate.queryForObject(
                "SELECT role FROM member_account WHERE id = ?", String.class, existingMemberId);
        Integer stagingTableCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = ? "
                        + "AND table_name = 'admin_account_migration_map'",
                Integer.class, database.schema());

        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6");
        assertThat(role).isEqualTo("MEMBER");
        assertThat(stagingTableCount).isEqualTo(1);
    }

    @Test
    @DisplayName("V6은 역할과 공동 승인 전환 입력의 정규화·참조·중복 제약을 강제한다")
    void V6제약_역할과공동승인전환입력_정규화참조중복을강제한다() {
        // given
        SchemaDatabase database = createSchemaDatabase();
        migrate(database, MigrationVersion.fromVersion("6"));
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database.dataSource());
        UUID adminId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO admin_account (id, login_id, password_hash) VALUES (?, ?, 'password-hash')",
                adminId, "legacy-admin");
        jdbcTemplate.update(
                "INSERT INTO member_account (id, email, password_hash, role) VALUES (?, ?, 'password-hash', 'ADMIN')",
                memberId, "active-admin@example.test");

        // when
        jdbcTemplate.update(
                "INSERT INTO admin_account_migration_map (admin_account_id, normalized_email, "
                        + "migration_disposition, member_account_id, approval_record_id) "
                        + "VALUES (?, ?, 'MIGRATE_ACTIVE', ?, ?)",
                adminId, "active-admin@example.test", memberId, "CHG-UNIFIED-AUTH-001");

        // then
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE member_account SET role = 'OPERATOR' WHERE id = ?", memberId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO admin_account_migration_map (admin_account_id, normalized_email, "
                        + "migration_disposition, approval_record_id) VALUES (?, ?, 'MIGRATE_ACTIVE', ?)",
                UUID.randomUUID(), "UPPERCASE@example.test", "CHG-UNIFIED-AUTH-002"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO admin_account_migration_map (admin_account_id, normalized_email, "
                        + "migration_disposition, approval_record_id) VALUES (?, ?, 'UNAPPROVED', ?)",
                UUID.randomUUID(), "another-admin@example.test", "CHG-UNIFIED-AUTH-003"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO admin_account_migration_map (admin_account_id, normalized_email, "
                        + "migration_disposition, approval_record_id) VALUES (?, ?, 'MIGRATE_ACTIVE', ?)",
                UUID.randomUUID(), "active-admin@example.test", "CHG-UNIFIED-AUTH-004"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("빈 데이터베이스에서 V7은 승인 입력 없이 전진 적용한다")
    void V7적용_빈데이터베이스_성공한다() {
        // given
        SchemaDatabase database = createSchemaDatabase();

        // when
        migrate(database, MigrationVersion.fromVersion("7"));

        // then
        List<String> versions = new JdbcTemplate(database.dataSource()).queryForList(
                "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank",
                String.class);
        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7");
    }

    @Test
    @DisplayName("V7은 승인된 active와 inactive 관리자를 계정 상태 계약대로 복사한다")
    void V7적용_승인된관리자매핑_계정상태계약대로복사한다() {
        // given
        SchemaDatabase database = createSchemaDatabase();
        migrate(database, MigrationVersion.fromVersion("6"));
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database.dataSource());
        UUID activeWithoutMemberId = UUID.randomUUID();
        UUID activeWithMemberAdminId = UUID.randomUUID();
        UUID activeExistingMemberId = UUID.randomUUID();
        UUID inactiveWithoutMemberId = UUID.randomUUID();
        UUID inactiveWithMemberAdminId = UUID.randomUUID();
        UUID inactiveExistingMemberId = UUID.randomUUID();

        insertLegacyAdmin(jdbcTemplate, activeWithoutMemberId, "legacy-active-new", true, BCRYPT_HASH);
        insertLegacyAdmin(jdbcTemplate, activeWithMemberAdminId, "legacy-active-existing", true, BCRYPT_HASH);
        insertLegacyAdmin(jdbcTemplate, inactiveWithoutMemberId, "legacy-inactive-new", false, BCRYPT_HASH);
        insertLegacyAdmin(jdbcTemplate, inactiveWithMemberAdminId, "legacy-inactive-existing", false, BCRYPT_HASH);
        jdbcTemplate.update(
                "INSERT INTO member_account (id, email, password_hash, status, email_verified_at, role) "
                        + "VALUES (?, ?, ?, 'ACTIVE', current_timestamp, 'MEMBER')",
                activeExistingMemberId, "active-existing@example.test", "existing-active-password");
        jdbcTemplate.update(
                "INSERT INTO member_account (id, email, password_hash, status, role) "
                        + "VALUES (?, ?, ?, 'DISABLED', 'ADMIN')",
                inactiveExistingMemberId, "inactive-existing@example.test", "existing-inactive-password");
        insertApprovedMap(jdbcTemplate, activeWithoutMemberId, "active-new@example.test", "MIGRATE_ACTIVE");
        insertApprovedMap(jdbcTemplate, activeWithMemberAdminId, "active-existing@example.test", "MIGRATE_ACTIVE");
        insertApprovedMap(jdbcTemplate, inactiveWithoutMemberId, "inactive-new@example.test", "PRESERVE_INACTIVE");
        insertApprovedMap(jdbcTemplate, inactiveWithMemberAdminId, "inactive-existing@example.test", "PRESERVE_INACTIVE");

        // when
        migrate(database, MigrationVersion.fromVersion("7"));

        // then
        assertThat(memberValue(jdbcTemplate, activeWithoutMemberId, "email"))
                .isEqualTo("active-new@example.test");
        assertThat(memberValue(jdbcTemplate, activeWithoutMemberId, "status")).isEqualTo("ACTIVE");
        assertThat(memberValue(jdbcTemplate, activeWithoutMemberId, "role")).isEqualTo("ADMIN");
        assertThat(memberValue(jdbcTemplate, activeWithoutMemberId, "email_verified_at")).isNotNull();
        assertThat(memberValue(jdbcTemplate, activeExistingMemberId, "role"))
                .isEqualTo("ADMIN");
        assertThat(memberValue(jdbcTemplate, activeExistingMemberId, "password_hash"))
                .isEqualTo("existing-active-password");
        assertThat(memberValue(jdbcTemplate, inactiveWithoutMemberId, "status")).isEqualTo("DISABLED");
        assertThat(memberValue(jdbcTemplate, inactiveWithoutMemberId, "role")).isEqualTo("MEMBER");
        assertThat(memberValue(jdbcTemplate, inactiveExistingMemberId, "role")).isEqualTo("ADMIN");
        assertThat(memberValue(jdbcTemplate, inactiveExistingMemberId, "password_hash"))
                .isEqualTo("existing-inactive-password");
        assertThat(mappedMemberId(jdbcTemplate, activeWithMemberAdminId)).isEqualTo(activeExistingMemberId);
        assertThat(mappedMemberId(jdbcTemplate, inactiveWithMemberAdminId)).isEqualTo(inactiveExistingMemberId);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM admin_account", Integer.class)).isEqualTo(4);
    }

    @Test
    @DisplayName("V7은 매핑이 누락된 legacy 관리자를 쓰기 전에 중단한다")
    void V7적용_승인매핑누락_쓰기전중단한다() {
        // given
        SchemaDatabase database = createSchemaDatabase();
        migrate(database, MigrationVersion.fromVersion("6"));
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database.dataSource());
        UUID legacyAdminId = UUID.randomUUID();
        insertLegacyAdmin(jdbcTemplate, legacyAdminId, "unmapped-legacy-admin", true, BCRYPT_HASH);

        // when
        assertThatThrownBy(() -> migrate(database, MigrationVersion.fromVersion("7")))
                .isInstanceOf(FlywayException.class);

        // then
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM member_account", Integer.class)).isZero();
        assertThat(appliedVersionCount(jdbcTemplate, "7")).isZero();
    }

    @Test
    @DisplayName("V7은 active 관리자가 비활성 회원과 충돌하면 쓰기 전에 중단한다")
    void V7적용_active관리자와비활성회원충돌_쓰기전중단한다() {
        // given
        SchemaDatabase database = createSchemaDatabase();
        migrate(database, MigrationVersion.fromVersion("6"));
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database.dataSource());
        UUID legacyAdminId = UUID.randomUUID();
        UUID disabledMemberId = UUID.randomUUID();
        insertLegacyAdmin(jdbcTemplate, legacyAdminId, "active-collision", true, BCRYPT_HASH);
        jdbcTemplate.update(
                "INSERT INTO member_account (id, email, password_hash, status, role) "
                        + "VALUES (?, ?, ?, 'DISABLED', 'MEMBER')",
                disabledMemberId, "active-collision@example.test", "disabled-member-password");
        insertApprovedMap(jdbcTemplate, legacyAdminId, "active-collision@example.test", "MIGRATE_ACTIVE");

        // when
        assertThatThrownBy(() -> migrate(database, MigrationVersion.fromVersion("7")))
                .isInstanceOf(FlywayException.class);

        // then
        assertThat(memberValue(jdbcTemplate, disabledMemberId, "status")).isEqualTo("DISABLED");
        assertThat(memberValue(jdbcTemplate, disabledMemberId, "role")).isEqualTo("MEMBER");
        assertThat(mappedMemberId(jdbcTemplate, legacyAdminId)).isNull();
        assertThat(appliedVersionCount(jdbcTemplate, "7")).isZero();
    }

    private void insertLegacyAdmin(JdbcTemplate jdbcTemplate, UUID id, String loginId,
            boolean active, String passwordHash) {
        jdbcTemplate.update(
                "INSERT INTO admin_account (id, login_id, password_hash, active) VALUES (?, ?, ?, ?)",
                id, loginId, passwordHash, active);
    }

    private void insertApprovedMap(JdbcTemplate jdbcTemplate, UUID adminAccountId,
            String email, String disposition) {
        jdbcTemplate.update(
                "INSERT INTO admin_account_migration_map (admin_account_id, normalized_email, "
                        + "migration_disposition, approval_record_id) VALUES (?, ?, ?, ?)",
                adminAccountId, email, disposition, "CHG-UNIFIED-AUTH-APPROVED");
    }

    private Object memberValue(JdbcTemplate jdbcTemplate, UUID memberId, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM member_account WHERE id = ?", Object.class, memberId);
    }

    private UUID mappedMemberId(JdbcTemplate jdbcTemplate, UUID adminAccountId) {
        return jdbcTemplate.queryForObject(
                "SELECT member_account_id FROM admin_account_migration_map WHERE admin_account_id = ?",
                UUID.class, adminAccountId);
    }

    private Integer appliedVersionCount(JdbcTemplate jdbcTemplate, String version) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = ?", Integer.class, version);
    }

    private SchemaDatabase createSchemaDatabase() {
        String schema = "unified_account_" + SCHEMA_SEQUENCE.incrementAndGet();
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

    private void migrate(SchemaDatabase database, MigrationVersion target) {
        Flyway.configure()
                .dataSource(database.dataSource())
                .schemas(database.schema())
                .defaultSchema(database.schema())
                .target(target)
                .load()
                .migrate();
    }

    private record SchemaDatabase(String schema, DataSource dataSource) {
    }
}

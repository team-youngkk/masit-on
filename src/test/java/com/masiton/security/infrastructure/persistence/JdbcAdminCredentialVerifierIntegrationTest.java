package com.masiton.security.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.masiton.security.application.AdminPrincipal;
import com.masiton.security.application.AdminRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
@DisplayName("JDBC 관리자 자격 증명 검증기")
class JdbcAdminCredentialVerifierIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17.10-alpine"))
            .withDatabaseName("masiton")
            .withUsername("masiton")
            .withPassword("masiton_local");

    private JdbcTemplate jdbcTemplate;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private JdbcAdminCredentialVerifier verifier;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        ));
        jdbcTemplate.execute("drop table if exists admin_account");
        jdbcTemplate.execute("""
                create table admin_account (
                    id uuid primary key,
                    login_id varchar(100) not null unique,
                    password_hash varchar(255) not null,
                    role varchar(16) not null,
                    active boolean not null
                )
                """);
        verifier = new JdbcAdminCredentialVerifier(jdbcTemplate, passwordEncoder);
    }

    @Test
    @DisplayName("활성 관리자 자격 증명이 일치하면 한 번의 조회로 주체를 반환한다")
    void authenticate_활성관리자_주체반환() {
        String adminId = insertAccount("admin", "correct-password", "ADMIN", true);

        Optional<AdminPrincipal> principal = verifier.authenticate("admin", "correct-password");

        assertThat(principal).contains(new AdminPrincipal(adminId, java.util.Set.of(AdminRole.ADMIN)));
    }

    @Test
    @DisplayName("없는 계정도 더미 BCrypt 검증을 수행하고 인증에 실패한다")
    void authenticate_없는계정_더미해시검증후실패() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.matches(eq("wrong-password"), anyString())).thenReturn(false);
        JdbcAdminCredentialVerifier verifier = new JdbcAdminCredentialVerifier(jdbcTemplate, encoder);

        Optional<AdminPrincipal> principal = verifier.authenticate("unknown", "wrong-password");

        assertThat(principal).isEmpty();
        verify(encoder).matches(eq("wrong-password"), anyString());
    }

    @Test
    @DisplayName("비활성 계정과 계약에 없는 권한은 인증하지 않는다")
    void authenticate_비활성또는알수없는권한_인증실패() {
        insertAccount("inactive", "correct-password", "ADMIN", false);
        insertAccount("unknown-role", "correct-password", "OPERATOR", true);

        assertThat(verifier.authenticate("inactive", "correct-password")).isEmpty();
        assertThat(verifier.authenticate("unknown-role", "correct-password")).isEmpty();
    }

    private String insertAccount(String loginId, String password, String role, boolean active) {
        String adminId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "insert into admin_account (id, login_id, password_hash, role, active) values (?, ?, ?, ?, ?)",
                UUID.fromString(adminId),
                loginId,
                passwordEncoder.encode(password),
                role,
                active
        );
        return adminId;
    }
}

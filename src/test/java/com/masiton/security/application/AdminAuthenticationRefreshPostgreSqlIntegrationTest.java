package com.masiton.security.application;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.masiton.security.application.port.out.LoginFailureStore;
import com.masiton.security.application.port.out.RefreshTokenStore;
import com.masiton.security.application.port.out.TokenIssuer;
import com.masiton.security.infrastructure.persistence.JdbcAdminCredentialVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
@DisplayName("관리자 인증 재발급 PostgreSQL 통합")
class AdminAuthenticationRefreshPostgreSqlIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17.10-alpine"))
            .withDatabaseName("masiton")
            .withUsername("masiton")
            .withPassword("masiton_local");

    private final RefreshTokenStore refreshTokenStore = mock(RefreshTokenStore.class);
    private final TokenIssuer tokenIssuer = mock(TokenIssuer.class);
    private AdminAuthenticationService service;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
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
        service = new AdminAuthenticationService(
                new JdbcAdminCredentialVerifier(jdbcTemplate, new BCryptPasswordEncoder()),
                mock(LoginFailureStore.class),
                refreshTokenStore,
                tokenIssuer,
                new SecurityTokenLifetime(Duration.ofMinutes(30), Duration.ofDays(14))
        );
    }

    @Test
    @DisplayName("회전된 Refresh Token의 UUID 관리자 식별자로 새 Access Token을 발급한다")
    void refresh_회전된토큰_UUID관리자_새AccessToken발급() {
        String adminId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "insert into admin_account (id, login_id, password_hash, role, active) values (?, ?, ?, ?, ?)",
                UUID.fromString(adminId), "admin", new BCryptPasswordEncoder().encode("correct-password"), "ADMIN", true
        );
        when(refreshTokenStore.rotate(eq("old-refresh-token"), any()))
                .thenReturn(new RefreshTokenRotation(adminId, "new-refresh-token"));
        when(tokenIssuer.issueAccessToken(any())).thenReturn("new-access-token");

        AuthenticationResult result = service.refresh("old-refresh-token");

        assertThat(result).isEqualTo(new AuthenticationResult("new-access-token", "new-refresh-token", 1800));
    }
}

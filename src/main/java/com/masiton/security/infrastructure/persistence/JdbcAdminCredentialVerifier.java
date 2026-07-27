package com.masiton.security.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.masiton.security.application.AdminPrincipal;
import com.masiton.security.application.AdminRole;
import com.masiton.security.application.port.out.AdminCredentialVerifier;

/**
 * The authentication application service only sees this port; SQL and BCrypt stay in infrastructure.
 */
@Component
public class JdbcAdminCredentialVerifier implements AdminCredentialVerifier {

    private static final String DUMMY_PASSWORD_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi.0P8EIw1PhqcoUL24TJnS0W9TuP.2";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public JdbcAdminCredentialVerifier(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Optional<AdminPrincipal> authenticate(String loginId, String password) {
        List<AdminAccount> accounts = jdbcTemplate.query(
                "select id, role, password_hash from admin_account where login_id = ? and active = true",
                (resultSet, rowNumber) -> new AdminAccount(
                        resultSet.getString("id"),
                        resultSet.getString("role"),
                        resultSet.getString("password_hash")
                ),
                loginId
        );

        AdminAccount account = accounts.stream().findFirst().orElse(null);
        String passwordHash = account == null ? DUMMY_PASSWORD_HASH : account.passwordHash();
        if (!passwordEncoder.matches(password, passwordHash) || account == null) {
            return Optional.empty();
        }
        return toPrincipal(account.adminId(), account.role());
    }

    @Override
    public Optional<AdminPrincipal> findActivePrincipalById(String adminId) {
        return findPrincipal(
                "select id, role from admin_account where id = ? and active = true",
                adminId
        );
    }

    private Optional<AdminPrincipal> findPrincipal(String sql, String value) {
        List<AdminAccount> accounts = jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> new AdminAccount(
                        resultSet.getString("id"),
                        resultSet.getString("role"),
                        null
                ),
                value
        );
        return accounts.stream()
                .findFirst()
                .flatMap(account -> toPrincipal(account.adminId(), account.role()));
    }

    private Optional<AdminPrincipal> toPrincipal(String adminId, String role) {
        if (adminId == null || role == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new AdminPrincipal(adminId, java.util.Set.of(AdminRole.valueOf(role))));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private record AdminAccount(String adminId, String role, String passwordHash) {
    }
}

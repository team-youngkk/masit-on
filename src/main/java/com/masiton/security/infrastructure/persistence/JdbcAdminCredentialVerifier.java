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

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public JdbcAdminCredentialVerifier(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public boolean matches(String loginId, String password) {
        List<String> passwordHashes = jdbcTemplate.query(
                "select password_hash from admin_account where login_id = ?",
                (resultSet, rowNumber) -> resultSet.getString("password_hash"),
                loginId
        );
        return passwordHashes.size() == 1 && passwordEncoder.matches(password, passwordHashes.getFirst());
    }

    @Override
    public Optional<AdminPrincipal> findActivePrincipal(String loginId) {
        return findPrincipal(
                "select id, role from admin_account where login_id = ? and active = true",
                loginId
        );
    }

    @Override
    public Optional<AdminPrincipal> findActivePrincipalById(String adminId) {
        return findPrincipal(
                "select id, role from admin_account where id = ? and active = true",
                adminId
        );
    }

    private Optional<AdminPrincipal> findPrincipal(String sql, String value) {
        List<AdminPrincipal> principals = jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> new AdminPrincipal(
                        resultSet.getObject("id").toString(),
                        java.util.Set.of(AdminRole.valueOf(resultSet.getString("role")))
                ),
                value
        );
        return principals.stream().findFirst();
    }
}

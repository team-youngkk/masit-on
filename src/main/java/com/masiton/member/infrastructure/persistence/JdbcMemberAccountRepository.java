package com.masiton.member.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import com.masiton.member.application.port.out.MemberAccountRepository;
import com.masiton.member.domain.model.MemberAccount;
import com.masiton.member.domain.model.MemberStatus;

@Component
public class JdbcMemberAccountRepository implements MemberAccountRepository {
    private static final RowMapper<MemberAccount> MAPPER = JdbcMemberAccountRepository::map;
    private final JdbcTemplate jdbcTemplate;

    public JdbcMemberAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<MemberAccount> findByEmail(String email) {
        return jdbcTemplate.query("SELECT id, email, password_hash, status, email_verified_at, deletion_requested_at, created_at "
                        + "FROM member_account WHERE email = ?", MAPPER, email).stream().findFirst();
    }

    @Override
    public Optional<MemberAccount> findById(UUID id) {
        return jdbcTemplate.query("SELECT id, email, password_hash, status, email_verified_at, deletion_requested_at, created_at "
                        + "FROM member_account WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    @Override
    public MemberAccount create(String email, String passwordHash, Instant now) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO member_account (id, email, password_hash, status, created_at, updated_at) VALUES (?, ?, ?, 'PENDING_VERIFICATION', ?, ?)",
                id, email, passwordHash, now, now);
        return findById(id).orElseThrow();
    }

    @Override
    public Optional<MemberAccount> createIfAbsent(String email, String passwordHash, Instant now) {
        UUID id = UUID.randomUUID();
        return jdbcTemplate.query("INSERT INTO member_account (id, email, password_hash, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'PENDING_VERIFICATION', ?, ?) ON CONFLICT (email) DO NOTHING "
                        + "RETURNING id, email, password_hash, status, email_verified_at, deletion_requested_at, created_at",
                MAPPER, id, email, passwordHash, now, now).stream().findFirst();
    }

    @Override
    public void activate(UUID id, Instant verifiedAt) {
        jdbcTemplate.update("UPDATE member_account SET status = 'ACTIVE', email_verified_at = ?, updated_at = ? "
                        + "WHERE id = ? AND status = 'PENDING_VERIFICATION'", verifiedAt, verifiedAt, id);
    }

    @Override
    public void changePassword(UUID id, String passwordHash, Instant now) {
        jdbcTemplate.update("UPDATE member_account SET password_hash = ?, updated_at = ? WHERE id = ? AND status = 'ACTIVE'",
                passwordHash, now, id);
    }

    @Override
    public void requestDeletion(UUID id, Instant now) {
        jdbcTemplate.update("UPDATE member_account SET status = 'DELETION_PENDING', deletion_requested_at = ?, updated_at = ? "
                        + "WHERE id = ? AND status = 'ACTIVE'", now, now, id);
    }

    private static MemberAccount map(ResultSet resultSet, int rowNum) throws SQLException {
        return new MemberAccount(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("email"),
                resultSet.getString("password_hash"),
                MemberStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("email_verified_at") == null ? null : resultSet.getTimestamp("email_verified_at").toInstant(),
                resultSet.getTimestamp("deletion_requested_at") == null ? null : resultSet.getTimestamp("deletion_requested_at").toInstant(),
                resultSet.getTimestamp("created_at").toInstant()
        );
    }
}

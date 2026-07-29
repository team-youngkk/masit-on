package com.masiton.member.infrastructure.persistence;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.masiton.member.application.port.out.MemberActionTokenRepository;
import com.masiton.member.domain.model.MemberActionPurpose;
import com.masiton.member.domain.model.MemberActionToken;

@Component
public class JdbcMemberActionTokenRepository implements MemberActionTokenRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcMemberActionTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void replace(MemberActionToken token, Instant issuedAt) {
        jdbcTemplate.update("UPDATE member_action_token SET status = 'REVOKED', completed_at = ? "
                        + "WHERE member_id = ? AND purpose = ? AND status = 'ISSUED'", issuedAt, token.memberId(), token.purpose().name());
        jdbcTemplate.update("INSERT INTO member_action_token (id, member_id, token_hash, purpose, status, issued_at, expires_at) "
                        + "VALUES (?, ?, ?, ?, 'ISSUED', ?, ?)", java.util.UUID.randomUUID(), token.memberId(), token.tokenHash(),
                token.purpose().name(), issuedAt, token.expiresAt());
    }

    @Override
    public Optional<MemberActionToken> consume(String rawToken, MemberActionPurpose purpose, Instant now) {
        byte[] hash = hash(rawToken);
        return jdbcTemplate.query("UPDATE member_action_token SET status = 'USED', completed_at = ? "
                        + "WHERE token_hash = ? AND purpose = ? AND status = 'ISSUED' AND expires_at > ? "
                        + "RETURNING member_id, token_hash, purpose, expires_at", (resultSet, rowNum) -> new MemberActionToken(
                        resultSet.getObject("member_id", java.util.UUID.class), resultSet.getBytes("token_hash"),
                        MemberActionPurpose.valueOf(resultSet.getString("purpose")), resultSet.getTimestamp("expires_at").toInstant()),
                now, hash, purpose.name(), now).stream().findFirst();
    }

    private byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

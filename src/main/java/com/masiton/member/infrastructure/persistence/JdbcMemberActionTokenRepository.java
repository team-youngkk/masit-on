package com.masiton.member.infrastructure.persistence;

import java.security.MessageDigest;
import java.sql.Timestamp;
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
                        + "WHERE member_id = ? AND purpose = ? AND status = 'ISSUED'", Timestamp.from(issuedAt), token.memberId(), token.purpose().name());
        jdbcTemplate.update("INSERT INTO member_action_token (id, member_id, token_hash, purpose, status, issued_at, expires_at) "
                + "VALUES (?, ?, ?, ?, 'ISSUED', ?, ?)", token.id(), token.memberId(), token.tokenHash(),
                token.purpose().name(), Timestamp.from(issuedAt), Timestamp.from(token.expiresAt()));
    }

    @Override
    public Optional<MemberActionToken> consume(String rawToken, MemberActionPurpose purpose, Instant now) {
        byte[] hash = hash(rawToken);
        return jdbcTemplate.query("UPDATE member_action_token SET status = 'USED', completed_at = ? "
                        + "WHERE token_hash = ? AND purpose = ? AND status = 'ISSUED' AND expires_at > ? "
                        + "RETURNING id, member_id, token_hash, purpose, expires_at", (resultSet, rowNum) -> new MemberActionToken(
                        resultSet.getObject("id", java.util.UUID.class), resultSet.getObject("member_id", java.util.UUID.class), resultSet.getBytes("token_hash"),
                        MemberActionPurpose.valueOf(resultSet.getString("purpose")), resultSet.getTimestamp("expires_at").toInstant()),
                Timestamp.from(now), hash, purpose.name(), Timestamp.from(now)).stream().findFirst();
    }

    @Override
    public void deleteByMemberId(java.util.UUID memberId) {
        jdbcTemplate.update("DELETE FROM member_action_token WHERE member_id = ?", memberId);
    }

    private byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

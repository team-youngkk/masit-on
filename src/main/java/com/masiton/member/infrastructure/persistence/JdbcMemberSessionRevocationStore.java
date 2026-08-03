package com.masiton.member.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.masiton.member.application.MemberSessionRevocation;
import com.masiton.member.application.port.out.MemberSessionRevocationStore;

@Component
public class JdbcMemberSessionRevocationStore implements MemberSessionRevocationStore {

    private static final String UPSERT = """
            INSERT INTO member_session_revocation (session_id, revoked_at, expires_at)
            VALUES (?, ?, ?)
            ON CONFLICT (session_id) DO UPDATE
            SET revoked_at = LEAST(member_session_revocation.revoked_at, EXCLUDED.revoked_at),
                expires_at = GREATEST(member_session_revocation.expires_at, EXCLUDED.expires_at)
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcMemberSessionRevocationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(MemberSessionRevocation revocation) {
        jdbcTemplate.update(
                UPSERT,
                revocation.sessionId(),
                java.sql.Timestamp.from(revocation.revokedAt()),
                java.sql.Timestamp.from(revocation.expiresAt())
        );
    }

    @Override
    public boolean isRevoked(java.util.UUID sessionId, java.time.Instant now) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM member_session_revocation WHERE session_id = ? AND expires_at > ?",
                Integer.class, sessionId, java.sql.Timestamp.from(now));
        return count != null && count > 0;
    }
}

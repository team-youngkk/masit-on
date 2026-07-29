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
                revocation.revokedAt(),
                revocation.expiresAt()
        );
    }
}

package com.masiton.member.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.masiton.member.application.MemberSessionRevocation;
import com.masiton.member.application.port.out.MemberSessionRevocationRecoveryJobStore;

@Component
public class JdbcMemberSessionRevocationRecoveryJobStore implements MemberSessionRevocationRecoveryJobStore {

    private static final long RETRY_DELAY_MINUTES = 15;

    private final JdbcTemplate jdbcTemplate;

    public JdbcMemberSessionRevocationRecoveryJobStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void enqueue(MemberSessionRevocation revocation, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO member_session_revocation_recovery
                    (session_id, revoked_at, expires_at, next_attempt_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (session_id) DO UPDATE
                SET revoked_at = LEAST(member_session_revocation_recovery.revoked_at, EXCLUDED.revoked_at),
                    expires_at = GREATEST(member_session_revocation_recovery.expires_at, EXCLUDED.expires_at),
                    next_attempt_at = LEAST(member_session_revocation_recovery.next_attempt_at, EXCLUDED.next_attempt_at)
                """,
                revocation.sessionId(), Timestamp.from(revocation.revokedAt()), Timestamp.from(revocation.expiresAt()),
                Timestamp.from(now));
    }

    @Override
    public List<MemberSessionRevocation> claimDue(Instant now, int limit) {
        Instant nextAttemptAt = nextAttemptAt(now);
        return jdbcTemplate.query("""
                WITH due AS (
                    SELECT session_id
                    FROM member_session_revocation_recovery
                    WHERE next_attempt_at <= ? AND expires_at > ?
                    ORDER BY next_attempt_at, revoked_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE member_session_revocation_recovery recovery
                SET attempt_count = recovery.attempt_count + 1,
                    last_attempt_at = ?,
                    next_attempt_at = ?
                FROM due
                WHERE recovery.session_id = due.session_id
                RETURNING recovery.session_id, recovery.revoked_at, recovery.expires_at
                """,
                (resultSet, rowNum) -> new MemberSessionRevocation(
                        resultSet.getObject("session_id", UUID.class),
                        resultSet.getTimestamp("revoked_at").toInstant(),
                        resultSet.getTimestamp("expires_at").toInstant()),
                Timestamp.from(now), Timestamp.from(now), limit, Timestamp.from(now), Timestamp.from(nextAttemptAt));
    }

    @Override
    public void complete(UUID sessionId) {
        jdbcTemplate.update("DELETE FROM member_session_revocation_recovery WHERE session_id = ?", sessionId);
    }

    @Override
    public void reschedule(UUID sessionId, Instant now) {
        jdbcTemplate.update("UPDATE member_session_revocation_recovery SET next_attempt_at = ? WHERE session_id = ?",
                Timestamp.from(nextAttemptAt(now)), sessionId);
    }

    @Override
    public List<UUID> findUnresolvedBefore(Instant cutoff, Instant now, int limit) {
        return jdbcTemplate.query("""
                SELECT session_id
                FROM member_session_revocation_recovery
                WHERE revoked_at <= ? AND expires_at > ?
                ORDER BY revoked_at
                LIMIT ?
                """, (resultSet, rowNum) -> resultSet.getObject("session_id", UUID.class),
                Timestamp.from(cutoff), Timestamp.from(now), limit);
    }

    private Instant nextAttemptAt(Instant now) {
        return now.plus(RETRY_DELAY_MINUTES, ChronoUnit.MINUTES);
    }
}

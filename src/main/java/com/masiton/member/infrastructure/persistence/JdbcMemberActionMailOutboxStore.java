package com.masiton.member.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.masiton.member.application.MemberActionMailOutboxDelivery;
import com.masiton.member.application.port.out.MemberActionMailOutboxStore;
import com.masiton.member.domain.model.MemberActionMailOutbox;
import com.masiton.member.domain.model.MemberActionPurpose;

@Component
public class JdbcMemberActionMailOutboxStore implements MemberActionMailOutboxStore {

    private static final long CLAIM_LEASE_MINUTES = 5;
    private static final long RETRY_DELAY_MINUTES = 1;

    private final JdbcTemplate jdbcTemplate;

    public JdbcMemberActionMailOutboxStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void enqueue(MemberActionMailOutbox outbox, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO member_action_mail_outbox
                    (id, member_action_token_id, purpose, encrypted_token, encryption_nonce, encryption_key_id,
                    status, attempt_count, next_attempt_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?)
                """,
                outbox.id(), outbox.memberActionTokenId(), outbox.purpose().name(), outbox.encryptedToken(),
                outbox.encryptionNonce(), outbox.encryptionKeyId(), Timestamp.from(now), Timestamp.from(now));
    }

    @Override
    public void cancelIneligible(Instant now) {
        jdbcTemplate.update("""
                UPDATE member_action_mail_outbox outbox
                SET status = 'CANCELLED', locked_until = NULL
                FROM member_action_token token
                WHERE outbox.member_action_token_id = token.id
                  AND outbox.status = 'PENDING'
                  AND (token.status <> 'ISSUED' OR token.expires_at <= ?)
                """, Timestamp.from(now));
    }

    @Override
    public List<MemberActionMailOutboxDelivery> claimDue(Instant now, int limit) {
        Instant leaseUntil = now.plus(CLAIM_LEASE_MINUTES, ChronoUnit.MINUTES);
        return jdbcTemplate.query("""
                WITH due AS (
                    SELECT outbox.id
                    FROM member_action_mail_outbox outbox
                    JOIN member_action_token token ON token.id = outbox.member_action_token_id
                    WHERE outbox.status = 'PENDING'
                      AND outbox.next_attempt_at <= ?
                      AND (outbox.locked_until IS NULL OR outbox.locked_until <= ?)
                      AND token.status = 'ISSUED'
                      AND token.expires_at > ?
                    ORDER BY outbox.next_attempt_at, outbox.created_at
                    FOR UPDATE OF outbox SKIP LOCKED
                    LIMIT ?
                ), claimed AS (
                    UPDATE member_action_mail_outbox outbox
                    SET attempt_count = outbox.attempt_count + 1,
                        locked_until = ?
                    FROM due
                    WHERE outbox.id = due.id
                    RETURNING outbox.id, outbox.member_action_token_id, outbox.purpose, outbox.encrypted_token,
                        outbox.encryption_nonce, outbox.encryption_key_id
                )
                SELECT claimed.id, claimed.member_action_token_id, claimed.purpose, claimed.encrypted_token,
                    claimed.encryption_nonce, claimed.encryption_key_id, account.email
                FROM claimed
                JOIN member_action_token token ON token.id = claimed.member_action_token_id
                JOIN member_account account ON account.id = token.member_id
                """, (resultSet, rowNum) -> new MemberActionMailOutboxDelivery(
                new MemberActionMailOutbox(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("member_action_token_id", UUID.class),
                        MemberActionPurpose.valueOf(resultSet.getString("purpose")),
                        resultSet.getBytes("encrypted_token"),
                        resultSet.getBytes("encryption_nonce"),
                        resultSet.getString("encryption_key_id")
                ),
                resultSet.getString("email")
        ), Timestamp.from(now), Timestamp.from(now), Timestamp.from(now), limit, Timestamp.from(leaseUntil));
    }

    @Override
    public boolean confirmDelivery(UUID outboxId, Instant now) {
        Boolean eligible = jdbcTemplate.queryForObject("""
                WITH cancelled AS (
                    UPDATE member_action_mail_outbox outbox
                    SET status = 'CANCELLED', locked_until = NULL
                    FROM member_action_token token
                    WHERE outbox.id = ?
                      AND outbox.member_action_token_id = token.id
                      AND outbox.status = 'PENDING'
                      AND (token.status <> 'ISSUED' OR token.expires_at <= ?)
                    RETURNING outbox.id
                )
                SELECT EXISTS (
                    SELECT 1
                    FROM member_action_mail_outbox outbox
                    JOIN member_action_token token ON token.id = outbox.member_action_token_id
                    WHERE outbox.id = ?
                      AND outbox.status = 'PENDING'
                      AND outbox.locked_until > ?
                      AND token.status = 'ISSUED'
                      AND token.expires_at > ?
                ) AND NOT EXISTS (SELECT 1 FROM cancelled)
                """, Boolean.class, outboxId, Timestamp.from(now), outboxId, Timestamp.from(now), Timestamp.from(now));
        return Boolean.TRUE.equals(eligible);
    }

    @Override
    public void markSent(UUID outboxId, Instant sentAt) {
        jdbcTemplate.update("""
                UPDATE member_action_mail_outbox
                SET status = 'SENT', sent_at = ?, locked_until = NULL
                WHERE id = ? AND status = 'PENDING'
                """, Timestamp.from(sentAt), outboxId);
    }

    @Override
    public void reschedule(UUID outboxId, Instant now) {
        jdbcTemplate.update("""
                UPDATE member_action_mail_outbox
                SET locked_until = NULL, next_attempt_at = ?
                WHERE id = ? AND status = 'PENDING'
                """, Timestamp.from(now.plus(RETRY_DELAY_MINUTES, ChronoUnit.MINUTES)), outboxId);
    }
}

package com.masiton.member.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.masiton.member.application.port.out.MemberDeletionJobStore;

@Component
public class JdbcMemberDeletionJobStore implements MemberDeletionJobStore {
    private final JdbcTemplate jdbcTemplate;

    public JdbcMemberDeletionJobStore(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public void enqueue(UUID memberId, Instant now) {
        jdbcTemplate.update("INSERT INTO member_deletion_job (member_id, requested_at, next_attempt_at) VALUES (?, ?, ?) "
                        + "ON CONFLICT (member_id) DO NOTHING", memberId, Timestamp.from(now), Timestamp.from(now));
    }

    @Override
    public List<UUID> claimDue(Instant now, int limit) {
        Instant next = now.plus(15, ChronoUnit.MINUTES);
        return jdbcTemplate.query("WITH due AS (SELECT member_id FROM member_deletion_job WHERE next_attempt_at <= ? "
                        + "ORDER BY requested_at FOR UPDATE SKIP LOCKED LIMIT ?) "
                        + "UPDATE member_deletion_job job SET attempt_count = job.attempt_count + 1, last_attempt_at = ?, "
                        + "next_attempt_at = ? FROM due WHERE job.member_id = due.member_id RETURNING job.member_id",
                (rs, row) -> rs.getObject(1, UUID.class), Timestamp.from(now), limit, Timestamp.from(now), Timestamp.from(next));
    }

    @Override
    public boolean hasExceededOneHour(UUID memberId, Instant now) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM member_deletion_job WHERE member_id = ? AND requested_at <= ?",
                Integer.class, memberId, Timestamp.from(now.minus(1, ChronoUnit.HOURS)));
        return count != null && count > 0;
    }

    @Override
    public void reschedule(UUID memberId, Instant now) {
        jdbcTemplate.update("UPDATE member_deletion_job SET next_attempt_at = ? WHERE member_id = ?",
                Timestamp.from(now.plus(15, ChronoUnit.MINUTES)), memberId);
    }

    @Override
    public void complete(UUID memberId) { jdbcTemplate.update("DELETE FROM member_deletion_job WHERE member_id = ?", memberId); }
}

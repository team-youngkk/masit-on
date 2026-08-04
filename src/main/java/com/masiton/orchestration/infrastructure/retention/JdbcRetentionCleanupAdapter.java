package com.masiton.orchestration.infrastructure.retention;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.masiton.member.application.port.out.MemberParticipationUnlinkPort;
import com.masiton.orchestration.application.retention.port.out.RetentionCleanupStore;

@Component
public class JdbcRetentionCleanupAdapter implements RetentionCleanupStore, MemberParticipationUnlinkPort {
    private final JdbcTemplate jdbcTemplate;

    public JdbcRetentionCleanupAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int unlinkExpiredSubmissionMembers(OffsetDateTime cutoff, OffsetDateTime unlinkedAt, int limit) {
        return unlinkExpired("submission", cutoff, unlinkedAt, limit);
    }

    @Override
    public int unlinkExpiredReportMembers(OffsetDateTime cutoff, OffsetDateTime unlinkedAt, int limit) {
        return unlinkExpired("report", cutoff, unlinkedAt, limit);
    }

    @Override
    public int deleteExpiredNotifications(OffsetDateTime cutoff, int limit) {
        return jdbcTemplate.update("WITH ranked AS ("
                + "SELECT id, member_id, created_at, row_number() OVER ("
                + "PARTITION BY member_id ORDER BY created_at DESC, id DESC) AS member_rank "
                + "FROM notification), candidates AS ("
                + "SELECT id FROM ranked WHERE created_at < ? AND member_rank > 200 "
                + "ORDER BY created_at, id LIMIT ?) "
                + "DELETE FROM notification target USING candidates "
                + "WHERE target.id = candidates.id", cutoff, limit);
    }

    @Override
    public void unlinkMemberParticipation(UUID memberId, OffsetDateTime unlinkedAt) {
        jdbcTemplate.update("UPDATE submission SET member_id = NULL, member_unlinked_at = ? WHERE member_id = ?",
                unlinkedAt, memberId);
        jdbcTemplate.update("UPDATE report SET member_id = NULL, member_unlinked_at = ? WHERE member_id = ?",
                unlinkedAt, memberId);
    }

    private int unlinkExpired(String table, OffsetDateTime cutoff, OffsetDateTime unlinkedAt, int limit) {
        return jdbcTemplate.update("WITH candidates AS ("
                + "SELECT id FROM " + table + " WHERE member_id IS NOT NULL AND terminal_at <= ? "
                + "ORDER BY terminal_at, id LIMIT ? FOR UPDATE SKIP LOCKED) "
                + "UPDATE " + table + " target SET member_id = NULL, member_unlinked_at = ? "
                + "FROM candidates WHERE target.id = candidates.id", cutoff, limit, unlinkedAt);
    }
}

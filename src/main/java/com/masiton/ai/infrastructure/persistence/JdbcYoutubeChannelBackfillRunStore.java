package com.masiton.ai.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.masiton.ai.application.port.out.YoutubeChannelBackfillRunStore;

@Repository
public class JdbcYoutubeChannelBackfillRunStore implements YoutubeChannelBackfillRunStore {
    private final JdbcTemplate jdbc;
    public JdbcYoutubeChannelBackfillRunStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override
    public Optional<Run> find(UUID creatorId, UUID runId) {
        return jdbc.query("SELECT * FROM youtube_channel_backfill_run WHERE creator_id=? AND id=?",
                (rs, n) -> map(rs), creatorId, runId).stream().findFirst();
    }

    @Override
    public List<UUID> findDueCreatorsForUpdate(OffsetDateTime dueBefore, int limit) {
        return jdbc.query("""
                SELECT w.creator_id
                  FROM youtube_channel_watch w
                  LEFT JOIN LATERAL (
                      SELECT r.status, r.stop_reason, r.updated_at
                        FROM youtube_channel_backfill_run r
                       WHERE r.creator_id=w.creator_id AND r.youtube_channel_id=w.youtube_channel_id
                       ORDER BY r.updated_at DESC, r.id DESC LIMIT 1
                  ) latest ON true
                 WHERE w.enabled=true AND w.subscription_status='ACTIVE'
                   AND (latest.updated_at IS NULL OR (
                       latest.status IN ('SUCCEEDED','FAILED','STOPPED')
                       AND latest.stop_reason IS DISTINCT FROM 'MANUAL'
                       AND latest.updated_at <= ?))
                   AND NOT EXISTS (
                       SELECT 1 FROM youtube_channel_backfill_run active
                        WHERE active.creator_id=w.creator_id AND active.status IN ('QUEUED','RUNNING'))
                 ORDER BY latest.updated_at ASC NULLS FIRST, w.creator_id
                 LIMIT ? FOR UPDATE OF w SKIP LOCKED
                """, (rs, n) -> rs.getObject("creator_id", UUID.class), dueBefore, limit);
    }

    @Override
    public Optional<StartRun> createOrReuse(UUID creatorId, OffsetDateTime now) {
        // Manual starts and scheduled starts serialize on the same Watch row in the application transaction.
        if (jdbc.query("""
                SELECT creator_id FROM youtube_channel_watch
                 WHERE creator_id=? AND enabled=true AND subscription_status='ACTIVE'
                 FOR UPDATE
                """, (rs, n) -> rs.getObject("creator_id", UUID.class), creatorId).isEmpty()) {
            return Optional.empty();
        }
        List<Run> existing = jdbc.query("""
                SELECT r.*
                  FROM youtube_channel_backfill_run r
                  JOIN youtube_channel_watch w ON w.creator_id = r.creator_id
                                                AND w.youtube_channel_id = r.youtube_channel_id
                 WHERE r.creator_id=? AND r.status IN ('QUEUED','RUNNING')
                   AND w.enabled=true AND w.subscription_status='ACTIVE'
                 ORDER BY r.created_at DESC
                 LIMIT 1
                """, (rs, n) -> map(rs), creatorId);
        if (!existing.isEmpty()) return Optional.of(new StartRun(existing.getFirst(), true));
        List<Run> resumable = jdbc.query("""
                WITH candidate AS (
                    SELECT r.id
                      FROM youtube_channel_backfill_run r
                      JOIN youtube_channel_watch w ON w.creator_id = r.creator_id
                                                    AND w.youtube_channel_id = r.youtube_channel_id
                     WHERE r.creator_id=?
                       AND (r.status='FAILED' OR (r.status='STOPPED'
                           AND r.stop_reason IN ('MAX_VIDEOS_PER_RUN', 'MAX_PAGES_PER_RUN')
                           AND r.page_token IS NOT NULL))
                       AND r.id = (
                           SELECT latest.id FROM youtube_channel_backfill_run latest
                            WHERE latest.creator_id=r.creator_id
                              AND latest.youtube_channel_id=r.youtube_channel_id
                            ORDER BY latest.updated_at DESC, latest.id DESC LIMIT 1)
                       AND w.enabled=true AND w.subscription_status='ACTIVE'
                     ORDER BY r.updated_at DESC, r.id DESC
                     LIMIT 1
                     FOR UPDATE SKIP LOCKED
                )
                UPDATE youtube_channel_backfill_run r
                   SET status='QUEUED', page_count=0, scanned_count=0,
                       last_error_category=NULL, stop_reason=NULL, lease_owner=NULL,
                       lease_expires_at=NULL, updated_at=?
                  FROM candidate c
                 WHERE r.id=c.id
                RETURNING r.*
                """, (rs, n) -> map(rs), creatorId, now);
        if (!resumable.isEmpty()) return Optional.of(new StartRun(resumable.getFirst(), false));
        List<Run> rows = jdbc.query("""
            INSERT INTO youtube_channel_backfill_run (id, creator_id, youtube_channel_id, status, created_at, updated_at)
            SELECT ?, w.creator_id, w.youtube_channel_id, 'QUEUED', ?, ? FROM youtube_channel_watch w
             WHERE w.creator_id=? AND w.enabled=true AND w.subscription_status='ACTIVE'
            ON CONFLICT DO NOTHING RETURNING *
            """, (rs,n)->map(rs), UUID.randomUUID(), now, now, creatorId);
        if (!rows.isEmpty()) return Optional.of(new StartRun(rows.getFirst(), false));
        return jdbc.query("""
                SELECT r.*
                  FROM youtube_channel_backfill_run r
                  JOIN youtube_channel_watch w ON w.creator_id = r.creator_id
                                                AND w.youtube_channel_id = r.youtube_channel_id
                 WHERE r.creator_id=? AND r.status IN ('QUEUED','RUNNING')
                   AND w.enabled=true AND w.subscription_status='ACTIVE'
                 ORDER BY r.created_at DESC
                 LIMIT 1
                """, (rs, n) -> map(rs), creatorId).stream()
                .findFirst()
                .map(run -> new StartRun(run, true));
    }

    @Override
    public Optional<ClaimedRun> claim(OffsetDateTime now, OffsetDateTime expires, String owner) {
        return jdbc.query("""
            WITH candidate AS (
                SELECT r.id
                  FROM youtube_channel_backfill_run r
                  JOIN youtube_channel_watch w
                    ON w.creator_id = r.creator_id
                   AND w.youtube_channel_id = r.youtube_channel_id
                 WHERE w.enabled = true
                   AND w.subscription_status = 'ACTIVE'
                   AND (r.status = 'QUEUED' OR (r.status = 'RUNNING' AND r.lease_expires_at < ?))
                 ORDER BY r.updated_at, r.id
                 LIMIT 1
                 FOR UPDATE SKIP LOCKED
            )
            UPDATE youtube_channel_backfill_run r SET status='RUNNING', lease_owner=?, lease_expires_at=?, updated_at=? FROM candidate c WHERE r.id=c.id
            RETURNING r.id, r.creator_id, r.youtube_channel_id, r.page_token, r.page_count, r.scanned_count, r.lease_owner
            """, (rs, n) -> new ClaimedRun(rs.getObject("id", UUID.class),
                    rs.getObject("creator_id", UUID.class), rs.getString("youtube_channel_id"),
                    rs.getString("page_token"), rs.getInt("page_count"), rs.getLong("scanned_count"),
                    rs.getString("lease_owner")), now, owner, expires, now).stream().findFirst();
    }

    @Override
    public boolean isClaimActive(UUID id, String owner, OffsetDateTime now) {
        return !jdbc.query("""
                SELECT r.id
                  FROM youtube_channel_backfill_run r
                  JOIN youtube_channel_watch w
                    ON w.creator_id = r.creator_id
                   AND w.youtube_channel_id = r.youtube_channel_id
                 WHERE r.id = ?
                   AND r.status = 'RUNNING'
                   AND r.lease_owner = ?
                   AND r.lease_expires_at > ?
                   AND w.enabled = true
                   AND w.subscription_status = 'ACTIVE'
                 FOR UPDATE OF r, w
                """, (rs, n) -> rs.getObject("id", UUID.class), id, owner, now).isEmpty();
    }

    @Override
    public void recordVideo(UUID runId, String videoId, boolean reused, OffsetDateTime now) {
        jdbc.update("""
                WITH inserted AS (
                    INSERT INTO youtube_channel_backfill_video (run_id, youtube_video_id, result, created_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (run_id, youtube_video_id) DO NOTHING
                    RETURNING result
                )
                UPDATE youtube_channel_backfill_run r
                   SET submitted_count = submitted_count + CASE WHEN inserted.result='SUBMITTED' THEN 1 ELSE 0 END,
                       reused_count = reused_count + CASE WHEN inserted.result='REUSED' THEN 1 ELSE 0 END,
                       updated_at = ?
                  FROM inserted
                 WHERE r.id = ?
                """, runId, videoId, reused ? "REUSED" : "SUBMITTED", now, now, runId);
    }

    @Override
    public void completePage(UUID id, String owner, int scanned,
                             String token, boolean done, boolean limitReached, OffsetDateTime now) {
        jdbc.update("""
                UPDATE youtube_channel_backfill_run r
                   SET status = CASE
                       WHEN EXISTS (
                           SELECT 1 FROM youtube_channel_watch w
                            WHERE w.creator_id = r.creator_id
                              AND w.youtube_channel_id = r.youtube_channel_id
                              AND w.enabled = true
                              AND w.subscription_status = 'ACTIVE'
                       ) THEN CASE WHEN ? THEN 'SUCCEEDED'
                                   WHEN ? THEN 'STOPPED'
                                   ELSE 'QUEUED' END
                       ELSE 'STOPPED' END,
                       stop_reason = CASE WHEN ? THEN 'MAX_VIDEOS_PER_RUN' ELSE NULL END,
                       page_count = page_count + 1,
                       scanned_count = scanned_count + ?,
                       page_token = ?,
                       lease_owner = NULL,
                       lease_expires_at = NULL,
                       updated_at = ?
                 WHERE id=? AND status='RUNNING' AND lease_owner=? AND lease_expires_at > ?
                """, done, limitReached, limitReached, scanned, token, now, id, owner, now);
    }

    @Override
    public void fail(UUID id, String owner, String error, OffsetDateTime now) {
        jdbc.update("""
                UPDATE youtube_channel_backfill_run
                   SET status='FAILED', last_error_category=?, lease_owner=NULL,
                       lease_expires_at=NULL, updated_at=?
                 WHERE id=? AND status='RUNNING' AND lease_owner=? AND lease_expires_at > ?
                """, error, now, id, owner, now);
    }

    @Override
    public void stop(UUID creatorId, UUID runId, OffsetDateTime now) {
        jdbc.update("""
                UPDATE youtube_channel_backfill_run
                   SET status='STOPPED', stop_reason='MANUAL', lease_owner=NULL,
                       lease_expires_at=NULL, updated_at=?
                 WHERE creator_id=? AND id=? AND status IN ('QUEUED','RUNNING')
                """, now, creatorId, runId);
    }

    @Override
    public void stopAtLimit(UUID runId, String owner, String reason, OffsetDateTime now) {
        if (!"MAX_PAGES_PER_RUN".equals(reason) && !"MAX_VIDEOS_PER_RUN".equals(reason)) {
            throw new IllegalArgumentException("Invalid backfill limit reason");
        }
        jdbc.update("""
                UPDATE youtube_channel_backfill_run
                   SET status='STOPPED', stop_reason=?, lease_owner=NULL,
                       lease_expires_at=NULL, updated_at=?
                 WHERE id=? AND status='RUNNING' AND lease_owner=? AND lease_expires_at > ?
                """, reason, now, runId, owner, now);
    }

    @Override
    public void stopRunsForInactiveWatches(OffsetDateTime now) {
        jdbc.update("""
                UPDATE youtube_channel_backfill_run r
                   SET status='STOPPED', stop_reason=NULL, lease_owner=NULL,
                       lease_expires_at=NULL, updated_at=?
                 WHERE r.status IN ('QUEUED','RUNNING')
                   AND NOT EXISTS (
                       SELECT 1 FROM youtube_channel_watch w
                        WHERE w.creator_id = r.creator_id
                          AND w.youtube_channel_id = r.youtube_channel_id
                          AND w.enabled = true
                          AND w.subscription_status = 'ACTIVE'
                   )
                """, now);
    }

    private Run map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Run(rs.getObject("id", UUID.class), rs.getObject("creator_id", UUID.class),
                rs.getString("status"), rs.getLong("scanned_count"), rs.getLong("submitted_count"),
                rs.getLong("reused_count"), rs.getString("last_error_category"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
    }
}

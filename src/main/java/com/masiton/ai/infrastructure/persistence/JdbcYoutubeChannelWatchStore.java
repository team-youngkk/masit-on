package com.masiton.ai.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.masiton.ai.application.port.out.YoutubeChannelWatchStore;

@Repository
public class JdbcYoutubeChannelWatchStore implements YoutubeChannelWatchStore {
    private static final int WATCH_LOCK_QUERY_TIMEOUT_SECONDS = 5;

    private final JdbcTemplate jdbcTemplate;

    public JdbcYoutubeChannelWatchStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Watch> find(String channelId) {
        List<Watch> rows = jdbcTemplate.query("""
                SELECT youtube_channel_id, enabled, subscription_status, subscription_token_hash
                  FROM youtube_channel_watch
                 WHERE youtube_channel_id = ?
                """, this::map, channelId);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<Watch> findForUpdate(String channelId) {
        String sql = """
                SELECT youtube_channel_id, enabled, subscription_status, subscription_token_hash
                  FROM youtube_channel_watch
                 WHERE youtube_channel_id = ?
                 FOR UPDATE
                """;
        List<Watch> rows = jdbcTemplate.query(connection -> {
            var statement = connection.prepareStatement(sql);
            statement.setQueryTimeout(WATCH_LOCK_QUERY_TIMEOUT_SECONDS);
            statement.setString(1, channelId);
            return statement;
        }, this::map);
        return rows.stream().findFirst();
    }

    @Override
    public WatchDetail upsert(UUID creatorId, String channelId, boolean enabled, String subscriptionStatus,
                              byte[] subscriptionTokenHash) {
        String requestedStatus = enabled && "ACTIVE".equals(subscriptionStatus) ? "UNKNOWN" : subscriptionStatus;
        return jdbcTemplate.queryForObject("""
                INSERT INTO youtube_channel_watch (
                    id, creator_id, youtube_channel_id, enabled, subscription_status,
                    subscription_token_hash, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (creator_id) DO UPDATE
                   SET youtube_channel_id = EXCLUDED.youtube_channel_id,
                       enabled = EXCLUDED.enabled,
                       subscription_status = CASE
                           WHEN youtube_channel_watch.enabled
                                AND youtube_channel_watch.subscription_status = 'ACTIVE'
                                AND youtube_channel_watch.subscription_token_hash IS NOT NULL
                                AND EXCLUDED.enabled
                           THEN youtube_channel_watch.subscription_status
                           ELSE EXCLUDED.subscription_status
                       END,
                       subscription_token_hash = CASE
                           WHEN youtube_channel_watch.enabled
                                AND youtube_channel_watch.subscription_status = 'ACTIVE'
                                AND youtube_channel_watch.subscription_token_hash IS NOT NULL
                                AND EXCLUDED.enabled
                           THEN youtube_channel_watch.subscription_token_hash
                           WHEN EXCLUDED.enabled AND EXCLUDED.subscription_token_hash IS NOT NULL
                           THEN EXCLUDED.subscription_token_hash
                           ELSE youtube_channel_watch.subscription_token_hash
                       END,
                       updated_at = CURRENT_TIMESTAMP
                RETURNING enabled, subscription_status, last_notification_at, last_renewed_at,
                          last_error_category, last_error_at
                """, this::mapDetail, UUID.randomUUID(), creatorId, channelId, enabled, requestedStatus,
                subscriptionTokenHash);
    }

    @Override
    public void markNotificationReceived(String channelId, OffsetDateTime receivedAt) {
        jdbcTemplate.update("""
                UPDATE youtube_channel_watch
                   SET last_notification_at = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE youtube_channel_id = ?
                """, receivedAt, channelId);
    }

    @Override
    public void markSubscriptionVerified(String channelId, OffsetDateTime verifiedAt) {
        jdbcTemplate.update("""
                UPDATE youtube_channel_watch
                   SET subscription_status = 'ACTIVE',
                       last_renewed_at = ?,
                       last_error_category = NULL,
                       last_error_at = NULL,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE youtube_channel_id = ?
                   AND enabled = true
                """, verifiedAt, channelId);
    }

    @Override
    public Optional<WatchDetail> markSubscriptionFailed(String channelId, String errorCategory,
                                                         byte[] expectedTokenHash) {
        List<WatchDetail> rows = jdbcTemplate.query("""
                UPDATE youtube_channel_watch
                   SET enabled = true,
                       subscription_status = 'RENEWAL_FAILED',
                       last_error_category = ?,
                       last_error_at = CURRENT_TIMESTAMP,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE youtube_channel_id = ?
                   AND subscription_token_hash = ?
                   AND enabled = true
                   AND subscription_status = 'UNKNOWN'
                RETURNING enabled, subscription_status, last_notification_at, last_renewed_at,
                          last_error_category, last_error_at
                """, this::mapDetail, errorCategory, channelId, expectedTokenHash);
        return rows.stream().findFirst();
    }

    @Override
    public void deletePending(String channelId, byte[] expectedTokenHash) {
        jdbcTemplate.update("""
                DELETE FROM youtube_channel_watch
                 WHERE youtube_channel_id = ?
                   AND subscription_token_hash = ?
                   AND enabled = true
                   AND subscription_status = 'UNKNOWN'
                """, channelId, expectedTokenHash);
    }

    @Override
    public Optional<WatchDetail> restoreActivation(UUID creatorId, String channelId, Watch previous,
                                                    byte[] expectedTokenHash) {
        int updated = jdbcTemplate.update("""
                UPDATE youtube_channel_watch
                   SET enabled = ?,
                       subscription_status = ?,
                       subscription_token_hash = ?,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE youtube_channel_id = ?
                   AND subscription_token_hash = ?
                   AND enabled = true
                   AND subscription_status = 'UNKNOWN'
                """, previous.enabled(), previous.subscriptionStatus(), previous.subscriptionTokenHash(),
                channelId, expectedTokenHash);
        if (updated == 0) {
            return Optional.empty();
        }
        return findDetail(channelId);
    }

    @Override
    public Optional<WatchDetail> findDetail(String channelId) {
        List<WatchDetail> rows = jdbcTemplate.query("""
                SELECT enabled, subscription_status, last_notification_at, last_renewed_at,
                       last_error_category, last_error_at
                  FROM youtube_channel_watch
                 WHERE youtube_channel_id = ?
                """, this::mapDetail, channelId);
        return rows.stream().findFirst();
    }

    @Override
    public WatchCandidatePage findCandidatePage(int limit, long offset) {
        long totalElements = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM creator c
                  LEFT JOIN youtube_channel_watch w ON w.creator_id = c.id
                 WHERE c.external_channel_id IS NOT NULL
                   AND ((c.publication_status = 'PUBLIC'
                         AND c.lifecycle_status = 'ACTIVE'
                         AND c.external_availability_status = 'AVAILABLE')
                        OR w.id IS NOT NULL)
                """, Long.class);
        List<WatchCandidate> rows = jdbcTemplate.query("""
                SELECT c.id AS creator_id,
                       c.channel_name,
                       c.external_channel_id,
                       (c.publication_status = 'PUBLIC' AND c.lifecycle_status = 'ACTIVE') AS publicly_visible,
                       (c.external_availability_status = 'AVAILABLE') AS externally_available,
                       w.id AS watch_id,
                       w.enabled AS watch_enabled,
                       w.subscription_status AS watch_subscription_status,
                       w.last_notification_at AS watch_last_notification_at,
                       w.last_renewed_at AS watch_last_renewed_at,
                       w.last_error_category AS watch_last_error_category,
                       w.last_error_at AS watch_last_error_at
                  FROM creator c
                  LEFT JOIN youtube_channel_watch w ON w.creator_id = c.id
                 WHERE c.external_channel_id IS NOT NULL
                   AND ((c.publication_status = 'PUBLIC'
                         AND c.lifecycle_status = 'ACTIVE'
                         AND c.external_availability_status = 'AVAILABLE')
                        OR w.id IS NOT NULL)
                 ORDER BY c.channel_name COLLATE "C", c.id
                 LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new WatchCandidate(
                        rs.getObject("creator_id", UUID.class),
                        rs.getString("channel_name"),
                        rs.getBoolean("publicly_visible"),
                        rs.getBoolean("externally_available"),
                        rs.getString("external_channel_id"),
                        rs.getObject("watch_id") == null
                                ? Optional.empty()
                                : Optional.of(new WatchDetail(
                                        rs.getBoolean("watch_enabled"),
                                        rs.getString("watch_subscription_status"),
                                        rs.getObject("watch_last_notification_at", OffsetDateTime.class),
                                        rs.getObject("watch_last_renewed_at", OffsetDateTime.class),
                                        rs.getString("watch_last_error_category"),
                                        rs.getObject("watch_last_error_at", OffsetDateTime.class)))), limit, offset);
        return new WatchCandidatePage(rows, totalElements);
    }

    private Watch map(ResultSet rs, int rowNum) throws SQLException {
        return new Watch(rs.getString("youtube_channel_id"), rs.getBoolean("enabled"),
                rs.getString("subscription_status"), rs.getBytes("subscription_token_hash"));
    }

    private WatchDetail mapDetail(ResultSet rs, int rowNum) throws SQLException {
        return new WatchDetail(rs.getBoolean("enabled"), rs.getString("subscription_status"),
                rs.getObject("last_notification_at", OffsetDateTime.class),
                rs.getObject("last_renewed_at", OffsetDateTime.class), rs.getString("last_error_category"),
                rs.getObject("last_error_at", OffsetDateTime.class));
    }
}

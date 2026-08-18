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
                RETURNING enabled, subscription_status, last_notification_at, last_renewed_at, last_error_category
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
                       updated_at = CURRENT_TIMESTAMP
                 WHERE youtube_channel_id = ?
                   AND subscription_token_hash = ?
                   AND enabled = true
                   AND subscription_status = 'UNKNOWN'
                RETURNING enabled, subscription_status, last_notification_at, last_renewed_at, last_error_category
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
                SELECT enabled, subscription_status, last_notification_at, last_renewed_at, last_error_category
                  FROM youtube_channel_watch
                 WHERE youtube_channel_id = ?
                """, this::mapDetail, channelId);
        return rows.stream().findFirst();
    }

    private Watch map(ResultSet rs, int rowNum) throws SQLException {
        return new Watch(rs.getString("youtube_channel_id"), rs.getBoolean("enabled"),
                rs.getString("subscription_status"), rs.getBytes("subscription_token_hash"));
    }

    private WatchDetail mapDetail(ResultSet rs, int rowNum) throws SQLException {
        return new WatchDetail(rs.getBoolean("enabled"), rs.getString("subscription_status"),
                rs.getObject("last_notification_at", OffsetDateTime.class),
                rs.getObject("last_renewed_at", OffsetDateTime.class), rs.getString("last_error_category"));
    }
}

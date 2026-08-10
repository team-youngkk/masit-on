package com.masiton.ai.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.masiton.ai.application.port.out.YoutubeChannelWatchStore;

@Repository
public class JdbcYoutubeChannelWatchStore implements YoutubeChannelWatchStore {
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

    private Watch map(ResultSet rs, int rowNum) throws SQLException {
        return new Watch(rs.getString("youtube_channel_id"), rs.getBoolean("enabled"),
                rs.getString("subscription_status"), rs.getBytes("subscription_token_hash"));
    }
}

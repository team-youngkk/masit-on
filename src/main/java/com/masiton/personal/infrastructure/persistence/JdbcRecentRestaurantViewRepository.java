package com.masiton.personal.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.masiton.personal.application.port.out.RecentRestaurantViewRepository;

@Component
public class JdbcRecentRestaurantViewRepository implements RecentRestaurantViewRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcRecentRestaurantViewRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsert(UUID memberId, UUID restaurantId, Instant viewedAt) {
        jdbcTemplate.update("INSERT INTO recent_restaurant_view (member_id, restaurant_id, last_viewed_at) VALUES (?, ?, ?) "
                        + "ON CONFLICT (member_id, restaurant_id) DO UPDATE SET "
                        + "last_viewed_at = GREATEST(recent_restaurant_view.last_viewed_at, EXCLUDED.last_viewed_at)",
                memberId, restaurantId, Timestamp.from(viewedAt));
        jdbcTemplate.update("DELETE FROM recent_restaurant_view WHERE member_id = ? AND restaurant_id IN ("
                        + "SELECT restaurant_id FROM recent_restaurant_view WHERE member_id = ? "
                        + "ORDER BY last_viewed_at DESC, restaurant_id DESC OFFSET 50)", memberId, memberId);
    }
}

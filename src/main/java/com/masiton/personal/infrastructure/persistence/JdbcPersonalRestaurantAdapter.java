package com.masiton.personal.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.masiton.personal.application.port.out.PersonalRestaurantStore;

@Repository
public class JdbcPersonalRestaurantAdapter implements PersonalRestaurantStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPersonalRestaurantAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void addFavorite(UUID memberId, UUID restaurantId, OffsetDateTime favoritedAt) {
        jdbcTemplate.update("""
                INSERT INTO favorite (member_id, restaurant_id, favorited_at) VALUES (?, ?, ?)
                ON CONFLICT (member_id, restaurant_id) DO NOTHING
                """, memberId, restaurantId, favoritedAt);
    }

    @Override
    public void removeFavorite(UUID memberId, UUID restaurantId) {
        jdbcTemplate.update("DELETE FROM favorite WHERE member_id = ? AND restaurant_id = ?",
                memberId, restaurantId);
    }

    @Override
    public boolean existsFavorite(UUID memberId, UUID restaurantId) {
        Boolean result = jdbcTemplate.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM favorite WHERE member_id = ? AND restaurant_id = ?)
                """, Boolean.class, memberId, restaurantId);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public void upsertRecentRestaurant(UUID memberId, UUID restaurantId, OffsetDateTime viewedAt) {
        jdbcTemplate.update("""
                INSERT INTO recent_restaurant_view (member_id, restaurant_id, last_viewed_at)
                VALUES (?, ?, ?)
                ON CONFLICT (member_id, restaurant_id) DO UPDATE
                SET last_viewed_at = GREATEST(recent_restaurant_view.last_viewed_at, EXCLUDED.last_viewed_at)
                """, memberId, restaurantId, viewedAt);
    }

    @Override
    public void pruneRecentRestaurantOverflow(UUID memberId, int limit) {
        jdbcTemplate.update("""
                DELETE FROM recent_restaurant_view target WHERE target.member_id = ?
                 AND target.restaurant_id IN (
                     SELECT restaurant_id FROM recent_restaurant_view WHERE member_id = ?
                     ORDER BY last_viewed_at DESC, restaurant_id OFFSET ?)
                """, memberId, memberId, limit);
    }

    @Override
    public int deleteRecentRestaurantViewsBefore(OffsetDateTime cutoff) {
        return jdbcTemplate.update(
                "DELETE FROM recent_restaurant_view WHERE last_viewed_at < ?", cutoff);
    }

    @Override
    public void removeRecentRestaurant(UUID memberId, UUID restaurantId) {
        jdbcTemplate.update("DELETE FROM recent_restaurant_view WHERE member_id = ? AND restaurant_id = ?",
                memberId, restaurantId);
    }
}

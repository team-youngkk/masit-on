package com.masiton.orchestration.infrastructure.query;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.masiton.personal.application.port.in.PersonalRestaurantItem;
import com.masiton.personal.application.port.in.PersonalRestaurantPage;
import com.masiton.personal.application.port.out.PersonalRestaurantQueryPort;

@Component
class PersonalRestaurantQueryAdapter implements PersonalRestaurantQueryPort {

    private final JdbcTemplate jdbcTemplate;

    PersonalRestaurantQueryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PersonalRestaurantPage findFavorites(UUID memberId, int page, int size) {
        long offset = ((long) page - 1) * size;
        List<PersonalRestaurantItem> items = jdbcTemplate.query("""
                SELECT relation.restaurant_id, r.name, region.name AS district,
                       category.name AS category, relation.favorited_at AS occurred_at
                  FROM favorite relation
                  JOIN restaurant r ON r.id = relation.restaurant_id
                  JOIN region ON region.id = r.region_id
                  JOIN food_category category ON category.id = r.food_category_id
                 WHERE relation.member_id = ?
                   AND r.publication_status = 'PUBLIC' AND r.lifecycle_status = 'ACTIVE'
                 ORDER BY relation.favorited_at DESC, relation.restaurant_id
                 LIMIT ? OFFSET ?
                """, this::mapItem, memberId, size, offset);
        long total = countPublic("favorite", memberId, null);
        return page(items, page, size, total);
    }

    @Override
    public PersonalRestaurantPage findRecentRestaurants(
            UUID memberId, OffsetDateTime cutoff, int retentionLimit, int page, int size
    ) {
        long offset = ((long) page - 1) * size;
        List<PersonalRestaurantItem> items = jdbcTemplate.query("""
                WITH retained AS (
                    SELECT restaurant_id, last_viewed_at
                      FROM recent_restaurant_view
                     WHERE member_id = ? AND last_viewed_at >= ?
                     ORDER BY last_viewed_at DESC, restaurant_id ASC
                     LIMIT ?
                )
                SELECT relation.restaurant_id, r.name, region.name AS district,
                       category.name AS category, relation.last_viewed_at AS occurred_at
                  FROM retained relation
                  JOIN restaurant r ON r.id = relation.restaurant_id
                  JOIN region ON region.id = r.region_id
                  JOIN food_category category ON category.id = r.food_category_id
                 WHERE r.publication_status = 'PUBLIC' AND r.lifecycle_status = 'ACTIVE'
                 ORDER BY relation.last_viewed_at DESC, relation.restaurant_id ASC
                 LIMIT ? OFFSET ?
                """, this::mapItem, memberId, cutoff, retentionLimit, size, offset);
        long total = countVisibleRecent(memberId, cutoff, retentionLimit);
        return page(items, page, size, total);
    }

    private long countPublic(String table, UUID memberId, OffsetDateTime cutoff) {
        String cutoffClause = cutoff == null ? "" : " AND relation.last_viewed_at >= ?";
        String sql = "SELECT count(*) FROM " + table + " relation JOIN restaurant r"
                + " ON r.id = relation.restaurant_id WHERE relation.member_id = ?"
                + cutoffClause
                + " AND r.publication_status = 'PUBLIC' AND r.lifecycle_status = 'ACTIVE'";
        Long value = cutoff == null
                ? jdbcTemplate.queryForObject(sql, Long.class, memberId)
                : jdbcTemplate.queryForObject(sql, Long.class, memberId, cutoff);
        return value == null ? 0 : value;
    }

    private long countVisibleRecent(UUID memberId, OffsetDateTime cutoff, int retentionLimit) {
        Long value = jdbcTemplate.queryForObject("""
                WITH retained AS (
                    SELECT restaurant_id
                      FROM recent_restaurant_view
                     WHERE member_id = ? AND last_viewed_at >= ?
                     ORDER BY last_viewed_at DESC, restaurant_id ASC
                     LIMIT ?
                )
                SELECT count(*)
                  FROM retained relation
                  JOIN restaurant r ON r.id = relation.restaurant_id
                 WHERE r.publication_status = 'PUBLIC' AND r.lifecycle_status = 'ACTIVE'
                """, Long.class, memberId, cutoff, retentionLimit);
        return value == null ? 0 : value;
    }

    private PersonalRestaurantPage page(List<PersonalRestaurantItem> items, int number, int size, long total) {
        int pages = total == 0 ? 0 : (int) ((total + size - 1) / size);
        return new PersonalRestaurantPage(items, number, size, total, pages, number < pages);
    }

    private PersonalRestaurantItem mapItem(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PersonalRestaurantItem(
                resultSet.getObject("restaurant_id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("district"),
                resultSet.getString("category"),
                resultSet.getObject("occurred_at", OffsetDateTime.class));
    }
}

package com.masiton.curation.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.masiton.curation.application.port.in.AdminCurationUseCase.CurationSummary;
import com.masiton.curation.application.port.out.CurationStore;
import com.masiton.curation.domain.model.CurationStatus;

@Repository
public class JdbcCurationStore implements CurationStore {

    private static final String COLUMNS = "id, title, description, publication_status, main_position, "
            + "created_by, updated_by, published_at, created_at, updated_at";
    private final JdbcTemplate jdbcTemplate;

    public JdbcCurationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void create(UUID id, String title, String description, UUID adminId, OffsetDateTime now) {
        jdbcTemplate.update("INSERT INTO curation (id, title, description, publication_status, created_by, "
                        + "updated_by, created_at, updated_at) VALUES (?, ?, ?, 'DRAFT', ?, ?, ?, ?)",
                id, title, description, adminId, adminId, now, now);
    }

    @Override
    public Optional<StoredCuration> find(UUID id, boolean lock) {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM curation WHERE id = ?"
                + (lock ? " FOR UPDATE" : ""), this::curation, id).stream().findFirst();
    }

    @Override
    public List<CurationSummary> findPage(CurationStatus status, int limit, long offset) {
        List<Object> args = new ArrayList<>();
        String where = "";
        if (status != null) {
            where = " WHERE publication_status = ?";
            args.add(status.name());
        }
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query("SELECT " + COLUMNS
                        + ", (SELECT count(*) FROM curation_restaurant cr WHERE cr.curation_id = curation.id)"
                        + " AS restaurant_count"
                        + ", EXISTS (SELECT 1 FROM curation_restaurant cr"
                        + " JOIN restaurant r ON r.id = cr.restaurant_id"
                        + " WHERE cr.curation_id = curation.id"
                        + " AND (r.publication_status <> 'PUBLIC' OR r.lifecycle_status <> 'ACTIVE'))"
                        + " AS has_hidden_restaurants"
                        + " FROM curation" + where
                        + " ORDER BY updated_at DESC, id ASC LIMIT ? OFFSET ?",
                (rs, row) -> summary(curation(rs, row), rs.getInt("restaurant_count"),
                        rs.getBoolean("has_hidden_restaurants")), args.toArray());
    }

    @Override
    public long count(CurationStatus status) {
        Long count = status == null
                ? jdbcTemplate.queryForObject("SELECT count(*) FROM curation", Long.class)
                : jdbcTemplate.queryForObject("SELECT count(*) FROM curation WHERE publication_status = ?",
                        Long.class, status.name());
        return count == null ? 0 : count;
    }

    @Override
    public List<StoredRestaurant> findRestaurants(UUID curationId) {
        return jdbcTemplate.query("SELECT restaurant_id, position FROM curation_restaurant "
                        + "WHERE curation_id = ? ORDER BY position ASC",
                (rs, row) -> new StoredRestaurant(rs.getObject("restaurant_id", UUID.class),
                        rs.getInt("position")), curationId);
    }

    @Override
    public void updateContent(UUID id, String title, String description, UUID adminId, OffsetDateTime now) {
        jdbcTemplate.update("UPDATE curation SET title = ?, description = ?, updated_by = ?, updated_at = ? "
                + "WHERE id = ?", title, description, adminId, now, id);
    }

    @Override
    public void replaceRestaurants(UUID id, List<UUID> restaurantIds, UUID adminId, OffsetDateTime now) {
        jdbcTemplate.update("DELETE FROM curation_restaurant WHERE curation_id = ?", id);
        for (int index = 0; index < restaurantIds.size(); index++) {
            jdbcTemplate.update("INSERT INTO curation_restaurant (curation_id, restaurant_id, position, added_at) "
                    + "VALUES (?, ?, ?, ?)", id, restaurantIds.get(index), index + 1, now);
        }
        jdbcTemplate.update("UPDATE curation SET updated_by = ?, updated_at = ? WHERE id = ?", adminId, now, id);
    }

    @Override
    public void lockMainOrder() {
        jdbcTemplate.queryForObject("SELECT pg_advisory_xact_lock(?)", Object.class, 0x4355524154494F4EL);
    }

    @Override
    public List<StoredCuration> lockPublished() {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM curation WHERE publication_status = 'PUBLISHED' "
                + "ORDER BY id ASC FOR UPDATE", this::curation);
    }

    @Override
    public void publish(UUID id, int position, UUID adminId, OffsetDateTime now) {
        jdbcTemplate.update("UPDATE curation SET publication_status = 'PUBLISHED', main_position = ?, "
                + "published_at = ?, updated_by = ?, updated_at = ? WHERE id = ?",
                position, now, adminId, now, id);
    }

    @Override
    public void unpublish(UUID id, int oldPosition, UUID adminId, OffsetDateTime now) {
        deferMainPositionConstraint();
        jdbcTemplate.update("UPDATE curation SET publication_status = 'DRAFT', main_position = NULL, "
                + "updated_by = ?, updated_at = ? WHERE id = ?", adminId, now, id);
        jdbcTemplate.update("UPDATE curation SET main_position = main_position - 1, updated_by = ?, updated_at = ? "
                + "WHERE publication_status = 'PUBLISHED' AND main_position > ?", adminId, now, oldPosition);
    }

    @Override
    public void replaceMainOrder(List<UUID> orderedIds, UUID adminId, OffsetDateTime now) {
        deferMainPositionConstraint();
        for (int index = 0; index < orderedIds.size(); index++) {
            jdbcTemplate.update("UPDATE curation SET main_position = ?, updated_by = ?, updated_at = ? WHERE id = ?",
                    index + 1, adminId, now, orderedIds.get(index));
        }
    }

    private void deferMainPositionConstraint() {
        jdbcTemplate.execute("SET CONSTRAINTS uq_curation__status_main_position DEFERRED");
    }

    private StoredCuration curation(ResultSet rs, int row) throws SQLException {
        Object rawPosition = rs.getObject("main_position");
        return new StoredCuration(rs.getObject("id", UUID.class), rs.getString("title"),
                rs.getString("description"), CurationStatus.valueOf(rs.getString("publication_status")),
                rawPosition == null ? null : rs.getInt("main_position"), rs.getObject("created_by", UUID.class),
                rs.getObject("updated_by", UUID.class), rs.getObject("published_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
    }

    private CurationSummary summary(StoredCuration value, int restaurantCount, boolean hasHiddenRestaurants) {
        return new CurationSummary(value.id(), value.title(), value.description(), value.status(),
                value.mainPosition(), restaurantCount, hasHiddenRestaurants,
                value.publishedAt(), value.createdAt(), value.updatedAt());
    }
}

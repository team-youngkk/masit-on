package com.masiton.personal.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.personal.application.port.in.PersonalCollectionUseCase.CollectionDetail;
import com.masiton.personal.application.port.in.PersonalCollectionUseCase.CollectionRestaurant;
import com.masiton.personal.application.port.in.PersonalCollectionUseCase.CollectionSummary;
import com.masiton.personal.application.port.in.PersonalCollectionUseCase.RestaurantItem;
import com.masiton.personal.application.port.out.PersonalCollectionStore;

@Repository
public class JdbcPersonalCollectionAdapter implements PersonalCollectionStore {

    private static final int COLLECTION_LIMIT = 20;
    private static final int RESTAURANT_LIMIT = 100;

    private final JdbcTemplate jdbcTemplate;

    public JdbcPersonalCollectionAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public CollectionSummary create(UUID memberId, UUID collectionId, String name, OffsetDateTime now) {
        boolean memberExists = !jdbcTemplate.query(
                "SELECT id FROM member_account WHERE id = ? FOR UPDATE",
                (rs, row) -> rs.getObject("id", UUID.class), memberId).isEmpty();
        if (!memberExists) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM personal_collection WHERE member_id = ?", Long.class, memberId);
        if (count != null && count >= COLLECTION_LIMIT) {
            throw new BusinessException(HttpStatus.CONFLICT, "COLLECTION_LIMIT_EXCEEDED",
                    "컬렉션은 최대 20개까지 만들 수 있습니다.");
        }
        jdbcTemplate.update("""
                INSERT INTO personal_collection (id, member_id, name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """, collectionId, memberId, name, now, now);
        return new CollectionSummary(collectionId, name, 0, now, now);
    }

    @Override
    public List<CollectionSummary> findAll(UUID memberId) {
        return jdbcTemplate.query("""
                SELECT pc.id, pc.name, pc.created_at, pc.updated_at,
                       count(r.id) AS restaurant_count
                  FROM personal_collection pc
                  LEFT JOIN collection_restaurant cr ON cr.collection_id = pc.id
                  LEFT JOIN restaurant r ON r.id = cr.restaurant_id
                    AND r.publication_status = 'PUBLIC' AND r.lifecycle_status = 'ACTIVE'
                 WHERE pc.member_id = ?
                 GROUP BY pc.id, pc.name, pc.created_at, pc.updated_at
                 ORDER BY pc.updated_at DESC, pc.id ASC
                 LIMIT 20
                """, this::summary, memberId);
    }

    @Override
    public Optional<CollectionDetail> findDetail(UUID memberId, UUID collectionId, int page, int size) {
        List<CollectionHeader> headers = jdbcTemplate.query("""
                SELECT pc.id, pc.name, pc.updated_at, count(r.id) AS restaurant_count
                  FROM personal_collection pc
                  LEFT JOIN collection_restaurant cr ON cr.collection_id = pc.id
                  LEFT JOIN restaurant r ON r.id = cr.restaurant_id
                    AND r.publication_status = 'PUBLIC' AND r.lifecycle_status = 'ACTIVE'
                 WHERE pc.id = ? AND pc.member_id = ?
                 GROUP BY pc.id, pc.name, pc.updated_at
                """, (rs, row) -> new CollectionHeader(rs.getObject("id", UUID.class),
                        rs.getString("name"), rs.getObject("updated_at", OffsetDateTime.class),
                        rs.getLong("restaurant_count")), collectionId, memberId);
        if (headers.isEmpty()) {
            return Optional.empty();
        }
        CollectionHeader header = headers.getFirst();
        List<RestaurantItem> items = jdbcTemplate.query("""
                SELECT r.id, r.name, r.road_address, cr.added_at
                  FROM collection_restaurant cr JOIN restaurant r ON r.id = cr.restaurant_id
                 WHERE cr.collection_id = ?
                   AND r.publication_status = 'PUBLIC' AND r.lifecycle_status = 'ACTIVE'
                 ORDER BY cr.added_at DESC, r.id ASC
                 LIMIT ? OFFSET ?
                """, (rs, row) -> new RestaurantItem(rs.getObject("id", UUID.class),
                        rs.getString("name"), rs.getString("road_address"),
                        rs.getObject("added_at", OffsetDateTime.class)),
                collectionId, size, (long) (page - 1) * size);
        long total = header.restaurantCount();
        int totalPages = total == 0 ? 0 : (int) ((total + size - 1) / size);
        return Optional.of(new CollectionDetail(header.id(), header.name(), total,
                header.updatedAt(), items, page, size, total, totalPages, page < totalPages));
    }

    @Override
    public Optional<CollectionSummary> rename(
            UUID memberId, UUID collectionId, String name, OffsetDateTime now) {
        List<CollectionSummary> current = lockedSummary(memberId, collectionId);
        if (current.isEmpty()) {
            return Optional.empty();
        }
        CollectionSummary value = current.getFirst();
        if (value.name().equals(name)) {
            return Optional.of(value);
        }
        jdbcTemplate.update("UPDATE personal_collection SET name = ?, updated_at = ? WHERE id = ?",
                name, now, collectionId);
        return Optional.of(new CollectionSummary(collectionId, name, value.restaurantCount(),
                value.createdAt(), now));
    }

    @Override
    public void delete(UUID memberId, UUID collectionId) {
        jdbcTemplate.update("DELETE FROM personal_collection WHERE id = ? AND member_id = ?",
                collectionId, memberId);
    }

    @Override
    public Optional<CollectionRestaurant> findRestaurant(
            UUID memberId, UUID collectionId, UUID restaurantId) {
        return jdbcTemplate.query("""
                SELECT cr.collection_id, cr.restaurant_id, cr.added_at
                  FROM collection_restaurant cr JOIN personal_collection pc ON pc.id = cr.collection_id
                 WHERE cr.collection_id = ? AND pc.member_id = ? AND cr.restaurant_id = ?
                """, this::relation, collectionId, memberId, restaurantId).stream().findFirst();
    }

    @Override
    public Optional<CollectionRestaurant> addRestaurant(
            UUID memberId, UUID collectionId, UUID restaurantId, OffsetDateTime now) {
        if (!lockOwnedCollection(memberId, collectionId)) {
            return Optional.empty();
        }
        Optional<CollectionRestaurant> existing = findRestaurant(memberId, collectionId, restaurantId);
        if (existing.isPresent()) {
            return existing;
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM collection_restaurant WHERE collection_id = ?",
                Long.class, collectionId);
        if (count != null && count >= RESTAURANT_LIMIT) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "COLLECTION_RESTAURANT_LIMIT_EXCEEDED",
                    "한 컬렉션에는 맛집을 최대 100개까지 담을 수 있습니다.");
        }
        int inserted = jdbcTemplate.update("""
                INSERT INTO collection_restaurant (collection_id, restaurant_id, added_at)
                VALUES (?, ?, ?) ON CONFLICT (collection_id, restaurant_id) DO NOTHING
                """, collectionId, restaurantId, now);
        Optional<CollectionRestaurant> result = findRestaurant(memberId, collectionId, restaurantId);
        if (inserted > 0) {
            jdbcTemplate.update("UPDATE personal_collection SET updated_at = ? WHERE id = ?", now, collectionId);
        }
        return result;
    }

    @Override
    public void removeRestaurant(
            UUID memberId, UUID collectionId, UUID restaurantId, OffsetDateTime now) {
        if (!lockOwnedCollection(memberId, collectionId)) {
            return;
        }
        int removed = jdbcTemplate.update(
                "DELETE FROM collection_restaurant WHERE collection_id = ? AND restaurant_id = ?",
                collectionId, restaurantId);
        if (removed > 0) {
            jdbcTemplate.update("UPDATE personal_collection SET updated_at = ? WHERE id = ?", now, collectionId);
        }
    }

    private List<CollectionSummary> lockedSummary(UUID memberId, UUID collectionId) {
        return jdbcTemplate.query("""
                SELECT pc.id, pc.name, pc.created_at, pc.updated_at,
                       (SELECT count(*) FROM collection_restaurant cr JOIN restaurant r
                          ON r.id = cr.restaurant_id
                         AND r.publication_status = 'PUBLIC' AND r.lifecycle_status = 'ACTIVE'
                         WHERE cr.collection_id = pc.id) AS restaurant_count
                  FROM personal_collection pc WHERE pc.id = ? AND pc.member_id = ? FOR UPDATE
                """, this::summary, collectionId, memberId);
    }

    private boolean lockOwnedCollection(UUID memberId, UUID collectionId) {
        return !jdbcTemplate.query("""
                SELECT id FROM personal_collection WHERE id = ? AND member_id = ? FOR UPDATE
                """, (rs, row) -> rs.getObject("id", UUID.class), collectionId, memberId).isEmpty();
    }

    private CollectionSummary summary(ResultSet rs, int row) throws SQLException {
        return new CollectionSummary(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getLong("restaurant_count"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }

    private CollectionRestaurant relation(ResultSet rs, int row) throws SQLException {
        return new CollectionRestaurant(rs.getObject("collection_id", UUID.class),
                rs.getObject("restaurant_id", UUID.class),
                rs.getObject("added_at", OffsetDateTime.class));
    }

    private record CollectionHeader(UUID id, String name, OffsetDateTime updatedAt,
                                    long restaurantCount) {
    }
}

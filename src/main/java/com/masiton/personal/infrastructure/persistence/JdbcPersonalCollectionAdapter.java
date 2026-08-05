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
import com.masiton.personal.application.port.in.PersonalCollectionUseCase.CollectionRestaurant;
import com.masiton.personal.application.port.in.PersonalCollectionUseCase.CollectionSummary;
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
    public boolean rename(
            UUID memberId, UUID collectionId, String name, OffsetDateTime now) {
        List<CollectionMetadata> current = lockedMetadata(memberId, collectionId);
        if (current.isEmpty()) {
            return false;
        }
        CollectionMetadata value = current.getFirst();
        if (value.name().equals(name)) {
            return true;
        }
        jdbcTemplate.update("UPDATE personal_collection SET name = ?, updated_at = ? WHERE id = ?",
                name, now, collectionId);
        return true;
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

    private List<CollectionMetadata> lockedMetadata(UUID memberId, UUID collectionId) {
        return jdbcTemplate.query("""
                SELECT id, name
                  FROM personal_collection
                 WHERE id = ? AND member_id = ?
                 FOR UPDATE
                """, (rs, row) -> new CollectionMetadata(
                        rs.getObject("id", UUID.class), rs.getString("name")), collectionId, memberId);
    }

    private boolean lockOwnedCollection(UUID memberId, UUID collectionId) {
        return !jdbcTemplate.query("""
                SELECT id FROM personal_collection WHERE id = ? AND member_id = ? FOR UPDATE
                """, (rs, row) -> rs.getObject("id", UUID.class), collectionId, memberId).isEmpty();
    }

    private CollectionRestaurant relation(ResultSet rs, int row) throws SQLException {
        return new CollectionRestaurant(rs.getObject("collection_id", UUID.class),
                rs.getObject("restaurant_id", UUID.class),
                rs.getObject("added_at", OffsetDateTime.class));
    }

    private record CollectionMetadata(UUID id, String name) {
    }
}

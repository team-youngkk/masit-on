package com.masiton.orchestration.infrastructure.query;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.masiton.personal.application.port.in.CollectionOption;
import com.masiton.personal.application.port.in.CollectionOption.AdditionStatus;
import com.masiton.personal.application.port.in.PersonalCollectionUseCase.CollectionDetail;
import com.masiton.personal.application.port.in.PersonalCollectionUseCase.CollectionSummary;
import com.masiton.personal.application.port.in.PersonalCollectionUseCase.RestaurantItem;
import com.masiton.personal.application.port.out.PersonalCollectionQueryPort;

@Component
class PersonalCollectionQueryAdapter implements PersonalCollectionQueryPort {

    private static final int RESTAURANT_LIMIT = 100;

    private final JdbcTemplate jdbcTemplate;

    PersonalCollectionQueryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
    public List<CollectionOption> findOptions(UUID memberId, UUID restaurantId) {
        return jdbcTemplate.query("""
                SELECT pc.id, pc.name,
                       count(r.id) AS public_restaurant_count,
                       count(cr.restaurant_id) AS actual_restaurant_count,
                       count(*) FILTER (WHERE cr.restaurant_id = ?) > 0 AS already_included
                  FROM personal_collection pc
                  LEFT JOIN collection_restaurant cr ON cr.collection_id = pc.id
                  LEFT JOIN restaurant r ON r.id = cr.restaurant_id
                    AND r.publication_status = 'PUBLIC' AND r.lifecycle_status = 'ACTIVE'
                 WHERE pc.member_id = ?
                 GROUP BY pc.id, pc.name, pc.updated_at
                 ORDER BY pc.updated_at DESC, pc.id ASC
                 LIMIT 20
                """, (rs, row) -> new CollectionOption(
                        rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getLong("public_restaurant_count"), additionStatus(
                                rs.getBoolean("already_included"),
                                rs.getLong("actual_restaurant_count"))),
                restaurantId, memberId);
    }

    @Override
    public Optional<CollectionSummary> findSummary(UUID memberId, UUID collectionId) {
        return jdbcTemplate.query("""
                SELECT pc.id, pc.name, pc.created_at, pc.updated_at,
                       count(r.id) AS restaurant_count
                  FROM personal_collection pc
                  LEFT JOIN collection_restaurant cr ON cr.collection_id = pc.id
                  LEFT JOIN restaurant r ON r.id = cr.restaurant_id
                    AND r.publication_status = 'PUBLIC' AND r.lifecycle_status = 'ACTIVE'
                 WHERE pc.id = ? AND pc.member_id = ?
                 GROUP BY pc.id, pc.name, pc.created_at, pc.updated_at
                """, this::summary, collectionId, memberId).stream().findFirst();
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

    private CollectionSummary summary(ResultSet rs, int row) throws SQLException {
        return new CollectionSummary(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getLong("restaurant_count"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }

    private AdditionStatus additionStatus(boolean alreadyIncluded, long actualRestaurantCount) {
        if (alreadyIncluded) {
            return AdditionStatus.ALREADY_INCLUDED;
        }
        if (actualRestaurantCount >= RESTAURANT_LIMIT) {
            return AdditionStatus.LIMIT_REACHED;
        }
        return AdditionStatus.AVAILABLE;
    }

    private record CollectionHeader(UUID id, String name, OffsetDateTime updatedAt,
                                    long restaurantCount) {
    }
}

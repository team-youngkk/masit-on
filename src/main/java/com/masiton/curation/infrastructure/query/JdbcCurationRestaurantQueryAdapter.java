package com.masiton.curation.infrastructure.query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.masiton.curation.application.port.out.CurationRestaurantQueryPort;

@Repository
public class JdbcCurationRestaurantQueryAdapter implements CurationRestaurantQueryPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcCurationRestaurantQueryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<RestaurantProjection> findAll(Collection<UUID> restaurantIds) {
        if (restaurantIds.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(restaurantIds.size(), "?"));
        return jdbcTemplate.query("SELECT id, name, publication_status, lifecycle_status FROM restaurant WHERE id IN ("
                        + placeholders + ")",
                (rs, row) -> new RestaurantProjection(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("publication_status"), rs.getString("lifecycle_status")),
                restaurantIds.toArray());
    }
}

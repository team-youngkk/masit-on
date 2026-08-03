package com.masiton.restaurant.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.masiton.restaurant.application.port.out.RestaurantMapPointRow;
import com.masiton.restaurant.application.port.out.RestaurantMapPointsCriteria;
import com.masiton.restaurant.application.port.out.RestaurantMapPointsQueryPort;

/**
 * RestaurantMapPointsQueryPort의 구현체다.
 * dependency-rules.md 7절 읽기 모델 예외에 따라 자기 소유 테이블(restaurant, food_category)만 조회한다.
 */
@Component
class RestaurantMapQueryAdapter implements RestaurantMapPointsQueryPort {

    private static final String BASE_CONDITION =
            "r.publication_status = 'PUBLIC' AND r.lifecycle_status = 'ACTIVE' "
                    + "AND r.latitude IS NOT NULL AND r.longitude IS NOT NULL";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    RestaurantMapQueryAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<RestaurantMapPointRow> findMatching(RestaurantMapPointsCriteria criteria, int fetchLimit) {
        StringBuilder where = new StringBuilder(BASE_CONDITION);
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (criteria.normalizedQuery() != null) {
            where.append(" AND lower(r.name) LIKE lower(:query) ESCAPE '\\'");
            params.addValue("query", "%" + escapeLikeWildcards(criteria.normalizedQuery()) + "%");
        }
        if (criteria.regionId() != null) {
            where.append(" AND r.region_id = :regionId");
            params.addValue("regionId", criteria.regionId());
        }
        if (criteria.foodCategoryId() != null) {
            where.append(" AND r.food_category_id = :foodCategoryId");
            params.addValue("foodCategoryId", criteria.foodCategoryId());
        }
        if (criteria.candidateRestaurantIds() != null) {
            if (criteria.candidateRestaurantIds().isEmpty()) {
                where.append(" AND 1 = 0");
            } else {
                where.append(" AND r.id IN (:candidateRestaurantIds)");
                params.addValue("candidateRestaurantIds", criteria.candidateRestaurantIds());
            }
        }

        params.addValue("fetchLimit", fetchLimit);

        return jdbcTemplate.query(
                "SELECT r.id AS id, r.name AS name, fc.name AS category, r.road_address AS address_summary, "
                        + "r.latitude AS latitude, r.longitude AS longitude "
                        + "FROM restaurant r "
                        + "JOIN food_category fc ON fc.id = r.food_category_id "
                        + "WHERE " + where
                        + " ORDER BY r.name COLLATE \"C\", r.id "
                        + "LIMIT :fetchLimit",
                params,
                (resultSet, rowNumber) -> new RestaurantMapPointRow(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("name"),
                        resultSet.getString("category"),
                        resultSet.getString("address_summary"),
                        resultSet.getBigDecimal("latitude"),
                        resultSet.getBigDecimal("longitude")));
    }

    /** LIKE 와일드카드(%, _)와 이스케이프 문자 자체를 리터럴로 취급하도록 이스케이프한다. */
    private static String escapeLikeWildcards(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

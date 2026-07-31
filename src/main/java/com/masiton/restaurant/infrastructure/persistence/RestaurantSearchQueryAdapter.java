package com.masiton.restaurant.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.masiton.restaurant.application.port.out.RestaurantSearchCriteria;
import com.masiton.restaurant.application.port.out.RestaurantSearchQueryPort;
import com.masiton.restaurant.application.port.out.RestaurantSearchQueryResult;
import com.masiton.restaurant.application.port.out.RestaurantSearchRow;
import com.masiton.restaurant.application.port.out.VisitedByRow;

/**
 * RestaurantSearchQueryPort의 구현체다.
 * dependency-rules.md 7절 읽기 모델 예외에 따라 자기 소유 테이블(restaurant, region, food_category)과
 * 다른 도메인 소유 테이블(visit, creator, video)을 읽기 전용 네이티브 SQL로 조회한다. 테이블은
 * 이름 문자열로만 참조하며 다른 도메인의 JPA Entity·Spring Data Repository는 import하지 않는다.
 */
@Component
class RestaurantSearchQueryAdapter implements RestaurantSearchQueryPort {

    private static final String BASE_CONDITION =
            "r.publication_status = 'PUBLIC' AND r.lifecycle_status = 'ACTIVE'";

    /**
     * PostgreSQL은 SELECT DISTINCT의 ORDER BY 표현식이 SELECT 목록과 문자 그대로 일치해야 한다.
     * COLLATE "C" 정렬은 DISTINCT와 같은 SELECT 절에 둘 수 없어 중복 제거를 내부 질의로 분리한다.
     */
    private static final String VISITED_BY_QUERY = """
            SELECT restaurant_id, creator_id, channel_name
            FROM (
                SELECT DISTINCT v.restaurant_id AS restaurant_id, c.id AS creator_id, c.channel_name AS channel_name
                FROM visit v
                JOIN creator c ON c.id = v.creator_id
                JOIN video vi ON vi.id = v.video_id
                WHERE v.restaurant_id IN (:restaurantIds)
                  AND v.publication_status = 'PUBLIC' AND v.lifecycle_status = 'ACTIVE'
                  AND c.publication_status = 'PUBLIC' AND c.lifecycle_status = 'ACTIVE'
                  AND c.external_availability_status = 'AVAILABLE'
                  AND vi.publication_status = 'PUBLIC' AND vi.lifecycle_status = 'ACTIVE'
                  AND vi.external_availability_status = 'AVAILABLE'
            ) distinct_visited_by
            ORDER BY channel_name COLLATE "C", creator_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    RestaurantSearchQueryAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public RestaurantSearchQueryResult search(RestaurantSearchCriteria criteria) {
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

        Long totalElements = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM restaurant r WHERE " + where, params, Long.class);

        params.addValue("limit", criteria.size());
        params.addValue("offset", (long) (criteria.page() - 1) * criteria.size());

        List<RestaurantSearchRow> rows = jdbcTemplate.query(
                "SELECT r.id AS id, r.name AS name, reg.name AS district, fc.name AS category "
                        + "FROM restaurant r "
                        + "JOIN region reg ON reg.id = r.region_id "
                        + "JOIN food_category fc ON fc.id = r.food_category_id "
                        + "WHERE " + where
                        + " ORDER BY r.name COLLATE \"C\", r.road_address COLLATE \"C\", r.id "
                        + "LIMIT :limit OFFSET :offset",
                params,
                (resultSet, rowNumber) -> new RestaurantSearchRow(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("name"),
                        resultSet.getString("district"),
                        resultSet.getString("category")));

        return new RestaurantSearchQueryResult(rows, totalElements == null ? 0L : totalElements);
    }

    @Override
    public List<VisitedByRow> findVisitedByRestaurantIds(List<UUID> restaurantIds) {
        if (restaurantIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource("restaurantIds", restaurantIds);
        return jdbcTemplate.query(
                VISITED_BY_QUERY,
                params,
                (resultSet, rowNumber) -> new VisitedByRow(
                        resultSet.getObject("restaurant_id", UUID.class),
                        resultSet.getObject("creator_id", UUID.class),
                        resultSet.getString("channel_name")));
    }

    /** LIKE 와일드카드(%, _)와 이스케이프 문자 자체를 리터럴로 취급하도록 이스케이프한다. */
    private static String escapeLikeWildcards(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

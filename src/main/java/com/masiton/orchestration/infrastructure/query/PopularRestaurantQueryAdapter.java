package com.masiton.orchestration.infrastructure.query;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.masiton.restaurant.application.port.out.PopularRestaurantQueryPort;
import com.masiton.restaurant.application.port.out.PopularRestaurantRow;

/**
 * PopularRestaurantQueryPort의 구현체다.
 * WS-06 소유 `favorite`와 WS-01 소유 `restaurant`를 함께 읽으므로 dependency-rules.md 7절 읽기 모델
 * 예외에 따라 orchestration에 두고 Projection 전용 SQL만 수행한다. Favorite 원본은 변경하지 않는다.
 */
@Component
class PopularRestaurantQueryAdapter implements PopularRestaurantQueryPort {

    /**
     * `favorite`의 PK가 (member_id, restaurant_id)이므로 맛집별 `count(*)`가 곧 서로 다른 회원의 현재 찜 수다.
     * INNER JOIN이 찜 1건 이상 조건을, `restaurant` 상태 조건이 공개 판정을 담당한다.
     * ADR-DATA-011 8절이 요구하는 실행계획 회귀 테스트가 실제 실행 SQL을 EXPLAIN하도록 상수로 노출한다.
     */
    static final String AGGREGATION_SQL = """
            SELECT r.id AS restaurant_id, r.name AS name, r.road_address AS road_address,
                   category.name AS category, count(*) AS favorite_count
              FROM favorite relation
              JOIN restaurant r ON r.id = relation.restaurant_id
              JOIN food_category category ON category.id = r.food_category_id
             WHERE r.publication_status = 'PUBLIC' AND r.lifecycle_status = 'ACTIVE'
             GROUP BY r.id, r.name, r.road_address, category.name
             ORDER BY count(*) DESC, r.id ASC
             LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    PopularRestaurantQueryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<PopularRestaurantRow> findTopByFavoriteCount(int limit) {
        return jdbcTemplate.query(AGGREGATION_SQL, this::mapRow, limit);
    }

    private PopularRestaurantRow mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PopularRestaurantRow(
                resultSet.getObject("restaurant_id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("road_address"),
                resultSet.getString("category"),
                resultSet.getLong("favorite_count"));
    }
}

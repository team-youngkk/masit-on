package com.masiton.orchestration.infrastructure.query;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.masiton.orchestration.application.query.RestaurantDetailBase;
import com.masiton.orchestration.application.query.RestaurantDetailBaseQueryPort;

/**
 * {@link RestaurantDetailBaseQueryPort}의 구현체다. restaurant·food_category를 읽기 전용
 * Projection으로 한 번에 조회하며 쓰기·Entity 상태 변경을 하지 않는다. 다른 도메인의 JPA Entity나
 * Spring Data Repository를 재사용하지 않고 raw SQL로 직접 매핑한다.
 */
@Component
class RestaurantDetailBaseQueryAdapter implements RestaurantDetailBaseQueryPort {

    private static final String SELECT_PUBLIC_DETAIL_SQL = """
            SELECT r.id AS id,
                   r.name AS name,
                   fc.name AS category_name,
                   r.road_address AS road_address,
                   r.detail_address AS detail_address,
                   r.phone_number AS phone_number,
                   r.kakao_place_url AS kakao_place_url
            FROM restaurant r
            JOIN food_category fc ON fc.id = r.food_category_id
            WHERE r.id = :restaurantId
              AND r.publication_status = 'PUBLIC'
              AND r.lifecycle_status = 'ACTIVE'
            """;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    RestaurantDetailBaseQueryAdapter(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    @Override
    public Optional<RestaurantDetailBase> findPublicDetailById(UUID restaurantId) {
        List<RestaurantDetailBase> rows = namedParameterJdbcTemplate.query(
                SELECT_PUBLIC_DETAIL_SQL,
                Map.of("restaurantId", restaurantId),
                (resultSet, rowNumber) -> new RestaurantDetailBase(
                        (UUID) resultSet.getObject("id"),
                        resultSet.getString("name"),
                        resultSet.getString("category_name"),
                        resultSet.getString("road_address"),
                        resultSet.getString("detail_address"),
                        resultSet.getString("phone_number"),
                        resultSet.getString("kakao_place_url")
                )
        );
        return rows.stream().findFirst();
    }
}

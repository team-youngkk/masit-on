package com.masiton.orchestration.infrastructure.query;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.masiton.orchestration.application.query.RestaurantDetailContentQueryPort;
import com.masiton.orchestration.application.query.VisitContentRow;

/**
 * {@link RestaurantDetailContentQueryPort}의 구현체다. visit·creator·video를 한 번의 조인 쿼리로
 * 읽어 N+1을 방지한다(query-composition.md 6절). video의 게시 채널명은 복합 FK로 creator의
 * channel_name과 항상 일치하므로 creator를 두 번 조인하지 않는다.
 */
@Component
class RestaurantDetailContentQueryAdapter implements RestaurantDetailContentQueryPort {

    private static final String SELECT_PUBLIC_CONTENT_SQL = """
            SELECT c.id AS creator_id,
                   c.channel_name AS channel_name,
                   c.channel_url AS channel_url,
                   vid.id AS video_id,
                   vid.title AS title,
                   vid.thumbnail_url AS thumbnail_url,
                   vid.source_url AS source_url
            FROM visit v
            JOIN creator c ON c.id = v.creator_id
            JOIN video vid ON vid.id = v.video_id
            WHERE v.restaurant_id = :restaurantId
              AND v.publication_status = 'PUBLIC'
              AND v.lifecycle_status = 'ACTIVE'
              AND c.publication_status = 'PUBLIC'
              AND c.lifecycle_status = 'ACTIVE'
              AND c.external_availability_status = 'AVAILABLE'
              AND vid.publication_status = 'PUBLIC'
              AND vid.lifecycle_status = 'ACTIVE'
              AND vid.external_availability_status = 'AVAILABLE'
            """;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    RestaurantDetailContentQueryAdapter(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    @Override
    public List<VisitContentRow> findPublicContentByRestaurantId(UUID restaurantId) {
        return namedParameterJdbcTemplate.query(
                SELECT_PUBLIC_CONTENT_SQL,
                Map.of("restaurantId", restaurantId),
                (resultSet, rowNumber) -> new VisitContentRow(
                        (UUID) resultSet.getObject("creator_id"),
                        resultSet.getString("channel_name"),
                        resultSet.getString("channel_url"),
                        (UUID) resultSet.getObject("video_id"),
                        resultSet.getString("title"),
                        resultSet.getString("thumbnail_url"),
                        resultSet.getString("source_url")
                )
        );
    }
}

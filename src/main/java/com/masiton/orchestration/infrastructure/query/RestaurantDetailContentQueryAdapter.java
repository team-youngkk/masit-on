package com.masiton.orchestration.infrastructure.query;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.masiton.orchestration.application.port.out.RestaurantDetailContentQueryPort;
import com.masiton.orchestration.application.port.out.VisitContentRow;

/**
 * RestaurantDetailContentQueryPort의 native SQL 기반 구현체다. query-composition.md 1절이
 * 지정한 위치(orchestration.infrastructure.query)에 둔다. orchestration은 어느 도메인의 JPA
 * Entity도 소유·import하지 않으므로(dependency-rules.md 8절) Spring Data JPA Repository 대신
 * JdbcTemplate으로 native SQL을 직접 실행한다.
 *
 * <p>visit·restaurant·creator·video는 같은 데이터베이스의 물리 테이블이므로 SQL 안에서 테이블명으로
 * JOIN한다. 판정 조건(BR-VISIT-005)은 visit.infrastructure.persistence.VisitQueryJpaRepository와
 * 동일하게 Visit·Restaurant·Creator·Video 모두 PUBLIC/ACTIVE를 만족해야 Row가
 * 존재하고, Creator·Video는 외부 상태도 AVAILABLE이어야 한다. BR-VISIT-005에 따라
 * 영상이 비공개·삭제·외부 이용 불가면 그 영상을 근거로 한 Visit 전체를 제외하며,
 * 이때 Creator만 단독으로 노출하지 않는다.
 */
@Component
class RestaurantDetailContentQueryAdapter implements RestaurantDetailContentQueryPort {

    private static final String FIND_VALID_VISIT_CONTENT_ROWS_SQL =
            "SELECT c.id AS creator_id, c.channel_name AS channel_name, c.channel_url AS channel_url, "
                    + "vi.id AS video_id, vi.title AS title, vi.thumbnail_url AS thumbnail_url, "
                    + "vi.source_url AS source_url "
                    + "FROM visit v "
                    + "JOIN restaurant r ON r.id = v.restaurant_id "
                    + "JOIN creator c ON c.id = v.creator_id "
                    + "JOIN video vi ON vi.id = v.video_id "
                    + "WHERE v.restaurant_id = ? "
                    + "AND v.publication_status = 'PUBLIC' AND v.lifecycle_status = 'ACTIVE' "
                    + "AND r.publication_status = 'PUBLIC' AND r.lifecycle_status = 'ACTIVE' "
                    + "AND c.publication_status = 'PUBLIC' AND c.lifecycle_status = 'ACTIVE' "
                    + "AND c.external_availability_status = 'AVAILABLE' "
                    + "AND vi.publication_status = 'PUBLIC' AND vi.lifecycle_status = 'ACTIVE' "
                    + "AND vi.external_availability_status = 'AVAILABLE'";

    private final JdbcTemplate jdbcTemplate;

    RestaurantDetailContentQueryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<VisitContentRow> findValidVisitContentRowsByRestaurantId(UUID restaurantId) {
        return jdbcTemplate.query(
                FIND_VALID_VISIT_CONTENT_ROWS_SQL,
                (rs, rowNum) -> new VisitContentRow(
                        (UUID) rs.getObject("creator_id"),
                        rs.getString("channel_name"),
                        rs.getString("channel_url"),
                        (UUID) rs.getObject("video_id"),
                        rs.getString("title"),
                        rs.getString("thumbnail_url"),
                        rs.getString("source_url")),
                restaurantId);
    }
}

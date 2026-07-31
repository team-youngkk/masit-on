package com.masiton.orchestration.infrastructure.query;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.masiton.orchestration.application.port.out.CreatorVisitedRestaurantPageResult;
import com.masiton.orchestration.application.port.out.CreatorVisitedRestaurantQueryPort;
import com.masiton.orchestration.application.port.out.CreatorVisitedRestaurantRow;

/**
 * CreatorVisitedRestaurantQueryPort의 native SQL 구현체다. dependency-rules.md 8절에 따라
 * orchestration은 다른 도메인의 JPA Entity·Repository를 소유·import하지 않으므로 visit·restaurant·
 * creator·video·region·food_category를 테이블명으로 직접 JOIN한다.
 *
 * <p>BR-VISIT-005: visit·restaurant·creator·video가 모두 PUBLIC·ACTIVE이고 creator·video는
 * 외부 AVAILABLE이어야 그 관계를 사용한다. 이 목록에도 video JOIN·조건이 필요한 이유는 근거
 * 영상이 무효(비공개·삭제·외부 이용 불가)면 그 영상을 근거로 한 Visit 전체가 제외되기 때문이다
 * ({@code RestaurantDetailContentQueryAdapter}와 동일한 판정). 상세 진입 자체의 404 판정은
 * Creator의 공개 입력 Port가 담당하고, 이 질의는 creatorId 하나의 유효성이 아니라 관계
 * 전체(restaurant·creator·video)의 유효성을 판정한다.
 *
 * <p>BR-CREATOR-010: 같은 맛집의 유효 관계가 여러 개면 한 번만 반환하고, 각 맛집의 가장 최근
 * 유효 관계 생성 시각(GROUP BY + MAX(v.created_at)) 내림차순, 동일하면 맛집 ID 오름차순으로
 * 정렬한다. 관계 시각 자체는 응답에 노출하지 않으므로 SELECT 목록에 포함하지 않는다.
 */
@Component
class CreatorVisitedRestaurantQueryAdapter implements CreatorVisitedRestaurantQueryPort {

    /** v·r·c·vi 별칭은 visit·restaurant·creator·video를 뜻한다. */
    private static final String VALID_VISIT_CONDITION =
            "v.creator_id = :creatorId "
                    + "AND v.publication_status = 'PUBLIC' AND v.lifecycle_status = 'ACTIVE' "
                    + "AND r.publication_status = 'PUBLIC' AND r.lifecycle_status = 'ACTIVE' "
                    + "AND c.publication_status = 'PUBLIC' AND c.lifecycle_status = 'ACTIVE' "
                    + "AND c.external_availability_status = 'AVAILABLE' "
                    + "AND vi.publication_status = 'PUBLIC' AND vi.lifecycle_status = 'ACTIVE' "
                    + "AND vi.external_availability_status = 'AVAILABLE'";

    private static final String COUNT_SQL =
            "SELECT count(*) FROM ("
                    + "SELECT r.id FROM visit v "
                    + "JOIN restaurant r ON r.id = v.restaurant_id "
                    + "JOIN creator c ON c.id = v.creator_id "
                    + "JOIN video vi ON vi.id = v.video_id "
                    + "WHERE " + VALID_VISIT_CONDITION
                    + " GROUP BY r.id) distinct_visited_restaurants";

    private static final String SELECT_PAGE_SQL =
            "SELECT r.id AS id, r.name AS name, reg.name AS district, fc.name AS category "
                    + "FROM visit v "
                    + "JOIN restaurant r ON r.id = v.restaurant_id "
                    + "JOIN creator c ON c.id = v.creator_id "
                    + "JOIN video vi ON vi.id = v.video_id "
                    + "JOIN region reg ON reg.id = r.region_id "
                    + "JOIN food_category fc ON fc.id = r.food_category_id "
                    + "WHERE " + VALID_VISIT_CONDITION
                    + " GROUP BY r.id, r.name, reg.name, fc.name "
                    + "ORDER BY MAX(v.created_at) DESC, r.id ASC "
                    + "LIMIT :limit OFFSET :offset";

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    CreatorVisitedRestaurantQueryAdapter(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    @Override
    public CreatorVisitedRestaurantPageResult findPage(UUID creatorId, int page, int size) {
        MapSqlParameterSource countParams = new MapSqlParameterSource("creatorId", creatorId);
        Long totalElements = namedParameterJdbcTemplate.queryForObject(COUNT_SQL, countParams, Long.class);

        MapSqlParameterSource pageParams = new MapSqlParameterSource("creatorId", creatorId)
                .addValue("limit", size)
                .addValue("offset", (long) (page - 1) * size);
        List<CreatorVisitedRestaurantRow> rows = namedParameterJdbcTemplate.query(
                SELECT_PAGE_SQL,
                pageParams,
                (resultSet, rowNumber) -> new CreatorVisitedRestaurantRow(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("name"),
                        resultSet.getString("district"),
                        resultSet.getString("category")));

        return new CreatorVisitedRestaurantPageResult(rows, totalElements == null ? 0L : totalElements);
    }
}

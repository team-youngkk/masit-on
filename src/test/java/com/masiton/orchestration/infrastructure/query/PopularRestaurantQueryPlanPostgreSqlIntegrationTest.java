package com.masiton.orchestration.infrastructure.query;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masiton.restaurant.application.port.out.PopularRestaurantRow;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-DATA-011 10절이 요구하는 대표 데이터 실행계획 점검이다.
 * 실행계획이 옛 쿼리를 검사하는 drift를 막기 위해 Adapter가 실제로 실행하는 SQL 상수를 그대로 EXPLAIN한다.
 * 스캔 방식(Seq Scan / Index Scan)은 플래너의 통계 판단이라 단정하지 않고,
 * `favorite`를 맛집마다 반복 조회하는 계획(상관 서브쿼리 등)으로 바뀌는 회귀만 고정한다.
 */
@SpringBootTest
@com.masiton.test.TestProfile
@DisplayName("인기 맛집 집계 실행계획")
class PopularRestaurantQueryPlanPostgreSqlIntegrationTest extends com.masiton.test.FullContextIntegrationTest {

    private static final UUID SEED_REGION_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID SEED_FOOD_CATEGORY_ID =
            UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final int RESTAURANT_COUNT = 200;
    private static final int MEMBER_COUNT = 50;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PopularRestaurantQueryAdapter adapter;

    @Test
    @DisplayName("대표 데이터에서 favorite를 맛집마다 반복 조회하지 않고 상위 20개를 집계한다")
    void 집계실행계획_대표데이터_favorite를반복조회하지않는다() {
        // given
        insertRepresentativeData();

        // when
        List<PopularRestaurantRow> rows = adapter.findTopByFavoriteCount(20);
        String plan = explainAggregation();
        List<String> favoriteScanNodes = plan.lines()
                .filter(line -> line.contains("on favorite"))
                .toList();

        // then
        assertThat(rows).hasSize(20);
        assertThat(rows)
                .isSortedAccordingTo(Comparator.comparingLong(PopularRestaurantRow::favoriteCount).reversed());
        assertThat(rows.getFirst().favoriteCount())
                .as("상위 20개 안에서 찜 수가 실제로 감소해야 정렬 단정이 유효하다")
                .isGreaterThan(rows.getLast().favoriteCount());
        assertThat(favoriteScanNodes)
                .as("favorite 스캔 노드가 정확히 1개여야 한다. 실제 계획:%n%s", plan)
                .hasSize(1);
        // `loops=1` 접두 일치는 loops=100 같은 값에도 걸리므로 닫는 괄호까지 포함해 정확히 비교한다.
        assertThat(favoriteScanNodes.getFirst())
                .as("favorite 스캔이 맛집마다 반복되지 않아야 한다. 실제 계획:%n%s", plan)
                .contains("loops=1)");
    }

    /** 맛집 200개, 회원 50명, 맛집별 찜 수 1~50건으로 상위권에 실제 찜 수 차이가 생기게 만든다. */
    private void insertRepresentativeData() {
        List<UUID> restaurantIds = new ArrayList<>();
        List<Object[]> restaurantBatchArgs = new ArrayList<>();
        for (int index = 0; index < RESTAURANT_COUNT; index++) {
            UUID restaurantId = UUID.randomUUID();
            restaurantIds.add(restaurantId);
            restaurantBatchArgs.add(new Object[] {
                    restaurantId, SEED_REGION_ID, SEED_FOOD_CATEGORY_ID, "부하 맛집",
                    "KAKAO-" + restaurantId, "https://example.com/place/" + restaurantId,
                    "서울특별시 종로구 테스트로 1", "02-1234-5678", "PUBLIC", "ACTIVE"});
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO restaurant
                    (id, region_id, food_category_id, name, kakao_place_id, kakao_place_url,
                     road_address, phone_number, publication_status, lifecycle_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, restaurantBatchArgs);

        List<UUID> memberIds = new ArrayList<>();
        List<Object[]> memberBatchArgs = new ArrayList<>();
        for (int index = 0; index < MEMBER_COUNT; index++) {
            UUID memberId = UUID.randomUUID();
            memberIds.add(memberId);
            memberBatchArgs.add(new Object[] {memberId, memberId + "@example.com", "password-hash"});
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO member_account (id, email, password_hash, email_verified_at, status)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, 'ACTIVE')
                """, memberBatchArgs);

        List<Object[]> favoriteBatchArgs = new ArrayList<>();
        for (int restaurantIndex = 0; restaurantIndex < RESTAURANT_COUNT; restaurantIndex++) {
            int favoriteCount = (restaurantIndex % MEMBER_COUNT) + 1;
            for (int memberIndex = 0; memberIndex < favoriteCount; memberIndex++) {
                favoriteBatchArgs.add(
                        new Object[] {memberIds.get(memberIndex), restaurantIds.get(restaurantIndex)});
            }
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO favorite (member_id, restaurant_id) VALUES (?, ?)", favoriteBatchArgs);
        jdbcTemplate.execute("ANALYZE favorite");
        jdbcTemplate.execute("ANALYZE restaurant");
    }

    private String explainAggregation() {
        List<Map<String, Object>> planRows = jdbcTemplate.queryForList(
                "EXPLAIN (ANALYZE, BUFFERS) "
                        + PopularRestaurantQueryAdapter.AGGREGATION_SQL.replace("LIMIT ?", "LIMIT 20"));
        return planRows.stream()
                .map(row -> String.valueOf(row.values().iterator().next()))
                .reduce((first, second) -> first + System.lineSeparator() + second)
                .orElse("");
    }
}

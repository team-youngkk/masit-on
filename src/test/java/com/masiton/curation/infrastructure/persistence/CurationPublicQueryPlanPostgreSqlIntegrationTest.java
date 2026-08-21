package com.masiton.curation.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

import com.masiton.curation.application.AdminCurationService;
import com.masiton.curation.application.port.out.CurationStore;
import com.masiton.curation.application.port.out.CurationStore.StoredCurationRestaurant;
import com.masiton.curation.domain.model.CurationStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TST-E2-PERF-001(1) 공개 큐레이션 조회 실행계획 검증이다.
 * {@code PopularRestaurantQueryPlanPostgreSqlIntegrationTest}와 같은 패턴을 따른다: 옛 쿼리를 검사하는
 * drift를 막기 위해 {@link JdbcCurationStore#FIND_RESTAURANTS_BY_CURATION_IDS_SQL_TEMPLATE} 그대로 EXPLAIN한다.
 *
 * <p>스캔 방식(Seq Scan / Index Scan / Bitmap 계열)은 플래너의 통계 판단이라 단정하지 않는다.
 * 대신 "큐레이션 건수만큼 {@code curation_restaurant}를 반복 조회하는 계획(상관 서브쿼리, N회 loops 등)으로
 * 바뀌는 회귀"만 고정한다. 이는 troubleshooting/pr-141-admin-curation-review.md가 기록한 관리자 쪽
 * 최대 20건 단건 반복 조회 사고와 동일한 유형이 공개 조회에도 재발하지 않는지 지키는 회귀 테스트다.
 */
@SpringBootTest
@com.masiton.test.TestProfile
@DisplayName("공개 큐레이션 조회 실행계획")
class CurationPublicQueryPlanPostgreSqlIntegrationTest extends com.masiton.test.FullContextIntegrationTest {

    private static final UUID SEED_REGION_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID SEED_FOOD_CATEGORY_ID =
            UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-06T00:00:00Z");

    /** FR-CURATION-003 메인 5개 상한. 애플리케이션 서비스로 게시하므로 이 상한을 그대로 지킨다. */
    private static final int PUBLISHED_CURATION_COUNT = 5;
    /** 대표 데이터 규모를 키우기 위한 게시되지 않은 초안 큐레이션. */
    private static final int DRAFT_CURATION_COUNT = 15;
    /** FR-CURATION-001 구성 상한 20건. */
    private static final int RESTAURANT_PER_CURATION = 20;
    /** 여러 큐레이션이 맛집을 공유하도록 만드는 풀 크기이며 비공개·삭제 맛집을 섞는다. */
    private static final int RESTAURANT_POOL_SIZE = 60;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdminCurationService adminCurationService;

    @Autowired
    private CurationStore store;

    @Test
    @DisplayName("대표 데이터에서 게시된 큐레이션 구성 맛집을 큐레이션 건수만큼 반복 조회하지 않는다")
    void 공개큐레이션구성조회_대표데이터_큐레이션건수만큼반복조회하지않는다() {
        // given
        List<UUID> publishedCurationIds = insertRepresentativeData();

        // when
        List<StoredCurationRestaurant> relations = store.findRestaurants(publishedCurationIds);
        String plan = explainFindRestaurantsByCurationIds(publishedCurationIds);
        List<String> curationRestaurantScanNodes = plan.lines()
                .filter(line -> line.contains("on curation_restaurant"))
                .toList();

        // then
        assertThat(relations).hasSize(PUBLISHED_CURATION_COUNT * RESTAURANT_PER_CURATION);
        assertThat(curationRestaurantScanNodes)
                .as("curation_restaurant 스캔 노드가 정확히 1개여야 한다. 실제 계획:%n%s", plan)
                .hasSize(1);
        // `loops=1` 접두 일치는 loops=10 같은 값에도 걸리므로 닫는 괄호까지 포함해 정확히 비교한다.
        assertThat(curationRestaurantScanNodes.getFirst())
                .as("curation_restaurant 스캔이 큐레이션마다 반복되지 않아야 한다. 실제 계획:%n%s", plan)
                .contains("loops=1)");
    }

    /**
     * 큐레이션 20건(게시 5건 + 초안 15건), 맛집 풀 60건(공개·비공개·삭제 섞임),
     * 게시된 큐레이션마다 구성 맛집 20건을 만든다.
     * 서비스 빈을 사용해 FR-CURATION-001~003 상한을 실제 검증 경로 그대로 지킨다.
     */
    private List<UUID> insertRepresentativeData() {
        UUID adminId = insertAdmin();
        // 구성 시점에는 AdminCurationService가 PUBLIC/ACTIVE 맛집만 허용하므로 전부 공개로 넣고,
        // 구성이 끝난 뒤에만 일부를 비공개·삭제로 낮춘다(관리자 큐레이션 상세 통합 테스트와 같은 순서).
        List<UUID> restaurantPool = insertActiveRestaurantPool();

        List<UUID> curationIds = new ArrayList<>();
        for (int index = 0; index < PUBLISHED_CURATION_COUNT + DRAFT_CURATION_COUNT; index++) {
            UUID curationId = insertDraftCuration(adminId, "큐레이션 " + index);
            curationIds.add(curationId);
            List<UUID> composition = new ArrayList<>();
            for (int position = 0; position < RESTAURANT_PER_CURATION; position++) {
                // 창이 풀 전체(60)를 순환하도록 index*20 오프셋을 준다. 각 창은 너비 20 < 60이라
                // 같은 큐레이션 안에서는 항상 서로 다른 맛집을 고른다. 이 덕분에 뒤에서 비공개·삭제로
                // 낮추는 40~59번 맛집도 일부 게시 큐레이션(예: index=2)의 구성에 포함된다.
                composition.add(restaurantPool.get(
                        (index * RESTAURANT_PER_CURATION + position) % restaurantPool.size()));
            }
            adminCurationService.replaceRestaurants(curationId, adminId, composition, "trace-seed-" + index);
        }

        downgradeSomeRestaurantsAfterComposition(restaurantPool);

        List<UUID> publishedCurationIds = curationIds.subList(0, PUBLISHED_CURATION_COUNT);
        for (int index = 0; index < publishedCurationIds.size(); index++) {
            adminCurationService.setPublication(publishedCurationIds.get(index), adminId,
                    CurationStatus.PUBLISHED, "trace-publish-" + index);
        }

        jdbcTemplate.execute("ANALYZE curation_restaurant");
        jdbcTemplate.execute("ANALYZE curation");
        return publishedCurationIds;
    }

    private UUID insertAdmin() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO admin_account (id, login_id, password_hash) VALUES (?, ?, 'hash')",
                id, "admin-" + id);
        return id;
    }

    private UUID insertDraftCuration(UUID adminId, String title) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO curation (id, title, description, created_by, updated_by, "
                        + "created_at, updated_at) VALUES (?, ?, '', ?, ?, ?, ?)",
                id, title, adminId, adminId, NOW, NOW);
        return id;
    }

    /** 전부 공개·활성으로 넣는다. AdminCurationService.replaceRestaurants는 비공개 맛집 구성을 거부한다. */
    private List<UUID> insertActiveRestaurantPool() {
        List<UUID> ids = new ArrayList<>();
        List<Object[]> batchArgs = new ArrayList<>();
        for (int index = 0; index < RESTAURANT_POOL_SIZE; index++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            batchArgs.add(new Object[] {
                    id, SEED_REGION_ID, SEED_FOOD_CATEGORY_ID, "대표 맛집 " + index,
                    "KAKAO-" + id, "https://example.com/place/" + id,
                    "서울특별시 종로구 대표로 1", "02-1234-5678", "PUBLIC", "ACTIVE"});
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO restaurant
                    (id, region_id, food_category_id, name, kakao_place_id, kakao_place_url,
                     road_address, phone_number, publication_status, lifecycle_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, batchArgs);
        return ids;
    }

    /**
     * 구성이 끝난 뒤 맛집 풀의 뒤쪽 20건을 비공개·삭제로 낮춰 대표 데이터에
     * "비공개·삭제 맛집이 섞인 게시 큐레이션"을 만든다. 이 SQL은 구성 저장 SQL과 무관하며
     * findRestaurants(Collection) 실행계획 검증 대상이 아니다.
     */
    private void downgradeSomeRestaurantsAfterComposition(List<UUID> restaurantPool) {
        List<UUID> toPrivate = restaurantPool.subList(40, 50);
        List<UUID> toDeleted = restaurantPool.subList(50, 60);
        jdbcTemplate.batchUpdate("UPDATE restaurant SET publication_status = 'PRIVATE' WHERE id = ?",
                toPrivate.stream().map(id -> new Object[] {id}).toList());
        jdbcTemplate.batchUpdate("UPDATE restaurant SET publication_status = 'PRIVATE', "
                        + "lifecycle_status = 'DELETED', deleted_at = ? WHERE id = ?",
                toDeleted.stream().map(id -> new Object[] {NOW, id}).toList());
    }

    private String explainFindRestaurantsByCurationIds(List<UUID> curationIds) {
        String literals = curationIds.stream()
                .map(id -> "'" + id + "'")
                .collect(Collectors.joining(","));
        String sql = JdbcCurationStore.FIND_RESTAURANTS_BY_CURATION_IDS_SQL_TEMPLATE.formatted(literals);
        List<Map<String, Object>> planRows = jdbcTemplate.queryForList("EXPLAIN (ANALYZE, BUFFERS) " + sql);
        return planRows.stream()
                .map(row -> String.valueOf(row.values().iterator().next()))
                .reduce((first, second) -> first + System.lineSeparator() + second)
                .orElse("");
    }
}

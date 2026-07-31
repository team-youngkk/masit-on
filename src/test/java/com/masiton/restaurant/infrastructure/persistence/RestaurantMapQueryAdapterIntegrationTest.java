package com.masiton.restaurant.infrastructure.persistence;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
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

import com.masiton.restaurant.application.port.out.RestaurantMapPointRow;
import com.masiton.restaurant.application.port.out.RestaurantMapPointsCriteria;
import com.masiton.restaurant.application.port.out.RestaurantMapPointsQueryPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RestaurantMapQueryAdapter의 지도 영역 bounds 조회를 실제 PostgreSQL로 검증한다.
 * 근거: docs/07-adr/integration/map-001-map-bounds-search.md
 */
@SpringBootTest
@com.masiton.test.TestProfile
@Testcontainers
@DisplayName("지도 영역 맛집 조회 Query Adapter")
class RestaurantMapQueryAdapterIntegrationTest {

    private static final UUID MAPO_REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000014");
    private static final UUID GANGNAM_REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000023");
    private static final UUID KOREAN_CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID JAPANESE_CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000003");

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17.10-alpine")
                    .withDatabaseName("masiton")
                    .withUsername("masiton")
                    .withPassword("masiton_local");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private RestaurantMapPointsQueryPort restaurantMapPointsQueryPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUpTransactionalTables() {
        jdbcTemplate.execute("TRUNCATE TABLE visit, video, creator, restaurant CASCADE");
    }

    @Test
    @DisplayName("영역 밖 좌표와 좌표 없는 맛집은 제외하고 영역 안의 공개 맛집만 반환한다")
    void findWithinBounds_영역과공개상태_경계안공개맛집만반환한다() {
        // given
        UUID insideId = insertRestaurant("영역 안 맛집", MAPO_REGION_ID, KOREAN_CATEGORY_ID,
                "PUBLIC", "ACTIVE", "37.5665", "126.9780");
        insertRestaurant("영역 밖 맛집", MAPO_REGION_ID, KOREAN_CATEGORY_ID, "PUBLIC", "ACTIVE", "37.9", "127.5");
        insertRestaurant("좌표 없는 맛집", MAPO_REGION_ID, KOREAN_CATEGORY_ID, "PUBLIC", "ACTIVE", null, null);
        insertRestaurant("비공개 맛집", MAPO_REGION_ID, KOREAN_CATEGORY_ID, "PRIVATE", "ACTIVE", "37.5665", "126.9780");

        // when
        List<RestaurantMapPointRow> rows = restaurantMapPointsQueryPort.findWithinBounds(
                bounds("37.5", "126.9", "37.6", "127.0"), 201);

        // then
        assertThat(rows).extracting(RestaurantMapPointRow::id).containsExactly(insideId);
    }

    @Test
    @DisplayName("경계선 위 좌표는 포함한다")
    void findWithinBounds_경계선위좌표_포함한다() {
        // given
        UUID onBoundaryId = insertRestaurant("경계 맛집", MAPO_REGION_ID, KOREAN_CATEGORY_ID,
                "PUBLIC", "ACTIVE", "37.5", "126.9");

        // when
        List<RestaurantMapPointRow> rows = restaurantMapPointsQueryPort.findWithinBounds(
                bounds("37.5", "126.9", "37.6", "127.0"), 201);

        // then
        assertThat(rows).extracting(RestaurantMapPointRow::id).containsExactly(onBoundaryId);
    }

    @Test
    @DisplayName("자치구·카테고리·이름·유튜버 조건을 영역과 AND로 조합한다")
    void findWithinBounds_기존필터AND조합() {
        // given
        UUID allMatchId = insertRestaurant("공덕 기준 맛집", MAPO_REGION_ID, KOREAN_CATEGORY_ID,
                "PUBLIC", "ACTIVE", "37.5665", "126.9780");
        insertRestaurant("공덕 강남 맛집", GANGNAM_REGION_ID, KOREAN_CATEGORY_ID,
                "PUBLIC", "ACTIVE", "37.5665", "126.9780");
        insertRestaurant("공덕 일식 맛집", MAPO_REGION_ID, JAPANESE_CATEGORY_ID,
                "PUBLIC", "ACTIVE", "37.5665", "126.9780");

        // when
        List<RestaurantMapPointRow> rows = restaurantMapPointsQueryPort.findWithinBounds(
                new RestaurantMapPointsCriteria(
                        new BigDecimal("37.5"), new BigDecimal("126.9"), new BigDecimal("37.6"), new BigDecimal("127.0"),
                        "공덕", MAPO_REGION_ID, KOREAN_CATEGORY_ID, null),
                201);

        // then
        assertThat(rows).extracting(RestaurantMapPointRow::id).containsExactly(allMatchId);
    }

    @Test
    @DisplayName("candidateRestaurantIds가 빈 집합이면 결과가 없다")
    void findWithinBounds_후보빈집합_결과없음() {
        // given
        insertRestaurant("공개 맛집", MAPO_REGION_ID, KOREAN_CATEGORY_ID, "PUBLIC", "ACTIVE", "37.5665", "126.9780");

        // when
        List<RestaurantMapPointRow> rows = restaurantMapPointsQueryPort.findWithinBounds(
                new RestaurantMapPointsCriteria(
                        new BigDecimal("37.5"), new BigDecimal("126.9"), new BigDecimal("37.6"), new BigDecimal("127.0"),
                        null, null, null, Set.of()),
                201);

        // then
        assertThat(rows).isEmpty();
    }

    @Test
    @DisplayName("이름 오름차순, 동률은 id 오름차순으로 안정 정렬한다")
    void findWithinBounds_이름과id오름차순으로정렬한다() {
        // given
        UUID second = insertRestaurant("나 맛집", MAPO_REGION_ID, KOREAN_CATEGORY_ID,
                "PUBLIC", "ACTIVE", "37.5665", "126.9780");
        UUID first = insertRestaurant("가 맛집", MAPO_REGION_ID, KOREAN_CATEGORY_ID,
                "PUBLIC", "ACTIVE", "37.5665", "126.9780");

        // when
        List<RestaurantMapPointRow> rows = restaurantMapPointsQueryPort.findWithinBounds(
                bounds("37.5", "126.9", "37.6", "127.0"), 201);

        // then
        assertThat(rows).extracting(RestaurantMapPointRow::id).containsExactly(first, second);
    }

    private RestaurantMapPointsCriteria bounds(String south, String west, String north, String east) {
        return new RestaurantMapPointsCriteria(
                new BigDecimal(south), new BigDecimal(west), new BigDecimal(north), new BigDecimal(east),
                null, null, null, null);
    }

    private UUID insertRestaurant(
            String name, UUID regionId, UUID foodCategoryId, String publicationStatus, String lifecycleStatus,
            String latitude, String longitude) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO restaurant "
                        + "(id, region_id, food_category_id, name, kakao_place_id, kakao_place_url, "
                        + "road_address, phone_number, latitude, longitude, publication_status, lifecycle_status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, regionId, foodCategoryId, name, "KAKAO-" + UUID.randomUUID(),
                "https://example.com/place/" + id, "서울특별시 테스트로 1", "02-1234-5678",
                latitude == null ? null : new BigDecimal(latitude),
                longitude == null ? null : new BigDecimal(longitude),
                publicationStatus, lifecycleStatus);
        return id;
    }
}

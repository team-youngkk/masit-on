package com.masiton.restaurant.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

import com.masiton.restaurant.application.port.out.RestaurantSearchCriteria;
import com.masiton.restaurant.application.port.out.RestaurantSearchQueryPort;
import com.masiton.restaurant.application.port.out.RestaurantSearchQueryResult;
import com.masiton.restaurant.application.port.out.RestaurantSearchRow;
import com.masiton.restaurant.application.port.out.VisitedByRow;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RestaurantSearchQueryAdapter의 네이티브 SQL 조회를 실제 PostgreSQL로 검증한다.
 * V5가 적재한 서울 자치구·대표 음식 카테고리 기준 데이터를 그대로 사용하고,
 * restaurant·creator·video·visit은 각 테스트가 직접 적재한 뒤 다음 테스트 실행 전 초기화한다.
 */
@SpringBootTest
@Testcontainers
@DisplayName("맛집 검색 Query Adapter")
class RestaurantSearchQueryAdapterIntegrationTest {

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
    private RestaurantSearchQueryPort restaurantSearchQueryPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUpTransactionalTables() {
        jdbcTemplate.execute("TRUNCATE TABLE visit, video, creator, restaurant CASCADE");
    }

    @Test
    @DisplayName("조건이 없으면 공개·활성 맛집만 반환하고 비공개·삭제 맛집은 제외한다")
    void search_조건없음_공개활성맛집만반환한다() {
        // given
        insertRestaurant("공개맛집", MAPO_REGION_ID, KOREAN_CATEGORY_ID, "PUBLIC", "ACTIVE");
        insertRestaurant("비공개맛집", MAPO_REGION_ID, KOREAN_CATEGORY_ID, "PRIVATE", "ACTIVE");

        // when
        RestaurantSearchQueryResult result = restaurantSearchQueryPort.search(criteria(null, null, null, null, 1, 20));

        // then
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.rows()).extracting(RestaurantSearchRow::name).containsExactly("공개맛집");
    }

    @Test
    @DisplayName("이름 검색은 부분 일치이고 대소문자를 구분하지 않는다")
    void search_이름부분일치_대소문자무시하고일치하는맛집만반환한다() {
        // given
        insertRestaurant("Blue Bottle Coffee", MAPO_REGION_ID, KOREAN_CATEGORY_ID, "PUBLIC", "ACTIVE");
        insertRestaurant("스타벅스", MAPO_REGION_ID, KOREAN_CATEGORY_ID, "PUBLIC", "ACTIVE");

        // when
        RestaurantSearchQueryResult result =
                restaurantSearchQueryPort.search(criteria("blue", null, null, null, 1, 20));

        // then
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.rows()).extracting(RestaurantSearchRow::name).containsExactly("Blue Bottle Coffee");
    }

    @Test
    @DisplayName("이름 검색어의 %, _는 LIKE 와일드카드가 아닌 리터럴 문자로 취급한다")
    void search_이름검색어에와일드카드문자_리터럴로만일치한다() {
        // given
        insertRestaurant("10% 커피", MAPO_REGION_ID, KOREAN_CATEGORY_ID, "PUBLIC", "ACTIVE");
        insertRestaurant("100 커피", MAPO_REGION_ID, KOREAN_CATEGORY_ID, "PUBLIC", "ACTIVE");

        // when
        RestaurantSearchQueryResult result =
                restaurantSearchQueryPort.search(criteria("10%", null, null, null, 1, 20));

        // then
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.rows()).extracting(RestaurantSearchRow::name).containsExactly("10% 커피");
    }

    @Test
    @DisplayName("매우 큰 페이지 번호도 오버플로 없이 빈 목록을 반환한다")
    void search_매우큰페이지번호_오버플로없이빈목록반환한다() {
        // given
        insertRestaurant("페이지테스트맛집", MAPO_REGION_ID, KOREAN_CATEGORY_ID, "PUBLIC", "ACTIVE");

        // when
        RestaurantSearchQueryResult result =
                restaurantSearchQueryPort.search(criteria(null, null, null, null, Integer.MAX_VALUE, 50));

        // then
        assertThat(result.rows()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("existsPublicCreator는 공개·활성 유튜버만 존재로 판정한다")
    void existsPublicCreator_공개활성유튜버만존재로판정한다() {
        // given
        UUID publicCreatorId = insertCreator("공개유튜버", "PUBLIC", "ACTIVE", "AVAILABLE");
        UUID privateCreatorId = insertCreator("비공개유튜버", "PRIVATE", "ACTIVE", "AVAILABLE");

        // when & then
        assertThat(restaurantSearchQueryPort.existsPublicCreator(publicCreatorId)).isTrue();
        assertThat(restaurantSearchQueryPort.existsPublicCreator(privateCreatorId)).isFalse();
        assertThat(restaurantSearchQueryPort.existsPublicCreator(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("district 필터는 지정한 자치구의 맛집만 반환한다")
    void search_district필터_지정한자치구만반환한다() {
        // given
        insertRestaurant("마포맛집", MAPO_REGION_ID, KOREAN_CATEGORY_ID, "PUBLIC", "ACTIVE");
        insertRestaurant("강남맛집", GANGNAM_REGION_ID, KOREAN_CATEGORY_ID, "PUBLIC", "ACTIVE");

        // when
        RestaurantSearchQueryResult result =
                restaurantSearchQueryPort.search(criteria(null, MAPO_REGION_ID, null, null, 1, 20));

        // then
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.rows()).extracting(RestaurantSearchRow::district).containsExactly("마포구");
    }

    @Test
    @DisplayName("category 필터는 지정한 카테고리의 맛집만 반환한다")
    void search_category필터_지정한카테고리만반환한다() {
        // given
        insertRestaurant("한식집", MAPO_REGION_ID, KOREAN_CATEGORY_ID, "PUBLIC", "ACTIVE");
        insertRestaurant("일식집", MAPO_REGION_ID, JAPANESE_CATEGORY_ID, "PUBLIC", "ACTIVE");

        // when
        RestaurantSearchQueryResult result =
                restaurantSearchQueryPort.search(criteria(null, null, JAPANESE_CATEGORY_ID, null, 1, 20));

        // then
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.rows()).extracting(RestaurantSearchRow::category).containsExactly("일식");
    }

    @Test
    @DisplayName("creatorId 필터는 공개·유효한 방문 근거가 있는 맛집만 반환하고 중복 없이 한 번만 반환한다")
    void search_creatorId필터_유효한방문근거가있는맛집만중복없이반환한다() {
        // given
        UUID visitedRestaurantId = insertRestaurant("방문맛집", MAPO_REGION_ID, KOREAN_CATEGORY_ID, "PUBLIC", "ACTIVE");
        UUID notVisitedRestaurantId =
                insertRestaurant("미방문맛집", MAPO_REGION_ID, KOREAN_CATEGORY_ID, "PUBLIC", "ACTIVE");
        UUID privatelyVisitedRestaurantId =
                insertRestaurant("비공개관계맛집", MAPO_REGION_ID, KOREAN_CATEGORY_ID, "PUBLIC", "ACTIVE");

        UUID creatorId = insertCreator("맛집유튜버", "PUBLIC", "ACTIVE", "AVAILABLE");
        String channelId = channelIdOf(creatorId);
        UUID videoId1 = insertVideo(creatorId, channelId, "PUBLIC", "ACTIVE", "AVAILABLE");
        UUID videoId2 = insertVideo(creatorId, channelId, "PUBLIC", "ACTIVE", "AVAILABLE");
        // 같은 맛집을 서로 다른 영상 두 건으로 방문 처리해 중복 제거를 검증한다.
        insertVisit(visitedRestaurantId, creatorId, videoId1, "PUBLIC", "ACTIVE");
        insertVisit(visitedRestaurantId, creatorId, videoId2, "PUBLIC", "ACTIVE");
        // 비공개 관계는 후보에서 제외되어야 한다.
        insertVisit(privatelyVisitedRestaurantId, creatorId, videoId1, "PRIVATE", "ACTIVE");

        // when
        RestaurantSearchQueryResult result =
                restaurantSearchQueryPort.search(criteria(null, null, null, creatorId, 1, 20));

        // then
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.rows()).extracting(RestaurantSearchRow::id).containsExactly(visitedRestaurantId);
        assertThat(notVisitedRestaurantId).isNotNull();
    }

    @Test
    @DisplayName("모든 조건을 조합하면 AND로 모두 만족하는 맛집만 반환한다")
    void search_전체조건조합_AND로모두만족하는맛집만반환한다() {
        // given
        UUID matchRestaurantId = insertRestaurant("공덕 한식당", MAPO_REGION_ID, KOREAN_CATEGORY_ID, "PUBLIC", "ACTIVE");
        insertRestaurant("공덕 한식당2", GANGNAM_REGION_ID, KOREAN_CATEGORY_ID, "PUBLIC", "ACTIVE");
        insertRestaurant("공덕 일식당", MAPO_REGION_ID, JAPANESE_CATEGORY_ID, "PUBLIC", "ACTIVE");
        UUID creatorId = insertCreator("조합유튜버", "PUBLIC", "ACTIVE", "AVAILABLE");
        String channelId = channelIdOf(creatorId);
        UUID videoId = insertVideo(creatorId, channelId, "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(matchRestaurantId, creatorId, videoId, "PUBLIC", "ACTIVE");

        // when
        RestaurantSearchQueryResult result = restaurantSearchQueryPort.search(
                criteria("공덕", MAPO_REGION_ID, KOREAN_CATEGORY_ID, creatorId, 1, 20));

        // then
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.rows()).extracting(RestaurantSearchRow::id).containsExactly(matchRestaurantId);
    }

    @Test
    @DisplayName("페이지 이동 중 결과 누락이나 중복 없이 모든 맛집을 반환하고 범위 밖 페이지는 빈 목록이다")
    void search_페이지이동_누락이나중복없이모든맛집을반환하고범위밖페이지는빈목록이다() {
        // given: 이름 오름차순 기준 25건을 적재한다.
        List<UUID> allIds = IntStream.rangeClosed(1, 25)
                .mapToObj(i -> insertRestaurant(
                        String.format("가게 %02d", i), MAPO_REGION_ID, KOREAN_CATEGORY_ID, "PUBLIC", "ACTIVE"))
                .toList();

        // when
        RestaurantSearchQueryResult page1 = restaurantSearchQueryPort.search(criteria(null, null, null, null, 1, 10));
        RestaurantSearchQueryResult page2 = restaurantSearchQueryPort.search(criteria(null, null, null, null, 2, 10));
        RestaurantSearchQueryResult page3 = restaurantSearchQueryPort.search(criteria(null, null, null, null, 3, 10));
        RestaurantSearchQueryResult outOfRangePage =
                restaurantSearchQueryPort.search(criteria(null, null, null, null, 4, 10));

        // then
        assertThat(page1.rows()).hasSize(10);
        assertThat(page2.rows()).hasSize(10);
        assertThat(page3.rows()).hasSize(5);
        assertThat(outOfRangePage.rows()).isEmpty();
        assertThat(outOfRangePage.totalElements()).isEqualTo(25);

        List<UUID> collected = List.of(page1, page2, page3).stream()
                .flatMap(result -> result.rows().stream())
                .map(RestaurantSearchRow::id)
                .collect(Collectors.toList());
        assertThat(collected).hasSize(25).containsExactlyInAnyOrderElementsOf(allIds);
        assertThat(collected).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("배치 방문 유튜버 조회는 비공개 관계를 제외하고 같은 창작자·맛집 조합을 중복 없이 반환한다")
    void findVisitedByRestaurantIds_비공개관계제외_중복없이반환한다() {
        // given
        UUID restaurantId = insertRestaurant("배치조회맛집", MAPO_REGION_ID, KOREAN_CATEGORY_ID, "PUBLIC", "ACTIVE");
        UUID publicCreatorId = insertCreator("공개채널", "PUBLIC", "ACTIVE", "AVAILABLE");
        String publicChannelId = channelIdOf(publicCreatorId);
        UUID videoId1 = insertVideo(publicCreatorId, publicChannelId, "PUBLIC", "ACTIVE", "AVAILABLE");
        UUID videoId2 = insertVideo(publicCreatorId, publicChannelId, "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(restaurantId, publicCreatorId, videoId1, "PUBLIC", "ACTIVE");
        insertVisit(restaurantId, publicCreatorId, videoId2, "PUBLIC", "ACTIVE");

        UUID privateCreatorId = insertCreator("비공개채널", "PRIVATE", "ACTIVE", "AVAILABLE");
        String privateChannelId = channelIdOf(privateCreatorId);
        UUID privateVideoId = insertVideo(privateCreatorId, privateChannelId, "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(restaurantId, privateCreatorId, privateVideoId, "PUBLIC", "ACTIVE");

        // when
        List<VisitedByRow> rows = restaurantSearchQueryPort.findVisitedByRestaurantIds(List.of(restaurantId));

        // then
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).creatorId()).isEqualTo(publicCreatorId);
        assertThat(rows.get(0).channelName()).isEqualTo("공개채널");
    }

    private RestaurantSearchCriteria criteria(
            String query, UUID regionId, UUID foodCategoryId, UUID creatorId, int page, int size) {
        return new RestaurantSearchCriteria(query, regionId, foodCategoryId, creatorId, page, size);
    }

    private UUID insertRestaurant(
            String name, UUID regionId, UUID foodCategoryId, String publicationStatus, String lifecycleStatus) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO restaurant "
                        + "(id, region_id, food_category_id, name, kakao_place_id, kakao_place_url, "
                        + "road_address, phone_number, publication_status, lifecycle_status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, regionId, foodCategoryId, name, "KAKAO-" + UUID.randomUUID(),
                "https://example.com/place/" + id, "서울특별시 테스트로 1", "02-1234-5678",
                publicationStatus, lifecycleStatus);
        return id;
    }

    private UUID insertCreator(
            String channelName, String publicationStatus, String lifecycleStatus, String externalAvailabilityStatus) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO creator "
                        + "(id, external_channel_id, channel_name, channel_url, "
                        + "publication_status, lifecycle_status, external_availability_status, "
                        + "external_status_checked_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, channelIdOf(id), channelName, "https://example.com/channel/" + id,
                publicationStatus, lifecycleStatus, externalAvailabilityStatus, OffsetDateTime.now());
        return id;
    }

    private UUID insertVideo(
            UUID creatorId,
            String publisherExternalChannelId,
            String publicationStatus,
            String lifecycleStatus,
            String externalAvailabilityStatus) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO video "
                        + "(id, creator_id, external_video_id, publisher_external_channel_id, title, "
                        + "source_url, thumbnail_url, publication_status, lifecycle_status, "
                        + "external_availability_status, external_status_checked_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, creatorId, shortId("VID-"), publisherExternalChannelId, "테스트 영상",
                "https://example.com/video/" + id, "https://example.com/thumbnail/" + id,
                publicationStatus, lifecycleStatus, externalAvailabilityStatus, OffsetDateTime.now());
        return id;
    }

    private UUID insertVisit(
            UUID restaurantId, UUID creatorId, UUID videoId, String publicationStatus, String lifecycleStatus) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO visit (id, restaurant_id, creator_id, video_id, publication_status, lifecycle_status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, restaurantId, creatorId, videoId, publicationStatus, lifecycleStatus);
        return id;
    }

    private String channelIdOf(UUID creatorId) {
        return "UC-" + creatorId;
    }

    private String shortId(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 20);
    }
}

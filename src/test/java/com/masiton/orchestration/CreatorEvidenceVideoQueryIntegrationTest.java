package com.masiton.orchestration;

import java.time.OffsetDateTime;
import java.util.List;
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

import com.masiton.orchestration.application.port.out.CreatorEvidenceVideoPageResult;
import com.masiton.orchestration.application.port.out.CreatorEvidenceVideoQueryPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API-CREATOR-DETAIL-003이 쓰는 {@link CreatorEvidenceVideoQueryPort}의 native SQL 구현체를
 * 실제 PostgreSQL로 검증한다. BR-VISIT-005(관계 유효성), BR-CREATOR-011(중복 제거·정렬),
 * creator-detail-api.md 7절(페이지)의 근거 판정을 다룬다.
 */
@SpringBootTest
@com.masiton.test.TestProfile
@Testcontainers
@DisplayName("유튜버 근거 영상 조회 Adapter")
class CreatorEvidenceVideoQueryIntegrationTest {

    private static final UUID SEED_REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID SEED_FOOD_CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CreatorEvidenceVideoQueryPort creatorEvidenceVideoQueryPort;

    @Test
    @DisplayName("네 대상 모두 공개·유효하면 영상의 title·thumbnailUrl·sourceUrl이 원본 값과 일치한다")
    void 네대상모두공개유효_영상필드가원본값과일치한다() {
        // given
        UUID creatorId = insertCreator("채널A", "PUBLIC", "ACTIVE", "AVAILABLE");
        UUID restaurantId = insertRestaurant("맛집A", "PUBLIC", "ACTIVE");
        UUID videoId = insertVideo(creatorId, "영상A", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(restaurantId, creatorId, videoId, "PUBLIC", "ACTIVE", OffsetDateTime.now());

        // when
        CreatorEvidenceVideoPageResult result = creatorEvidenceVideoQueryPort.findPage(creatorId, 1, 20);

        // then
        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).id()).isEqualTo(videoId);
        assertThat(result.rows().get(0).title()).isEqualTo("영상A");
        assertThat(result.rows().get(0).thumbnailUrl()).isEqualTo("https://i.ytimg.com/" + videoId + ".jpg");
        assertThat(result.rows().get(0).sourceUrl()).isEqualTo("https://www.youtube.com/watch?v=" + videoId);
    }

    @Test
    @DisplayName("근거 맛집이 비공개면 그 맛집을 대상으로 한 관계 전체를 제외한다")
    void 근거맛집이비공개_그맛집을대상으로한관계전체를제외한다() {
        // given: BR-VISIT-005 — 맛집까지 공개·유효해야 조회 관계로 사용한다.
        UUID creatorId = insertCreator("채널B", "PUBLIC", "ACTIVE", "AVAILABLE");
        UUID restaurantId = insertRestaurant("맛집B", "PRIVATE", "ACTIVE");
        UUID videoId = insertVideo(creatorId, "영상B", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(restaurantId, creatorId, videoId, "PUBLIC", "ACTIVE", OffsetDateTime.now());

        // when
        CreatorEvidenceVideoPageResult result = creatorEvidenceVideoQueryPort.findPage(creatorId, 1, 20);

        // then
        assertThat(result.rows()).isEmpty();
    }

    @Test
    @DisplayName("영상 자체가 외부 이용 불가면 근거 영상 목록에서 제외한다")
    void 영상이외부이용불가_근거영상목록에서제외한다() {
        // given: ck_video__external_unavailable_private 제약상 UNAVAILABLE은 PRIVATE와 함께여야 한다.
        UUID creatorId = insertCreator("채널C", "PUBLIC", "ACTIVE", "AVAILABLE");
        UUID restaurantId = insertRestaurant("맛집C", "PUBLIC", "ACTIVE");
        UUID videoId = insertVideo(creatorId, "영상C", "PRIVATE", "ACTIVE", "UNAVAILABLE");
        insertVisit(restaurantId, creatorId, videoId, "PUBLIC", "ACTIVE", OffsetDateTime.now());

        // when
        CreatorEvidenceVideoPageResult result = creatorEvidenceVideoQueryPort.findPage(creatorId, 1, 20);

        // then
        assertThat(result.rows()).isEmpty();
    }

    @Test
    @DisplayName("맛집 하나만 무효여도 다른 유효 관계의 영상은 유지된다")
    void 맛집하나만무효_다른유효관계의영상은유지된다() {
        // given
        UUID creatorId = insertCreator("채널P", "PUBLIC", "ACTIVE", "AVAILABLE");
        UUID validRestaurantId = insertRestaurant("유효맛집", "PUBLIC", "ACTIVE");
        UUID validVideoId = insertVideo(creatorId, "유효영상", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(validRestaurantId, creatorId, validVideoId, "PUBLIC", "ACTIVE", OffsetDateTime.now());

        UUID invalidRestaurantId = insertRestaurant("무효맛집", "PRIVATE", "ACTIVE");
        UUID otherVideoId = insertVideo(creatorId, "무효근거영상", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(invalidRestaurantId, creatorId, otherVideoId, "PUBLIC", "ACTIVE", OffsetDateTime.now());

        // when
        CreatorEvidenceVideoPageResult result = creatorEvidenceVideoQueryPort.findPage(creatorId, 1, 20);

        // then
        assertThat(result.rows()).extracting("id").containsExactly(validVideoId);
    }

    @Test
    @DisplayName("같은 영상의 유효 관계가 여러 개면 한 번만 반환한다")
    void 같은영상의유효관계다수_한번만반환한다() {
        // given
        UUID creatorId = insertCreator("채널D", "PUBLIC", "ACTIVE", "AVAILABLE");
        UUID videoId = insertVideo(creatorId, "영상D", "PUBLIC", "ACTIVE", "AVAILABLE");
        UUID restaurantId1 = insertRestaurant("맛집D1", "PUBLIC", "ACTIVE");
        UUID restaurantId2 = insertRestaurant("맛집D2", "PUBLIC", "ACTIVE");
        insertVisit(restaurantId1, creatorId, videoId, "PUBLIC", "ACTIVE", OffsetDateTime.now().minusDays(1));
        insertVisit(restaurantId2, creatorId, videoId, "PUBLIC", "ACTIVE", OffsetDateTime.now());

        // when
        CreatorEvidenceVideoPageResult result = creatorEvidenceVideoQueryPort.findPage(creatorId, 1, 20);

        // then
        assertThat(result.rows()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Visit 자체가 비공개면 목록에서 제외한다")
    void Visit이비공개_목록에서제외한다() {
        // given: BR-VISIT-005 — 관계 자체도 공개·활성이어야 조회에 사용한다.
        UUID creatorId = insertCreator("채널V1", "PUBLIC", "ACTIVE", "AVAILABLE");
        UUID restaurantId = insertRestaurant("비공개관계맛집", "PUBLIC", "ACTIVE");
        UUID videoId = insertVideo(creatorId, "비공개관계영상", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(restaurantId, creatorId, videoId, "PRIVATE", "ACTIVE", OffsetDateTime.now());

        // when
        CreatorEvidenceVideoPageResult result = creatorEvidenceVideoQueryPort.findPage(creatorId, 1, 20);

        // then
        assertThat(result.rows()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    @DisplayName("Visit 자체가 삭제되면 목록에서 제외한다")
    void Visit이삭제_목록에서제외한다() {
        // given: ck_visit__deleted_pair가 DELETED를 PRIVATE와만 허용하므로 두 값을 함께 넣는다.
        UUID creatorId = insertCreator("채널V2", "PUBLIC", "ACTIVE", "AVAILABLE");
        UUID restaurantId = insertRestaurant("삭제관계맛집", "PUBLIC", "ACTIVE");
        UUID videoId = insertVideo(creatorId, "삭제관계영상", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(restaurantId, creatorId, videoId, "PRIVATE", "DELETED", OffsetDateTime.now());

        // when
        CreatorEvidenceVideoPageResult result = creatorEvidenceVideoQueryPort.findPage(creatorId, 1, 20);

        // then
        assertThat(result.rows()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    @DisplayName("다른 유튜버의 유효 관계는 요청한 유튜버 목록에 섞이지 않는다")
    void 다른유튜버의유효관계_요청한유튜버목록에섞이지않는다() {
        // given
        UUID creatorId = insertCreator("채널S1", "PUBLIC", "ACTIVE", "AVAILABLE");
        UUID ownRestaurantId = insertRestaurant("본인맛집", "PUBLIC", "ACTIVE");
        UUID ownVideoId = insertVideo(creatorId, "본인영상", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(ownRestaurantId, creatorId, ownVideoId, "PUBLIC", "ACTIVE", OffsetDateTime.now());

        UUID otherCreatorId = insertCreator("채널S2", "PUBLIC", "ACTIVE", "AVAILABLE");
        UUID otherRestaurantId = insertRestaurant("타인맛집", "PUBLIC", "ACTIVE");
        UUID otherVideoId = insertVideo(otherCreatorId, "타인영상", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(otherRestaurantId, otherCreatorId, otherVideoId, "PUBLIC", "ACTIVE", OffsetDateTime.now());

        // when
        CreatorEvidenceVideoPageResult result = creatorEvidenceVideoQueryPort.findPage(creatorId, 1, 20);

        // then
        assertThat(result.rows()).extracting("id").containsExactly(ownVideoId);
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("가장 최근 유효 관계 생성 시각 내림차순, 동일하면 영상 ID 오름차순으로 정렬한다")
    void 정렬_최신관계시각내림차순_동일시각은영상ID오름차순() {
        // given
        UUID creatorId = insertCreator("채널E", "PUBLIC", "ACTIVE", "AVAILABLE");
        UUID restaurantId = insertRestaurant("맛집E", "PUBLIC", "ACTIVE");
        OffsetDateTime sameInstant = OffsetDateTime.now().minusHours(1);

        UUID oldestVideoId = insertVideo(creatorId, "가장오래된영상", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(restaurantId, creatorId, oldestVideoId, "PUBLIC", "ACTIVE", sameInstant.minusDays(1));

        UUID newestVideoId = insertVideo(creatorId, "가장최신영상", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(restaurantId, creatorId, newestVideoId, "PUBLIC", "ACTIVE", sameInstant.plusDays(1));

        UUID tieVideoIdA = insertVideo(creatorId, "동시각영상A", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(restaurantId, creatorId, tieVideoIdA, "PUBLIC", "ACTIVE", sameInstant);

        UUID tieVideoIdB = insertVideo(creatorId, "동시각영상B", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(restaurantId, creatorId, tieVideoIdB, "PUBLIC", "ACTIVE", sameInstant);

        /*
         * PostgreSQL uuid는 16바이트를 부호 없이 비교해 정렬한다. UUID.compareTo는 상위·하위 64비트를
         * 부호 있는 long으로 비교하므로 최상위 바이트가 0x80 이상인 값에서 DB와 순서가 달라진다.
         * 정규 문자열 사전순은 바이트 순서와 같으므로 이 비교로 DB와 같은 기대값을 만든다.
         */
        List<UUID> expectedTieOrder = tieVideoIdA.toString().compareTo(tieVideoIdB.toString()) <= 0
                ? List.of(tieVideoIdA, tieVideoIdB)
                : List.of(tieVideoIdB, tieVideoIdA);

        // when
        CreatorEvidenceVideoPageResult result = creatorEvidenceVideoQueryPort.findPage(creatorId, 1, 20);

        // then
        assertThat(result.rows()).extracting("id").containsExactly(
                newestVideoId, expectedTieOrder.get(0), expectedTieOrder.get(1), oldestVideoId);
    }

    @Test
    @DisplayName("page·size에 맞는 LIMIT·OFFSET을 적용하고 totalElements는 조건 전체 개수를 유지한다")
    void 페이지네이션_size와offset적용_totalElements는전체개수를유지한다() {
        // given: 5건을 만들고 size=2, page=2로 조회
        UUID creatorId = insertCreator("채널F", "PUBLIC", "ACTIVE", "AVAILABLE");
        UUID restaurantId = insertRestaurant("맛집F", "PUBLIC", "ACTIVE");
        List<UUID> videoIds = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID videoId = insertVideo(creatorId, "영상F" + i, "PUBLIC", "ACTIVE", "AVAILABLE");
            videoIds.add(videoId);
            insertVisit(restaurantId, creatorId, videoId, "PUBLIC", "ACTIVE",
                    OffsetDateTime.now().minusMinutes(i));
        }

        // when
        CreatorEvidenceVideoPageResult result = creatorEvidenceVideoQueryPort.findPage(creatorId, 2, 2);

        // then: 최신순 정렬 기준 3~4번째(0-base 인덱스 2,3)가 두 번째 페이지다.
        assertThat(result.totalElements()).isEqualTo(5L);
        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows()).extracting("id")
                .containsExactly(videoIds.get(2), videoIds.get(3));
    }

    @Test
    @DisplayName("유효 관계가 없으면 빈 목록과 전체 개수 0을 반환한다")
    void 유효관계없음_빈목록과전체개수0을반환한다() {
        // given
        UUID creatorId = insertCreator("채널G", "PUBLIC", "ACTIVE", "AVAILABLE");

        // when
        CreatorEvidenceVideoPageResult result = creatorEvidenceVideoQueryPort.findPage(creatorId, 1, 20);

        // then
        assertThat(result.rows()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    private UUID insertRestaurant(String name, String publicationStatus, String lifecycleStatus) {
        UUID id = UUID.randomUUID();
        OffsetDateTime deletedAt = "DELETED".equals(lifecycleStatus) ? OffsetDateTime.now() : null;
        jdbcTemplate.update(
                "INSERT INTO restaurant "
                        + "(id, region_id, food_category_id, name, kakao_place_id, kakao_place_url, "
                        + "road_address, phone_number, publication_status, lifecycle_status, deleted_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, SEED_REGION_ID, SEED_FOOD_CATEGORY_ID, name, "KAKAO-" + id,
                "https://example.com/place/" + id, "서울특별시 종로구 테스트로 1", "02-1234-5678",
                publicationStatus, lifecycleStatus, deletedAt);
        return id;
    }

    private UUID insertCreator(
            String channelName, String publicationStatus, String lifecycleStatus, String externalAvailabilityStatus) {
        UUID id = UUID.randomUUID();
        OffsetDateTime deletedAt = "DELETED".equals(lifecycleStatus) ? OffsetDateTime.now() : null;
        jdbcTemplate.update(
                "INSERT INTO creator "
                        + "(id, external_channel_id, channel_name, channel_url, publication_status, "
                        + "lifecycle_status, external_availability_status, external_status_checked_at, deleted_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, "UC-" + id, channelName, "https://www.youtube.com/channel/" + id,
                publicationStatus, lifecycleStatus, externalAvailabilityStatus, OffsetDateTime.now(), deletedAt);
        return id;
    }

    private UUID insertVideo(
            UUID creatorId, String title, String publicationStatus, String lifecycleStatus,
            String externalAvailabilityStatus) {
        UUID id = UUID.randomUUID();
        OffsetDateTime deletedAt = "DELETED".equals(lifecycleStatus) ? OffsetDateTime.now() : null;
        String publisherExternalChannelId = jdbcTemplate.queryForObject(
                "SELECT external_channel_id FROM creator WHERE id = ?", String.class, creatorId);
        jdbcTemplate.update(
                "INSERT INTO video "
                        + "(id, creator_id, external_video_id, publisher_external_channel_id, title, "
                        + "source_url, thumbnail_url, publication_status, lifecycle_status, "
                        + "external_availability_status, external_status_checked_at, deleted_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, creatorId, shortId("VID-"), publisherExternalChannelId, title,
                "https://www.youtube.com/watch?v=" + id, "https://i.ytimg.com/" + id + ".jpg",
                publicationStatus, lifecycleStatus, externalAvailabilityStatus, OffsetDateTime.now(), deletedAt);
        return id;
    }

    private void insertVisit(
            UUID restaurantId, UUID creatorId, UUID videoId, String publicationStatus, String lifecycleStatus,
            OffsetDateTime createdAt) {
        OffsetDateTime deletedAt = "DELETED".equals(lifecycleStatus) ? OffsetDateTime.now() : null;
        jdbcTemplate.update(
                "INSERT INTO visit "
                        + "(id, restaurant_id, creator_id, video_id, publication_status, lifecycle_status, "
                        + "created_at, deleted_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), restaurantId, creatorId, videoId, publicationStatus, lifecycleStatus,
                createdAt, deletedAt);
    }

    /** varchar(32) 컬럼(external_video_id)에 맞도록 UUID를 잘라 짧은 식별자를 만든다. */
    private String shortId(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 20);
    }
}

package com.masiton.orchestration;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masiton.orchestration.application.port.in.FindValidVisitContentByRestaurantQuery;
import com.masiton.orchestration.application.port.in.VisitContentResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * query-composition.md 5절이 지정한 콘텐츠 Query(공개·유효 Visit 기준 방문 유튜버·관련 영상)를
 * 실제 PostgreSQL에서 검증한다. business-rules.md BR-VISIT-004·005, BR-CREATOR-007,
 * BR-VIDEO-005·009 근거 판정을 다룬다. Restaurant ID 후보 조회(WS-01용)는
 * visit.VisitQueryIntegrationTest가 검증한다.
 */
import com.masiton.test.FullContextIntegrationTest;

@SpringBootTest
@DisplayName("맛집 상세 콘텐츠(방문 유튜버·관련 영상) 조회")
class VisitContentQueryIntegrationTest extends FullContextIntegrationTest {

    private static final UUID SEED_REGION_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID SEED_FOOD_CATEGORY_ID =
            UUID.fromString("20000000-0000-4000-8000-000000000001");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FindValidVisitContentByRestaurantQuery findValidVisitContentByRestaurantQuery;

    @Test
    @DisplayName("네대상모두공개유효_방문유튜버와영상필드가원본값과일치한다")
    void 네대상모두공개유효_방문유튜버와영상필드가원본값과일치한다() {
        // given
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        insertRestaurant(restaurantId, "PUBLIC", "ACTIVE");
        insertCreator(creatorId, "채널A", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVideo(videoId, creatorId, "영상A", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(UUID.randomUUID(), restaurantId, creatorId, videoId, "PUBLIC", "ACTIVE");

        // when
        VisitContentResult result = findValidVisitContentByRestaurantQuery
                .findValidVisitContentByRestaurant(restaurantId);

        // then
        assertThat(result.visitedBy()).extracting("id").containsExactly(creatorId);
        assertThat(result.visitedBy().get(0).channelUrl())
                .isEqualTo("https://www.youtube.com/channel/" + creatorId);
        assertThat(result.videos()).extracting("id").containsExactly(videoId);
        assertThat(result.videos().get(0).thumbnailUrl()).isEqualTo("https://i.ytimg.com/" + videoId + ".jpg");
        assertThat(result.videos().get(0).sourceUrl()).isEqualTo("https://www.youtube.com/watch?v=" + videoId);
    }

    @Test
    @DisplayName("Restaurant가비공개_두목록모두빈배열을반환한다")
    void Restaurant가비공개_두목록모두빈배열을반환한다() {
        // given
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        insertRestaurant(restaurantId, "PRIVATE", "ACTIVE");
        insertCreator(creatorId, "채널B", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVideo(videoId, creatorId, "영상B", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(UUID.randomUUID(), restaurantId, creatorId, videoId, "PUBLIC", "ACTIVE");

        // when
        VisitContentResult result = findValidVisitContentByRestaurantQuery
                .findValidVisitContentByRestaurant(restaurantId);

        // then
        assertThat(result.visitedBy()).isEmpty();
        assertThat(result.videos()).isEmpty();
    }

    @Test
    @DisplayName("Creator가비공개_두목록모두빈배열을반환한다")
    void Creator가비공개_두목록모두빈배열을반환한다() {
        // given
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        insertRestaurant(restaurantId, "PUBLIC", "ACTIVE");
        insertCreator(creatorId, "채널D", "PRIVATE", "ACTIVE", "AVAILABLE");
        insertVideo(videoId, creatorId, "영상D", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(UUID.randomUUID(), restaurantId, creatorId, videoId, "PUBLIC", "ACTIVE");

        // when
        VisitContentResult result = findValidVisitContentByRestaurantQuery
                .findValidVisitContentByRestaurant(restaurantId);

        // then
        assertThat(result.visitedBy()).isEmpty();
        assertThat(result.videos()).isEmpty();
    }

    @Test
    @DisplayName("Creator가외부이용불가_visitedBy에서제외된다")
    void Creator가외부이용불가_visitedBy에서제외된다() {
        // given: ck_creator__external_unavailable_private 제약상 UNAVAILABLE은 PRIVATE와 함께여야 한다.
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        insertRestaurant(restaurantId, "PUBLIC", "ACTIVE");
        insertCreator(creatorId, "채널F", "PRIVATE", "ACTIVE", "UNAVAILABLE");
        insertVideo(videoId, creatorId, "영상F", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(UUID.randomUUID(), restaurantId, creatorId, videoId, "PUBLIC", "ACTIVE");

        // when
        VisitContentResult result = findValidVisitContentByRestaurantQuery
                .findValidVisitContentByRestaurant(restaurantId);

        // then
        assertThat(result.visitedBy()).isEmpty();
    }

    @Test
    @DisplayName("Video가비공개_그Video를근거로한Visit전체를제외한다")
    void Video가비공개_그Video를근거로한Visit전체를제외한다() {
        // given: BR-VISIT-005 — 연결된 영상까지 공개·유효해야 조회 관계로 사용한다.
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        insertRestaurant(restaurantId, "PUBLIC", "ACTIVE");
        insertCreator(creatorId, "채널G", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVideo(videoId, creatorId, "영상G", "PRIVATE", "ACTIVE", "AVAILABLE");
        insertVisit(UUID.randomUUID(), restaurantId, creatorId, videoId, "PUBLIC", "ACTIVE");

        // when
        VisitContentResult result = findValidVisitContentByRestaurantQuery
                .findValidVisitContentByRestaurant(restaurantId);

        // then
        assertThat(result.visitedBy()).isEmpty();
        assertThat(result.videos()).isEmpty();
    }

    @Test
    @DisplayName("Visit가비공개_공개RestaurantCreatorVideo여도두목록에서제외한다")
    void Visit가비공개_공개RestaurantCreatorVideo여도두목록에서제외한다() {
        // given
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        insertRestaurant(restaurantId, "PUBLIC", "ACTIVE");
        insertCreator(creatorId, "채널H", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVideo(videoId, creatorId, "영상H", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(UUID.randomUUID(), restaurantId, creatorId, videoId, "PRIVATE", "ACTIVE");

        // when
        VisitContentResult result = findValidVisitContentByRestaurantQuery
                .findValidVisitContentByRestaurant(restaurantId);

        // then
        assertThat(result.visitedBy()).isEmpty();
        assertThat(result.videos()).isEmpty();
    }

    @Test
    @DisplayName("Visit가삭제인데공개상태_DB삭제상태쌍제약으로저장을거부한다")
    void Visit가삭제인데공개상태_DB삭제상태쌍제약으로저장을거부한다() {
        // given: ck_visit__deleted_pair는 삭제 Visit을 PRIVATE로 함께 전환하도록 강제한다.
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        insertRestaurant(restaurantId, "PUBLIC", "ACTIVE");
        insertCreator(creatorId, "채널I", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVideo(videoId, creatorId, "영상I", "PUBLIC", "ACTIVE", "AVAILABLE");

        // when & then
        assertThatThrownBy(() ->
                insertVisit(UUID.randomUUID(), restaurantId, creatorId, videoId, "PUBLIC", "DELETED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Visit가삭제_공개RestaurantCreatorVideo여도두목록에서제외한다")
    void Visit가삭제_공개RestaurantCreatorVideo여도두목록에서제외한다() {
        // given: DB 제약상 삭제 Visit은 반드시 PRIVATE이므로 유효한 삭제 상태 쌍으로 저장한다.
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        insertRestaurant(restaurantId, "PUBLIC", "ACTIVE");
        insertCreator(creatorId, "채널I", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVideo(videoId, creatorId, "영상I", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(UUID.randomUUID(), restaurantId, creatorId, videoId, "PRIVATE", "DELETED");

        // when
        VisitContentResult result = findValidVisitContentByRestaurantQuery
                .findValidVisitContentByRestaurant(restaurantId);

        // then
        assertThat(result.visitedBy()).isEmpty();
        assertThat(result.videos()).isEmpty();
    }

    @Test
    @DisplayName("같은Restaurant에같은Creator가다른Video로두번방문_visitedBy는Creator기준한번만videos는둘다반환한다")
    void 같은Restaurant에같은Creator가다른Video로두번방문_visitedBy는Creator기준한번만videos는둘다반환한다() {
        // given
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId1 = UUID.randomUUID();
        UUID videoId2 = UUID.randomUUID();
        insertRestaurant(restaurantId, "PUBLIC", "ACTIVE");
        insertCreator(creatorId, "채널L", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVideo(videoId1, creatorId, "가영상", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVideo(videoId2, creatorId, "나영상", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(UUID.randomUUID(), restaurantId, creatorId, videoId1, "PUBLIC", "ACTIVE");
        insertVisit(UUID.randomUUID(), restaurantId, creatorId, videoId2, "PUBLIC", "ACTIVE");

        // when
        VisitContentResult result = findValidVisitContentByRestaurantQuery
                .findValidVisitContentByRestaurant(restaurantId);

        // then
        assertThat(result.visitedBy()).extracting("id").containsExactly(creatorId);
        assertThat(result.videos()).extracting("id").containsExactlyInAnyOrder(videoId1, videoId2);
        assertThat(result.videos()).extracting("title").containsExactly("가영상", "나영상");
    }

    @Test
    @DisplayName("관계없음_두목록모두빈배열을반환한다")
    void 관계없음_두목록모두빈배열을반환한다() {
        // given
        UUID restaurantId = UUID.randomUUID();
        insertRestaurant(restaurantId, "PUBLIC", "ACTIVE");

        // when
        VisitContentResult result = findValidVisitContentByRestaurantQuery
                .findValidVisitContentByRestaurant(restaurantId);

        // then
        assertThat(result.visitedBy()).isEmpty();
        assertThat(result.videos()).isEmpty();
    }

    private void insertRestaurant(UUID id, String publicationStatus, String lifecycleStatus) {
        OffsetDateTime deletedAt = "DELETED".equals(lifecycleStatus) ? OffsetDateTime.now() : null;
        jdbcTemplate.update(
                "INSERT INTO restaurant "
                        + "(id, region_id, food_category_id, name, kakao_place_id, kakao_place_url, "
                        + "road_address, phone_number, publication_status, lifecycle_status, deleted_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                SEED_REGION_ID,
                SEED_FOOD_CATEGORY_ID,
                "테스트 맛집",
                "KAKAO-" + id,
                "https://example.com/place/" + id,
                "서울특별시 종로구 테스트로 1",
                "02-1234-5678",
                publicationStatus,
                lifecycleStatus,
                deletedAt);
    }

    private void insertCreator(
            UUID id,
            String channelName,
            String publicationStatus,
            String lifecycleStatus,
            String externalAvailabilityStatus) {
        OffsetDateTime deletedAt = "DELETED".equals(lifecycleStatus) ? OffsetDateTime.now() : null;
        jdbcTemplate.update(
                "INSERT INTO creator "
                        + "(id, external_channel_id, channel_name, channel_url, publication_status, "
                        + "lifecycle_status, external_availability_status, external_status_checked_at, deleted_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                "UC-" + id,
                channelName,
                "https://www.youtube.com/channel/" + id,
                publicationStatus,
                lifecycleStatus,
                externalAvailabilityStatus,
                OffsetDateTime.now(),
                deletedAt);
    }

    private void insertVideo(
            UUID id,
            UUID creatorId,
            String title,
            String publicationStatus,
            String lifecycleStatus,
            String externalAvailabilityStatus) {
        OffsetDateTime deletedAt = "DELETED".equals(lifecycleStatus) ? OffsetDateTime.now() : null;
        String publisherExternalChannelId = jdbcTemplate.queryForObject(
                "SELECT external_channel_id FROM creator WHERE id = ?", String.class, creatorId);
        jdbcTemplate.update(
                "INSERT INTO video "
                        + "(id, creator_id, external_video_id, publisher_external_channel_id, title, "
                        + "source_url, thumbnail_url, publication_status, lifecycle_status, "
                        + "external_availability_status, external_status_checked_at, deleted_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                creatorId,
                shortId("VID-"),
                publisherExternalChannelId,
                title,
                "https://www.youtube.com/watch?v=" + id,
                "https://i.ytimg.com/" + id + ".jpg",
                publicationStatus,
                lifecycleStatus,
                externalAvailabilityStatus,
                OffsetDateTime.now(),
                deletedAt);
    }

    private void insertVisit(
            UUID id,
            UUID restaurantId,
            UUID creatorId,
            UUID videoId,
            String publicationStatus,
            String lifecycleStatus) {
        OffsetDateTime deletedAt = "DELETED".equals(lifecycleStatus) ? OffsetDateTime.now() : null;
        jdbcTemplate.update(
                "INSERT INTO visit "
                        + "(id, restaurant_id, creator_id, video_id, publication_status, lifecycle_status, "
                        + "deleted_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,
                restaurantId,
                creatorId,
                videoId,
                publicationStatus,
                lifecycleStatus,
                deletedAt);
    }

    /** varchar(32) 컬럼(external_video_id)에 맞도록 UUID를 잘라 짧은 식별자를 만든다. */
    private String shortId(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 20);
    }
}

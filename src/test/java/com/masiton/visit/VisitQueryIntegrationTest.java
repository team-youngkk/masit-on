package com.masiton.visit;

import java.time.OffsetDateTime;
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

import com.masiton.visit.application.port.in.CreatorRestaurantCandidates;
import com.masiton.visit.application.port.in.FindDistinctValidRestaurantIdsByCreatorQuery;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * business-rules.md BR-VISIT-001~007, BR-CREATOR-007, BR-VIDEO-005·009 근거 공개·유효 판정을
 * 실제 PostgreSQL에서 검증한다. ConstraintViolationIntegrationTest와 같은 패턴(순수 JDBC INSERT
 * Fixture, PostgreSQLContainer)을 따르되, 판정 대상인 publication_status·lifecycle_status·
 * external_availability_status를 시나리오별로 다르게 구성한다. 각 테스트는 고유 UUID를 스스로
 * 준비하므로 다른 테스트 데이터에 의존하지 않는다.
 *
 * <p>맛집 상세 콘텐츠(방문 유튜버·관련 영상) 조회는 orchestration.VisitContentQueryIntegrationTest가
 * 검증한다(query-composition.md 5절에 따라 이관됨).
 */
@SpringBootTest
@com.masiton.test.TestProfile
@DisplayName("Visit 공개·유효 조합 판정")
class VisitQueryIntegrationTest extends com.masiton.test.FullContextIntegrationTest {

    // seed-data-plan.md 2·3절 고정 기준 데이터. 초기 스키마 baseline이 적재하므로 참조만 하고 수정하지 않는다.
    private static final UUID SEED_REGION_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID SEED_FOOD_CATEGORY_ID =
            UUID.fromString("20000000-0000-4000-8000-000000000001");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FindDistinctValidRestaurantIdsByCreatorQuery findDistinctValidRestaurantIdsByCreatorQuery;

    @Test
    @DisplayName("네대상모두공개유효_creatorPublic참에후보Restaurant를포함한다")
    void 네대상모두공개유효_creatorPublic참에후보Restaurant를포함한다() {
        // given
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        insertRestaurant(restaurantId, "PUBLIC", "ACTIVE");
        insertCreator(creatorId, "채널A", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVideo(videoId, creatorId, "영상A", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(UUID.randomUUID(), restaurantId, creatorId, videoId, "PUBLIC", "ACTIVE");

        // when
        CreatorRestaurantCandidates result = findDistinctValidRestaurantIdsByCreatorQuery
                .findDistinctValidRestaurantIdsByCreator(creatorId);

        // then
        assertThat(result.creatorPublic()).isTrue();
        assertThat(result.restaurantIds()).containsExactly(restaurantId);
    }

    @Test
    @DisplayName("Restaurant가비공개_creatorPublic은참이지만후보에서제외된다")
    void Restaurant가비공개_creatorPublic은참이지만후보에서제외된다() {
        // given
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        insertRestaurant(restaurantId, "PRIVATE", "ACTIVE");
        insertCreator(creatorId, "채널B", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVideo(videoId, creatorId, "영상B", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(UUID.randomUUID(), restaurantId, creatorId, videoId, "PUBLIC", "ACTIVE");

        // when
        CreatorRestaurantCandidates result = findDistinctValidRestaurantIdsByCreatorQuery
                .findDistinctValidRestaurantIdsByCreator(creatorId);

        // then
        assertThat(result.creatorPublic()).isTrue();
        assertThat(result.restaurantIds()).isEmpty();
    }

    @Test
    @DisplayName("Restaurant가삭제상태_후보에서제외된다")
    void Restaurant가삭제상태_후보에서제외된다() {
        // given
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        insertRestaurant(restaurantId, "PRIVATE", "DELETED");
        insertCreator(creatorId, "채널C", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVideo(videoId, creatorId, "영상C", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(UUID.randomUUID(), restaurantId, creatorId, videoId, "PUBLIC", "ACTIVE");

        // when
        CreatorRestaurantCandidates result = findDistinctValidRestaurantIdsByCreatorQuery
                .findDistinctValidRestaurantIdsByCreator(creatorId);

        // then
        assertThat(result.restaurantIds()).isEmpty();
    }

    @Test
    @DisplayName("Creator가비공개_creatorPublic거짓을반환한다")
    void Creator가비공개_creatorPublic거짓을반환한다() {
        // given
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        insertRestaurant(restaurantId, "PUBLIC", "ACTIVE");
        insertCreator(creatorId, "채널D", "PRIVATE", "ACTIVE", "AVAILABLE");
        insertVideo(videoId, creatorId, "영상D", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(UUID.randomUUID(), restaurantId, creatorId, videoId, "PUBLIC", "ACTIVE");

        // when
        CreatorRestaurantCandidates result = findDistinctValidRestaurantIdsByCreatorQuery
                .findDistinctValidRestaurantIdsByCreator(creatorId);

        // then: creator-discovery-api.md 127행 근거 — 호출자가 이 값으로 400 처리를 판단한다
        assertThat(result.creatorPublic()).isFalse();
        assertThat(result.restaurantIds()).isEmpty();
    }

    @Test
    @DisplayName("Creator가삭제상태_creatorPublic거짓을반환한다")
    void Creator가삭제상태_creatorPublic거짓을반환한다() {
        // given
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        insertRestaurant(restaurantId, "PUBLIC", "ACTIVE");
        insertCreator(creatorId, "채널E", "PRIVATE", "DELETED", "AVAILABLE");
        insertVideo(videoId, creatorId, "영상E", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(UUID.randomUUID(), restaurantId, creatorId, videoId, "PUBLIC", "ACTIVE");

        // when
        CreatorRestaurantCandidates result = findDistinctValidRestaurantIdsByCreatorQuery
                .findDistinctValidRestaurantIdsByCreator(creatorId);

        // then
        assertThat(result.creatorPublic()).isFalse();
        assertThat(result.restaurantIds()).isEmpty();
    }

    @Test
    @DisplayName("Creator가외부이용불가_creatorPublic거짓을반환한다")
    void Creator가외부이용불가_creatorPublic거짓을반환한다() {
        // given: ck_creator__external_unavailable_private 제약상 UNAVAILABLE은 PRIVATE와 함께여야 한다.
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        insertRestaurant(restaurantId, "PUBLIC", "ACTIVE");
        insertCreator(creatorId, "채널F", "PRIVATE", "ACTIVE", "UNAVAILABLE");
        insertVideo(videoId, creatorId, "영상F", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(UUID.randomUUID(), restaurantId, creatorId, videoId, "PUBLIC", "ACTIVE");

        // when
        CreatorRestaurantCandidates result = findDistinctValidRestaurantIdsByCreatorQuery
                .findDistinctValidRestaurantIdsByCreator(creatorId);

        // then
        assertThat(result.creatorPublic()).isFalse();
        assertThat(result.restaurantIds()).isEmpty();
    }

    @Test
    @DisplayName("존재하지않는Creator_creatorPublic거짓을반환한다")
    void 존재하지않는Creator_creatorPublic거짓을반환한다() {
        // given: creator 테이블에 아예 행이 없는 무작위 UUID
        UUID neverInsertedCreatorId = UUID.randomUUID();

        // when
        CreatorRestaurantCandidates result = findDistinctValidRestaurantIdsByCreatorQuery
                .findDistinctValidRestaurantIdsByCreator(neverInsertedCreatorId);

        // then
        assertThat(result.creatorPublic()).isFalse();
        assertThat(result.restaurantIds()).isEmpty();
    }

    @Test
    @DisplayName("Video가비공개_후보에서제외된다")
    void Video가비공개_후보에서제외된다() {
        // given
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        insertRestaurant(restaurantId, "PUBLIC", "ACTIVE");
        insertCreator(creatorId, "채널G", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVideo(videoId, creatorId, "영상G", "PRIVATE", "ACTIVE", "AVAILABLE");
        insertVisit(UUID.randomUUID(), restaurantId, creatorId, videoId, "PUBLIC", "ACTIVE");

        // when
        CreatorRestaurantCandidates result = findDistinctValidRestaurantIdsByCreatorQuery
                .findDistinctValidRestaurantIdsByCreator(creatorId);

        // then
        assertThat(result.restaurantIds()).isEmpty();
    }

    @Test
    @DisplayName("Video가삭제상태_후보에서제외된다")
    void Video가삭제상태_후보에서제외된다() {
        // given
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        insertRestaurant(restaurantId, "PUBLIC", "ACTIVE");
        insertCreator(creatorId, "채널H", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVideo(videoId, creatorId, "영상H", "PRIVATE", "DELETED", "AVAILABLE");
        insertVisit(UUID.randomUUID(), restaurantId, creatorId, videoId, "PUBLIC", "ACTIVE");

        // when
        CreatorRestaurantCandidates result = findDistinctValidRestaurantIdsByCreatorQuery
                .findDistinctValidRestaurantIdsByCreator(creatorId);

        // then
        assertThat(result.restaurantIds()).isEmpty();
    }

    @Test
    @DisplayName("Video가외부이용불가_후보에서제외된다")
    void Video가외부이용불가_후보에서제외된다() {
        // given: ck_video__external_unavailable_private 제약상 UNAVAILABLE은 PRIVATE와 함께여야 한다.
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        insertRestaurant(restaurantId, "PUBLIC", "ACTIVE");
        insertCreator(creatorId, "채널I", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVideo(videoId, creatorId, "영상I", "PRIVATE", "ACTIVE", "UNAVAILABLE");
        insertVisit(UUID.randomUUID(), restaurantId, creatorId, videoId, "PUBLIC", "ACTIVE");

        // when
        CreatorRestaurantCandidates result = findDistinctValidRestaurantIdsByCreatorQuery
                .findDistinctValidRestaurantIdsByCreator(creatorId);

        // then
        assertThat(result.restaurantIds()).isEmpty();
    }

    @Test
    @DisplayName("Visit자체가비공개_후보에서제외된다")
    void Visit자체가비공개_후보에서제외된다() {
        // given
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        insertRestaurant(restaurantId, "PUBLIC", "ACTIVE");
        insertCreator(creatorId, "채널J", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVideo(videoId, creatorId, "영상J", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(UUID.randomUUID(), restaurantId, creatorId, videoId, "PRIVATE", "ACTIVE");

        // when
        CreatorRestaurantCandidates result = findDistinctValidRestaurantIdsByCreatorQuery
                .findDistinctValidRestaurantIdsByCreator(creatorId);

        // then
        assertThat(result.restaurantIds()).isEmpty();
    }

    @Test
    @DisplayName("Visit자체가삭제상태_후보에서제외된다")
    void Visit자체가삭제상태_후보에서제외된다() {
        // given
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        insertRestaurant(restaurantId, "PUBLIC", "ACTIVE");
        insertCreator(creatorId, "채널K", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVideo(videoId, creatorId, "영상K", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(UUID.randomUUID(), restaurantId, creatorId, videoId, "PRIVATE", "DELETED");

        // when
        CreatorRestaurantCandidates result = findDistinctValidRestaurantIdsByCreatorQuery
                .findDistinctValidRestaurantIdsByCreator(creatorId);

        // then
        assertThat(result.restaurantIds()).isEmpty();
    }

    @Test
    @DisplayName("같은Restaurant에같은Creator가다른Video로두번방문_후보는한번만반환한다")
    void 같은Restaurant에같은Creator가다른Video로두번방문_후보는한번만반환한다() {
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
        CreatorRestaurantCandidates result = findDistinctValidRestaurantIdsByCreatorQuery
                .findDistinctValidRestaurantIdsByCreator(creatorId);

        // then
        assertThat(result.restaurantIds()).containsExactly(restaurantId);
    }

    @Test
    @DisplayName("같은Video가여러Restaurant의근거_각Restaurant에서독립적으로판정된다")
    void 같은Video가여러Restaurant의근거_각Restaurant에서독립적으로판정된다() {
        // given
        UUID restaurantId1 = UUID.randomUUID();
        UUID restaurantId2 = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        insertRestaurant(restaurantId1, "PUBLIC", "ACTIVE");
        insertRestaurant(restaurantId2, "PRIVATE", "ACTIVE");
        insertCreator(creatorId, "채널M", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVideo(videoId, creatorId, "영상M", "PUBLIC", "ACTIVE", "AVAILABLE");
        insertVisit(UUID.randomUUID(), restaurantId1, creatorId, videoId, "PUBLIC", "ACTIVE");
        insertVisit(UUID.randomUUID(), restaurantId2, creatorId, videoId, "PUBLIC", "ACTIVE");

        // when
        CreatorRestaurantCandidates result = findDistinctValidRestaurantIdsByCreatorQuery
                .findDistinctValidRestaurantIdsByCreator(creatorId);

        // then: 공개 Restaurant1만 후보에 포함되고, 비공개 Restaurant2는 제외된다.
        assertThat(result.restaurantIds()).containsExactly(restaurantId1);
    }

    @Test
    @DisplayName("관계없음_공개Creator이면creatorPublic참에빈후보를반환한다")
    void 관계없음_공개Creator이면creatorPublic참에빈후보를반환한다() {
        // given
        UUID creatorId = UUID.randomUUID();
        insertCreator(creatorId, "채널N", "PUBLIC", "ACTIVE", "AVAILABLE");

        // when
        CreatorRestaurantCandidates result = findDistinctValidRestaurantIdsByCreatorQuery
                .findDistinctValidRestaurantIdsByCreator(creatorId);

        // then
        assertThat(result.creatorPublic()).isTrue();
        assertThat(result.restaurantIds()).isEmpty();
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

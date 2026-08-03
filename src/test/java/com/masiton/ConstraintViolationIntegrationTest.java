package com.masiton;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * constraint-mapping.md 2~4절 근거 제약이 실제 PostgreSQL에서 강제되는지 확인한다.
 * 각 시나리오는 순수 JDBC INSERT로 정상 저장(Given)과 제약 위반 저장(When/Then)을 수행하고,
 * 위반 시 {@link DataIntegrityViolationException}(SQLSTATE 23503/23505/23514 공통 변환)이
 * 발생하는지 단언한다. 각 테스트는 고유한 UUID·외부 식별자를 스스로 준비하므로
 * 다른 테스트가 만든 데이터에 의존하지 않는다.
 */
@SpringBootTest
@com.masiton.test.TestProfile
@Testcontainers
@DisplayName("제약 조건 위반")
class ConstraintViolationIntegrationTest {

    // seed-data-plan.md 2·3절 고정 기준 데이터. 초기 스키마 baseline이 적재하므로 참조만 하고 수정하지 않는다.
    private static final UUID SEED_REGION_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID SEED_FOOD_CATEGORY_ID =
            UUID.fromString("20000000-0000-4000-8000-000000000001");

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

    @Test
    @DisplayName("동일 kakao_place_id로 Restaurant를 두 번 저장하면 uk_restaurant__kakao_place_id 위반으로 실패한다")
    void Restaurant저장_동일카카오장소ID로중복저장_유일제약위반으로실패한다() {
        // given
        String kakaoPlaceId = "KAKAO-" + UUID.randomUUID();
        insertRestaurant(UUID.randomUUID(), kakaoPlaceId, SEED_REGION_ID);

        // when & then
        assertThatThrownBy(() -> insertRestaurant(UUID.randomUUID(), kakaoPlaceId, SEED_REGION_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("동일 external_channel_id로 Creator를 두 번 저장하면 uk_creator__external_channel_id 위반으로 실패한다")
    void Creator저장_동일채널ID로중복저장_유일제약위반으로실패한다() {
        // given
        String externalChannelId = "UC-" + UUID.randomUUID();
        insertCreator(UUID.randomUUID(), externalChannelId);

        // when & then
        assertThatThrownBy(() -> insertCreator(UUID.randomUUID(), externalChannelId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("동일 external_video_id로 Video를 두 번 저장하면 uk_video__external_video_id 위반으로 실패한다")
    void Video저장_동일원본영상ID로중복저장_유일제약위반으로실패한다() {
        // given
        UUID creatorId = UUID.randomUUID();
        String channelId = "UC-" + UUID.randomUUID();
        insertCreator(creatorId, channelId);
        String externalVideoId = shortId("VID-");
        insertVideo(UUID.randomUUID(), creatorId, externalVideoId, channelId);

        // when & then
        assertThatThrownBy(() -> insertVideo(UUID.randomUUID(), creatorId, externalVideoId, channelId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName(
            "동일 (restaurant, creator, video) 조합으로 Visit을 두 번 저장하면 "
                    + "uk_visit__restaurant_creator_video 위반으로 실패한다")
    void Visit저장_동일맛집유튜버영상조합으로중복저장_유일제약위반으로실패한다() {
        // given
        UUID restaurantId = UUID.randomUUID();
        insertRestaurant(restaurantId, "KAKAO-" + UUID.randomUUID(), SEED_REGION_ID);
        UUID creatorId = UUID.randomUUID();
        String channelId = "UC-" + UUID.randomUUID();
        insertCreator(creatorId, channelId);
        UUID videoId = UUID.randomUUID();
        insertVideo(videoId, creatorId, shortId("VID-"), channelId);
        insertVisit(UUID.randomUUID(), restaurantId, creatorId, videoId);

        // when & then
        assertThatThrownBy(() -> insertVisit(UUID.randomUUID(), restaurantId, creatorId, videoId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Video의 게시 채널이 연결된 Creator의 채널과 다르면 fk_video__creator_channel 복합 FK 위반으로 실패한다")
    void Video저장_게시채널이연결된Creator채널과다름_복합FK위반으로실패한다() {
        // given: creator는 channelA로 존재하지만, video는 channelB를 게시 채널로 지정한다.
        UUID creatorId = UUID.randomUUID();
        String actualChannelId = "UC-" + UUID.randomUUID();
        insertCreator(creatorId, actualChannelId);
        String mismatchedChannelId = "UC-" + UUID.randomUUID();

        // when & then
        assertThatThrownBy(() ->
                        insertVideo(UUID.randomUUID(), creatorId, shortId("VID-"), mismatchedChannelId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Visit의 creator_id가 Video의 creator_id와 다르면 fk_visit__video_creator 복합 FK 위반으로 실패한다")
    void Visit저장_creatorId가Video의creatorId와다름_복합FK위반으로실패한다() {
        // given: video는 creatorA에 연결되어 있고, visit은 creatorB를 지정한다.
        UUID restaurantId = UUID.randomUUID();
        insertRestaurant(restaurantId, "KAKAO-" + UUID.randomUUID(), SEED_REGION_ID);

        UUID creatorAId = UUID.randomUUID();
        String channelA = "UC-" + UUID.randomUUID();
        insertCreator(creatorAId, channelA);
        UUID videoId = UUID.randomUUID();
        insertVideo(videoId, creatorAId, shortId("VID-"), channelA);

        UUID creatorBId = UUID.randomUUID();
        String channelB = "UC-" + UUID.randomUUID();
        insertCreator(creatorBId, channelB);

        // when & then
        assertThatThrownBy(() -> insertVisit(UUID.randomUUID(), restaurantId, creatorBId, videoId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("lifecycle_status가 DELETED인데 deleted_at이 null이면 ck_restaurant__deleted_pair 위반으로 실패한다")
    void Restaurant저장_삭제상태인데삭제시각이null_CHECK제약위반으로실패한다() {
        // given: 정상 ACTIVE 저장은 성공한다.
        insertRestaurant(UUID.randomUUID(), "KAKAO-" + UUID.randomUUID(), SEED_REGION_ID);

        // when & then: DELETED인데 deleted_at을 채우지 않는다.
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO restaurant "
                                + "(id, region_id, food_category_id, name, kakao_place_id, kakao_place_url, "
                                + "road_address, phone_number, lifecycle_status, deleted_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DELETED', NULL)",
                        UUID.randomUUID(),
                        SEED_REGION_ID,
                        SEED_FOOD_CATEGORY_ID,
                        "테스트 맛집",
                        "KAKAO-" + UUID.randomUUID(),
                        "https://example.com/place/" + UUID.randomUUID(),
                        "서울특별시 종로구 테스트로 1",
                        "02-1234-5678"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("존재하지 않는 region_id로 Restaurant를 저장하면 fk_restaurant__region 위반으로 실패한다")
    void Restaurant저장_존재하지않는Region참조_FK위반으로실패한다() {
        // given
        UUID nonExistentRegionId = UUID.randomUUID();

        // when & then
        assertThatThrownBy(() -> insertRestaurant(
                        UUID.randomUUID(), "KAKAO-" + UUID.randomUUID(), nonExistentRegionId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("profile_image_url이 빈 문자열이면 ck_creator__profile_image_url_https 위반으로 실패한다")
    void Creator저장_프로필이미지URL빈문자열_CHECK제약위반으로실패한다() {
        // given
        UUID creatorId = UUID.randomUUID();
        String externalChannelId = "UC-" + UUID.randomUUID();

        // when & then
        assertThatThrownBy(() ->
                        insertCreatorWithDisplayFields(creatorId, externalChannelId, "", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("profile_image_url이 https가 아니면 ck_creator__profile_image_url_https 위반으로 실패한다")
    void Creator저장_프로필이미지URL이HTTPS가아님_CHECK제약위반으로실패한다() {
        // given
        UUID creatorId = UUID.randomUUID();
        String externalChannelId = "UC-" + UUID.randomUUID();

        // when & then
        assertThatThrownBy(() -> insertCreatorWithDisplayFields(
                        creatorId, externalChannelId, "http://example.com/profile.png", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("description이 빈 문자열이면 ck_creator__description_not_blank 위반으로 실패한다")
    void Creator저장_소개빈문자열_CHECK제약위반으로실패한다() {
        // given
        UUID creatorId = UUID.randomUUID();
        String externalChannelId = "UC-" + UUID.randomUUID();

        // when & then
        assertThatThrownBy(() ->
                        insertCreatorWithDisplayFields(creatorId, externalChannelId, null, "", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("handle이 빈 문자열이면 ck_creator__handle_not_blank 위반으로 실패한다")
    void Creator저장_handle빈문자열_CHECK제약위반으로실패한다() {
        // given
        UUID creatorId = UUID.randomUUID();
        String externalChannelId = "UC-" + UUID.randomUUID();

        // when & then
        assertThatThrownBy(() ->
                        insertCreatorWithDisplayFields(creatorId, externalChannelId, null, null, ""))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("상세 표시 열이 모두 NULL이거나 모두 유효한 값이면 정상 저장된다")
    void Creator저장_상세표시열null과유효한값_모두성공한다() {
        // given
        UUID nullDisplayCreatorId = UUID.randomUUID();
        UUID filledDisplayCreatorId = UUID.randomUUID();

        // when
        insertCreatorWithDisplayFields(
                nullDisplayCreatorId, "UC-" + UUID.randomUUID(), null, null, null);
        insertCreatorWithDisplayFields(
                filledDisplayCreatorId,
                "UC-" + UUID.randomUUID(),
                "https://example.com/profile.png",
                "채널 소개",
                "@handle");

        // then
        Integer savedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM creator WHERE id IN (?, ?)",
                Integer.class,
                nullDisplayCreatorId,
                filledDisplayCreatorId);
        assertThat(savedCount).isEqualTo(2);
    }

    private void insertCreatorWithDisplayFields(
            UUID id, String externalChannelId, String profileImageUrl, String description, String handle) {
        jdbcTemplate.update(
                "INSERT INTO creator "
                        + "(id, external_channel_id, channel_name, channel_url, external_status_checked_at, "
                        + "profile_image_url, description, handle) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                externalChannelId,
                "테스트 채널",
                "https://example.com/channel/" + externalChannelId,
                OffsetDateTime.now(),
                profileImageUrl,
                description,
                handle);
    }

    private void insertRestaurant(UUID id, String kakaoPlaceId, UUID regionId) {
        jdbcTemplate.update(
                "INSERT INTO restaurant "
                        + "(id, region_id, food_category_id, name, kakao_place_id, kakao_place_url, "
                        + "road_address, phone_number) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                regionId,
                SEED_FOOD_CATEGORY_ID,
                "테스트 맛집",
                kakaoPlaceId,
                "https://example.com/place/" + kakaoPlaceId,
                "서울특별시 종로구 테스트로 1",
                "02-1234-5678");
    }

    private void insertCreator(UUID id, String externalChannelId) {
        jdbcTemplate.update(
                "INSERT INTO creator "
                        + "(id, external_channel_id, channel_name, channel_url, external_status_checked_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                id,
                externalChannelId,
                "테스트 채널",
                "https://example.com/channel/" + externalChannelId,
                OffsetDateTime.now());
    }

    private void insertVideo(UUID id, UUID creatorId, String externalVideoId, String publisherExternalChannelId) {
        jdbcTemplate.update(
                "INSERT INTO video "
                        + "(id, creator_id, external_video_id, publisher_external_channel_id, title, "
                        + "source_url, thumbnail_url, external_status_checked_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                creatorId,
                externalVideoId,
                publisherExternalChannelId,
                "테스트 영상",
                "https://example.com/video/" + externalVideoId,
                "https://example.com/thumbnail/" + externalVideoId,
                OffsetDateTime.now());
    }

    private void insertVisit(UUID id, UUID restaurantId, UUID creatorId, UUID videoId) {
        jdbcTemplate.update(
                "INSERT INTO visit (id, restaurant_id, creator_id, video_id) VALUES (?, ?, ?, ?)",
                id,
                restaurantId,
                creatorId,
                videoId);
    }

    /** varchar(32) 컬럼(external_video_id)에 맞도록 UUID를 잘라 짧은 식별자를 만든다. */
    private String shortId(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 20);
    }
}

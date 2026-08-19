package com.masiton.orchestration.infrastructure.rollback;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.masiton.orchestration.application.port.out.AiRegisteredContentStore;
import com.masiton.test.FullContextIntegrationTest;

/**
 * PR #244 리뷰 지적사항: {@code RegistrationUnitCommandService}가 동시 요청에 선점당한 등록을
 * 보상할 때 {@code makePrivateIfCreated}(감사 보존용 PRIVATE 전환)를 쓰면 {@code kakao_place_id}
 * unique 제약이 남아 재시도가 영구히 막힌다는 지적을 반영해, {@code deleteIfCreated}가 실제로
 * 행을 지워 같은 {@code kakao_place_id}로 재시도할 수 있음을 PostgreSQL로 검증한다.
 */
@DisplayName("AI 등록 콘텐츠 하드 삭제 PostgreSQL 검증")
@SpringBootTest
class JdbcAiRegisteredContentStorePostgreSqlIntegrationTest extends FullContextIntegrationTest {

    private static final UUID REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID FOOD_CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");

    @Autowired
    private AiRegisteredContentStore store;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String kakaoPlaceId;
    private String channelId;
    private String videoExternalId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        kakaoPlaceId = "kakao-" + suffix;
        channelId = "channel-" + suffix;
        videoExternalId = "video-" + suffix;
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM visit WHERE creator_id IN (SELECT id FROM creator WHERE external_channel_id = ?)",
                channelId);
        jdbcTemplate.update("DELETE FROM video WHERE external_video_id = ?", videoExternalId);
        jdbcTemplate.update("DELETE FROM restaurant WHERE kakao_place_id = ?", kakaoPlaceId);
        jdbcTemplate.update("DELETE FROM creator WHERE external_channel_id = ?", channelId);
    }

    @Test
    @DisplayName("동시 요청에 선점당한 등록의 4종 콘텐츠를 하드 삭제하면 같은 kakaoPlaceId로 즉시 재시도할 수 있다")
    void deleteIfCreated_보상삭제후_같은kakaoPlaceId로재등록할수있다() {
        // Given: 경쟁에서 진 시도가 이미 만든 4종 콘텐츠
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        insertFixtures(restaurantId, creatorId, videoId, visitId);
        assertThat(count("restaurant", "kakao_place_id = ?", kakaoPlaceId)).isEqualTo(1);

        // When
        store.deleteIfCreated(restaurantId, true, creatorId, true, videoId, true, visitId, true);

        // Then: 행 자체가 사라져 unique 제약이 더 이상 재시도를 막지 않는다
        assertThat(count("restaurant", "id = ?", restaurantId)).isZero();
        assertThat(count("creator", "id = ?", creatorId)).isZero();
        assertThat(count("video", "id = ?", videoId)).isZero();
        assertThat(count("visit", "id = ?", visitId)).isZero();
        assertThat(count("restaurant", "kakao_place_id = ?", kakaoPlaceId)).isZero();

        UUID retryRestaurantId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO restaurant (id, region_id, food_category_id, name, kakao_place_id, kakao_place_url,
                    road_address, phone_number)
                VALUES (?, ?, ?, '행복식당(재시도)', ?, 'https://place.map.kakao.com/1', '서울특별시 마포구 월드컵로 1', '02-1234-5678')
                """, retryRestaurantId, REGION_ID, FOOD_CATEGORY_ID, kakaoPlaceId);

        assertThat(count("restaurant", "id = ?", retryRestaurantId)).isEqualTo(1);
    }

    @Test
    @DisplayName("기존 미완성 Video에 이번에 만든 Creator를 연결한 경우 Creator를 지우지 않고 보존하며 예외 없이 완료한다")
    void deleteIfCreated_기존Video가참조하는Creator는삭제하지않고예외없이완료한다() {
        // Given: VerifiedVideoRegistrationService.existingWithCreator()가 creator_id가 비어 있던
        // 기존 Video에 이번 시도의 새 Creator를 연결한 상태(videoCreated=false, creatorCreated=true)
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        insertFixtures(restaurantId, creatorId, videoId, visitId);

        // When
        store.deleteIfCreated(restaurantId, true, creatorId, true, videoId, false, visitId, true);

        // Then: restaurant·visit는 지워지지만, video가 참조하는 creator는 FK 위반 없이 보존된다
        assertThat(count("restaurant", "id = ?", restaurantId)).isZero();
        assertThat(count("visit", "id = ?", visitId)).isZero();
        assertThat(count("video", "id = ?", videoId)).isEqualTo(1);
        assertThat(count("creator", "id = ?", creatorId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT creator_id FROM video WHERE id = ?", UUID.class, videoId))
                .isEqualTo(creatorId);

        UUID retryRestaurantId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO restaurant (id, region_id, food_category_id, name, kakao_place_id, kakao_place_url,
                    road_address, phone_number)
                VALUES (?, ?, ?, '행복식당(재시도)', ?, 'https://place.map.kakao.com/1', '서울특별시 마포구 월드컵로 1', '02-1234-5678')
                """, retryRestaurantId, REGION_ID, FOOD_CATEGORY_ID, kakaoPlaceId);
        assertThat(count("restaurant", "id = ?", retryRestaurantId)).isEqualTo(1);
    }

    @Test
    @DisplayName("재사용 자원(created=false)은 하드 삭제 대상에서 제외한다")
    void deleteIfCreated_재사용자원은삭제하지않는다() {
        // Given: 크리에이터·영상은 재사용(created=false), 맛집·방문만 이번에 새로 만든 자원
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        insertFixtures(restaurantId, creatorId, videoId, visitId);

        // When
        store.deleteIfCreated(restaurantId, true, creatorId, false, videoId, false, visitId, true);

        // Then
        assertThat(count("restaurant", "id = ?", restaurantId)).isZero();
        assertThat(count("visit", "id = ?", visitId)).isZero();
        assertThat(count("creator", "id = ?", creatorId)).isEqualTo(1);
        assertThat(count("video", "id = ?", videoId)).isEqualTo(1);
    }

    private void insertFixtures(UUID restaurantId, UUID creatorId, UUID videoId, UUID visitId) {
        jdbcTemplate.update("""
                INSERT INTO creator (id, external_channel_id, channel_name, channel_url, external_status_checked_at)
                VALUES (?, ?, '테스트 채널', 'https://example.com/channel', now())
                """, creatorId, channelId);
        jdbcTemplate.update("""
                INSERT INTO restaurant (id, region_id, food_category_id, name, kakao_place_id, kakao_place_url,
                    road_address, phone_number)
                VALUES (?, ?, ?, '행복식당', ?, 'https://place.map.kakao.com/1', '서울특별시 마포구 월드컵로 1', '02-1234-5678')
                """, restaurantId, REGION_ID, FOOD_CATEGORY_ID, kakaoPlaceId);
        jdbcTemplate.update("""
                INSERT INTO video (id, creator_id, external_video_id, publisher_external_channel_id, title,
                    source_url, thumbnail_url, external_status_checked_at)
                VALUES (?, ?, ?, ?, '테스트 영상', 'https://example.com/video', 'https://example.com/thumb', now())
                """, videoId, creatorId, videoExternalId, channelId);
        jdbcTemplate.update("INSERT INTO visit (id, restaurant_id, creator_id, video_id) VALUES (?, ?, ?, ?)",
                visitId, restaurantId, creatorId, videoId);
    }

    private int count(String table, String predicate, Object... args) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table + " WHERE " + predicate,
                Integer.class, args);
    }
}

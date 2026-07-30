package com.masiton.personalization.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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

import com.masiton.personalization.application.port.in.PersonalRestaurantPage;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@com.masiton.test.TestProfile
@Testcontainers
@DisplayName("개인 맛집 JDBC 조회")
class JdbcPersonalRestaurantAdapterIntegrationTest {

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

    @Autowired
    private JdbcPersonalRestaurantAdapter adapter;

    @BeforeEach
    void clearRecentRestaurantViews() {
        jdbcTemplate.update("DELETE FROM recent_restaurant_view");
    }

    @Test
    @DisplayName("최근 목록은 30일 이내 전체 최신 50건에서 공개 ACTIVE 맛집만 안정 정렬해 조회한다")
    void 최근목록조회_보존범위와공개상태_후보를먼저제한하고안정정렬한다() {
        // given
        UUID memberId = UUID.randomUUID();
        insertMember(memberId);
        OffsetDateTime now = OffsetDateTime.parse("2026-07-30T00:00:00Z");
        List<UUID> restaurantIds = new ArrayList<>();

        for (int index = 0; index < 51; index++) {
            UUID restaurantId = UUID.randomUUID();
            restaurantIds.add(restaurantId);
            String publicationStatus = index == 0 ? "PRIVATE" : "PUBLIC";
            insertRestaurant(restaurantId, publicationStatus);
            insertRecent(memberId, restaurantId, now.minusHours(index));
        }

        UUID expiredRestaurantId = UUID.randomUUID();
        insertRestaurant(expiredRestaurantId, "PUBLIC");
        insertRecent(memberId, expiredRestaurantId, now.minusDays(31));

        // when
        PersonalRestaurantPage result = adapter.findRecentRestaurants(
                memberId, now.minusDays(30), 50, 1, 50);

        // then
        assertThat(result.totalElements()).isEqualTo(49);
        assertThat(result.items())
                .extracting(item -> item.restaurantId())
                .containsExactlyElementsOf(restaurantIds.subList(1, 50))
                .doesNotContain(restaurantIds.get(50), expiredRestaurantId);
        assertThat(countRecent(memberId)).isEqualTo(52);
    }

    @Test
    @DisplayName("최근 목록은 같은 조회 시각이면 맛집 ID 오름차순으로 페이지를 나눈다")
    void 최근목록조회_동일한조회시각_맛집아이디순으로페이지를나눈다() {
        // given
        UUID memberId = UUID.randomUUID();
        insertMember(memberId);
        OffsetDateTime viewedAt = OffsetDateTime.parse("2026-07-29T00:00:00Z");
        UUID firstRestaurantId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID secondRestaurantId = UUID.fromString("00000000-0000-4000-8000-000000000002");
        UUID thirdRestaurantId = UUID.fromString("00000000-0000-4000-8000-000000000003");
        for (UUID restaurantId : List.of(thirdRestaurantId, firstRestaurantId, secondRestaurantId)) {
            insertRestaurant(restaurantId, "PUBLIC");
            insertRecent(memberId, restaurantId, viewedAt);
        }

        // when
        PersonalRestaurantPage firstPage = adapter.findRecentRestaurants(
                memberId, viewedAt.minusDays(30), 50, 1, 2);
        PersonalRestaurantPage secondPage = adapter.findRecentRestaurants(
                memberId, viewedAt.minusDays(30), 50, 2, 2);

        // then
        assertThat(firstPage.items())
                .extracting(item -> item.restaurantId())
                .containsExactly(firstRestaurantId, secondRestaurantId);
        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(secondPage.items())
                .extracting(item -> item.restaurantId())
                .containsExactly(thirdRestaurantId);
        assertThat(secondPage.totalElements()).isEqualTo(3);
        assertThat(secondPage.totalPages()).isEqualTo(2);
        assertThat(secondPage.hasNext()).isFalse();
    }

    @Test
    @DisplayName("upsert 보존 정리는 30일 경과 여부와 무관하게 최신 50건 초과분만 삭제한다")
    void 최근기록정리_모두30일경과_최신50건은유지한다() {
        // given
        UUID memberId = UUID.randomUUID();
        insertMember(memberId);
        OffsetDateTime base = OffsetDateTime.parse("2026-06-01T00:00:00Z");
        List<UUID> restaurantIds = new ArrayList<>();
        for (int index = 0; index < 51; index++) {
            UUID restaurantId = UUID.randomUUID();
            restaurantIds.add(restaurantId);
            insertRestaurant(restaurantId, "PUBLIC");
            insertRecent(memberId, restaurantId, base.minusHours(index));
        }

        // when
        adapter.pruneRecentRestaurantOverflow(memberId, 50);

        // then
        assertThat(countRecent(memberId)).isEqualTo(50);
        assertThat(hasRecent(memberId, restaurantIds.get(0))).isTrue();
        assertThat(hasRecent(memberId, restaurantIds.get(50))).isFalse();
    }

    @Test
    @DisplayName("전역 보존 정리는 정확히 30일인 행을 유지하고 그 이전 행만 멱등 삭제한다")
    void 보존정리_30일경계와반복실행_이전행만한번삭제한다() {
        // given
        OffsetDateTime cutoff = OffsetDateTime.parse("2026-06-30T00:00:00Z");
        UUID firstMemberId = UUID.randomUUID();
        UUID secondMemberId = UUID.randomUUID();
        insertMember(firstMemberId);
        insertMember(secondMemberId);
        UUID expiredRestaurantId = UUID.randomUUID();
        UUID boundaryRestaurantId = UUID.randomUUID();
        UUID retainedRestaurantId = UUID.randomUUID();
        insertRestaurant(expiredRestaurantId, "PUBLIC");
        insertRestaurant(boundaryRestaurantId, "PUBLIC");
        insertRestaurant(retainedRestaurantId, "PUBLIC");
        insertRecent(firstMemberId, expiredRestaurantId, cutoff.minusNanos(1_000));
        insertRecent(firstMemberId, boundaryRestaurantId, cutoff);
        insertRecent(secondMemberId, retainedRestaurantId, cutoff.plusNanos(1_000));

        // when
        int firstDeletedCount = adapter.deleteRecentRestaurantViewsBefore(cutoff);
        int secondDeletedCount = adapter.deleteRecentRestaurantViewsBefore(cutoff);

        // then
        assertThat(firstDeletedCount).isEqualTo(1);
        assertThat(secondDeletedCount).isZero();
        assertThat(hasRecent(firstMemberId, expiredRestaurantId)).isFalse();
        assertThat(hasRecent(firstMemberId, boundaryRestaurantId)).isTrue();
        assertThat(hasRecent(secondMemberId, retainedRestaurantId)).isTrue();
    }

    @Test
    @DisplayName("삭제 대상이 없는 보존 정리는 0건으로 성공한다")
    void 보존정리_삭제대상없음_0건으로성공한다() {
        // when
        int deletedCount = adapter.deleteRecentRestaurantViewsBefore(
                OffsetDateTime.parse("1900-01-01T00:00:00Z"));

        // then
        assertThat(deletedCount).isZero();
    }

    private void insertMember(UUID memberId) {
        jdbcTemplate.update("""
                INSERT INTO member_account
                    (id, email, password_hash, email_verified_at, status)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, 'ACTIVE')
                """, memberId, memberId + "@example.com", "password-hash");
    }

    private void insertRestaurant(UUID restaurantId, String publicationStatus) {
        jdbcTemplate.update("""
                INSERT INTO restaurant
                    (id, region_id, food_category_id, name, kakao_place_id, kakao_place_url,
                     road_address, phone_number, publication_status, lifecycle_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                """,
                restaurantId,
                SEED_REGION_ID,
                SEED_FOOD_CATEGORY_ID,
                "테스트 맛집",
                "KAKAO-" + restaurantId,
                "https://example.com/place/" + restaurantId,
                "서울특별시 종로구 테스트로 1",
                "02-1234-5678",
                publicationStatus);
    }

    private void insertRecent(UUID memberId, UUID restaurantId, OffsetDateTime viewedAt) {
        jdbcTemplate.update("""
                INSERT INTO recent_restaurant_view (member_id, restaurant_id, last_viewed_at)
                VALUES (?, ?, ?)
                """, memberId, restaurantId, viewedAt);
    }

    private long countRecent(UUID memberId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM recent_restaurant_view WHERE member_id = ?",
                Long.class,
                memberId);
        return count == null ? 0 : count;
    }

    private boolean hasRecent(UUID memberId, UUID restaurantId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS(
                    SELECT 1 FROM recent_restaurant_view
                    WHERE member_id = ? AND restaurant_id = ?)
                """, Boolean.class, memberId, restaurantId);
        return Boolean.TRUE.equals(exists);
    }
}

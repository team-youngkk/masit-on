package com.masiton.personal.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

import com.masiton.personal.application.RecordRecentRestaurantViewService;
import com.masiton.personal.application.port.in.PersonalRestaurantPage;
import com.masiton.personal.application.port.out.PersonalRestaurantQueryPort;

import static org.assertj.core.api.Assertions.assertThat;

import com.masiton.test.FullContextIntegrationTest;

@SpringBootTest
@DisplayName("개인 맛집 JDBC 조회")
class JdbcPersonalRestaurantAdapterIntegrationTest extends FullContextIntegrationTest {

    private static final UUID SEED_REGION_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID SEED_FOOD_CATEGORY_ID =
            UUID.fromString("20000000-0000-4000-8000-000000000001");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JdbcPersonalRestaurantAdapter adapter;

    @Autowired
    private PersonalRestaurantQueryPort queries;

    @Autowired
    private RecordRecentRestaurantViewService recordRecentRestaurantViewService;

    @BeforeEach
    void clearPersonalRestaurantRelations() {
        jdbcTemplate.update("DELETE FROM recent_restaurant_view");
        jdbcTemplate.update("DELETE FROM favorite");
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
        PersonalRestaurantPage result = queries.findRecentRestaurants(
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
        PersonalRestaurantPage firstPage = queries.findRecentRestaurants(
                memberId, viewedAt.minusDays(30), 50, 1, 2);
        PersonalRestaurantPage secondPage = queries.findRecentRestaurants(
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

    @Test
    @DisplayName("같은 회원의 서로 다른 최근 기록을 동시에 저장해도 최신 50건만 남는다")
    void 최근기록_동시저장_회원별최신50건상한을유지한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        insertMember(memberId);
        List<UUID> restaurantIds = new ArrayList<>();
        for (int index = 0; index < 60; index++) {
            UUID restaurantId = UUID.randomUUID();
            restaurantIds.add(restaurantId);
            insertRestaurant(restaurantId, "PUBLIC");
        }
        CountDownLatch ready = new CountDownLatch(restaurantIds.size());
        CountDownLatch start = new CountDownLatch(1);

        // when
        try (ExecutorService executor = Executors.newFixedThreadPool(restaurantIds.size())) {
            List<? extends Future<?>> futures = restaurantIds.stream()
                    .map(restaurantId -> executor.submit(() -> {
                        ready.countDown();
                        await(start);
                        recordRecentRestaurantViewService.record(memberId, restaurantId);
                    }))
                    .toList();
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }

        // then
        assertThat(countRecent(memberId)).isEqualTo(50);
    }

    @Test
    @DisplayName("찜 추가와 목록 조회 및 해제는 최신순과 멱등 상태를 유지한다")
    void 찜_추가조회해제_최신순으로조회하고해제한다() {
        // given
        UUID memberId = UUID.randomUUID();
        insertMember(memberId);
        UUID olderRestaurantId = UUID.randomUUID();
        UUID newerRestaurantId = UUID.randomUUID();
        insertRestaurant(olderRestaurantId, "PUBLIC");
        insertRestaurant(newerRestaurantId, "PUBLIC");
        OffsetDateTime favoritedAt = OffsetDateTime.parse("2026-07-30T00:00:00Z");

        // when
        adapter.addFavorite(memberId, olderRestaurantId, favoritedAt.minusMinutes(1));
        adapter.addFavorite(memberId, newerRestaurantId, favoritedAt);
        PersonalRestaurantPage added = queries.findFavorites(memberId, 1, 20);
        adapter.removeFavorite(memberId, newerRestaurantId);
        adapter.removeFavorite(memberId, newerRestaurantId);
        PersonalRestaurantPage removed = queries.findFavorites(memberId, 1, 20);

        // then
        assertThat(added.items())
                .extracting(item -> item.restaurantId())
                .containsExactly(newerRestaurantId, olderRestaurantId);
        assertThat(added.totalElements()).isEqualTo(2);
        assertThat(removed.items())
                .extracting(item -> item.restaurantId())
                .containsExactly(olderRestaurantId);
        assertThat(adapter.existsFavorite(memberId, newerRestaurantId)).isFalse();
    }

    @Test
    @DisplayName("같은 회원과 맛집을 동시에 찜해도 한 건으로 수렴한다")
    void 찜_동시추가_한건으로수렴한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        insertMember(memberId);
        insertRestaurant(restaurantId, "PUBLIC");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        OffsetDateTime favoritedAt = OffsetDateTime.parse("2026-07-30T00:00:00Z");

        // when
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<?>> futures = List.of(
                    executor.submit(() -> addFavoriteAfterStart(
                            memberId, restaurantId, favoritedAt, ready, start)),
                    executor.submit(() -> addFavoriteAfterStart(
                            memberId, restaurantId, favoritedAt, ready, start)));
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }

        // then
        assertThat(countFavorite(memberId, restaurantId)).isEqualTo(1);
    }

    @Test
    @DisplayName("찜과 최근 목록은 다른 회원의 관계를 노출하지 않는다")
    void 개인목록_다른회원관계_회원별로격리한다() {
        // given
        UUID firstMemberId = UUID.randomUUID();
        UUID secondMemberId = UUID.randomUUID();
        UUID firstRestaurantId = UUID.randomUUID();
        UUID secondRestaurantId = UUID.randomUUID();
        insertMember(firstMemberId);
        insertMember(secondMemberId);
        insertRestaurant(firstRestaurantId, "PUBLIC");
        insertRestaurant(secondRestaurantId, "PUBLIC");
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-07-30T00:00:00Z");
        adapter.addFavorite(firstMemberId, firstRestaurantId, occurredAt);
        adapter.addFavorite(secondMemberId, secondRestaurantId, occurredAt);
        insertRecent(firstMemberId, firstRestaurantId, occurredAt);
        insertRecent(secondMemberId, secondRestaurantId, occurredAt);

        // when
        adapter.removeFavorite(firstMemberId, firstRestaurantId);
        adapter.removeRecentRestaurant(firstMemberId, firstRestaurantId);
        PersonalRestaurantPage favorites = queries.findFavorites(secondMemberId, 1, 20);
        PersonalRestaurantPage recents = queries.findRecentRestaurants(
                secondMemberId, occurredAt.minusDays(30), 50, 1, 20);

        // then
        assertThat(favorites.items())
                .extracting(item -> item.restaurantId())
                .containsExactly(secondRestaurantId);
        assertThat(favorites.totalElements()).isEqualTo(1);
        assertThat(recents.items())
                .extracting(item -> item.restaurantId())
                .containsExactly(secondRestaurantId);
        assertThat(recents.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("찜과 최근 목록은 가장 큰 페이지 번호에서도 오류 없이 빈 목록을 반환한다")
    void 개인목록_가장큰페이지번호_빈목록을반환한다() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        insertMember(memberId);
        insertRestaurant(restaurantId, "PUBLIC");
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-07-30T00:00:00Z");
        adapter.addFavorite(memberId, restaurantId, occurredAt);
        insertRecent(memberId, restaurantId, occurredAt);

        // when
        PersonalRestaurantPage favorites = queries.findFavorites(
                memberId, Integer.MAX_VALUE, 50);
        PersonalRestaurantPage recents = queries.findRecentRestaurants(
                memberId, occurredAt.minusDays(30), 50, Integer.MAX_VALUE, 50);

        // then
        assertThat(favorites.items()).isEmpty();
        assertThat(favorites.totalElements()).isEqualTo(1);
        assertThat(recents.items()).isEmpty();
        assertThat(recents.totalElements()).isEqualTo(1);
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

    private long countFavorite(UUID memberId, UUID restaurantId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM favorite WHERE member_id = ? AND restaurant_id = ?
                """, Long.class, memberId, restaurantId);
        return count == null ? 0 : count;
    }

    private void addFavoriteAfterStart(
            UUID memberId,
            UUID restaurantId,
            OffsetDateTime favoritedAt,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        adapter.addFavorite(memberId, restaurantId, favoritedAt);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 대기가 중단되었습니다.", exception);
        }
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

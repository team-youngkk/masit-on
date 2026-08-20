package com.masiton.restaurant.application.query;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

import com.masiton.restaurant.application.port.in.PopularRestaurantSummary;
import com.masiton.restaurant.application.port.in.PopularRestaurantUseCase;

import static org.assertj.core.api.Assertions.assertThat;

import com.masiton.test.FullContextIntegrationTest;

@SpringBootTest
@DisplayName("인기 맛집 실시간 집계 조회")
class PopularRestaurantQueryPostgreSqlIntegrationTest extends FullContextIntegrationTest {

    private static final UUID SEED_REGION_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID SEED_FOOD_CATEGORY_ID =
            UUID.fromString("20000000-0000-4000-8000-000000000001");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PopularRestaurantUseCase query;

    /**
     * 맛집·회원 행까지 정리해 각 테스트가 스스로 만든 데이터만 보게 한다.
     * `favorite`만 지우면 "찜 0건 공개 맛집 제외" 커버리지가 앞선 테스트의 잔여 행에 의존한다.
     * 맛집을 참조하는 `visit`·`recent_restaurant_view` 등은 FK가 `ON DELETE RESTRICT`이므로,
     * 이 클래스에 그 행을 만드는 테스트를 추가하면 여기서 함께 정리해야 한다.
     */
    @BeforeEach
    void clearAggregationSources() {
        cleanupTransactionalState(jdbcTemplate);
    }

    @Test
    @DisplayName("찜이 있어도 비공개거나 비활성인 맛집은 제외하고 공개·활성 맛집만 포함한다")
    void findPopularRestaurants_비공개비활성맛집_제외하고공개활성맛집만포함한다() {
        // given
        UUID memberId = UUID.randomUUID();
        insertMember(memberId);
        UUID publicActiveId = UUID.randomUUID();
        UUID privateActiveId = UUID.randomUUID();
        UUID deletedId = UUID.randomUUID();
        insertRestaurant(publicActiveId, "PUBLIC", "ACTIVE");
        insertRestaurant(privateActiveId, "PRIVATE", "ACTIVE");
        insertRestaurant(deletedId, "PRIVATE", "DELETED");
        insertFavorite(memberId, publicActiveId, OffsetDateTime.parse("2026-07-01T00:00:00Z"));
        insertFavorite(memberId, privateActiveId, OffsetDateTime.parse("2026-07-01T00:00:00Z"));
        insertFavorite(memberId, deletedId, OffsetDateTime.parse("2026-07-01T00:00:00Z"));

        // when
        List<PopularRestaurantSummary> result = query.findPopularRestaurants();

        // then
        assertThat(result).extracting(PopularRestaurantSummary::restaurantId)
                .containsExactly(publicActiveId);
    }

    @Test
    @DisplayName("찜이 없는 공개·활성 맛집은 찜 1건 이상 조건에 걸려 제외한다")
    void findPopularRestaurants_찜0건공개활성맛집_찜1건이상조건으로제외한다() {
        // given
        UUID memberId = UUID.randomUUID();
        insertMember(memberId);
        UUID favoritedId = UUID.randomUUID();
        UUID neverFavoritedId = UUID.randomUUID();
        insertRestaurant(favoritedId, "PUBLIC", "ACTIVE");
        insertRestaurant(neverFavoritedId, "PUBLIC", "ACTIVE");
        insertFavorite(memberId, favoritedId, OffsetDateTime.parse("2026-07-01T00:00:00Z"));

        // when
        List<PopularRestaurantSummary> result = query.findPopularRestaurants();

        // then
        assertThat(result).extracting(PopularRestaurantSummary::restaurantId)
                .containsExactly(favoritedId)
                .doesNotContain(neverFavoritedId);
        assertThat(result.getFirst().favoriteCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("맛집을 비공개로 전환하면 다음 조회에서 제외되고 하위 맛집 순위가 당겨진다")
    void findPopularRestaurants_공개에서비공개로전환_다음조회에서제외하고순위를당긴다() {
        // given
        UUID firstMemberId = UUID.randomUUID();
        UUID secondMemberId = UUID.randomUUID();
        insertMember(firstMemberId);
        insertMember(secondMemberId);
        UUID topRestaurantId = UUID.randomUUID();
        UUID secondRestaurantId = UUID.randomUUID();
        insertRestaurant(topRestaurantId, "PUBLIC", "ACTIVE");
        insertRestaurant(secondRestaurantId, "PUBLIC", "ACTIVE");
        OffsetDateTime favoritedAt = OffsetDateTime.parse("2026-07-01T00:00:00Z");
        insertFavorite(firstMemberId, topRestaurantId, favoritedAt);
        insertFavorite(secondMemberId, topRestaurantId, favoritedAt);
        insertFavorite(firstMemberId, secondRestaurantId, favoritedAt);

        // when
        List<PopularRestaurantSummary> beforeTransition = query.findPopularRestaurants();
        jdbcTemplate.update(
                "UPDATE restaurant SET publication_status = 'PRIVATE' WHERE id = ?", topRestaurantId);
        List<PopularRestaurantSummary> afterTransition = query.findPopularRestaurants();

        // then
        assertThat(beforeTransition).extracting(PopularRestaurantSummary::restaurantId)
                .containsExactly(topRestaurantId, secondRestaurantId);
        assertThat(afterTransition).extracting(PopularRestaurantSummary::restaurantId)
                .containsExactly(secondRestaurantId);
        assertThat(afterTransition.getFirst().rank()).isEqualTo(1);
    }

    @Test
    @DisplayName("맛집을 삭제 상태로 전환하면 다음 조회에서 제외한다")
    void findPopularRestaurants_활성에서삭제로전환_다음조회에서제외한다() {
        // given
        UUID memberId = UUID.randomUUID();
        insertMember(memberId);
        UUID restaurantId = UUID.randomUUID();
        insertRestaurant(restaurantId, "PUBLIC", "ACTIVE");
        insertFavorite(memberId, restaurantId, OffsetDateTime.parse("2026-07-01T00:00:00Z"));

        // when
        List<PopularRestaurantSummary> beforeDeletion = query.findPopularRestaurants();
        jdbcTemplate.update("""
                UPDATE restaurant
                   SET publication_status = 'PRIVATE',
                       lifecycle_status = 'DELETED',
                       deleted_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """, restaurantId);
        List<PopularRestaurantSummary> afterDeletion = query.findPopularRestaurants();

        // then
        assertThat(beforeDeletion).extracting(PopularRestaurantSummary::restaurantId)
                .containsExactly(restaurantId);
        assertThat(afterDeletion).isEmpty();
    }

    @Test
    @DisplayName("찜 수가 같으면 맛집 ID 오름차순으로 안정 정렬한다")
    void findPopularRestaurants_찜수동점_맛집아이디오름차순으로정렬한다() {
        // given
        UUID firstMemberId = UUID.randomUUID();
        UUID secondMemberId = UUID.randomUUID();
        insertMember(firstMemberId);
        insertMember(secondMemberId);
        UUID higherId = UUID.fromString("00000000-0000-4000-8000-000000000002");
        UUID lowerId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        insertRestaurant(higherId, "PUBLIC", "ACTIVE");
        insertRestaurant(lowerId, "PUBLIC", "ACTIVE");
        OffsetDateTime favoritedAt = OffsetDateTime.parse("2026-07-01T00:00:00Z");
        insertFavorite(firstMemberId, higherId, favoritedAt);
        insertFavorite(secondMemberId, higherId, favoritedAt);
        insertFavorite(firstMemberId, lowerId, favoritedAt);
        insertFavorite(secondMemberId, lowerId, favoritedAt);

        // when
        List<PopularRestaurantSummary> result = query.findPopularRestaurants();

        // then
        assertThat(result).extracting(PopularRestaurantSummary::restaurantId)
                .containsExactly(lowerId, higherId);
    }

    @Test
    @DisplayName("21개 이상 후보가 있으면 찜 수 상위 20개만 1부터 연속된 순위로 반환한다")
    void findPopularRestaurants_후보21개이상_상위20개만연속된순위로반환한다() {
        // given
        UUID memberId = UUID.randomUUID();
        insertMember(memberId);
        List<UUID> restaurantIds = new ArrayList<>();
        OffsetDateTime favoritedAt = OffsetDateTime.parse("2026-07-01T00:00:00Z");
        for (int index = 0; index < 21; index++) {
            UUID restaurantId = UUID.randomUUID();
            restaurantIds.add(restaurantId);
            insertRestaurant(restaurantId, "PUBLIC", "ACTIVE");
        }
        // 후보마다 서로 다른 찜 수를 부여한다: index가 클수록 찜 수가 많다.
        for (int index = 0; index < 21; index++) {
            UUID restaurantId = restaurantIds.get(index);
            for (int favoriteIndex = 0; favoriteIndex <= index; favoriteIndex++) {
                UUID favoritingMemberId = UUID.randomUUID();
                insertMember(favoritingMemberId);
                insertFavorite(favoritingMemberId, restaurantId, favoritedAt);
            }
        }
        UUID leastFavoritedId = restaurantIds.get(0);

        // when
        List<PopularRestaurantSummary> result = query.findPopularRestaurants();

        // then
        assertThat(result).hasSize(20);
        assertThat(result).extracting(PopularRestaurantSummary::rank)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 20).boxed().toList());
        assertThat(result).extracting(PopularRestaurantSummary::restaurantId)
                .doesNotContain(leastFavoritedId);
    }

    @Test
    @DisplayName("찜 시점과 무관하게 현재 찜 관계 전체를 집계한다")
    void findPopularRestaurants_찜시점무관_현재관계전체를집계한다() {
        // given
        UUID memberId = UUID.randomUUID();
        insertMember(memberId);
        UUID restaurantId = UUID.randomUUID();
        insertRestaurant(restaurantId, "PUBLIC", "ACTIVE");
        insertFavorite(memberId, restaurantId, OffsetDateTime.parse("2020-01-01T00:00:00Z"));

        // when
        List<PopularRestaurantSummary> result = query.findPopularRestaurants();

        // then
        assertThat(result).extracting(PopularRestaurantSummary::restaurantId)
                .containsExactly(restaurantId);
        assertThat(result.get(0).favoriteCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("찜 추가와 해제 커밋 뒤 다음 조회에 즉시 반영한다")
    void findPopularRestaurants_찜추가와해제_다음조회에즉시반영한다() {
        // given
        UUID firstMemberId = UUID.randomUUID();
        UUID secondMemberId = UUID.randomUUID();
        insertMember(firstMemberId);
        insertMember(secondMemberId);
        UUID restaurantId = UUID.randomUUID();
        insertRestaurant(restaurantId, "PUBLIC", "ACTIVE");
        OffsetDateTime favoritedAt = OffsetDateTime.parse("2026-07-01T00:00:00Z");
        insertFavorite(firstMemberId, restaurantId, favoritedAt);
        insertFavorite(secondMemberId, restaurantId, favoritedAt);

        // when
        List<PopularRestaurantSummary> beforeRemoval = query.findPopularRestaurants();
        jdbcTemplate.update(
                "DELETE FROM favorite WHERE member_id = ? AND restaurant_id = ?",
                secondMemberId, restaurantId);
        List<PopularRestaurantSummary> afterRemoval = query.findPopularRestaurants();

        // then
        assertThat(beforeRemoval.get(0).favoriteCount()).isEqualTo(2L);
        assertThat(afterRemoval.get(0).favoriteCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("조건에 맞는 맛집이 없으면 예외 없이 빈 목록을 반환한다")
    void findPopularRestaurants_조건에맞는맛집없음_빈목록을반환한다() {
        // given
        UUID memberId = UUID.randomUUID();
        insertMember(memberId);
        UUID privateRestaurantId = UUID.randomUUID();
        insertRestaurant(privateRestaurantId, "PRIVATE", "ACTIVE");
        insertFavorite(memberId, privateRestaurantId, OffsetDateTime.parse("2026-07-01T00:00:00Z"));

        // when
        List<PopularRestaurantSummary> result = query.findPopularRestaurants();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("회원 탈퇴로 찜 관계가 CASCADE 삭제되면 집계에서 함께 줄어든다")
    void findPopularRestaurants_회원탈퇴로찜관계삭제_집계가줄어든다() {
        // given
        UUID firstMemberId = UUID.randomUUID();
        UUID secondMemberId = UUID.randomUUID();
        insertMember(firstMemberId);
        insertMember(secondMemberId);
        UUID restaurantId = UUID.randomUUID();
        insertRestaurant(restaurantId, "PUBLIC", "ACTIVE");
        OffsetDateTime favoritedAt = OffsetDateTime.parse("2026-07-01T00:00:00Z");
        insertFavorite(firstMemberId, restaurantId, favoritedAt);
        insertFavorite(secondMemberId, restaurantId, favoritedAt);

        // when
        List<PopularRestaurantSummary> beforeWithdrawal = query.findPopularRestaurants();
        jdbcTemplate.update("DELETE FROM member_account WHERE id = ?", secondMemberId);
        List<PopularRestaurantSummary> afterWithdrawal = query.findPopularRestaurants();

        // then
        assertThat(beforeWithdrawal.get(0).favoriteCount()).isEqualTo(2L);
        assertThat(afterWithdrawal.get(0).favoriteCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("조회는 favorite 원본 행 수를 변경하지 않는다")
    void findPopularRestaurants_조회수행_favorite원본행수를변경하지않는다() {
        // given
        UUID memberId = UUID.randomUUID();
        insertMember(memberId);
        UUID restaurantId = UUID.randomUUID();
        insertRestaurant(restaurantId, "PUBLIC", "ACTIVE");
        insertFavorite(memberId, restaurantId, OffsetDateTime.parse("2026-07-01T00:00:00Z"));
        long beforeCount = countFavorite();

        // when
        query.findPopularRestaurants();
        long afterCount = countFavorite();

        // then
        assertThat(afterCount).isEqualTo(beforeCount);
    }

    @Test
    @DisplayName("탈퇴 요청 전이만으로는 집계가 줄지 않고 찜 관계를 정리한 시점에 줄어든다")
    void findPopularRestaurants_탈퇴요청전이와정리_정리시점에집계가줄어든다() {
        // given
        UUID stayingMemberId = UUID.randomUUID();
        UUID withdrawingMemberId = UUID.randomUUID();
        insertMember(stayingMemberId);
        insertMember(withdrawingMemberId);
        UUID restaurantId = UUID.randomUUID();
        insertRestaurant(restaurantId, "PUBLIC", "ACTIVE");
        OffsetDateTime favoritedAt = OffsetDateTime.parse("2026-07-01T00:00:00Z");
        insertFavorite(stayingMemberId, restaurantId, favoritedAt);
        insertFavorite(withdrawingMemberId, restaurantId, favoritedAt);

        // when
        jdbcTemplate.update("""
                UPDATE member_account
                   SET status = 'DELETION_PENDING', deletion_requested_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """, withdrawingMemberId);
        List<PopularRestaurantSummary> afterDeletionRequest = query.findPopularRestaurants();
        jdbcTemplate.update("DELETE FROM member_account WHERE id = ?", withdrawingMemberId);
        List<PopularRestaurantSummary> afterCleanup = query.findPopularRestaurants();

        // then
        assertThat(afterDeletionRequest.getFirst().favoriteCount())
                .as("ADR-DATA-011 5·7절: 집계는 회원 상태를 조인하지 않고 찜 관계 존재만 판정한다")
                .isEqualTo(2L);
        assertThat(afterCleanup.getFirst().favoriteCount())
                .as("BR-POPULAR-003의 '해당 트랜잭션'은 찜 관계를 제거하는 정리 트랜잭션이다")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("찜을 동시에 추가·해제하는 동안에도 집계에 음수나 중복 맛집이 나오지 않는다")
    void findPopularRestaurants_찜동시변경_음수와중복없이연속된순위를유지한다() throws Exception {
        // given
        UUID restaurantId = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
        UUID otherRestaurantId = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
        insertRestaurant(restaurantId, "PUBLIC", "ACTIVE");
        insertRestaurant(otherRestaurantId, "PUBLIC", "ACTIVE");
        OffsetDateTime favoritedAt = OffsetDateTime.parse("2026-07-01T00:00:00Z");
        UUID anchorMemberId = UUID.randomUUID();
        insertMember(anchorMemberId);
        insertFavorite(anchorMemberId, restaurantId, favoritedAt);
        insertFavorite(anchorMemberId, otherRestaurantId, favoritedAt);

        int writerCount = 8;
        List<UUID> writerMemberIds = new ArrayList<>();
        for (int index = 0; index < writerCount; index++) {
            UUID memberId = UUID.randomUUID();
            insertMember(memberId);
            writerMemberIds.add(memberId);
        }
        CountDownLatch ready = new CountDownLatch(writerCount + 1);
        CountDownLatch start = new CountDownLatch(1);
        List<List<PopularRestaurantSummary>> observations = new ArrayList<>();

        // when
        try (ExecutorService executor = Executors.newFixedThreadPool(writerCount + 1)) {
            List<Future<?>> futures = new ArrayList<>();
            for (UUID writerMemberId : writerMemberIds) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    for (int round = 0; round < 20; round++) {
                        insertFavorite(writerMemberId, restaurantId, favoritedAt);
                        jdbcTemplate.update(
                                "DELETE FROM favorite WHERE member_id = ? AND restaurant_id = ?",
                                writerMemberId, restaurantId);
                    }
                }));
            }
            futures.add(executor.submit(() -> {
                ready.countDown();
                await(start);
                List<List<PopularRestaurantSummary>> collected = new ArrayList<>();
                for (int round = 0; round < 40; round++) {
                    collected.add(query.findPopularRestaurants());
                }
                synchronized (observations) {
                    observations.addAll(collected);
                }
            }));
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }

        // then
        assertThat(observations).isNotEmpty();
        for (List<PopularRestaurantSummary> observation : observations) {
            assertThat(observation).extracting(PopularRestaurantSummary::favoriteCount)
                    .allSatisfy(favoriteCount -> assertThat(favoriteCount).isPositive());
            // 단일 SQL 스냅샷이므로 경합 중에도 anchor 1건 이상, writer 전원 동시 찜 이하로 결정적이다.
            assertThat(observation).filteredOn(item -> item.restaurantId().equals(restaurantId))
                    .singleElement()
                    .satisfies(item -> assertThat(item.favoriteCount()).isBetween(1L, 1L + writerCount));
            assertThat(observation).extracting(PopularRestaurantSummary::restaurantId).doesNotHaveDuplicates();
            assertThat(observation).extracting(PopularRestaurantSummary::rank)
                    .containsExactlyElementsOf(
                            IntStream.rangeClosed(1, observation.size()).boxed().toList());
        }
    }

    private void insertMember(UUID memberId) {
        jdbcTemplate.update("""
                INSERT INTO member_account
                    (id, email, password_hash, email_verified_at, status)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, 'ACTIVE')
                """, memberId, memberId + "@example.com", "password-hash");
    }

    private void insertRestaurant(UUID restaurantId, String publicationStatus, String lifecycleStatus) {
        OffsetDateTime deletedAt = "DELETED".equals(lifecycleStatus)
                ? OffsetDateTime.parse("2026-07-01T00:00:00Z")
                : null;
        jdbcTemplate.update("""
                INSERT INTO restaurant
                    (id, region_id, food_category_id, name, kakao_place_id, kakao_place_url,
                     road_address, phone_number, publication_status, lifecycle_status, deleted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                restaurantId,
                SEED_REGION_ID,
                SEED_FOOD_CATEGORY_ID,
                "테스트 맛집",
                "KAKAO-" + restaurantId,
                "https://example.com/place/" + restaurantId,
                "서울특별시 종로구 테스트로 1",
                "02-1234-5678",
                publicationStatus,
                lifecycleStatus,
                deletedAt);
    }

    private void insertFavorite(UUID memberId, UUID restaurantId, OffsetDateTime favoritedAt) {
        jdbcTemplate.update("""
                INSERT INTO favorite (member_id, restaurant_id, favorited_at)
                VALUES (?, ?, ?)
                """, memberId, restaurantId, favoritedAt);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 대기가 중단되었습니다.", exception);
        }
    }

    private long countFavorite() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM favorite", Long.class);
        return count == null ? 0 : count;
    }
}

package com.masiton.personal.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masiton.common.web.BusinessException;
import com.masiton.personal.application.PersonalCollectionService;
import com.masiton.personal.application.port.in.CollectionOption.AdditionStatus;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@com.masiton.test.TestProfile
@Testcontainers
@DisplayName("개인 컬렉션 JDBC 저장소")
class JdbcPersonalCollectionAdapterIntegrationTest {

    private static final UUID REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-03T10:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.10-alpine")
            .withDatabaseName("masiton").withUsername("masiton").withPassword("masiton_local");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    JdbcPersonalCollectionAdapter adapter;

    @Autowired
    PersonalCollectionService service;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void clear() {
        jdbcTemplate.update("DELETE FROM idempotency_record WHERE api_scope = 'POST:/api/me/collections'");
        jdbcTemplate.update("DELETE FROM collection_restaurant");
        jdbcTemplate.update("DELETE FROM personal_collection");
    }

    @Test
    @DisplayName("동시 생성 요청에도 회원당 컬렉션은 20개를 넘지 않는다")
    void create_19개에서동시요청_정확히20개까지만생성한다() throws Exception {
        // given
        UUID memberId = insertMember();
        inTransaction(() -> {
            for (int index = 0; index < 19; index++) {
                adapter.create(memberId, UUID.randomUUID(), "목록 " + index, NOW.plusSeconds(index));
            }
        });

        // when
        List<String> results = runConcurrently(8, index -> inTransactionResult(() -> {
            try {
                adapter.create(memberId, UUID.randomUUID(), "동시 목록 " + index, NOW.plusMinutes(1));
                return "CREATED";
            } catch (BusinessException exception) {
                return exception.code();
            }
        }));

        // then
        assertThat(results).filteredOn("CREATED"::equals).hasSize(1);
        assertThat(results).filteredOn("COLLECTION_LIMIT_EXCEEDED"::equals).hasSize(7);
        assertThat(count("personal_collection", "member_id", memberId)).isEqualTo(20L);
    }

    @Test
    @DisplayName("동시 맛집 추가 요청에도 컬렉션당 관계는 100개를 넘지 않는다")
    void addRestaurant_99개에서동시요청_정확히100개까지만추가한다() throws Exception {
        // given
        UUID memberId = insertMember();
        UUID collectionId = UUID.randomUUID();
        List<UUID> restaurantIds = new ArrayList<>();
        for (int index = 0; index < 107; index++) {
            restaurantIds.add(insertRestaurant());
        }
        inTransaction(() -> {
            adapter.create(memberId, collectionId, "가고 싶은 곳", NOW);
            for (int index = 0; index < 99; index++) {
                adapter.addRestaurant(memberId, collectionId, restaurantIds.get(index), NOW.plusSeconds(index));
            }
        });

        // when
        List<String> results = runConcurrently(8, index -> inTransactionResult(() -> {
            try {
                adapter.addRestaurant(memberId, collectionId, restaurantIds.get(99 + index), NOW.plusMinutes(2));
                return "ADDED";
            } catch (BusinessException exception) {
                return exception.code();
            }
        }));

        // then
        assertThat(results).filteredOn("ADDED"::equals).hasSize(1);
        assertThat(results).filteredOn("COLLECTION_RESTAURANT_LIMIT_EXCEEDED"::equals).hasSize(7);
        assertThat(count("collection_restaurant", "collection_id", collectionId)).isEqualTo(100L);
    }

    @Test
    @Transactional
    @DisplayName("타 회원 컬렉션은 조회와 변경에서 존재하지 않는 자원처럼 취급한다")
    void ownership_타회원컬렉션_조회와변경에서은닉한다() {
        // given
        UUID ownerId = insertMember();
        UUID strangerId = insertMember();
        UUID collectionId = UUID.randomUUID();
        UUID restaurantId = insertRestaurant();
        adapter.create(ownerId, collectionId, "소유자 목록", NOW);
        adapter.addRestaurant(ownerId, collectionId, restaurantId, NOW);

        // when & then
        assertThat(adapter.findDetail(strangerId, collectionId, 1, 20)).isEmpty();
        assertThat(adapter.rename(strangerId, collectionId, "탈취 시도", NOW.plusMinutes(1))).isEmpty();
        assertThat(adapter.addRestaurant(strangerId, collectionId, insertRestaurant(), NOW.plusMinutes(1))).isEmpty();

        adapter.removeRestaurant(strangerId, collectionId, restaurantId, NOW.plusMinutes(1));
        adapter.delete(strangerId, collectionId);

        assertThat(adapter.findDetail(ownerId, collectionId, 1, 20)).isPresent();
        assertThat(adapter.findRestaurant(ownerId, collectionId, restaurantId)).isPresent();
    }

    @Test
    @Transactional
    @DisplayName("회원 탈퇴 시 컬렉션과 구성 관계를 함께 물리 삭제한다")
    void memberDelete_소유컬렉션과구성관계_CASCADE삭제한다() {
        // given
        UUID memberId = insertMember();
        UUID collectionId = UUID.randomUUID();
        UUID restaurantId = insertRestaurant();
        adapter.create(memberId, collectionId, "탈퇴 전 목록", NOW);
        adapter.addRestaurant(memberId, collectionId, restaurantId, NOW);

        // when
        jdbcTemplate.update("DELETE FROM member_account WHERE id = ?", memberId);

        // then
        assertThat(count("personal_collection", "id", collectionId)).isZero();
        assertThat(count("collection_restaurant", "collection_id", collectionId)).isZero();
    }

    @Test
    @Transactional
    @DisplayName("컬렉션 삭제는 맛집과 찜 관계에 영향을 주지 않는다")
    void delete_컬렉션삭제_맛집과찜은유지한다() {
        // given
        UUID memberId = insertMember();
        UUID collectionId = UUID.randomUUID();
        UUID restaurantId = insertRestaurant();
        adapter.create(memberId, collectionId, "삭제할 목록", NOW);
        adapter.addRestaurant(memberId, collectionId, restaurantId, NOW);
        jdbcTemplate.update("INSERT INTO favorite (member_id, restaurant_id) VALUES (?, ?)", memberId, restaurantId);

        // when
        adapter.delete(memberId, collectionId);

        // then
        assertThat(count("collection_restaurant", "collection_id", collectionId)).isZero();
        assertThat(count("restaurant", "id", restaurantId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM favorite WHERE member_id = ? AND restaurant_id = ?",
                Long.class, memberId, restaurantId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("같은 멱등 키와 같은 이름은 생성 응답을 재생하고 다른 이름 재사용은 거부한다")
    void create_멱등키재시도와다른본문재사용_replay와conflict로수렴한다() throws Exception {
        // given
        UUID memberId = insertMember();

        // when
        var first = service.create(memberId, "collection-create-key", "  가족과 갈 곳  ");
        var replay = service.create(memberId, "collection-create-key", "가족과 갈 곳");

        // then
        assertThat(objectMapper.readTree(replay.responseBody()))
                .isEqualTo(objectMapper.readTree(first.responseBody()));
        assertThat(count("personal_collection", "member_id", memberId)).isEqualTo(1L);
        assertThatThrownBy(() -> service.create(memberId, "collection-create-key", "다른 목록"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
        assertThat(count("personal_collection", "member_id", memberId)).isEqualTo(1L);
    }

    @Test
    @Transactional
    @DisplayName("회원 행이 없으면 생성 요청을 인증 오류로 거부한다")
    void create_없는회원_AUTHENTICATION_REQUIRED를반환한다() {
        // given
        UUID deletedMemberId = UUID.randomUUID();

        // when & then
        assertThatThrownBy(() -> adapter.create(
                deletedMemberId, UUID.randomUUID(), "만들 수 없는 목록", NOW))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("AUTHENTICATION_REQUIRED"));
        assertThat(count("personal_collection", "member_id", deletedMemberId)).isZero();
    }

    @Test
    @Transactional
    @DisplayName("회원 행 잠금 뒤 20개 상한을 검사한다")
    void create_컬렉션20개_추가생성을거부한다() {
        UUID memberId = insertMember();
        for (int index = 0; index < 20; index++) {
            adapter.create(memberId, UUID.randomUUID(), "목록 " + index, NOW.plusSeconds(index));
        }

        assertThatThrownBy(() -> adapter.create(memberId, UUID.randomUUID(), "초과", NOW.plusMinutes(1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("COLLECTION_LIMIT_EXCEEDED"));
    }

    @Test
    @Transactional
    @DisplayName("중복 추가는 최초 시각을 유지하고 비공개 맛집은 상세에서 숨긴다")
    void detail_중복과비공개전환_관계는유지하고공개항목만반환한다() {
        UUID memberId = insertMember();
        UUID collectionId = UUID.randomUUID();
        UUID restaurantId = insertRestaurant();
        adapter.create(memberId, collectionId, "저녁", NOW);

        var first = adapter.addRestaurant(memberId, collectionId, restaurantId, NOW.plusMinutes(1));
        var replay = adapter.addRestaurant(memberId, collectionId, restaurantId, NOW.plusMinutes(2));

        assertThat(replay.orElseThrow().addedAt()).isEqualTo(first.orElseThrow().addedAt());
        jdbcTemplate.update("UPDATE restaurant SET publication_status = 'PRIVATE' WHERE id = ?", restaurantId);
        var detail = adapter.findDetail(memberId, collectionId, 1, 20).orElseThrow();
        assertThat(detail.items()).isEmpty();
        assertThat(detail.restaurantCount()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM collection_restaurant WHERE collection_id = ?", Long.class, collectionId))
                .isEqualTo(1L);
    }

    @Test
    @Transactional
    @DisplayName("옵션 조회는 공개 개수와 실제 상한 및 이미 포함 우선순위를 반영한다")
    void findOptions_공개개수와실제관계수_추가상태를계산한다() {
        // given
        UUID memberId = insertMember();
        UUID targetRestaurantId = insertRestaurant();
        UUID availableCollectionId = UUID.randomUUID();
        UUID includedCollectionId = UUID.randomUUID();
        UUID limitedCollectionId = UUID.randomUUID();
        adapter.create(memberId, availableCollectionId, "추가 가능", NOW);
        adapter.create(memberId, includedCollectionId, "이미 포함", NOW.plusSeconds(1));
        adapter.create(memberId, limitedCollectionId, "상한 도달", NOW.plusSeconds(2));

        UUID visibleRestaurantId = insertRestaurant();
        jdbcTemplate.update("""
                INSERT INTO collection_restaurant (collection_id, restaurant_id, added_at)
                VALUES (?, ?, ?), (?, ?, ?)
                """, availableCollectionId, visibleRestaurantId, NOW,
                includedCollectionId, targetRestaurantId, NOW);

        List<UUID> limitedRestaurantIds = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            UUID restaurantId = insertRestaurant();
            limitedRestaurantIds.add(restaurantId);
            jdbcTemplate.update("""
                    INSERT INTO collection_restaurant (collection_id, restaurant_id, added_at)
                    VALUES (?, ?, ?)
                    """, limitedCollectionId, restaurantId, NOW.plusSeconds(index));
        }
        for (int index = 1; index < limitedRestaurantIds.size(); index++) {
            jdbcTemplate.update("UPDATE restaurant SET publication_status = 'PRIVATE' WHERE id = ?",
                    limitedRestaurantIds.get(index));
            jdbcTemplate.update("""
                    INSERT INTO collection_restaurant (collection_id, restaurant_id, added_at)
                    VALUES (?, ?, ?)
                    """, includedCollectionId, limitedRestaurantIds.get(index), NOW.plusSeconds(index));
        }

        // when
        var options = service.getCollectionOptions(memberId, targetRestaurantId);

        // then
        assertThat(options).filteredOn(option -> option.collectionId().equals(availableCollectionId))
                .singleElement().satisfies(option -> {
                    assertThat(option.restaurantCount()).isEqualTo(1);
                    assertThat(option.additionStatus()).isEqualTo(AdditionStatus.AVAILABLE);
                });
        assertThat(options).filteredOn(option -> option.collectionId().equals(includedCollectionId))
                .singleElement().satisfies(option -> {
                    assertThat(option.restaurantCount()).isEqualTo(1);
                    assertThat(option.additionStatus()).isEqualTo(AdditionStatus.ALREADY_INCLUDED);
                });
        assertThat(options).filteredOn(option -> option.collectionId().equals(limitedCollectionId))
                .singleElement().satisfies(option -> {
                    assertThat(option.restaurantCount()).isEqualTo(1);
                    assertThat(option.additionStatus()).isEqualTo(AdditionStatus.LIMIT_REACHED);
                });
    }

    @Test
    @Transactional
    @DisplayName("비공개 맛집은 옵션 조회에서 RESTAURANT_NOT_FOUND를 반환한다")
    void findOptions_비공개맛집_RESTAURANT_NOT_FOUND를반환한다() {
        UUID memberId = insertMember();
        UUID restaurantId = insertRestaurant();
        jdbcTemplate.update("UPDATE restaurant SET publication_status = 'PRIVATE' WHERE id = ?", restaurantId);

        assertThatThrownBy(() -> service.getCollectionOptions(memberId, restaurantId))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("RESTAURANT_NOT_FOUND"));
    }

    @Test
    @Transactional
    @DisplayName("삭제된 맛집은 옵션 조회에서 RESTAURANT_NOT_FOUND를 반환한다")
    void findOptions_삭제된맛집_RESTAURANT_NOT_FOUND를반환한다() {
        UUID memberId = insertMember();
        UUID restaurantId = insertRestaurant();
        jdbcTemplate.update("""
                UPDATE restaurant
                   SET publication_status = 'PRIVATE', lifecycle_status = 'DELETED', deleted_at = ?
                 WHERE id = ?
                """, NOW, restaurantId);

        assertThatThrownBy(() -> service.getCollectionOptions(memberId, restaurantId))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("RESTAURANT_NOT_FOUND"));
    }

    private UUID insertMember() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO member_account (id, email, password_hash, email_verified_at, status)
                VALUES (?, ?, 'password-hash', CURRENT_TIMESTAMP, 'ACTIVE')
                """, id, id + "@example.com");
        return id;
    }

    private UUID insertRestaurant() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO restaurant
                    (id, region_id, food_category_id, name, kakao_place_id, kakao_place_url,
                     road_address, phone_number, publication_status, lifecycle_status)
                VALUES (?, ?, ?, '테스트 맛집', ?, ?, '서울 테스트로 1', '02-1234-5678', 'PUBLIC', 'ACTIVE')
                """, id, REGION_ID, CATEGORY_ID, "KAKAO-" + id, "https://example.com/" + id);
        return id;
    }

    private long count(String table, String column, UUID id) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Long.class, id);
        return count == null ? 0L : count;
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private <T> T inTransactionResult(java.util.function.Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    private <T> List<T> runConcurrently(
            int requestCount, java.util.function.IntFunction<T> request) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(requestCount)) {
            List<Future<T>> futures = new ArrayList<>();
            for (int index = 0; index < requestCount; index++) {
                int requestIndex = index;
                futures.add(executor.submit(() -> {
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return request.apply(requestIndex);
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        }
    }
}

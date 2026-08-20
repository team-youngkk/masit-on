package com.masiton.orchestration.infrastructure.query;

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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masiton.personal.application.PersonalCollectionService;
import com.masiton.personal.application.port.in.CollectionOption.AdditionStatus;
import com.masiton.personal.application.port.out.PersonalCollectionQueryPort;
import com.masiton.personal.infrastructure.persistence.JdbcPersonalCollectionAdapter;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@com.masiton.test.TestProfile
@DisplayName("개인 컬렉션 조합 조회 어댑터")
class PersonalCollectionQueryAdapterIntegrationTest extends com.masiton.test.FullContextIntegrationTest {

    private static final UUID REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-05T10:00:00Z");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    JdbcPersonalCollectionAdapter store;

    @Autowired
    PersonalCollectionQueryPort queries;

    @Autowired
    PersonalCollectionService service;

    @BeforeEach
    void clear() {
        jdbcTemplate.update("DELETE FROM collection_restaurant");
        jdbcTemplate.update("DELETE FROM personal_collection");
    }

    @Test
    @Transactional
    @DisplayName("목록과 단건 요약은 공개·활성 맛집 수만 반환한다")
    void summary_공개와비공개관계_공개수만반환한다() {
        UUID memberId = insertMember();
        UUID collectionId = UUID.randomUUID();
        UUID publicRestaurantId = insertRestaurant("PUBLIC", "ACTIVE");
        UUID privateRestaurantId = insertRestaurant("PRIVATE", "ACTIVE");
        store.create(memberId, collectionId, "가고 싶은 곳", NOW);
        store.addRestaurant(memberId, collectionId, publicRestaurantId, NOW.plusSeconds(1));
        store.addRestaurant(memberId, collectionId, privateRestaurantId, NOW.plusSeconds(2));

        var all = queries.findAll(memberId);
        var summary = queries.findSummary(memberId, collectionId).orElseThrow();
        service.rename(memberId, collectionId, "다시 갈 곳");
        var renamed = service.getSummary(memberId, collectionId);

        assertThat(all).singleElement().satisfies(item -> {
            assertThat(item.collectionId()).isEqualTo(collectionId);
            assertThat(item.restaurantCount()).isEqualTo(1);
        });
        assertThat(summary.restaurantCount()).isEqualTo(1);
        assertThat(renamed.name()).isEqualTo("다시 갈 곳");
        assertThat(renamed.restaurantCount()).isEqualTo(1);
    }

    @Test
    @Transactional
    @DisplayName("상세는 소유권을 은닉하고 공개 맛집을 안정적으로 페이지 조회한다")
    void detail_타회원과두번째페이지_404와페이지계약을유지한다() {
        UUID memberId = insertMember();
        UUID strangerId = insertMember();
        UUID collectionId = UUID.randomUUID();
        store.create(memberId, collectionId, "서울 맛집", NOW);
        for (int index = 0; index < 3; index++) {
            store.addRestaurant(memberId, collectionId,
                    insertRestaurant("PUBLIC", "ACTIVE"), NOW.plusSeconds(index + 1));
        }

        var detail = queries.findDetail(memberId, collectionId, 2, 2).orElseThrow();

        assertThat(detail.items()).hasSize(1);
        assertThat(detail.restaurantCount()).isEqualTo(3);
        assertThat(detail.totalElements()).isEqualTo(3);
        assertThat(detail.totalPages()).isEqualTo(2);
        assertThat(detail.hasNext()).isFalse();
        assertThat(queries.findDetail(strangerId, collectionId, 1, 20)).isEmpty();
        assertThat(queries.findDetail(memberId, UUID.randomUUID(), 1, 20)).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("옵션은 공개 수와 실제 수를 분리하고 이미 포함 상태를 우선한다")
    void options_실제100개와대상포함_상태우선순위를유지한다() {
        UUID memberId = insertMember();
        UUID targetRestaurantId = insertRestaurant("PUBLIC", "ACTIVE");
        UUID includedCollectionId = UUID.randomUUID();
        UUID limitedCollectionId = UUID.randomUUID();
        store.create(memberId, includedCollectionId, "이미 포함", NOW);
        store.create(memberId, limitedCollectionId, "상한 도달", NOW.plusSeconds(1));

        List<UUID> includedRestaurantIds = new ArrayList<>();
        includedRestaurantIds.add(targetRestaurantId);
        for (int index = 1; index < 100; index++) {
            includedRestaurantIds.add(insertRestaurant(index == 1 ? "PUBLIC" : "PRIVATE", "ACTIVE"));
        }
        for (int index = 0; index < includedRestaurantIds.size(); index++) {
            jdbcTemplate.update("""
                    INSERT INTO collection_restaurant (collection_id, restaurant_id, added_at)
                    VALUES (?, ?, ?)
                    """, includedCollectionId, includedRestaurantIds.get(index), NOW.plusSeconds(index));
            jdbcTemplate.update("""
                    INSERT INTO collection_restaurant (collection_id, restaurant_id, added_at)
                    VALUES (?, ?, ?)
                    """, limitedCollectionId,
                    insertRestaurant(index == 0 ? "PUBLIC" : "PRIVATE", "ACTIVE"),
                    NOW.plusSeconds(index));
        }

        var options = queries.findOptions(memberId, targetRestaurantId);

        assertThat(options).filteredOn(option -> option.collectionId().equals(includedCollectionId))
                .singleElement().satisfies(option -> {
                    assertThat(option.restaurantCount()).isEqualTo(2);
                    assertThat(option.additionStatus()).isEqualTo(AdditionStatus.ALREADY_INCLUDED);
                });
        assertThat(options).filteredOn(option -> option.collectionId().equals(limitedCollectionId))
                .singleElement().satisfies(option -> {
                    assertThat(option.restaurantCount()).isEqualTo(1);
                    assertThat(option.additionStatus()).isEqualTo(AdditionStatus.LIMIT_REACHED);
                });
    }

    private UUID insertMember() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO member_account (id, email, password_hash, email_verified_at, status)
                VALUES (?, ?, 'password-hash', CURRENT_TIMESTAMP, 'ACTIVE')
                """, id, id + "@example.com");
        return id;
    }

    private UUID insertRestaurant(String publicationStatus, String lifecycleStatus) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO restaurant
                    (id, region_id, food_category_id, name, kakao_place_id, kakao_place_url,
                     road_address, phone_number, publication_status, lifecycle_status)
                VALUES (?, ?, ?, '테스트 맛집', ?, ?, '서울 테스트로 1', '02-1234-5678', ?, ?)
                """, id, REGION_ID, CATEGORY_ID, "KAKAO-" + id, "https://example.com/" + id,
                publicationStatus, lifecycleStatus);
        return id;
    }
}

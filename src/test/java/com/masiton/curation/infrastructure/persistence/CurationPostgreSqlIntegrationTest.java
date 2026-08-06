package com.masiton.curation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masiton.common.web.BusinessException;
import com.masiton.curation.application.AdminCurationService;
import com.masiton.curation.application.PublicCurationService;
import com.masiton.curation.application.port.out.CurationStore;
import com.masiton.curation.domain.model.CurationStatus;

@SpringBootTest
@com.masiton.test.TestProfile
@Testcontainers
@DisplayName("큐레이션 PostgreSQL 통합")
class CurationPostgreSqlIntegrationTest {

    private static final UUID REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-05T00:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.10-alpine")
            .withDatabaseName("masiton").withUsername("masiton").withPassword("masiton_local");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired JdbcCurationStore store;
    @Autowired AdminCurationService service;
    @Autowired PublicCurationService publicService;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void clear() {
        jdbcTemplate.update("DELETE FROM curation_restaurant");
        jdbcTemplate.update("DELETE FROM curation");
    }

    @Test
    @DisplayName("구성 전체 교체 검증이 실패하면 기존 관계와 순서를 유지한다")
    void 구성교체_중간검증실패_기존구성유지() {
        UUID adminId = insertAdmin();
        UUID curationId = insertDraft(adminId);
        UUID first = insertRestaurant();
        UUID second = insertRestaurant();
        service.replaceRestaurants(curationId, adminId, List.of(first, second), "trace-initial");

        assertThatThrownBy(() -> service.replaceRestaurants(curationId, adminId,
                List.of(second, UUID.randomUUID()), "trace-failed"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("RESTAURANT_NOT_FOUND"));

        assertThat(store.findRestaurants(curationId)).extracting(CurationStore.StoredRestaurant::restaurantId)
                .containsExactly(first, second);
    }

    @Test
    @DisplayName("게시 중단은 위치를 압축하고 전체 순서 교체는 원자적으로 반영한다")
    void 게시순서_중단과전체교체_연속위치반영() {
        UUID adminId = insertAdmin();
        UUID first = insertDraft(adminId);
        UUID second = insertDraft(adminId);
        UUID third = insertDraft(adminId);
        service.setPublication(first, adminId, CurationStatus.PUBLISHED, "trace-1");
        service.setPublication(second, adminId, CurationStatus.PUBLISHED, "trace-2");
        service.setPublication(third, adminId, CurationStatus.PUBLISHED, "trace-3");

        service.setPublication(second, adminId, CurationStatus.DRAFT, "trace-4");
        assertThat(mainOrder()).containsExactly(first, third);

        service.replaceMainOrder(adminId, List.of(third, first), "trace-5");
        assertThat(mainOrder()).containsExactly(third, first);
    }

    @Test
    @DisplayName("서로 다른 초안의 최초 동시 게시는 고유한 연속 슬롯으로 직렬화된다")
    void 동시게시_빈게시집합_고유슬롯보장() throws Exception {
        UUID adminId = insertAdmin();
        UUID first = insertDraft(adminId);
        UUID second = insertDraft(adminId);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<?>> futures = List.of(
                    executor.submit(() -> publishAfter(start, first, adminId, "trace-a")),
                    executor.submit(() -> publishAfter(start, second, adminId, "trace-b")));
            start.countDown();
            for (Future<?> future : futures) future.get(20, TimeUnit.SECONDS);
        }

        assertThat(jdbcTemplate.queryForList("SELECT main_position FROM curation "
                + "WHERE publication_status = 'PUBLISHED' ORDER BY main_position", Integer.class))
                .containsExactly(1, 2);
    }

    @Test
    @DisplayName("비공개된 연결 맛집은 경고하고 변경 관리자만 updated_by에 반영한다")
    void 관리자상세_맛집상태변경_관계경고와감사필드() {
        UUID creator = insertAdmin();
        UUID editor = insertAdmin();
        UUID curationId = UUID.randomUUID();
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                store.create(curationId, "제목", "", creator, NOW));
        UUID restaurantId = insertRestaurant();
        service.replaceRestaurants(curationId, editor, List.of(restaurantId), "trace-link");
        jdbcTemplate.update("UPDATE restaurant SET publication_status = 'PRIVATE' WHERE id = ?", restaurantId);

        var detail = service.getCuration(curationId);

        assertThat(store.find(curationId, false)).hasValueSatisfying(stored -> {
            assertThat(stored.createdBy()).isEqualTo(creator);
            assertThat(stored.updatedBy()).isEqualTo(editor);
        });
        assertThat(detail.items()).singleElement().satisfies(item -> {
            assertThat(item.availability()).isEqualTo("PRIVATE");
            assertThat(item.warning()).isNotBlank();
        });
        assertThat(service.getCurations(null, 1, 20).items()).singleElement().satisfies(summary -> {
            assertThat(summary.restaurantCount()).isEqualTo(1);
            assertThat(summary.hasHiddenRestaurants()).isTrue();
        });
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM curation_restaurant WHERE curation_id = ?",
                Long.class, curationId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("공개 목록과 상세는 게시·메인·구성 순서를 지키고 비공개 또는 삭제 맛집을 숨긴다")
    void 공개조회_게시와공개상태필터_순서와빈구성유지() {
        UUID adminId = insertAdmin();
        UUID firstCurationId = insertDraft(adminId);
        UUID secondCurationId = insertDraft(adminId);
        UUID draftCurationId = insertDraft(adminId);
        service.setPublication(firstCurationId, adminId, CurationStatus.PUBLISHED, "trace-publish-1");
        service.setPublication(secondCurationId, adminId, CurationStatus.PUBLISHED, "trace-publish-2");
        UUID firstRestaurantId = insertRestaurant("첫 맛집", "서울 첫길 1");
        UUID privateRestaurantId = insertRestaurant("비공개 맛집", "서울 숨김길 2");
        UUID lastRestaurantId = insertRestaurant("마지막 맛집", "서울 마지막길 3");
        service.replaceRestaurants(firstCurationId, adminId,
                List.of(firstRestaurantId, privateRestaurantId, lastRestaurantId), "trace-items");
        jdbcTemplate.update("UPDATE restaurant SET publication_status = 'PRIVATE' WHERE id = ?",
                privateRestaurantId);

        var list = publicService.getPublishedCurations();

        assertThat(list).extracting(item -> item.curationId())
                .containsExactly(firstCurationId, secondCurationId);
        assertThat(list.getFirst().items()).extracting(item -> item.restaurantId())
                .containsExactly(firstRestaurantId, lastRestaurantId);
        assertThat(list.getFirst().items()).extracting(item -> item.roadAddress())
                .containsExactly("서울 첫길 1", "서울 마지막길 3");
        assertThat(list.get(1).items()).isEmpty();

        jdbcTemplate.update("UPDATE restaurant SET publication_status = 'PRIVATE', "
                        + "lifecycle_status = 'DELETED', deleted_at = ? WHERE id IN (?, ?)",
                NOW, firstRestaurantId, lastRestaurantId);
        assertThat(publicService.getPublishedCuration(firstCurationId).items()).isEmpty();
        assertThatThrownBy(() -> publicService.getPublishedCuration(draftCurationId))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("CURATION_NOT_FOUND"));
        assertThatThrownBy(() -> publicService.getPublishedCuration(UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("CURATION_NOT_FOUND"));
    }

    private void publishAfter(CountDownLatch start, UUID curationId, UUID adminId, String traceId) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
            service.setPublication(curationId, adminId, CurationStatus.PUBLISHED, traceId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private List<UUID> mainOrder() {
        return jdbcTemplate.queryForList("SELECT id FROM curation WHERE publication_status = 'PUBLISHED' "
                + "ORDER BY main_position", UUID.class);
    }

    private UUID insertAdmin() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO admin_account (id, login_id, password_hash) VALUES (?, ?, 'hash')",
                id, "admin-" + id);
        return id;
    }

    private UUID insertDraft(UUID adminId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO curation (id, title, description, created_by, updated_by, created_at, updated_at) "
                + "VALUES (?, '제목', '', ?, ?, ?, ?)", id, adminId, adminId, NOW, NOW);
        return id;
    }

    private UUID insertRestaurant() {
        return insertRestaurant("테스트 맛집", "서울 테스트로 1");
    }

    private UUID insertRestaurant(String name, String roadAddress) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO restaurant (id, region_id, food_category_id, name, kakao_place_id, "
                        + "kakao_place_url, road_address, phone_number, publication_status, lifecycle_status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, '02-1234-5678', 'PUBLIC', 'ACTIVE')",
                id, REGION_ID, CATEGORY_ID, name, "KAKAO-" + id, "https://example.com/" + id, roadAddress);
        return id;
    }
}

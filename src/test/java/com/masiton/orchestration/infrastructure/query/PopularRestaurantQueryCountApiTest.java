package com.masiton.orchestration.infrastructure.query;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masiton.ai.infrastructure.persistence.AiTemporaryInputCleanupScheduler;
import com.masiton.ai.infrastructure.worker.AiExtractionWorkerScheduler;
import com.masiton.common.idempotency.infrastructure.scheduling.IdempotencyRecordCleanupScheduler;
import com.masiton.member.application.MemberActionMailOutboxService;
import com.masiton.member.application.MemberDeletionCleanupService;
import com.masiton.member.application.MemberSessionRevocationRecoveryService;
import com.masiton.orchestration.infrastructure.retention.RetentionCleanupScheduler;
import com.masiton.personal.infrastructure.scheduling.RecentRestaurantViewCleanupScheduler;
import com.masiton.test.QueryCountingDataSourceConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TST-E2-PERF-001(2) 인기 맛집 조회의 쿼리 수가 찜 건수·맛집 건수와 무관하게 상수임을 고정한다.
 * ADR-DATA-011에 따라 {@code PopularRestaurantQueryAdapter}는 단일 집계 SQL만 실행하므로
 * 회원·찜이 늘어도 반복 조회가 생기지 않아야 한다. {@link QueryCountingDataSourceConfiguration}를
 * 공유 fixture로 재사용한다.
 */
@SpringBootTest
@com.masiton.test.TestProfile
@AutoConfigureMockMvc
@Testcontainers
@Import(QueryCountingDataSourceConfiguration.class)
@DisplayName("인기 맛집 조회 쿼리 수")
class PopularRestaurantQueryCountApiTest {

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
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // The global query counter must observe only the two measured MockMvc requests.
    @MockitoBean
    private AiExtractionWorkerScheduler aiExtractionWorkerScheduler;

    @MockitoBean
    private AiTemporaryInputCleanupScheduler aiTemporaryInputCleanupScheduler;

    @MockitoBean
    private IdempotencyRecordCleanupScheduler idempotencyRecordCleanupScheduler;

    @MockitoBean
    private MemberActionMailOutboxService memberActionMailOutboxService;

    @MockitoBean
    private MemberDeletionCleanupService memberDeletionCleanupService;

    @MockitoBean
    private MemberSessionRevocationRecoveryService memberSessionRevocationRecoveryService;

    @MockitoBean
    private RecentRestaurantViewCleanupScheduler recentRestaurantViewCleanupScheduler;

    @MockitoBean
    private RetentionCleanupScheduler retentionCleanupScheduler;

    @Test
    @DisplayName("인기 맛집 조회는 찜 건수와 공개 맛집 건수가 늘어도 쿼리 수가 같다")
    void 인기맛집조회_찜과맛집건수증가_쿼리수상수() throws Exception {
        // given: 맛집 1개, 찜 1건
        UUID smallMemberId = insertMember();
        UUID smallRestaurantId = insertRestaurant();
        insertFavorite(smallMemberId, smallRestaurantId);

        // 두 측정의 Hikari 커넥션 풀 상태를 동일하게 맞추기 위해 측정 전에 한 번 호출한다.
        mockMvc.perform(get("/api/restaurants/popular")).andExpect(status().isOk());

        QueryCountingDataSourceConfiguration.reset();
        mockMvc.perform(get("/api/restaurants/popular")).andExpect(status().isOk());
        int smallScenarioQueryCount = QueryCountingDataSourceConfiguration.preparedStatementCount();

        // given: 맛집 200개, 회원 50명, 맛집별 찜 1~50건 (PopularRestaurantQueryPlan 테스트와 같은 규모)
        List<UUID> memberIds = new ArrayList<>();
        for (int index = 0; index < 50; index++) {
            memberIds.add(insertMember());
        }
        for (int restaurantIndex = 0; restaurantIndex < 200; restaurantIndex++) {
            UUID restaurantId = insertRestaurant();
            int favoriteCount = (restaurantIndex % memberIds.size()) + 1;
            for (int memberIndex = 0; memberIndex < favoriteCount; memberIndex++) {
                insertFavorite(memberIds.get(memberIndex), restaurantId);
            }
        }

        QueryCountingDataSourceConfiguration.reset();
        mockMvc.perform(get("/api/restaurants/popular")).andExpect(status().isOk());
        int largeScenarioQueryCount = QueryCountingDataSourceConfiguration.preparedStatementCount();

        // then
        assertThat(largeScenarioQueryCount)
                .as("맛집 1개·찜 1건일 때 쿼리 수 %d, 맛집 200개·찜 최대 50건일 때 쿼리 수 %d",
                        smallScenarioQueryCount, largeScenarioQueryCount)
                .isEqualTo(smallScenarioQueryCount);
    }

    private UUID insertMember() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO member_account (id, email, password_hash, email_verified_at, status) "
                        + "VALUES (?, ?, 'password-hash', CURRENT_TIMESTAMP, 'ACTIVE')",
                id, id + "@example.com");
        return id;
    }

    private UUID insertRestaurant() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO restaurant (id, region_id, food_category_id, name, kakao_place_id, "
                        + "kakao_place_url, road_address, phone_number, publication_status, lifecycle_status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, '02-1234-5678', 'PUBLIC', 'ACTIVE')",
                id, SEED_REGION_ID, SEED_FOOD_CATEGORY_ID, "대표 맛집", "KAKAO-" + id,
                "https://example.com/" + id, "서울특별시 종로구 대표로 1");
        return id;
    }

    private void insertFavorite(UUID memberId, UUID restaurantId) {
        jdbcTemplate.update("INSERT INTO favorite (member_id, restaurant_id) VALUES (?, ?)",
                memberId, restaurantId);
    }
}

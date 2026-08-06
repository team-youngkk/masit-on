package com.masiton.curation.presentation;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masiton.curation.application.AdminCurationService;
import com.masiton.curation.domain.model.CurationStatus;
import com.masiton.test.QueryCountingDataSourceConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TST-E2-PERF-001(2) 공개 큐레이션 조회의 N+1 회귀를 고정한다.
 * troubleshooting/pr-141-admin-curation-review.md가 기록한 "구성 맛집 최대 20건 단건 반복 조회" 사고와
 * 같은 유형이 공개 목록·상세 조회에도 재발하지 않는지 쿼리 실행 수로 직접 검증한다.
 *
 * <p>{@link QueryCountingDataSourceConfiguration}를 공유 fixture로 사용한다. 구성 항목 수가 1건에서
 * 최대치로 늘어도 실행되는 쿼리 수가 상수임을 확인하며, 이는 findPublished + findRestaurants(Collection) +
 * findRestaurantReferences(Collection) 세 번의 배치 조회로 고정돼야 한다는 계약을 회귀 테스트로 지킨다.
 */
@SpringBootTest
@com.masiton.test.TestProfile
@AutoConfigureMockMvc
@Testcontainers
@Import(QueryCountingDataSourceConfiguration.class)
@DisplayName("공개 큐레이션 조회 쿼리 수")
class PublicCurationQueryCountApiTest {

    private static final UUID SEED_REGION_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID SEED_FOOD_CATEGORY_ID =
            UUID.fromString("20000000-0000-4000-8000-000000000001");

    /** FR-CURATION-003 메인 5개 상한. */
    private static final int PUBLISHED_CURATION_COUNT = 5;
    /** FR-CURATION-001 구성 상한 20건. */
    private static final int MAX_RESTAURANT_PER_CURATION = 20;

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

    @Autowired
    private AdminCurationService adminCurationService;

    @BeforeEach
    void clear() {
        jdbcTemplate.update("DELETE FROM curation_restaurant");
        jdbcTemplate.update("DELETE FROM curation");
    }

    @Test
    @DisplayName("공개 큐레이션 목록 조회는 게시 큐레이션과 구성 맛집이 늘어도 쿼리 수가 같다")
    void 공개목록조회_게시큐레이션과구성맛집수증가_쿼리수상수() throws Exception {
        UUID adminId = insertAdmin();

        UUID smallCurationId = insertDraftCuration(adminId, "작은 큐레이션");
        adminCurationService.replaceRestaurants(smallCurationId, adminId,
                List.of(insertRestaurant()), "trace-small");
        adminCurationService.setPublication(smallCurationId, adminId, CurationStatus.PUBLISHED, "trace-small-pub");

        QueryCountingDataSourceConfiguration.reset();
        mockMvc.perform(get("/api/curations")).andExpect(status().isOk());
        int smallScenarioQueryCount = QueryCountingDataSourceConfiguration.preparedStatementCount();

        clear();
        for (int index = 0; index < PUBLISHED_CURATION_COUNT; index++) {
            UUID curationId = insertDraftCuration(adminId, "큰 큐레이션 " + index);
            adminCurationService.replaceRestaurants(curationId, adminId,
                    insertRestaurants(MAX_RESTAURANT_PER_CURATION), "trace-large-" + index);
            adminCurationService.setPublication(curationId, adminId,
                    CurationStatus.PUBLISHED, "trace-large-pub-" + index);
        }

        QueryCountingDataSourceConfiguration.reset();
        mockMvc.perform(get("/api/curations")).andExpect(status().isOk());
        int largeScenarioQueryCount = QueryCountingDataSourceConfiguration.preparedStatementCount();

        assertThat(largeScenarioQueryCount)
                .as("게시 큐레이션 1건·구성 1건일 때 쿼리 수 %d, 게시 큐레이션 %d건·구성 각 %d건일 때 쿼리 수 %d",
                        smallScenarioQueryCount, PUBLISHED_CURATION_COUNT, MAX_RESTAURANT_PER_CURATION,
                        largeScenarioQueryCount)
                .isEqualTo(smallScenarioQueryCount);
    }

    @Test
    @DisplayName("공개 큐레이션 상세 조회는 구성 맛집이 1개여도 20개여도 쿼리 수가 같다")
    void 공개상세조회_구성맛집1개와20개_쿼리수동일() throws Exception {
        UUID adminId = insertAdmin();

        UUID smallCurationId = insertDraftCuration(adminId, "작은 큐레이션");
        adminCurationService.replaceRestaurants(smallCurationId, adminId,
                List.of(insertRestaurant()), "trace-small");
        adminCurationService.setPublication(smallCurationId, adminId, CurationStatus.PUBLISHED, "trace-small-pub");

        QueryCountingDataSourceConfiguration.reset();
        mockMvc.perform(get("/api/curations/{id}", smallCurationId)).andExpect(status().isOk());
        int smallScenarioQueryCount = QueryCountingDataSourceConfiguration.preparedStatementCount();

        UUID largeCurationId = insertDraftCuration(adminId, "큰 큐레이션");
        adminCurationService.replaceRestaurants(largeCurationId, adminId,
                insertRestaurants(MAX_RESTAURANT_PER_CURATION), "trace-large");
        adminCurationService.setPublication(largeCurationId, adminId, CurationStatus.PUBLISHED, "trace-large-pub");

        QueryCountingDataSourceConfiguration.reset();
        mockMvc.perform(get("/api/curations/{id}", largeCurationId)).andExpect(status().isOk());
        int largeScenarioQueryCount = QueryCountingDataSourceConfiguration.preparedStatementCount();

        assertThat(largeScenarioQueryCount)
                .as("구성 맛집 1개일 때 쿼리 수 %d, 구성 맛집 %d개일 때 쿼리 수 %d",
                        smallScenarioQueryCount, MAX_RESTAURANT_PER_CURATION, largeScenarioQueryCount)
                .isEqualTo(smallScenarioQueryCount);
    }

    private UUID insertAdmin() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO admin_account (id, login_id, password_hash) VALUES (?, ?, 'hash')",
                id, "admin-" + id);
        return id;
    }

    private UUID insertDraftCuration(UUID adminId, String title) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO curation (id, title, description, created_by, updated_by, "
                        + "created_at, updated_at) VALUES (?, ?, '', ?, ?, ?, ?)",
                id, title, adminId, adminId, OffsetDateTime.now(), OffsetDateTime.now());
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

    private List<UUID> insertRestaurants(int count) {
        List<UUID> ids = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            ids.add(insertRestaurant());
        }
        return ids;
    }
}

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masiton.curation.application.AdminCurationService;
import com.masiton.curation.domain.model.CurationStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;

/**
 * TST-E2-PERF-001(3) NFR-PERFORMANCE-006 공개 큐레이션 조회 내부 처리 p95 500ms 이하 검증이다.
 *
 * <p><b>한계(반드시 읽을 것)</b>: 이 테스트는 단일 프로세스·순차 MockMvc 호출로 p95를 추정한다.
 * NFR-PERFORMANCE-006과 ADR-DATA-011 10절이 요구하는 "정상 부하 50명·20 RPS 동시 부하 테스트"를
 * 대체하지 못한다. k6 같은 부하 도구는 ADR-PERF-001이 아직 미승인 상태라 도입하지 않았고(이슈가
 * "미결정 기술을 완료 조건으로 추가하지 않는다"고 못박음), 동시 접속·네트워크 계층 지연도 재현하지
 * 않는다. 여기서 측정하는 값은 "MockMvc 디스패치를 포함한 순차 처리 시간"이며 CI 머신 성능에 따라
 * 흔들릴 수 있는 flaky 후보다. 앞쪽 호출을 warm-up으로 버리고 표본을 50회 이상 확보해 흔들림을
 * 줄였을 뿐, 절대 기준으로 신뢰하지 않는다.
 */
@Disabled("NFR-PERFORMANCE-006 부하 검증은 ADR-PERF-001 승인 후 k6로 대체한다. 수동 실행용")
@SpringBootTest
@com.masiton.test.TestProfile
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("공개 큐레이션 조회 내부 처리 p95")
class PublicCurationPerformanceIntegrationTest {

    private static final UUID SEED_REGION_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID SEED_FOOD_CATEGORY_ID =
            UUID.fromString("20000000-0000-4000-8000-000000000001");

    private static final int WARMUP_CALL_COUNT = 10;
    private static final int MEASURED_CALL_COUNT = 60;
    private static final long P95_BUDGET_MILLIS = 500L;

    private static final int PUBLISHED_CURATION_COUNT = 5;
    private static final int RESTAURANT_PER_CURATION = 20;

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
    @DisplayName("대표 데이터에서 공개 큐레이션 목록 조회 내부 처리 p95가 500ms 이하다")
    void 공개목록조회_대표데이터_내부처리p95가500ms이하다() throws Exception {
        insertRepresentativeData();

        long p95Millis = measureP95Millis(() -> mockMvc.perform(get("/api/curations"))
                .andExpect(status().isOk()));

        assertThat(p95Millis)
                .as("NFR-PERFORMANCE-006 500ms 예산 대비 측정된 p95(ms). 이 값은 순차 MockMvc 호출 기준이며 "
                        + "50명·20 RPS 동시 부하 결과가 아니다.")
                .isLessThanOrEqualTo(P95_BUDGET_MILLIS);
    }

    @Test
    @DisplayName("대표 데이터에서 공개 큐레이션 상세 조회 내부 처리 p95가 500ms 이하다")
    void 공개상세조회_대표데이터_내부처리p95가500ms이하다() throws Exception {
        List<UUID> publishedCurationIds = insertRepresentativeData();
        UUID curationId = publishedCurationIds.getFirst();

        long p95Millis = measureP95Millis(() -> mockMvc.perform(get("/api/curations/{id}", curationId))
                .andExpect(status().isOk()));

        assertThat(p95Millis)
                .as("NFR-PERFORMANCE-006 500ms 예산 대비 측정된 p95(ms). 이 값은 순차 MockMvc 호출 기준이며 "
                        + "50명·20 RPS 동시 부하 결과가 아니다.")
                .isLessThanOrEqualTo(P95_BUDGET_MILLIS);
    }

    /**
     * 앞쪽 {@link #WARMUP_CALL_COUNT}회를 버리고 이후 {@link #MEASURED_CALL_COUNT}회를 측정해
     * JIT 예열·최초 커넥션 획득 비용이 p95를 왜곡하지 않게 한다. {@code Thread.sleep()}은 쓰지 않는다.
     */
    private long measureP95Millis(MockMvcCall call) throws Exception {
        for (int index = 0; index < WARMUP_CALL_COUNT; index++) {
            call.run();
        }
        List<Long> elapsedMillis = new ArrayList<>();
        for (int index = 0; index < MEASURED_CALL_COUNT; index++) {
            long startNanos = System.nanoTime();
            call.run();
            elapsedMillis.add((System.nanoTime() - startNanos) / 1_000_000);
        }
        elapsedMillis.sort(Long::compareTo);
        int p95Index = (int) Math.ceil(elapsedMillis.size() * 0.95) - 1;
        return elapsedMillis.get(Math.max(0, Math.min(p95Index, elapsedMillis.size() - 1)));
    }

    @FunctionalInterface
    private interface MockMvcCall {
        void run() throws Exception;
    }

    /** 게시 큐레이션 5건(메인 상한), 각 구성 맛집 20건(구성 상한)을 만든다. */
    private List<UUID> insertRepresentativeData() {
        UUID adminId = insertAdmin();
        List<UUID> publishedCurationIds = new ArrayList<>();
        for (int index = 0; index < PUBLISHED_CURATION_COUNT; index++) {
            UUID curationId = insertDraftCuration(adminId, "큐레이션 " + index);
            adminCurationService.replaceRestaurants(curationId, adminId,
                    insertRestaurants(RESTAURANT_PER_CURATION), "trace-seed-" + index);
            adminCurationService.setPublication(curationId, adminId, CurationStatus.PUBLISHED,
                    "trace-publish-" + index);
            publishedCurationIds.add(curationId);
        }
        return publishedCurationIds;
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

    private List<UUID> insertRestaurants(int count) {
        List<UUID> ids = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            UUID id = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO restaurant (id, region_id, food_category_id, name, kakao_place_id, "
                            + "kakao_place_url, road_address, phone_number, publication_status, lifecycle_status) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, '02-1234-5678', 'PUBLIC', 'ACTIVE')",
                    id, SEED_REGION_ID, SEED_FOOD_CATEGORY_ID, "대표 맛집", "KAKAO-" + id,
                    "https://example.com/" + id, "서울특별시 종로구 대표로 1");
            ids.add(id);
        }
        return ids;
    }
}

package com.masiton.orchestration.presentation.detail;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masiton.orchestration.application.port.out.RestaurantDetailContentQueryPort;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code RestaurantDetailContentQueryPort}만 {@link MockitoBean}으로 대체하고 나머지는 실제
 * Spring 컨텍스트(PostgreSQL, 트랜잭션 AOP)를 그대로 사용한다. {@code VisitContentQueryService}의
 * 실제 {@code @Transactional(readOnly = true)} Proxy를 통해 예외가 전파되는 경로를 검증해야
 * {@code RestaurantDetailQueryService}가 콘텐츠 실패를 Mock 없이도 격리하는지 확인할 수 있다
 * (transaction-boundaries.md 5절). Mockito Mock으로 Port만 교체하고 Service Bean은 실제로 두므로
 * 이 경로의 실제 트랜잭션 전파 동작이 그대로 재현된다.
 *
 * <p>별도 테스트 클래스로 분리한 이유는 {@link MockitoBean}이 클래스 전체 컨텍스트의 Port Bean을
 * 교체하므로, 실제 DB 콘텐츠를 검증하는 {@link RestaurantDetailApiTest}의 다른 테스트에 영향을
 * 주지 않기 위함이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("맛집 상세 조회 API 콘텐츠 실패 격리")
class RestaurantDetailContentFailureIntegrationTest {

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

    @MockitoBean
    private RestaurantDetailContentQueryPort restaurantDetailContentQueryPort;

    @Test
    @DisplayName(
            "콘텐츠 Port가 실제 Transactional Proxy를 통해 예외를 던져도 "
                    + "UnexpectedRollbackException 없이 200과 TEMPORARILY_UNAVAILABLE을 반환한다")
    void 상세조회_콘텐츠Port가실제프록시에서예외_UnexpectedRollback없이200과TEMPORARILY_UNAVAILABLE을반환한다()
            throws Exception {
        // given
        UUID restaurantId = UUID.randomUUID();
        insertRestaurant(restaurantId, "콘텐츠실패맛집");
        when(restaurantDetailContentQueryPort.findValidVisitContentRowsByRestaurantId(any()))
                .thenThrow(new RuntimeException("일시적인 콘텐츠 저장소 오류"));

        // when & then
        mockMvc.perform(get("/api/restaurants/{restaurantId}", restaurantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("콘텐츠실패맛집"))
                .andExpect(jsonPath("$.contentStatus").value("TEMPORARILY_UNAVAILABLE"))
                .andExpect(jsonPath("$.visitedBy", org.hamcrest.Matchers.hasSize(0)))
                .andExpect(jsonPath("$.videos", org.hamcrest.Matchers.hasSize(0)));
    }

    private void insertRestaurant(UUID id, String name) {
        UUID regionId = jdbcTemplate.queryForObject(
                "SELECT id FROM region ORDER BY sort_order LIMIT 1", UUID.class);
        jdbcTemplate.update(
                "INSERT INTO restaurant "
                        + "(id, region_id, food_category_id, name, kakao_place_id, kakao_place_url, "
                        + "road_address, phone_number, publication_status, lifecycle_status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PUBLIC', 'ACTIVE')",
                id,
                regionId,
                SEED_FOOD_CATEGORY_ID,
                name,
                "KAKAO-" + id,
                "https://place.map.kakao.com/" + id,
                "서울특별시 종로구 테스트로 1",
                "02-1234-5678");
    }
}

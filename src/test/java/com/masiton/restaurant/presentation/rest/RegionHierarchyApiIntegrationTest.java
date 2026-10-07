package com.masiton.restaurant.presentation.rest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("지역 계층 공개 API")
class RegionHierarchyApiIntegrationTest extends com.masiton.test.FullContextIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUpTransactionalState() {
        cleanupTransactionalState(jdbcTemplate);
    }

    @AfterEach
    void restoreRegionActivity() {
        jdbcTemplate.update("UPDATE region SET active = true");
    }

    @Test
    @DisplayName("인증 없이 전체 지역 트리의 code·name·children 형태와 빈 하위를 반환한다")
    void 지역조회_무인증전체트리_계약형태와빈하위를반환한다() throws Exception {
        // given: V20 지역 마스터가 초기화된 상태다.
        // when
        mockMvc.perform(get("/api/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(16))
                .andExpect(jsonPath("$.items[0].code").value("1100000000"))
                .andExpect(jsonPath("$.items[0].name").value("서울특별시"))
                .andExpect(jsonPath("$.items[0].children").isArray())
                .andExpect(jsonPath("$.items[7].code").value("3611000000"))
                .andExpect(jsonPath("$.items[7].children").isEmpty());
        // then: 무인증 공개 트리의 구조와 세종의 빈 하위를 확인했다.
    }

    @Test
    @DisplayName("지역 트리 쿼리 파라미터는 보안 우회가 아니라 INVALID_REQUEST로 거부한다")
    void 지역조회_쿼리파라미터_400으로거부한다() throws Exception {
        // given: 공개 지역 API에 허용되지 않은 쿼리 파라미터를 전달한다.
        // when / then
        mockMvc.perform(get("/api/regions").param("active", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("비활성 부모의 직접 지역 코드는 실제 RegionPort 검증에서 400으로 거부한다")
    void 지역검색_비활성부모의직접코드_400으로거부한다() throws Exception {
        // given: 서울 시도 부모를 비활성화한다.
        jdbcTemplate.update("UPDATE region SET active = false WHERE administrative_code = '1100000000'");

        // when / then
        mockMvc.perform(get("/api/restaurants").param("regionCode", "1144000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("regionCode"));
    }

    @Test
    @DisplayName("활성 지역에 연결된 맛집이 없어도 유효한 세종 코드는 200 빈 결과를 반환한다")
    void 지역검색_유효하지만맛집없음_200빈결과를반환한다() throws Exception {
        // given: 활성 세종 지역에는 테스트 맛집을 적재하지 않는다.
        // when / then
        mockMvc.perform(get("/api/restaurants").param("regionCode", "3611000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    @DisplayName("비활성 부모 아래 자식은 공개 지역 트리에서도 함께 제외한다")
    void 지역조회_비활성부모_자식도제외한다() throws Exception {
        // given: 서울 시도 부모를 비활성화한다.
        jdbcTemplate.update("UPDATE region SET active = false WHERE administrative_code = '1100000000'");

        // when / then
        mockMvc.perform(get("/api/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value("1200000000"))
                .andExpect(jsonPath("$.items[0].children").isArray());
    }
}

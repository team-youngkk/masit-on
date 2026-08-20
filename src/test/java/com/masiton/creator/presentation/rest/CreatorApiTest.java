package com.masiton.creator.presentation.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API-CREATOR-DISCOVERY-001. 이 클래스의 테스트는 creator 테이블에 아무 것도 적재하지 않으므로
 * 빈 목록 계약 검증과 쿼리 파라미터 검증이 서로의 데이터에 영향을 주지 않는다.
 */
@SpringBootTest
@com.masiton.test.TestProfile
@AutoConfigureMockMvc
@DisplayName("유튜버 필터 선택 목록 API")
class CreatorApiTest extends com.masiton.test.FullContextIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("등록된 공개 유튜버가 없으면 200과 빈 items를 반환한다")
    void 조회_등록된공개유튜버없음_200과빈items를반환한다() throws Exception {
        mockMvc.perform(get("/api/creators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("지원하지 않는 쿼리 파라미터가 있으면 400 INVALID_REQUEST를 반환한다")
    void 조회_지원하지않는쿼리파라미터존재_400INVALID_REQUEST를반환한다() throws Exception {
        mockMvc.perform(get("/api/creators").param("page", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.traceId").exists());
    }
}

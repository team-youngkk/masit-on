package com.masiton.common.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@com.masiton.test.TestProfile
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("상태 확인 경로")
class HealthCheckIntegrationTest {

    private static final int REDIS_PORT = 6379;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17.10-alpine")
                    .withDatabaseName("masiton")
                    .withUsername("masiton")
                    .withPassword("masiton_local");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:8.8-alpine").withExposedPorts(REDIS_PORT);

    @DynamicPropertySource
    static void registerDependencies(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("live는 의존 서비스와 무관하게 프로세스 생존만 보고한다")
    void live조회_정상기동_UP를반환한다() throws Exception {
        mockMvc.perform(get("/internal/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("ready는 PostgreSQL 준비 상태를 포함한다")
    void ready조회_PostgreSQL정상_UP와db구성요소를반환한다() throws Exception {
        mockMvc.perform(get("/internal/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @Test
    @DisplayName("dependencies는 PostgreSQL과 Redis를 각각 구분해 보고한다")
    void dependencies조회_저장소정상_db와redis를개별표시한다() throws Exception {
        mockMvc.perform(get("/internal/health/dependencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.redis.status").value("UP"));
    }

    @Test
    @DisplayName("상태 응답에 접속 정보와 예외 메시지를 노출하지 않는다")
    void dependencies조회_정상_세부정보를노출하지않는다() throws Exception {
        mockMvc.perform(get("/internal/health/dependencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db.details").doesNotExist())
                .andExpect(jsonPath("$.components.redis.details").doesNotExist());
    }
}

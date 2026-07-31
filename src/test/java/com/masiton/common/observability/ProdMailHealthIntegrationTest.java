package com.masiton.common.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

@SpringBootTest(properties = {
        "management.health.mail.enabled=true",
        "management.endpoint.health.group.dependencies.include=db,redis,mail",
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=1",
        "spring.mail.properties.mail.smtp.connectiontimeout=500"
})
@com.masiton.test.TestProfile
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("운영 SMTP 상태 확인")
class ProdMailHealthIntegrationTest {

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
    @DisplayName("SMTP 연결이 실패하면 dependencies의 mail이 DOWN이고 503을 반환한다")
    void dependencies조회_SMTP연결실패_mailDOWN과503을반환한다() throws Exception {
        mockMvc.perform(get("/internal/health/dependencies"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.redis.status").value("UP"))
                .andExpect(jsonPath("$.components.mail.status").value("DOWN"));
    }
}

package com.masiton.security.infrastructure.persistence;

import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

import com.masiton.test.FullContextIntegrationTest;

@DisplayName("확인 토큰 PostgreSQL 동시성")
class ConfirmationTokenPostgreSqlIntegrationTest {

    private JdbcTemplate jdbcTemplate;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
                FullContextIntegrationTest.POSTGRES.getJdbcUrl(),
                FullContextIntegrationTest.POSTGRES.getUsername(),
                FullContextIntegrationTest.POSTGRES.getPassword()));
        jdbcTemplate.execute("DELETE FROM confirmation_token");
        executorService = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    @DisplayName("동일 토큰의 동시 확정은 하나만 생성하고 나머지는 저장된 결과를 재생한다")
    void confirmation_동일토큰동시확정_하나생성나머지재생() throws Exception {
        UUID adminId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into admin_account (id, login_id, password_hash, role, active)
                values (?, ?, 'hash', 'ADMIN', true)
                """, adminId, "admin-" + adminId);

        UUID tokenId = UUID.randomUUID();
        UUID createdResourceId = UUID.randomUUID();
        jdbcTemplate.update("""
                        insert into confirmation_token (
                            id, token_hash, admin_account_id, resource_type, candidate_schema_version,
                            identity_key, candidate_snapshot, status, issued_at, expires_at)
                        values (?, ?, ?, 'RESTAURANT', 1, 'kakao:123', cast(? as jsonb), 'ISSUED', now(), now() + interval '10 minutes')
                        """,
                tokenId,
                MessageDigest.getInstance("SHA-256").digest("raw-token".getBytes()),
                adminId,
                "{\"name\":\"candidate\"}");

        CountDownLatch start = new CountDownLatch(1);
        Future<String> first = executorService.submit(() -> completeOrReplay(tokenId, createdResourceId, start));
        Future<String> second = executorService.submit(() -> completeOrReplay(tokenId, createdResourceId, start));
        start.countDown();

        List<String> outcomes = List.of(first.get(), second.get());
        assertThat(outcomes).containsExactlyInAnyOrder("CREATED", "REPLAYED");
        assertThat(jdbcTemplate.queryForObject(
                "select result_resource_id from confirmation_token where id = ?", UUID.class, tokenId))
            .isEqualTo(createdResourceId);
        assertThat(jdbcTemplate.queryForObject(
                "select status from confirmation_token where id = ?", String.class, tokenId))
            .isEqualTo("CREATED");
    }

    private String completeOrReplay(UUID tokenId, UUID resourceId, CountDownLatch start) throws Exception {
        start.await();
        try (java.sql.Connection connection = java.sql.DriverManager.getConnection(
                FullContextIntegrationTest.POSTGRES.getJdbcUrl(),
                FullContextIntegrationTest.POSTGRES.getUsername(),
                FullContextIntegrationTest.POSTGRES.getPassword())) {
            connection.setAutoCommit(false);
            try (java.sql.PreparedStatement select = connection.prepareStatement(
                    "select status, result_resource_id from confirmation_token where id = ? for update")) {
                select.setObject(1, tokenId);
                try (java.sql.ResultSet resultSet = select.executeQuery()) {
                    resultSet.next();
                    if (!"ISSUED".equals(resultSet.getString("status"))) {
                        connection.commit();
                        return "REPLAYED";
                    }
                }
            }
            try (java.sql.PreparedStatement complete = connection.prepareStatement("""
                    update confirmation_token
                       set status = 'CREATED', result_resource_id = ?, completed_at = ?
                     where id = ? and status = 'ISSUED'
                    """)) {
                complete.setObject(1, resourceId);
                complete.setObject(2, OffsetDateTime.now());
                complete.setObject(3, tokenId);
                assertThat(complete.executeUpdate()).isEqualTo(1);
            }
            connection.commit();
            return "CREATED";
        }
    }
}

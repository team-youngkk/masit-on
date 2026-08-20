package com.masiton.test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestProfile
@SpringBootTest
public abstract class FullContextIntegrationTest {

    private static final int REDIS_PORT = 6379;

    public static final PostgreSQLContainer POSTGRES;
    public static final GenericContainer<?> REDIS;

    static {
        POSTGRES = new PostgreSQLContainer("postgres:17.10-alpine")
                .withDatabaseName("masiton")
                .withUsername("masiton")
                .withPassword("masiton_test")
                .withCommand("postgres", "-c", "max_connections=500", "-c", "shared_buffers=128MB");
        POSTGRES.start();

        REDIS = new GenericContainer<>("redis:8.8-alpine")
                .withExposedPorts(REDIS_PORT);
        REDIS.start();
    }

    @DynamicPropertySource
    static void dependencyProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    protected org.springframework.jdbc.core.JdbcTemplate baseJdbcTemplate;

    @org.junit.jupiter.api.BeforeEach
    void autoCleanupTransactionalState() {
        if (baseJdbcTemplate != null) {
            cleanupTransactionalState(baseJdbcTemplate);
        }
    }

    public static void cleanupTransactionalState(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("TRUNCATE TABLE ai_candidate_tag_review, ai_extraction_manual_review");
        jdbcTemplate.execute("DELETE FROM ai_registration_unit_review");
        jdbcTemplate.execute("DELETE FROM ai_registration_unit");
        jdbcTemplate.execute("DELETE FROM visit_tag");
        jdbcTemplate.execute("DELETE FROM tag_definition WHERE source <> 'SEED'");
        jdbcTemplate.update("UPDATE tag_definition SET status = 'ACTIVE' WHERE source = 'SEED'");
        jdbcTemplate.execute("DELETE FROM ai_candidate_snapshot");
        jdbcTemplate.execute("DELETE FROM ai_extraction_attempt");
        jdbcTemplate.execute("DELETE FROM ai_extraction_temporary_input");
        jdbcTemplate.execute("DELETE FROM ai_extraction_job");
        jdbcTemplate.execute("DELETE FROM youtube_channel_watch");
        jdbcTemplate.execute("DELETE FROM confirmation_token");
        jdbcTemplate.execute("DELETE FROM curation_restaurant");
        jdbcTemplate.execute("DELETE FROM curation");
        jdbcTemplate.execute("DELETE FROM collection_restaurant");
        jdbcTemplate.execute("DELETE FROM personal_collection");
        jdbcTemplate.execute("DELETE FROM favorite");
        jdbcTemplate.execute("DELETE FROM recent_restaurant_view");
        jdbcTemplate.execute("DELETE FROM notification");
        jdbcTemplate.execute("DELETE FROM moderation_history");
        jdbcTemplate.execute("DELETE FROM report");
        jdbcTemplate.execute("DELETE FROM submission");
        jdbcTemplate.execute("DELETE FROM visit");
        jdbcTemplate.execute("DELETE FROM video");
        jdbcTemplate.execute("DELETE FROM creator");
        jdbcTemplate.execute("DELETE FROM restaurant");
        jdbcTemplate.execute("DELETE FROM member_action_token");
        jdbcTemplate.execute("DELETE FROM member_action_mail_outbox");
        jdbcTemplate.execute("DELETE FROM member_deletion_job");
        jdbcTemplate.execute("DELETE FROM member_session_revocation_recovery");
        jdbcTemplate.execute("DELETE FROM member_session_revocation");
        jdbcTemplate.execute("DELETE FROM admin_account_migration_map");
        jdbcTemplate.execute("DELETE FROM admin_account");
        jdbcTemplate.execute("DELETE FROM member_account");
    }
}

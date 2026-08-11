package com.masiton.ai.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masiton.ai.application.port.out.AiExtractionWorkerStore;
import com.masiton.ai.application.port.out.AiExtractionWorkerStore.ClaimedJob;
import com.masiton.test.TestProfile;

@SpringBootTest
@TestProfile
@Testcontainers
@DisplayName("AI 추출 워커 PostgreSQL lease")
class JdbcAiExtractionWorkerStoreIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.10-alpine")
            .withDatabaseName("masiton")
            .withUsername("masiton")
            .withPassword("masiton_test");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AiExtractionWorkerStore store;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM ai_extraction_attempt");
        jdbcTemplate.update("DELETE FROM ai_extraction_job");
    }

    @Test
    @DisplayName("동시 Worker는 같은 작업을 중복 claim하지 않는다")
    void claim_동시Worker_서로다른작업을claim한다() throws Exception {
        // given
        OffsetDateTime now = OffsetDateTime.parse("2026-08-11T00:00:00Z");
        UUID first = insertQueued("video-1", "REALTIME", now.minusSeconds(1));
        UUID second = insertQueued("video-2", "REALTIME", now);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<ClaimedJob> claim = () -> {
            ready.countDown();
            start.await();
            return store.claim("worker-" + UUID.randomUUID(), now, now.plusSeconds(120), 3,
                    now.minusDays(1), 100).orElseThrow();
        };

        // when
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(claim);
            var two = executor.submit(claim);
            ready.await();
            start.countDown();

            // then
            assertThat(List.of(one.get().jobId(), two.get().jobId()))
                    .containsExactlyInAnyOrder(first, second);
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ai_extraction_job WHERE execution_status = 'RUNNING'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("만료 lease는 새 Worker가 복구하고 이전 소유자는 heartbeat할 수 없다")
    void claim_만료Lease_복구하고이전소유권을거부한다() {
        // given
        OffsetDateTime now = OffsetDateTime.parse("2026-08-11T00:05:00Z");
        UUID jobId = insertRunning("video-recovery", now.minusMinutes(3), now.minusSeconds(1));

        // when
        ClaimedJob claimed = store.claim("worker-new", now, now.plusSeconds(120), 3,
                now.minusDays(1), 100).orElseThrow();

        // then
        assertThat(claimed.jobId()).isEqualTo(jobId);
        assertThat(claimed.attemptNo()).isEqualTo(2);
        assertThat(store.heartbeat(jobId, "worker-old", now.plusSeconds(1), now.plusSeconds(121))).isFalse();
        assertThat(store.heartbeat(jobId, "worker-new", now.plusSeconds(1), now.plusSeconds(121))).isTrue();
        assertThat(store.recordRetryableFailure(jobId, "worker-old", 2, now, now.plusSeconds(2),
                "TIMEOUT")).isFalse();
        assertThat(store.completeFailure(jobId, "worker-old", 2, now, now.plusSeconds(2),
                "TIMEOUT")).isFalse();
        assertThat(store.beginRetry(jobId, "worker-old", now.plusSeconds(2), now.plusSeconds(122),
                3, now.minusDays(1), 100)).isEmpty();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT error_category FROM ai_extraction_attempt
                 WHERE job_id = ? AND attempt_no = 1
                """, String.class, jobId)).isEqualTo("LEASE_EXPIRED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM ai_extraction_attempt WHERE job_id = ?
                """, Integer.class, jobId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT lease_owner FROM ai_extraction_job WHERE id = ?
                """, String.class, jobId)).isEqualTo("worker-new");
    }

    private UUID insertQueued(String videoId, String priority, OffsetDateTime createdAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ai_extraction_job (
                    id, source, priority, youtube_channel_id, youtube_video_id, video_url,
                    input_mode, input_hash, provider, model_version, prompt_version, schema_version, created_at
                ) VALUES (?, 'WEBHOOK', ?, 'channel-id', ?, ?, 'GEMINI_VIDEO_URL', ?,
                          'GOOGLE_GEMINI', 'gemini-3-flash-preview', 'P1', 'S1', ?)
                """, id, priority, videoId, "https://www.youtube.com/watch?v=" + videoId,
                hash(videoId), createdAt);
        return id;
    }

    private UUID insertRunning(String videoId, OffsetDateTime startedAt, OffsetDateTime leaseExpiresAt) {
        UUID id = insertQueued(videoId, "REALTIME", startedAt.minusSeconds(1));
        jdbcTemplate.update("""
                UPDATE ai_extraction_job
                   SET execution_status = 'RUNNING', started_at = ?, attempt_count = 1,
                       lease_owner = 'worker-old', lease_expires_at = ?
                 WHERE id = ?
                """, startedAt, leaseExpiresAt, id);
        return id;
    }

    private byte[] hash(String value) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}

package com.masiton.ai.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static com.masiton.test.IntegrationTestFixtures.sha256;

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

    @Test
    @DisplayName("시도를 소진한 만료 lease는 마지막 시도를 기록하고 작업을 실패 처리한다")
    void failExpiredExhausted_시도소진Lease만료_작업과마지막시도를실패처리한다() {
        // given
        OffsetDateTime now = OffsetDateTime.parse("2026-08-11T00:05:00Z");
        UUID jobId = insertRunning("video-exhausted", now.minusMinutes(3), now.minusSeconds(1));
        jdbcTemplate.update("UPDATE ai_extraction_job SET attempt_count = 3 WHERE id = ?", jobId);

        // when
        int changed = store.failExpiredExhausted(now, 3);

        // then
        assertThat(changed).isEqualTo(1);
        assertThat(jdbcTemplate.queryForMap("""
                SELECT execution_status, error_category, lease_owner, lease_expires_at
                  FROM ai_extraction_job WHERE id = ?
                """, jobId))
                .containsEntry("execution_status", "FAILED")
                .containsEntry("error_category", "LEASE_EXPIRED")
                .containsEntry("lease_owner", null)
                .containsEntry("lease_expires_at", null);
        assertThat(jdbcTemplate.queryForMap("""
                SELECT attempt_no, outcome, error_category
                  FROM ai_extraction_attempt WHERE job_id = ?
                """, jobId))
                .containsEntry("attempt_no", 3)
                .containsEntry("outcome", "FAILED")
                .containsEntry("error_category", "LEASE_EXPIRED");
    }

    @Test
    @DisplayName("lease 보유 Worker는 작업 실패와 provider request id 없는 시도를 함께 기록한다")
    void completeFailure_lease보유Worker_작업과시도를함께기록한다() {
        // given
        OffsetDateTime now = OffsetDateTime.parse("2026-08-11T00:05:00Z");
        UUID jobId = insertQueued("video-failure", "REALTIME", now.minusSeconds(1));
        ClaimedJob claimed = store.claim("worker-owner", now, now.plusSeconds(120), 3,
                now.minusDays(1), 100).orElseThrow();

        // when
        boolean completed = store.completeFailure(claimed.jobId(), "worker-owner", claimed.attemptNo(),
                now, now.plusSeconds(2), "SCHEMA");

        // then
        assertThat(completed).isTrue();
        assertThat(jobId).isEqualTo(claimed.jobId());
        assertThat(jdbcTemplate.queryForMap("""
                SELECT execution_status, error_category FROM ai_extraction_job WHERE id = ?
                """, jobId))
                .containsEntry("execution_status", "FAILED")
                .containsEntry("error_category", "SCHEMA");
        assertThat(jdbcTemplate.queryForMap("""
                SELECT attempt_no, provider_request_id, outcome, error_category
                  FROM ai_extraction_attempt WHERE job_id = ?
                """, jobId))
                .containsEntry("attempt_no", 1)
                .containsEntry("provider_request_id", null)
                .containsEntry("outcome", "FAILED")
                .containsEntry("error_category", "SCHEMA");
    }

    @Test
    @DisplayName("재시도 가능한 실패는 lease 소유권을 유지하며 시도 행을 기록한다")
    void recordRetryableFailure_lease보유Worker_시도를기록한다() {
        // given
        OffsetDateTime now = OffsetDateTime.parse("2026-08-11T00:05:00Z");
        UUID jobId = insertQueued("video-retryable", "REALTIME", now.minusSeconds(1));
        ClaimedJob claimed = store.claim("worker-owner", now, now.plusSeconds(120), 3,
                now.minusDays(1), 100).orElseThrow();

        // when
        boolean recorded = store.recordRetryableFailure(claimed.jobId(), "worker-owner", 1,
                now, now.plusSeconds(2), "TIMEOUT");

        // then
        assertThat(recorded).isTrue();
        assertThat(claimed.jobId()).isEqualTo(jobId);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT execution_status FROM ai_extraction_job WHERE id = ?
                """, String.class, jobId)).isEqualTo("RUNNING");
        assertThat(jdbcTemplate.queryForMap("""
                SELECT attempt_no, provider_request_id, outcome, error_category
                  FROM ai_extraction_attempt WHERE job_id = ?
                """, jobId))
                .containsEntry("attempt_no", 1)
                .containsEntry("provider_request_id", null)
                .containsEntry("outcome", "FAILED")
                .containsEntry("error_category", "TIMEOUT");
    }

    @Test
    @DisplayName("quota hard stop은 lease 보유 작업을 새 시도 없이 실패 처리한다")
    void failWithoutAttempt_lease보유Worker_시도없이실패처리한다() {
        // given
        OffsetDateTime now = OffsetDateTime.parse("2026-08-11T00:05:00Z");
        UUID jobId = insertQueued("video-retry-quota", "REALTIME", now.minusSeconds(1));
        ClaimedJob claimed = store.claim("worker-owner", now, now.plusSeconds(120), 3,
                now.minusDays(1), 100).orElseThrow();

        // when
        boolean failed = store.failWithoutAttempt(claimed.jobId(), "worker-owner",
                now.plusSeconds(2), "QUOTA_HARD_STOP");

        // then
        assertThat(failed).isTrue();
        assertThat(claimed.jobId()).isEqualTo(jobId);
        assertThat(jdbcTemplate.queryForMap("""
                SELECT execution_status, error_category FROM ai_extraction_job WHERE id = ?
                """, jobId))
                .containsEntry("execution_status", "FAILED")
                .containsEntry("error_category", "QUOTA_HARD_STOP");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM ai_extraction_attempt WHERE job_id = ?
                """, Integer.class, jobId)).isZero();
    }

    @Test
    @DisplayName("quota 사용량은 창 안의 완료 시도와 아직 기록되지 않은 진행 시도를 합산한다")
    void quotaUsage_완료시도와진행시도_창안건수를합산한다() {
        // given
        OffsetDateTime now = OffsetDateTime.parse("2026-08-11T00:05:00Z");
        UUID completedJob = insertQueued("video-completed", "REALTIME", now.minusSeconds(2));
        ClaimedJob completed = store.claim("worker-completed", now, now.plusSeconds(120), 3,
                now.minusDays(1), 100).orElseThrow();
        assertThat(completed.jobId()).isEqualTo(completedJob);
        assertThat(store.completeFailure(completed.jobId(), "worker-completed", 1,
                now, now.plusSeconds(1), "SCHEMA")).isTrue();

        UUID runningJob = insertQueued("video-running", "REALTIME", now.plusSeconds(2));
        ClaimedJob running = store.claim("worker-running", now.plusSeconds(3), now.plusSeconds(123), 3,
                now.minusDays(1), 100).orElseThrow();
        assertThat(running.jobId()).isEqualTo(runningJob);

        // when & then
        assertThat(store.quotaUsage(now.minusDays(1))).isEqualTo(2);
        assertThat(store.quotaUsage(now.plusSeconds(2))).isEqualTo(1);
    }

    @Test
    @DisplayName("quota hard stop은 대기 작업을 Provider 시도 없이 실패 처리한다")
    void failQueuedForQuota_대기작업_시도없이실패처리한다() {
        // given
        OffsetDateTime now = OffsetDateTime.parse("2026-08-11T00:05:00Z");
        UUID jobId = insertQueued("video-quota", "REALTIME", now.minusSeconds(1));

        // when
        int changed = store.failQueuedForQuota(now);

        // then
        assertThat(changed).isEqualTo(1);
        assertThat(jdbcTemplate.queryForMap("""
                SELECT execution_status, error_category, finished_at
                  FROM ai_extraction_job WHERE id = ?
                """, jobId))
                .containsEntry("execution_status", "FAILED")
                .containsEntry("error_category", "QUOTA_HARD_STOP")
                .containsKey("finished_at");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM ai_extraction_attempt WHERE job_id = ?
                """, Integer.class, jobId)).isZero();
    }

    private UUID insertQueued(String videoId, String priority, OffsetDateTime createdAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ai_extraction_job (
                    id, source, priority, youtube_channel_id, youtube_video_id, video_url,
                    input_mode, input_hash, provider, model_version, prompt_version, schema_version, created_at
                ) VALUES (?, 'WEBHOOK', ?, 'channel-id', ?, ?, 'GEMINI_VIDEO_URL', ?,
                          'GOOGLE_GEMINI', 'gemini-3.5-flash-lite', 'P1', 'S1', ?)
                """, id, priority, videoId, "https://www.youtube.com/watch?v=" + videoId,
                sha256(videoId), createdAt);
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

}

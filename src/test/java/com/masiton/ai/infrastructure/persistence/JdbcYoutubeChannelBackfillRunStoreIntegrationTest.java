package com.masiton.ai.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.OffsetDateTime;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.masiton.ai.application.port.out.YoutubeChannelBackfillRunStore;
import com.masiton.ai.application.port.in.AiExtractionJobUseCase;
import com.masiton.ai.application.YoutubeChannelBackfillService;
import com.masiton.ai.application.port.out.YoutubeChannelVideoQueryPort;
import com.masiton.ai.application.port.out.YoutubeChannelBackfillMetrics;
import com.masiton.ai.infrastructure.worker.YoutubeChannelBackfillProperties;
import com.masiton.test.FullContextIntegrationTest;
import com.masiton.test.TestProfile;

@SpringBootTest
@TestProfile
@DisplayName("YouTube 채널 백필 run PostgreSQL 통합")
class JdbcYoutubeChannelBackfillRunStoreIntegrationTest extends FullContextIntegrationTest {

    @Autowired
    private YoutubeChannelBackfillRunStore store;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AiExtractionJobUseCase jobs;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("수동 POST 없이 활성 채널의 첫 실행과 완료 다음 주기 실행을 생성한다")
    void 자동접수_활성채널_첫실행과다음주기를등록한다() {
        // Given
        UUID creatorId = UUID.randomUUID();
        String channelId = "channel-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        insertCreatorAndWatch(creatorId, channelId, true, "ACTIVE");
        try {
            // When
            scheduleAt(now);
            UUID first = jdbcTemplate.queryForObject(
                    "SELECT id FROM youtube_channel_backfill_run WHERE creator_id=?", UUID.class, creatorId);
            scheduleAt(now.plusHours(2));
            assertThat(runCount(creatorId)).isEqualTo(1);
            jdbcTemplate.update("UPDATE youtube_channel_backfill_run SET status='SUCCEEDED', updated_at=? WHERE id=?",
                    now, first);
            scheduleAt(now.plusSeconds(3599));
            assertThat(runCount(creatorId)).isEqualTo(1);
            scheduleAt(now.plusHours(1));

            // Then
            assertThat(runCount(creatorId)).isEqualTo(2);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT page_token FROM youtube_channel_backfill_run WHERE creator_id=? AND status='QUEUED'",
                    String.class, creatorId)).isNull();
        } finally {
            deleteFixture(creatorId);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"MAX_PAGES_PER_RUN", "MAX_VIDEOS_PER_RUN", "FAILED"})
    @DisplayName("상한 중지와 실패 실행은 다음 주기에 기존 커서와 처리 누계를 유지해 재개한다")
    void 자동접수_상한또는실패_주기도래시커서재개한다(String reason) {
        // Given
        UUID creatorId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        insertCreatorAndWatch(creatorId, "channel-" + UUID.randomUUID(), true, "ACTIVE");
        try {
            UUID runId = store.createOrReuse(creatorId, now).orElseThrow().run().runId();
            jdbcTemplate.update("""
                    UPDATE youtube_channel_backfill_run
                       SET status=?, stop_reason=?, page_token='resume-token',
                           scanned_count=50, page_count=1, submitted_count=7, updated_at=?
                     WHERE id=?
                    """, reason.equals("FAILED") ? "FAILED" : "STOPPED",
                    reason.equals("FAILED") ? null : reason, now, runId);

            // When
            scheduleAt(now.plusSeconds(3599));
            assertThat(store.find(creatorId, runId).orElseThrow().status()).isNotEqualTo("QUEUED");
            scheduleAt(now.plusHours(1));

            // Then
            assertThat(runCount(creatorId)).isEqualTo(1);
            assertThat(store.find(creatorId, runId).orElseThrow().status()).isEqualTo("QUEUED");
            assertThat(store.find(creatorId, runId).orElseThrow().submittedCount()).isEqualTo(7);
            var claimed = store.claim(now.plusHours(1), now.plusHours(2), "owner").orElseThrow();
            assertThat(claimed.pageToken()).isEqualTo("resume-token");
            assertThat(claimed.pageCount()).isZero();
        } finally {
            deleteFixture(creatorId);
        }
    }

    @Test
    @DisplayName("비활성·미검증 채널과 수동 중지 실행은 자동 접수하지 않는다")
    void 자동접수_비활성또는수동중지_등록하지않는다() {
        // Given
        UUID inactive = UUID.randomUUID();
        UUID unverified = UUID.randomUUID();
        UUID stopped = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        insertCreatorAndWatch(inactive, "channel-" + UUID.randomUUID(), false, "INACTIVE");
        insertCreatorAndWatch(unverified, "channel-" + UUID.randomUUID(), true, "UNKNOWN");
        insertCreatorAndWatch(stopped, "channel-" + UUID.randomUUID(), true, "ACTIVE");
        try {
            UUID runId = store.createOrReuse(stopped, now).orElseThrow().run().runId();
            store.stop(stopped, runId, now);
            // When
            scheduleAt(now.plusDays(1));
            // Then
            assertThat(runCount(inactive)).isZero();
            assertThat(runCount(unverified)).isZero();
            assertThat(runCount(stopped)).isEqualTo(1);
            assertThat(store.find(stopped, runId).orElseThrow().status()).isEqualTo("STOPPED");
        } finally {
            deleteFixture(inactive);
            deleteFixture(unverified);
            deleteFixture(stopped);
        }
    }

    @Test
    @DisplayName("동시 스케줄러는 Watch 잠금과 활성 실행 unique로 한 실행만 생성한다")
    void 자동접수_동시스케줄러_실행을하나만생성한다() throws Exception {
        // Given
        UUID creatorId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        insertCreatorAndWatch(creatorId, "channel-" + UUID.randomUUID(), true, "ACTIVE");
        var gate = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Void> schedule = () -> {
                gate.await();
                scheduleAt(now);
                return null;
            };
            // When
            var first = executor.submit(schedule);
            var second = executor.submit(schedule);
            gate.countDown();
            first.get(10, java.util.concurrent.TimeUnit.SECONDS);
            second.get(10, java.util.concurrent.TimeUnit.SECONDS);
            // Then
            assertThat(runCount(creatorId)).isEqualTo(1);
        } finally {
            deleteFixture(creatorId);
        }
    }

    private void scheduleAt(OffsetDateTime now) {
        YoutubeChannelBackfillProperties policy = new YoutubeChannelBackfillProperties();
        policy.setEnabled(true);
        policy.setRunIntervalSeconds(3600);
        var service = new YoutubeChannelBackfillService(store, mock(YoutubeChannelVideoQueryPort.class),
                jobs, policy, Clock.fixed(now.toInstant(), ZoneOffset.UTC), mock(YoutubeChannelBackfillMetrics.class));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> service.scheduleDue());
    }

    private long runCount(UUID creatorId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM youtube_channel_backfill_run WHERE creator_id=?", Long.class, creatorId);
    }

    @Test
    @DisplayName("페이지 상한 중지는 커서와 처리 누계를 유지하고 수동 중지는 재개하지 않는다")
    void stopAtLimit_페이지상한_커서재개와수동중지를구분한다() {
        // Given
        UUID creatorId = UUID.randomUUID();
        String channelId = "channel-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        insertCreatorAndWatch(creatorId, channelId, true, "ACTIVE");
        try {
            UUID runId = store.createOrReuse(creatorId, now).orElseThrow().run().runId();
            store.claim(now, now.plusMinutes(2), "owner").orElseThrow();
            store.recordVideo(runId, "video-1", false, now);
            store.completePage(runId, "owner", 10, "page-11", false, false, now);
            store.claim(now, now.plusMinutes(2), "owner").orElseThrow();

            // When
            store.stopAtLimit(runId, "stale-owner", "MAX_PAGES_PER_RUN", now);
            assertThat(store.find(creatorId, runId).orElseThrow().status()).isEqualTo("RUNNING");
            store.stopAtLimit(runId, "owner", "MAX_PAGES_PER_RUN", now.plusMinutes(3));
            assertThat(store.find(creatorId, runId).orElseThrow().status()).isEqualTo("RUNNING");
            store.stopAtLimit(runId, "owner", "MAX_PAGES_PER_RUN", now);
            YoutubeChannelBackfillRunStore.StartRun resumed = store.createOrReuse(creatorId, now).orElseThrow();
            YoutubeChannelBackfillRunStore.ClaimedRun claimed = store.claim(now, now.plusMinutes(2), "next-owner")
                    .orElseThrow();

            // Then
            assertThat(resumed.run().runId()).isEqualTo(runId);
            assertThat(resumed.run().submittedCount()).isEqualTo(1);
            assertThat(claimed.pageToken()).isEqualTo("page-11");
            assertThat(claimed.pageCount()).isZero();
            assertThat(claimed.scannedCount()).isZero();
            store.stop(creatorId, runId, now);
            assertThat(store.createOrReuse(creatorId, now).orElseThrow().run().runId()).isNotEqualTo(runId);
        } finally {
            deleteFixture(creatorId);
        }
    }

    @Test
    @DisplayName("영상 원장 저장 실패는 Job 생성도 롤백하고 재접수는 신규로 집계한다")
    void 접수_원장저장실패_Job과누계를함께롤백한다() {
        // Given
        UUID creatorId = UUID.randomUUID();
        String channelId = "channel-" + UUID.randomUUID();
        String videoId = "video-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        insertCreatorAndWatch(creatorId, channelId, true, "ACTIVE");
        try {
            UUID runId = store.createOrReuse(creatorId, now).orElseThrow().run().runId();
            store.claim(now, now.plusMinutes(2), "owner").orElseThrow();
            jdbcTemplate.execute("ALTER TABLE youtube_channel_backfill_video ADD CONSTRAINT test_reject_ledger "
                    + "CHECK (youtube_video_id <> '" + videoId + "') NOT VALID");

            // When
            assertThatThrownBy(() -> jobs.submitBackfillIfClaimActive(runId, "owner", channelId, videoId))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

            // Then
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM ai_extraction_job WHERE youtube_video_id=?",
                    Long.class, videoId)).isZero();
            assertThat(store.find(creatorId, runId).orElseThrow().submittedCount()).isZero();
            jdbcTemplate.execute("ALTER TABLE youtube_channel_backfill_video DROP CONSTRAINT test_reject_ledger");
            assertThat(jobs.submitBackfillIfClaimActive(runId, "owner", channelId, videoId)
                    .orElseThrow().reused()).isFalse();
            assertThat(store.find(creatorId, runId).orElseThrow().submittedCount()).isEqualTo(1);
        } finally {
            jdbcTemplate.execute("ALTER TABLE youtube_channel_backfill_video DROP CONSTRAINT IF EXISTS test_reject_ledger");
            jdbcTemplate.update("DELETE FROM ai_extraction_job WHERE youtube_video_id=?", videoId);
            deleteFixture(creatorId);
        }
    }

    @Test
    @DisplayName("lease 재확보 뒤 같은 영상을 접수해도 최초 신규 처리 누계를 유지한다")
    void 접수_Lease재확보후재시도_최초신규누계를유지한다() {
        // Given
        UUID creatorId = UUID.randomUUID();
        String channelId = "channel-" + UUID.randomUUID();
        String videoId = "video-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        insertCreatorAndWatch(creatorId, channelId, true, "ACTIVE");
        try {
            UUID runId = store.createOrReuse(creatorId, now).orElseThrow().run().runId();
            store.claim(now, now.plusMinutes(2), "owner").orElseThrow();
            jobs.submitBackfillIfClaimActive(runId, "owner", channelId, videoId).orElseThrow();
            jdbcTemplate.update("UPDATE youtube_channel_backfill_run SET lease_expires_at=? WHERE id=?",
                    now.minusSeconds(1), runId);

            // When
            store.claim(now, now.plusMinutes(2), "recovered-owner").orElseThrow();
            assertThat(jobs.submitBackfillIfClaimActive(runId, "owner", channelId, videoId)).isEmpty();
            assertThat(jobs.submitBackfillIfClaimActive(runId, "recovered-owner", channelId, videoId)
                    .orElseThrow().reused()).isTrue();

            // Then
            YoutubeChannelBackfillRunStore.Run run = store.find(creatorId, runId).orElseThrow();
            assertThat(run.submittedCount()).isEqualTo(1);
            assertThat(run.reusedCount()).isZero();
        } finally {
            jdbcTemplate.update("DELETE FROM ai_extraction_job WHERE youtube_video_id=?", videoId);
            deleteFixture(creatorId);
        }
    }

    @Test
    @DisplayName("활성 Watch의 백필 run은 중복 접수 시 하나로 수렴한다")
    void createOrReuse_활성Watch_중복접수는하나로수렴한다() {
        UUID creatorId = UUID.randomUUID();
        String channelId = "channel-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-09-15T00:00:00Z");
        insertCreatorAndWatch(creatorId, channelId, true, "ACTIVE");

        try {
            YoutubeChannelBackfillRunStore.StartRun first = store.createOrReuse(creatorId, now).orElseThrow();
            YoutubeChannelBackfillRunStore.StartRun second = store.createOrReuse(creatorId, now.plusSeconds(1)).orElseThrow();

            assertThat(first.reused()).isFalse();
            assertThat(second.reused()).isTrue();
            assertThat(second.run().runId()).isEqualTo(first.run().runId());
        } finally {
            deleteFixture(creatorId);
        }
    }

    @Test
    @DisplayName("비활성 Watch에는 백필 run을 만들지 않는다")
    void createOrReuse_비활성Watch_빈결과를반환한다() {
        UUID creatorId = UUID.randomUUID();
        String channelId = "channel-" + UUID.randomUUID();
        insertCreatorAndWatch(creatorId, channelId, false, "INACTIVE");

        try {
            assertThat(store.createOrReuse(creatorId, OffsetDateTime.now())).isEmpty();
        } finally {
            deleteFixture(creatorId);
        }
    }

    @Test
    @DisplayName("진행 중 run이 있어도 Watch를 비활성화하면 새 보정 접수를 거부한다")
    void createOrReuse_진행중Run과비활성Watch_빈결과를반환한다() {
        UUID creatorId = UUID.randomUUID();
        String channelId = "channel-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-09-15T00:00:00Z");
        insertCreatorAndWatch(creatorId, channelId, true, "ACTIVE");

        try {
            store.createOrReuse(creatorId, now).orElseThrow();
            jdbcTemplate.update("""
                    UPDATE youtube_channel_watch
                       SET enabled = false, subscription_status = 'INACTIVE'
                     WHERE creator_id = ?
                    """, creatorId);

            assertThat(store.createOrReuse(creatorId, now.plusSeconds(1))).isEmpty();
        } finally {
            deleteFixture(creatorId);
        }
    }

    @Test
    @DisplayName("영상 상한으로 중지된 run은 저장한 커서에서 새 실행으로 재개한다")
    void createOrReuse_영상상한중지Run_다음커서에서재개한다() {
        UUID creatorId = UUID.randomUUID();
        String channelId = "channel-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-09-15T00:00:00Z");
        insertCreatorAndWatch(creatorId, channelId, true, "ACTIVE");

        try {
            UUID runId = store.createOrReuse(creatorId, now).orElseThrow().run().runId();
            jdbcTemplate.update("""
                    UPDATE youtube_channel_backfill_run
                       SET status='STOPPED', stop_reason='MAX_VIDEOS_PER_RUN', page_token='resume-token',
                           page_count=2, scanned_count=75, submitted_count=50, reused_count=25,
                           updated_at=?
                     WHERE id=?
                    """, now.plusSeconds(1), runId);

            YoutubeChannelBackfillRunStore.StartRun resumed = store.createOrReuse(creatorId, now.plusSeconds(2))
                    .orElseThrow();
            YoutubeChannelBackfillRunStore.ClaimedRun claimed = store.claim(
                    now.plusSeconds(2), now.plusMinutes(2), "owner").orElseThrow();

            assertThat(resumed.reused()).isFalse();
            assertThat(resumed.run().runId()).isEqualTo(runId);
            assertThat(resumed.run().status()).isEqualTo("QUEUED");
            assertThat(resumed.run().scannedCount()).isZero();
            assertThat(resumed.run().submittedCount()).isEqualTo(50);
            assertThat(resumed.run().reusedCount()).isEqualTo(25);
            assertThat(claimed.runId()).isEqualTo(runId);
            assertThat(claimed.pageToken()).isEqualTo("resume-token");
            assertThat(claimed.pageCount()).isZero();
            assertThat(claimed.scannedCount()).isZero();
        } finally {
            deleteFixture(creatorId);
        }
    }

    @Test
    @DisplayName("같은 run의 영상 처리 결과는 재시도해도 신규·재사용 누계를 중복 집계하지 않는다")
    void recordVideo_같은Run영상재시도_누계를한번만집계한다() {
        UUID creatorId = UUID.randomUUID();
        String channelId = "channel-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-09-15T00:00:00Z");
        insertCreatorAndWatch(creatorId, channelId, true, "ACTIVE");

        try {
            UUID runId = store.createOrReuse(creatorId, now).orElseThrow().run().runId();
            store.recordVideo(runId, "video-1", false, now);
            store.recordVideo(runId, "video-1", true, now.plusSeconds(1));
            store.recordVideo(runId, "video-2", true, now.plusSeconds(1));

            YoutubeChannelBackfillRunStore.Run run = store.find(creatorId, runId).orElseThrow();
            assertThat(run.submittedCount()).isEqualTo(1);
            assertThat(run.reusedCount()).isEqualTo(1);
        } finally {
            deleteFixture(creatorId);
        }
    }

    @Test
    @DisplayName("만료된 lease는 다른 소유자가 같은 run을 재확보할 수 있다")
    void claim_lease만료_다른소유자가재확보한다() {
        UUID creatorId = UUID.randomUUID();
        String channelId = "channel-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-09-15T00:00:00Z");
        insertCreatorAndWatch(creatorId, channelId, true, "ACTIVE");

        try {
            UUID runId = store.createOrReuse(creatorId, now).orElseThrow().run().runId();
            YoutubeChannelBackfillRunStore.ClaimedRun first = store.claim(
                    now, now.plusMinutes(2), "owner-1").orElseThrow();
            YoutubeChannelBackfillRunStore.ClaimedRun recovered = store.claim(
                    now.plusMinutes(3), now.plusMinutes(5), "owner-2").orElseThrow();

            assertThat(first.runId()).isEqualTo(runId);
            assertThat(recovered.runId()).isEqualTo(runId);
            assertThat(recovered.leaseOwner()).isEqualTo("owner-2");
        } finally {
            deleteFixture(creatorId);
        }
    }

    @Test
    @DisplayName("Watch가 비활성화되면 진행 중 백필 run을 중지한다")
    void stopRunsForInactiveWatches_감시비활성화_백필을중지한다() {
        UUID creatorId = UUID.randomUUID();
        String channelId = "channel-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-09-15T00:00:00Z");
        insertCreatorAndWatch(creatorId, channelId, true, "ACTIVE");

        try {
            YoutubeChannelBackfillRunStore.Run run = store.createOrReuse(creatorId, now).orElseThrow().run();
            jdbcTemplate.update("""
                    UPDATE youtube_channel_watch
                       SET enabled = false, subscription_status = 'INACTIVE'
                     WHERE creator_id = ?
                    """, creatorId);

            store.stopRunsForInactiveWatches(now.plusSeconds(1));

            assertThat(store.find(creatorId, run.runId()).orElseThrow().status()).isEqualTo("STOPPED");
            assertThat(store.claim(now.plusSeconds(2), now.plusMinutes(2), "owner")).isEmpty();
        } finally {
            deleteFixture(creatorId);
        }
    }

    private void insertCreatorAndWatch(UUID creatorId, String channelId, boolean enabled, String status) {
        jdbcTemplate.update("""
                INSERT INTO creator (
                    id, external_channel_id, channel_name, channel_url,
                    publication_status, lifecycle_status, external_availability_status,
                    external_status_checked_at
                ) VALUES (?, ?, ?, ?, 'PUBLIC', 'ACTIVE', 'AVAILABLE', ?)
                """, creatorId, channelId, "fixture-" + creatorId,
                "https://example.com/channel/" + creatorId,
                OffsetDateTime.parse("2026-09-14T00:00:00Z"));
        jdbcTemplate.update("""
                INSERT INTO youtube_channel_watch (
                    id, creator_id, youtube_channel_id, enabled, subscription_status
                ) VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), creatorId, channelId, enabled, status);
    }

    private void deleteFixture(UUID creatorId) {
        jdbcTemplate.update("DELETE FROM youtube_channel_backfill_run WHERE creator_id = ?", creatorId);
        jdbcTemplate.update("DELETE FROM youtube_channel_watch WHERE creator_id = ?", creatorId);
        jdbcTemplate.update("DELETE FROM creator WHERE id = ?", creatorId);
    }
}

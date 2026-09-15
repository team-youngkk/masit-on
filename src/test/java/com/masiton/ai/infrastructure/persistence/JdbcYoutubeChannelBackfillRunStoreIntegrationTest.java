package com.masiton.ai.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.masiton.ai.application.port.out.YoutubeChannelBackfillRunStore;
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
            assertThat(claimed.runId()).isEqualTo(runId);
            assertThat(claimed.pageToken()).isEqualTo("resume-token");
            assertThat(claimed.pageCount()).isZero();
            assertThat(claimed.scannedCount()).isZero();
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

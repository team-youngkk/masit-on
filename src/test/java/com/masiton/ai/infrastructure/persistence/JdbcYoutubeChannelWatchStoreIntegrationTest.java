package com.masiton.ai.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masiton.ai.application.port.out.YoutubeChannelWatchStore;
import com.masiton.ai.application.YoutubeChannelWatchPersistenceService;
import com.masiton.test.IntegrationTestFixtures;

@SpringBootTest
@com.masiton.test.TestProfile
@Testcontainers
@DisplayName("YouTube 채널 Watch PostgreSQL 통합")
class JdbcYoutubeChannelWatchStoreIntegrationTest {

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
    private YoutubeChannelWatchStore store;

    @Autowired
    private YoutubeChannelWatchPersistenceService watchPersistence;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("PUBLIC·ACTIVE·AVAILABLE Creator의 Watch를 활성화하면 외부 확인 전 UNKNOWN으로 둔다")
    void upsert_공개활성가용Creator에활성Watch_외부확인전UNKNOWN으로둔다() {
        // given
        UUID creatorId = UUID.randomUUID();
        String channelId = "channel-" + UUID.randomUUID();
        insertCreator(creatorId, channelId);

        try {
            // when
            store.upsert(creatorId, channelId, true, "ACTIVE", IntegrationTestFixtures.sha256("verify-token"));

            // then
            YoutubeChannelWatchStore.Watch watch = store.find(channelId).orElseThrow();
            assertThat(watch.acceptsNotifications()).isFalse();
            assertThat(watch.enabled()).isTrue();
            assertThat(watch.subscriptionStatus()).isEqualTo("UNKNOWN");
            assertThat(readTokenHash(channelId))
                    .containsExactly(IntegrationTestFixtures.sha256("verify-token"));
        } finally {
            deleteFixture(creatorId, channelId);
        }
    }

    @Test
    @DisplayName("기존 Watch를 비활성화해도 알림 메타데이터와 토큰 해시는 보존한다")
    void upsert_기존Watch를비활성화_메타데이터와토큰해시를보존한다() {
        // given
        UUID creatorId = UUID.randomUUID();
        String channelId = "channel-" + UUID.randomUUID();
        byte[] tokenHash = IntegrationTestFixtures.sha256("token-" + UUID.randomUUID());
        OffsetDateTime lastNotificationAt = OffsetDateTime.parse("2026-08-11T01:02:03Z");
        OffsetDateTime lastRenewedAt = OffsetDateTime.parse("2026-08-11T02:03:04Z");
        insertCreator(creatorId, channelId);
        insertWatch(creatorId, channelId, tokenHash, lastNotificationAt, lastRenewedAt, "TIMEOUT");

        try {
            // when
            YoutubeChannelWatchStore.WatchDetail detail = store.upsert(
                    creatorId, channelId, false, "INACTIVE", null);

            // then
            assertThat(detail.enabled()).isFalse();
            assertThat(detail.subscriptionStatus()).isEqualTo("INACTIVE");
            assertThat(detail.lastNotificationAt()).isEqualTo(lastNotificationAt);
            assertThat(detail.lastRenewedAt()).isEqualTo(lastRenewedAt);
            assertThat(detail.lastErrorCategory()).isEqualTo("TIMEOUT");
            assertThat(readTokenHash(channelId)).containsExactly(tokenHash);
        } finally {
            deleteFixture(creatorId, channelId);
        }
    }

    @Test
    @DisplayName("Watch 상태 조회는 PostgreSQL의 실패 상태·시각·오류 범주를 그대로 매핑하고 없는 행은 비운다")
    void findDetail_실패상태와시각매핑_없는행은빈결과를반환한다() {
        UUID creatorId = UUID.randomUUID();
        String channelId = "channel-" + UUID.randomUUID();
        OffsetDateTime lastNotificationAt = OffsetDateTime.parse("2026-08-12T01:02:03Z");
        OffsetDateTime lastRenewedAt = OffsetDateTime.parse("2026-08-13T04:05:06Z");
        insertCreator(creatorId, channelId);
        insertWatch(creatorId, channelId, null, lastNotificationAt, lastRenewedAt, "SUBSCRIPTION_TIMEOUT");
        jdbcTemplate.update("UPDATE youtube_channel_watch SET enabled = true, subscription_status = 'RENEWAL_FAILED' WHERE youtube_channel_id = ?", channelId);

        try {
            assertThat(store.findDetail(channelId)).contains(new YoutubeChannelWatchStore.WatchDetail(
                    true, "RENEWAL_FAILED", lastNotificationAt, lastRenewedAt, "SUBSCRIPTION_TIMEOUT", null));
            assertThat(store.findDetail("missing-" + UUID.randomUUID())).isEmpty();
        } finally {
            deleteFixture(creatorId, channelId);
        }
    }

    @Test
    @DisplayName("활성 Watch의 알림 수신 시각을 갱신한다")
    void markNotificationReceived_활성행_수신시각을갱신한다() {
        // given
        UUID activeCreatorId = UUID.randomUUID();
        String activeChannelId = "channel-" + UUID.randomUUID();
        OffsetDateTime previousAt = OffsetDateTime.parse("2026-08-11T03:04:05Z");
        OffsetDateTime receivedAt = OffsetDateTime.parse("2026-08-12T03:04:05Z");
        insertCreator(activeCreatorId, activeChannelId);
        insertWatch(activeCreatorId, activeChannelId, null, previousAt, null, null);

        try {
            // when
            store.markNotificationReceived(activeChannelId, receivedAt);

            // then
            assertThat(readLastNotificationAt(activeChannelId)).isEqualTo(receivedAt);
        } finally {
            deleteFixture(activeCreatorId, activeChannelId);
        }
    }

    @Test
    @DisplayName("Webhook의 Watch 행 잠금이 끝나기 전 감시 비활성화는 대기한다")
    void findForUpdate_동시비활성화_기존트랜잭션종료까지대기한다() throws Exception {
        UUID creatorId = UUID.randomUUID();
        String channelId = "channel-" + UUID.randomUUID();
        insertCreator(creatorId, channelId);
        insertWatch(creatorId, channelId, null, null, null, null);
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var webhook = executor.submit(() -> transaction.execute(status -> {
                assertThat(store.findForUpdate(channelId)).isPresent();
                lockAcquired.countDown();
                await(releaseLock);
                store.markNotificationReceived(channelId, OffsetDateTime.parse("2026-08-12T03:04:05Z"));
                return null;
            }));
            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

            var disable = executor.submit(() -> transaction.execute(status -> {
                jdbcTemplate.update("""
                        UPDATE youtube_channel_watch
                           SET enabled = false, subscription_status = 'INACTIVE'
                         WHERE youtube_channel_id = ?
                        """, channelId);
                return null;
            }));
            assertThatThrownBy(() -> disable.get(100, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseLock.countDown();
            webhook.get(5, TimeUnit.SECONDS);
            disable.get(5, TimeUnit.SECONDS);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT enabled FROM youtube_channel_watch WHERE youtube_channel_id = ?",
                    Boolean.class, channelId)).isFalse();
        } finally {
            deleteFixture(creatorId, channelId);
        }
    }

    @Test
    @DisplayName("ACTIVE Watch瑜 중복 활성화해도 기존 상태와 검증 Token 해시를 유지한다")
    void prepareActivation_기존ACTIVE중복활성화_기존상태와토큰해시를유지한다() {
        UUID creatorId = UUID.randomUUID();
        String channelId = "channel-" + UUID.randomUUID();
        byte[] existingHash = IntegrationTestFixtures.sha256("existing-token");
        byte[] newHash = IntegrationTestFixtures.sha256("new-token");
        insertCreator(creatorId, channelId);
        insertWatch(creatorId, channelId, existingHash, null, OffsetDateTime.parse("2026-08-11T02:03:04Z"), null);

        try {
            YoutubeChannelWatchPersistenceService.ActivationPreparation preparation =
                    watchPersistence.prepareActivation(creatorId, channelId, newHash);

            assertThat(preparation.subscriptionRequestRequired()).isFalse();
            assertThat(preparation.detail().subscriptionStatus()).isEqualTo("ACTIVE");
            assertThat(readTokenHash(channelId)).containsExactly(existingHash);
        } finally {
            deleteFixture(creatorId, channelId);
        }
    }

    @Test
    @DisplayName("구독 실패는 Watch를 RENEWAL_FAILED와 오류 범주로 기록한다")
    void markSubscriptionFailed_기존Watch_실패상태와오류범주를기록한다() {
        UUID creatorId = UUID.randomUUID();
        String channelId = "channel-" + UUID.randomUUID();
        insertCreator(creatorId, channelId);
        insertWatch(creatorId, channelId, IntegrationTestFixtures.sha256("old-token"), null, null, null);
        jdbcTemplate.update("UPDATE youtube_channel_watch SET subscription_status = 'UNKNOWN' WHERE youtube_channel_id = ?",
                channelId);

        try {
            YoutubeChannelWatchStore.WatchDetail detail = store.markSubscriptionFailed(
                    channelId, "SUBSCRIPTION_5XX", IntegrationTestFixtures.sha256("old-token")).orElseThrow();

            assertThat(detail.enabled()).isTrue();
            assertThat(detail.subscriptionStatus()).isEqualTo("RENEWAL_FAILED");
            assertThat(detail.lastErrorCategory()).isEqualTo("SUBSCRIPTION_5XX");
            assertThat(detail.lastErrorAt()).isNotNull();
        } finally {
            deleteFixture(creatorId, channelId);
        }
    }

    @Test
    @DisplayName("명시적 구독 실패는 신규 pending Watch를 삭제한다")
    void compensateExplicitFailure_신규pendingWatch_삭제한다() {
        UUID creatorId = UUID.randomUUID();
        String channelId = "channel-" + UUID.randomUUID();
        byte[] pendingHash = IntegrationTestFixtures.sha256("pending-token");
        insertCreator(creatorId, channelId);

        try {
            YoutubeChannelWatchPersistenceService.ActivationPreparation preparation =
                    watchPersistence.prepareActivation(creatorId, channelId, pendingHash);

            watchPersistence.compensateExplicitFailure(creatorId, channelId, preparation);

            assertThat(store.find(channelId)).isEmpty();
        } finally {
            deleteFixture(creatorId, channelId);
        }
    }

    @Test
    @DisplayName("명시적 구독 실패는 기존 pending Watch 상태와 토큰 해시를 복원한다")
    void compensateExplicitFailure_기존pendingWatch_상태와토큰해시를복원한다() {
        UUID creatorId = UUID.randomUUID();
        String channelId = "channel-" + UUID.randomUUID();
        byte[] previousHash = IntegrationTestFixtures.sha256("previous-token");
        byte[] pendingHash = IntegrationTestFixtures.sha256("pending-token");
        insertCreator(creatorId, channelId);
        insertWatch(creatorId, channelId, previousHash, null, null, "OLD_ERROR");
        jdbcTemplate.update("UPDATE youtube_channel_watch SET subscription_status = 'UNKNOWN' WHERE youtube_channel_id = ?",
                channelId);

        try {
            YoutubeChannelWatchPersistenceService.ActivationPreparation preparation =
                    watchPersistence.prepareActivation(creatorId, channelId, pendingHash);

            watchPersistence.compensateExplicitFailure(creatorId, channelId, preparation);

            YoutubeChannelWatchStore.Watch restored = store.find(channelId).orElseThrow();
            assertThat(restored.subscriptionStatus()).isEqualTo("UNKNOWN");
            assertThat(restored.subscriptionTokenHash()).containsExactly(previousHash);
        } finally {
            deleteFixture(creatorId, channelId);
        }
    }

    @Test
    @DisplayName("비활성화가 선행되면 timeout 보상이 Watch를 다시 활성화하지 않는다")
    void markSubscriptionFailed_비활성화선행_pending보상은상태를보존한다() {
        UUID creatorId = UUID.randomUUID();
        String channelId = "channel-" + UUID.randomUUID();
        byte[] pendingHash = IntegrationTestFixtures.sha256("pending-token");
        insertCreator(creatorId, channelId);

        try {
            watchPersistence.prepareActivation(creatorId, channelId, pendingHash);
            store.upsert(creatorId, channelId, false, "INACTIVE", null);

            assertThat(store.markSubscriptionFailed(channelId, "SUBSCRIPTION_TIMEOUT", pendingHash)).isEmpty();
            YoutubeChannelWatchStore.Watch watch = store.find(channelId).orElseThrow();
            assertThat(watch.enabled()).isFalse();
            assertThat(watch.subscriptionStatus()).isEqualTo("INACTIVE");
        } finally {
            deleteFixture(creatorId, channelId);
        }
    }

    @Test
    @DisplayName("비활성화가 선행되면 명시적 실패 보상이 상태를 복원하지 않는다")
    void compensateExplicitFailure_비활성화선행_pending보상은상태를보존한다() {
        UUID creatorId = UUID.randomUUID();
        String channelId = "channel-" + UUID.randomUUID();
        byte[] previousHash = IntegrationTestFixtures.sha256("previous-token");
        byte[] pendingHash = IntegrationTestFixtures.sha256("pending-token");
        insertCreator(creatorId, channelId);
        insertWatch(creatorId, channelId, previousHash, null, null, null);
        jdbcTemplate.update("UPDATE youtube_channel_watch SET subscription_status = 'UNKNOWN' WHERE youtube_channel_id = ?",
                channelId);

        try {
            YoutubeChannelWatchPersistenceService.ActivationPreparation preparation =
                    watchPersistence.prepareActivation(creatorId, channelId, pendingHash);
            store.upsert(creatorId, channelId, false, "INACTIVE", null);

            watchPersistence.compensateExplicitFailure(creatorId, channelId, preparation);

            YoutubeChannelWatchStore.Watch watch = store.find(channelId).orElseThrow();
            assertThat(watch.enabled()).isFalse();
            assertThat(watch.subscriptionStatus()).isEqualTo("INACTIVE");
        } finally {
            deleteFixture(creatorId, channelId);
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for Watch row lock");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Watch row lock", exception);
        }
    }

    private void insertCreator(UUID creatorId, String channelId) {
        jdbcTemplate.update("""
                INSERT INTO creator (
                    id, external_channel_id, channel_name, channel_url,
                    publication_status, lifecycle_status, external_availability_status,
                    external_status_checked_at
                ) VALUES (?, ?, ?, ?, 'PUBLIC', 'ACTIVE', 'AVAILABLE', ?)
                """, creatorId, channelId, "fixture-" + creatorId,
                "https://example.com/channel/" + creatorId, OffsetDateTime.parse("2026-08-11T00:00:00Z"));
    }

    private void insertWatch(UUID creatorId, String channelId, byte[] tokenHash,
                             OffsetDateTime lastNotificationAt, OffsetDateTime lastRenewedAt,
                             String lastErrorCategory) {
        jdbcTemplate.update("""
                INSERT INTO youtube_channel_watch (
                    id, creator_id, youtube_channel_id, enabled, subscription_status,
                    subscription_token_hash, last_notification_at, last_renewed_at, last_error_category
                ) VALUES (?, ?, ?, true, 'ACTIVE', ?, ?, ?, ?)
                """, UUID.randomUUID(), creatorId, channelId, tokenHash,
                lastNotificationAt, lastRenewedAt, lastErrorCategory);
    }

    private byte[] readTokenHash(String channelId) {
        return jdbcTemplate.queryForObject(
                "SELECT subscription_token_hash FROM youtube_channel_watch WHERE youtube_channel_id = ?",
                byte[].class, channelId);
    }

    private OffsetDateTime readLastNotificationAt(String channelId) {
        return jdbcTemplate.queryForObject(
                "SELECT last_notification_at FROM youtube_channel_watch WHERE youtube_channel_id = ?",
                (resultSet, rowNum) -> resultSet.getObject(1, OffsetDateTime.class), channelId);
    }

    private void deleteFixture(UUID creatorId, String channelId) {
        jdbcTemplate.update("DELETE FROM youtube_channel_watch WHERE creator_id = ? OR youtube_channel_id = ?",
                creatorId, channelId);
        jdbcTemplate.update("DELETE FROM creator WHERE id = ?", creatorId);
    }
}

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

package com.masiton.ai.application.port.out;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface YoutubeChannelWatchStore {
    Optional<Watch> find(String channelId);

    Optional<WatchDetail> findDetail(String channelId);

    WatchCandidatePage findCandidatePage(int limit, long offset);

    Optional<Watch> findForUpdate(String channelId);

    WatchDetail upsert(UUID creatorId, String channelId, boolean enabled, String subscriptionStatus,
                       byte[] subscriptionTokenHash);

    void markNotificationReceived(String channelId, OffsetDateTime receivedAt);

    void markSubscriptionVerified(String channelId, OffsetDateTime verifiedAt);

    Optional<WatchDetail> markSubscriptionFailed(String channelId, String errorCategory,
                                                  byte[] expectedTokenHash);

    void deletePending(String channelId, byte[] expectedTokenHash);

    Optional<WatchDetail> restoreActivation(UUID creatorId, String channelId, Watch previous,
                                            byte[] expectedTokenHash);

    record Watch(String channelId, boolean enabled, String subscriptionStatus, byte[] subscriptionTokenHash) {
        public boolean acceptsNotifications() {
            return enabled && "ACTIVE".equals(subscriptionStatus);
        }
    }

    record WatchDetail(boolean enabled, String subscriptionStatus, OffsetDateTime lastNotificationAt,
                       OffsetDateTime lastRenewedAt, String lastErrorCategory, OffsetDateTime lastErrorAt) {
        public WatchDetail(boolean enabled, String subscriptionStatus, OffsetDateTime lastNotificationAt,
                           OffsetDateTime lastRenewedAt, String lastErrorCategory) {
            this(enabled, subscriptionStatus, lastNotificationAt, lastRenewedAt, lastErrorCategory, null);
        }
    }

    record WatchCandidatePage(List<WatchCandidate> items, long totalElements) { }

    record WatchCandidate(UUID creatorId, String channelName, boolean publiclyVisible,
                          boolean externallyAvailable, String externalChannelId,
                          Optional<WatchDetail> watch) { }
}

package com.masiton.ai.application.port.out;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.Map;

public interface YoutubeChannelWatchStore {
    Optional<Watch> find(String channelId);

    Optional<WatchDetail> findDetail(String channelId);

    Map<String, WatchDetail> findDetailsByChannelIds(List<String> channelIds);

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
}

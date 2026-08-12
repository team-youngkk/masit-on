package com.masiton.ai.application.port.out;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface YoutubeChannelWatchStore {
    Optional<Watch> find(String channelId);

    Optional<Watch> findForUpdate(String channelId);

    WatchDetail upsert(UUID creatorId, String channelId, boolean enabled, String subscriptionStatus);

    void markNotificationReceived(String channelId, OffsetDateTime receivedAt);

    record Watch(String channelId, boolean enabled, String subscriptionStatus, byte[] subscriptionTokenHash) {
        public boolean acceptsNotifications() {
            return enabled && "ACTIVE".equals(subscriptionStatus);
        }
    }

    record WatchDetail(boolean enabled, String subscriptionStatus, OffsetDateTime lastNotificationAt,
                       OffsetDateTime lastRenewedAt, String lastErrorCategory) { }
}

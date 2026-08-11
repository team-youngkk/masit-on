package com.masiton.ai.application.port.out;

import java.util.Optional;

public interface YoutubeChannelWatchStore {
    Optional<Watch> find(String channelId);

    record Watch(String channelId, boolean enabled, String subscriptionStatus, byte[] subscriptionTokenHash) {
        public boolean acceptsNotifications() {
            return enabled && "ACTIVE".equals(subscriptionStatus);
        }
    }
}

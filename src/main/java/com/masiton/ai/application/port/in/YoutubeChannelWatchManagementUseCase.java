package com.masiton.ai.application.port.in;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;

public interface YoutubeChannelWatchManagementUseCase {

    WatchStatus setEnabled(UUID creatorId, boolean enabled);

    WatchStatus getStatus(UUID creatorId);

    WatchPage getStatuses(int page, int size);

    record WatchPage(List<WatchSummary> items, int number, int size, long totalElements,
                     long totalPages, boolean hasNext) { }

    record WatchSummary(UUID creatorId, String channelName, boolean publiclyVisible,
                        boolean externallyAvailable, WatchStatus status) { }

    record WatchStatus(boolean enabled, String subscriptionStatus, OffsetDateTime lastNotificationAt,
                       OffsetDateTime lastRenewedAt, String lastErrorCategory, OffsetDateTime lastErrorAt) {
        public WatchStatus(boolean enabled, String subscriptionStatus, OffsetDateTime lastNotificationAt,
                           OffsetDateTime lastRenewedAt, String lastErrorCategory) {
            this(enabled, subscriptionStatus, lastNotificationAt, lastRenewedAt, lastErrorCategory, null);
        }
    }
}

package com.masiton.ai.application.port.in;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface YoutubeChannelWatchManagementUseCase {

    WatchStatus setEnabled(UUID creatorId, boolean enabled);

    WatchStatus getStatus(UUID creatorId);

    record WatchStatus(boolean enabled, String subscriptionStatus, OffsetDateTime lastNotificationAt,
                       OffsetDateTime lastRenewedAt, String lastErrorCategory) { }
}

package com.masiton.notification.application.port.in;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.masiton.notification.domain.model.NotificationRequestType;
import com.masiton.notification.domain.model.NotificationStatus;

public record NotificationItem(
        UUID notificationId,
        NotificationRequestType requestType,
        UUID requestId,
        NotificationStatus status,
        String title,
        String message,
        boolean read,
        OffsetDateTime readAt,
        OffsetDateTime createdAt
) {
}

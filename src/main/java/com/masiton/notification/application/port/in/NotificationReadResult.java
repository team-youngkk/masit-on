package com.masiton.notification.application.port.in;

import java.time.OffsetDateTime;
import java.util.UUID;

/** read는 항상 true다. readAt은 이번 요청 또는 이전 요청이 확정한 최초 읽음 시각이다. */
public record NotificationReadResult(UUID notificationId, boolean read, OffsetDateTime readAt) {
}

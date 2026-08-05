package com.masiton.notification.application.port.in;

import java.time.OffsetDateTime;

/** readAt은 updatedCount가 0이어도 이번 요청이 고정한 기준 시각을 항상 채운다. */
public record NotificationReadAllResult(int updatedCount, int unreadCount, OffsetDateTime readAt) {
}

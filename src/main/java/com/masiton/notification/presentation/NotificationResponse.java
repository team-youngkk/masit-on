package com.masiton.notification.presentation;

import java.time.OffsetDateTime;
import java.util.List;

import com.masiton.notification.application.port.in.NotificationItem;
import com.masiton.notification.application.port.in.NotificationPage;
import com.masiton.notification.application.port.in.NotificationReadAllResult;
import com.masiton.notification.application.port.in.NotificationReadResult;

final class NotificationResponse {

    private NotificationResponse() {
    }

    record NotificationList(List<Item> items, int unreadCount, Page page) {

        static NotificationList from(NotificationPage result) {
            return new NotificationList(
                    result.items().stream().map(Item::from).toList(),
                    result.unreadCount(),
                    new Page(result.number(), result.size(), result.totalElements(),
                            result.totalPages(), result.hasNext()));
        }
    }

    record Item(
            String notificationId,
            String type,
            String requestType,
            String requestId,
            String status,
            String title,
            String message,
            boolean read,
            OffsetDateTime readAt,
            OffsetDateTime createdAt
    ) {

        static Item from(NotificationItem item) {
            return new Item(
                    item.notificationId().toString(),
                    item.requestType().notificationType(),
                    item.requestType().name(),
                    item.requestId().toString(),
                    item.status().name(),
                    item.title(),
                    item.message(),
                    item.read(),
                    item.readAt(),
                    item.createdAt());
        }
    }

    record Page(int number, int size, long totalElements, int totalPages, boolean hasNext) {
    }

    record UnreadCount(int unreadCount) {
    }

    record ReadState(String notificationId, boolean read, OffsetDateTime readAt) {

        static ReadState from(NotificationReadResult result) {
            return new ReadState(result.notificationId().toString(), result.read(), result.readAt());
        }
    }

    record ReadAllState(int updatedCount, int unreadCount, OffsetDateTime readAt) {

        static ReadAllState from(NotificationReadAllResult result) {
            return new ReadAllState(result.updatedCount(), result.unreadCount(), result.readAt());
        }
    }
}

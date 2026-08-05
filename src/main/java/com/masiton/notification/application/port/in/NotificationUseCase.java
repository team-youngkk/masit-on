package com.masiton.notification.application.port.in;

import java.util.UUID;

public interface NotificationUseCase {

    NotificationPage getNotifications(UUID memberId, int page, int size);

    int getUnreadCount(UUID memberId);

    NotificationReadResult markAsRead(UUID memberId, UUID notificationId);

    NotificationReadAllResult markAllAsRead(UUID memberId);
}

package com.masiton.notification.application.port.in;

import java.util.List;

/**
 * unreadCount는 items·totalElements와 같은 SELECT에서 계산해 항상 같은 스냅숏의 값이어야 한다
 * (Controller가 별도 호출로 다시 계산하지 않는다).
 */
public record NotificationPage(
        List<NotificationItem> items,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        int unreadCount
) {
}

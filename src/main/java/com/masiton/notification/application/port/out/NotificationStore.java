package com.masiton.notification.application.port.out;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import com.masiton.notification.domain.model.NotificationCreationResult;
import com.masiton.notification.domain.model.NotificationRequestType;
import com.masiton.notification.domain.model.NotificationStatus;

/**
 * retentionCutoff·retentionLimit은 호출자(Application)가 Clock에서 파생해 전달한다
 * (ADR-DATA-012 8절). 보존 범위 밖 알림은 존재하지 않는 알림과 같은 결과로 수렴해야 한다.
 */
public interface NotificationStore {

    NotificationCreationResult insertIfAbsent(
            UUID id,
            UUID memberId,
            NotificationRequestType requestType,
            UUID requestId,
            NotificationStatus status,
            String title,
            String message,
            OffsetDateTime createdAt);

    /** 존재하지 않거나 다른 회원 소유이거나 보존 범위 밖이면 빈 값을 반환한다. */
    Optional<OffsetDateTime> markAsRead(
            UUID memberId,
            UUID notificationId,
            OffsetDateTime readAt,
            OffsetDateTime retentionCutoff,
            int retentionLimit);

    int markAllAsRead(
            UUID memberId, OffsetDateTime requestTime, OffsetDateTime retentionCutoff, int retentionLimit);
}

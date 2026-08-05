package com.masiton.notification.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.common.web.BusinessException;
import com.masiton.notification.application.port.in.CreateNotificationUseCase;
import com.masiton.notification.application.port.in.NotificationPage;
import com.masiton.notification.application.port.in.NotificationReadAllResult;
import com.masiton.notification.application.port.in.NotificationReadResult;
import com.masiton.notification.application.port.in.NotificationUseCase;
import com.masiton.notification.application.port.out.NotificationQueryPort;
import com.masiton.notification.application.port.out.NotificationStore;
import com.masiton.notification.domain.model.NotificationCreationResult;
import com.masiton.notification.domain.model.NotificationMessage;
import com.masiton.notification.domain.model.NotificationRequestType;
import com.masiton.notification.domain.model.NotificationStatus;
import com.masiton.notification.domain.service.NotificationMessageCatalog;

@Service
public class NotificationService implements NotificationUseCase, CreateNotificationUseCase {

    /** BR-NOTIFICATION-003·데이터 계약 7절: 90일 이내 또는 회원별 최신 200개 중 더 넓은 범위를 보존한다. */
    private static final int RETENTION_DAYS = 90;
    private static final int RETENTION_LIMIT = 200;

    private final NotificationStore store;
    private final NotificationQueryPort queries;
    private final Clock clock;

    public NotificationService(
            NotificationStore store,
            NotificationQueryPort queries,
            @Qualifier("notificationClock") Clock clock
    ) {
        this.store = store;
        this.queries = queries;
        this.clock = clock;
    }

    /**
     * 호출자(WS-12 상태 전이 Application Service)의 트랜잭션에 반드시 참여해야 하므로
     * 전파 옵션을 MANDATORY로 강제한다(ADR-NOTIFY-002 5절). memberId가 없으면 생성을 거부한다.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public NotificationCreationResult create(
            UUID memberId,
            NotificationRequestType requestType,
            UUID requestId,
            NotificationStatus status
    ) {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId 없이는 알림을 생성할 수 없습니다.");
        }
        Objects.requireNonNull(requestType, "requestType은 필수입니다.");
        Objects.requireNonNull(requestId, "requestId는 필수입니다.");
        Objects.requireNonNull(status, "status는 필수입니다.");
        NotificationMessage message = NotificationMessageCatalog.messageFor(requestType, status);
        return store.insertIfAbsent(
                UUID.randomUUID(), memberId, requestType, requestId, status,
                message.title(), message.message(), now());
    }

    /**
     * items·totalElements·unreadCount를 한 번의 Query Port 호출(같은 SELECT 스냅숏)로 완성한다.
     * Controller가 별도로 getUnreadCount를 다시 호출하면 서로 다른 트랜잭션의 값이 섞이므로
     * 이 메서드 하나로 계약 응답을 끝맺는다.
     */
    @Override
    @Transactional(readOnly = true)
    public NotificationPage getNotifications(UUID memberId, int page, int size) {
        OffsetDateTime now = now();
        return queries.findByMember(memberId, now.minusDays(RETENTION_DAYS), RETENTION_LIMIT, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public int getUnreadCount(UUID memberId) {
        OffsetDateTime now = now();
        return queries.countUnread(memberId, now.minusDays(RETENTION_DAYS), RETENTION_LIMIT);
    }

    @Override
    @Transactional
    public NotificationReadResult markAsRead(UUID memberId, UUID notificationId) {
        OffsetDateTime now = now();
        return store.markAsRead(memberId, notificationId, now, now.minusDays(RETENTION_DAYS), RETENTION_LIMIT)
                .map(readAt -> new NotificationReadResult(notificationId, true, readAt))
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "요청한 알림을 찾을 수 없습니다."));
    }

    @Override
    @Transactional
    public NotificationReadAllResult markAllAsRead(UUID memberId) {
        OffsetDateTime requestTime = now();
        OffsetDateTime retentionCutoff = requestTime.minusDays(RETENTION_DAYS);
        int updatedCount = store.markAllAsRead(memberId, requestTime, retentionCutoff, RETENTION_LIMIT);
        int unreadCount = queries.countUnread(memberId, retentionCutoff, RETENTION_LIMIT);
        return new NotificationReadAllResult(updatedCount, unreadCount, requestTime);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}

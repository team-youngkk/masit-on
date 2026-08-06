package com.masiton.notification.application.port.out;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.masiton.notification.application.port.in.NotificationPage;

/**
 * retentionCutoff·retentionLimit은 호출자(Application)가 Clock에서 파생해 전달한다
 * (ADR-DATA-012 8절). 이 Port는 필터링만 하며 cleanup(삭제·갱신)을 수행하지 않는다.
 */
public interface NotificationQueryPort {

    NotificationPage findByMember(UUID memberId, OffsetDateTime retentionCutoff, int retentionLimit, int page, int size);

    int countUnread(UUID memberId, OffsetDateTime retentionCutoff, int retentionLimit);
}

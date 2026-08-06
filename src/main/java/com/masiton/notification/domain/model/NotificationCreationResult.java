package com.masiton.notification.domain.model;

import java.util.UUID;

/** created가 false이면 같은 (요청, 상태) 알림이 이미 있어 새 행 대신 기존 행으로 수렴했다는 뜻이다. */
public record NotificationCreationResult(UUID notificationId, boolean created) {
}

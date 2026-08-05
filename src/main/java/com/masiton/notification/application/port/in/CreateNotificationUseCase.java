package com.masiton.notification.application.port.in;

import java.util.UUID;

import com.masiton.notification.domain.model.NotificationCreationResult;
import com.masiton.notification.domain.model.NotificationRequestType;
import com.masiton.notification.domain.model.NotificationStatus;

/**
 * 생성 규칙과 저장소는 WS-13이 소유하고, 상태 전이 트랜잭션 orchestration은 WS-12가 소유한다
 * (ADR-NOTIFY-002 9절). 호출자는 자신의 트랜잭션 안에서 이 메서드를 호출해야 하며(전파 MANDATORY),
 * 탈퇴로 요청의 member_id 연결이 이미 제거된 요청에는 이 UseCase를 호출하지 않아야 한다.
 */
public interface CreateNotificationUseCase {

    NotificationCreationResult create(
            UUID memberId,
            NotificationRequestType requestType,
            UUID requestId,
            NotificationStatus status);
}

package com.masiton.notification.domain.service;

import java.util.EnumMap;
import java.util.Map;

import com.masiton.notification.domain.model.NotificationMessage;
import com.masiton.notification.domain.model.NotificationRequestType;
import com.masiton.notification.domain.model.NotificationStatus;

/**
 * 회원 입력 원문, 관리자 내부 메모와 신고자 식별자를 알림 Snapshot에 복제하지 않기 위해
 * (requestType, status) 조합별 고정 표시문만 서버가 구성한다(ADR-NOTIFY-002 8절).
 */
public final class NotificationMessageCatalog {

    private static final Map<NotificationRequestType, Map<NotificationStatus, NotificationMessage>> MESSAGES =
            buildCatalog();

    private NotificationMessageCatalog() {
    }

    public static NotificationMessage messageFor(NotificationRequestType requestType, NotificationStatus status) {
        NotificationMessage message = MESSAGES.get(requestType).get(status);
        if (message == null) {
            throw new IllegalArgumentException("정의되지 않은 알림 조합입니다: " + requestType + ", " + status);
        }
        return message;
    }

    private static Map<NotificationRequestType, Map<NotificationStatus, NotificationMessage>> buildCatalog() {
        Map<NotificationRequestType, Map<NotificationStatus, NotificationMessage>> catalog =
                new EnumMap<>(NotificationRequestType.class);

        Map<NotificationStatus, NotificationMessage> submission = new EnumMap<>(NotificationStatus.class);
        submission.put(NotificationStatus.IN_REVIEW,
                new NotificationMessage("제보 검토가 시작되었습니다.", "접수한 제보를 검토하고 있습니다."));
        submission.put(NotificationStatus.ACCEPTED,
                new NotificationMessage("제보가 승인되었습니다.", "접수한 제보가 승인되어 반영을 진행합니다."));
        submission.put(NotificationStatus.REJECTED,
                new NotificationMessage("제보가 반려되었습니다.", "접수한 제보가 반려되었습니다. 사유는 상세에서 확인하세요."));
        submission.put(NotificationStatus.COMPLETED,
                new NotificationMessage("제보 처리가 완료되었습니다.", "접수한 제보에 대한 조치가 완료되었습니다."));
        catalog.put(NotificationRequestType.SUBMISSION, Map.copyOf(submission));

        Map<NotificationStatus, NotificationMessage> report = new EnumMap<>(NotificationStatus.class);
        report.put(NotificationStatus.IN_REVIEW,
                new NotificationMessage("신고 검토가 시작되었습니다.", "접수한 신고를 검토하고 있습니다."));
        report.put(NotificationStatus.ACCEPTED,
                new NotificationMessage("신고가 승인되었습니다.", "접수한 신고가 승인되어 조치를 진행합니다."));
        report.put(NotificationStatus.REJECTED,
                new NotificationMessage("신고가 반려되었습니다.", "접수한 신고가 반려되었습니다. 사유는 상세에서 확인하세요."));
        report.put(NotificationStatus.COMPLETED,
                new NotificationMessage("신고 처리가 완료되었습니다.", "접수한 신고에 대한 조치가 완료되었습니다."));
        catalog.put(NotificationRequestType.REPORT, Map.copyOf(report));

        return Map.copyOf(catalog);
    }
}

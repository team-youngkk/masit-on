package com.masiton.notification.domain.model;

/** notification 테이블은 submission_id/report_id 중 정확히 하나만 값을 가지므로 이 값으로 요청 종류를 판별한다. */
public enum NotificationRequestType {

    SUBMISSION,
    REPORT;

    public String notificationType() {
        return switch (this) {
            case SUBMISSION -> "SUBMISSION_STATUS_CHANGED";
            case REPORT -> "REPORT_STATUS_CHANGED";
        };
    }
}

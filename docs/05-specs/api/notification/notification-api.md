---
id: API-NOTIFICATION-001
title: 사용자 알림 API
status: approved
related_prd:
  - PRD-NOTIFICATION-001
workstream: WS-13
owner: 이우람
reviewers:
  - 김인안
related_requirements:
  - FR-NOTIFICATION-001
  - FR-NOTIFICATION-002
  - FR-NOTIFICATION-003
  - FR-NOTIFICATION-004
related_business_rules:
  - BR-NOTIFICATION-001
  - BR-NOTIFICATION-002
  - BR-NOTIFICATION-003
  - BR-NOTIFICATION-004
related_nfr:
  - NFR-INTEGRITY-005
  - NFR-RELIABILITY-004
  - NFR-PRIVACY-005
  - NFR-TEST-005
related_documents:
  - ../../../04-product/prd/notification/user-notification.md
  - ../common/second-expansion-contract.md
  - ../participation/submission-report-api.md
  - ../../data/second-expansion-data-contract.md
  - ../../../07-adr/integration/notify-002-in-app-notification-reliability.md
  - ../../../07-adr/data/data-012-second-expansion-retention-cleanup.md
---

# 사용자 알림 API

## 1. API 목록

모든 경로는 현재 회원 본인 전용이다.

| API ID | Method | Path | 설명 |
|---|---|---|---|
| API-NOTIFICATION-001 | GET | `/api/me/notifications` | 알림 목록과 미읽음 수 |
| API-NOTIFICATION-002 | GET | `/api/me/notifications/unread-count` | 정확한 미읽음 수 |
| API-NOTIFICATION-003 | PUT | `/api/me/notifications/{notificationId}/read` | 개별 읽음 |
| API-NOTIFICATION-004 | PUT | `/api/me/notifications/read-all` | 전체 읽음 |

알림 설정 변경·동의·해지, 삭제, 안 읽음 복원, 이메일·푸시·실시간 전송 API는 제공하지 않는다.

## 2. 목록과 미읽음 수

`GET /api/me/notifications?page=1&size=20`는 생성 시각 내림차순, 알림 ID 오름차순으로 반환한다.

```json
{
  "items": [{
    "notificationId": "01K4NOTIFICATION000000001",
    "type": "REPORT_STATUS_CHANGED",
    "requestType": "REPORT",
    "requestId": "01K4REPORT000000000000001",
    "status": "IN_REVIEW",
    "title": "신고 검토가 시작되었습니다.",
    "message": "접수한 신고를 검토하고 있습니다.",
    "read": false,
    "readAt": null,
    "createdAt": "2026-08-03T10:00:00+09:00"
  }],
  "unreadCount": 101,
  "page": { "number": 1, "size": 20, "totalElements": 101, "totalPages": 6, "hasNext": true }
}
```

별도 배지 조회는 `200 OK`와 `{ "unreadCount": 101 }`을 반환한다. 서버는 `101`을 그대로 보내고 클라이언트가 `99+`로 표시한다.

## 3. 읽음 처리

개별 읽음은 본문 없는 `PUT`이며 현재 상태를 반환한다.

```json
{ "notificationId": "01K4NOTIFICATION000000001", "read": true, "readAt": "2026-08-03T10:05:00+09:00" }
```

이미 읽은 알림은 최초 `readAt`을 유지한다. 타 회원 또는 없는 알림은 `404 NOTIFICATION_NOT_FOUND`다. 형식이 맞지 않는 `notificationId`도 같은 `404`로 통일하며 `400`으로 구분하지 않는다(식별자 계약 4절 회원 본인 전용 자원 규칙).

전체 읽음은 본문 없는 `PUT`이며 요청 시점에 보존 중인 현재 회원의 미읽음 전체를 한 번에 처리한다.

```json
{ "updatedCount": 17, "unreadCount": 0, "readAt": "2026-08-03T10:05:00+09:00" }
```

반복 요청은 `updatedCount: 0`인 `200 OK`다. 동시에 새 알림이 생성되면 전체 읽음 트랜잭션이 선택한 시점 이후 알림은 읽음으로 바꾸지 않으며 응답 `unreadCount`에 반영한다.

## 4. 생성·보존 경계

- 외부 생성 API는 없다. 제보·신고가 `IN_REVIEW`, `ACCEPTED`, `REJECTED`, `COMPLETED`로 전이하는 트랜잭션에서 요청 회원 알림을 생성한다.
- `(requestType, requestId, status)`는 고유하며 상태 전이 재시도에서 중복 생성하지 않는다.
- 알림은 생성 후 90일 이내이거나 회원별 최근 200개이면 보관하고 두 조건을 모두 벗어나면 삭제한다. 보존 작업 실패는 목록 조회를 실패시키지 않고 운영 재시도 대상으로 남긴다.
- 회원 탈퇴 시 알림을 삭제한다.

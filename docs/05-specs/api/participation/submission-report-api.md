---
id: API-PARTICIPATION-001
title: 사용자 제보·신고 API
status: approved
related_prd:
  - PRD-PARTICIPATION-001
workstream: WS-12
owner: 김인안
reviewers:
  - 이우람
related_requirements:
  - FR-SUBMISSION-001
  - FR-SUBMISSION-002
  - FR-SUBMISSION-003
  - FR-REPORT-001
  - FR-REPORT-002
  - FR-REPORT-003
related_business_rules:
  - BR-SUBMISSION-001
  - BR-SUBMISSION-002
  - BR-SUBMISSION-003
  - BR-SUBMISSION-004
  - BR-REPORT-001
  - BR-REPORT-002
  - BR-REPORT-003
  - BR-REPORT-004
related_nfr:
  - NFR-SECURITY-006
  - NFR-INTEGRITY-005
  - NFR-OBSERVABILITY-004
  - NFR-PRIVACY-005
  - NFR-TEST-005
related_documents:
  - ../../../04-product/prd/participation/user-submission-report.md
  - ../common/second-expansion-contract.md
  - ../notification/notification-api.md
  - ../../data/second-expansion-data-contract.md
  - ../../../07-adr/integration/notify-002-in-app-notification-reliability.md
  - ../../../07-adr/data/data-012-second-expansion-retention-cleanup.md
---

# 사용자 제보·신고 API

## 1. API 목록

제보와 신고는 별도 자원·경로를 사용한다.

| API ID | Method | Path | 접근 | 설명 |
|---|---|---|---|---|
| API-SUBMISSION-001 | POST | `/api/me/submissions` | 회원 | 제보 접수 |
| API-SUBMISSION-002 | GET | `/api/me/submissions` | 회원 | 내 제보 목록 |
| API-SUBMISSION-003 | GET | `/api/me/submissions/{submissionId}` | 회원 | 내 제보 상세 |
| API-REPORT-001 | POST | `/api/me/reports` | 회원 | 신고 접수 |
| API-REPORT-002 | GET | `/api/me/reports` | 회원 | 내 신고 목록 |
| API-REPORT-003 | GET | `/api/me/reports/{reportId}` | 회원 | 내 신고 상세 |
| API-ADMIN-SUBMISSION-001 | GET | `/api/admin/submissions` | 관리자 | 제보 검토 목록 |
| API-ADMIN-SUBMISSION-002 | GET | `/api/admin/submissions/{submissionId}` | 관리자 | 제보 검토 상세 |
| API-ADMIN-SUBMISSION-003 | PUT | `/api/admin/submissions/{submissionId}/status` | 관리자 | 제보 상태 설정 |
| API-ADMIN-REPORT-001 | GET | `/api/admin/reports` | 관리자 | 신고 검토 목록 |
| API-ADMIN-REPORT-002 | GET | `/api/admin/reports/{reportId}` | 관리자 | 신고 검토 상세 |
| API-ADMIN-REPORT-003 | PUT | `/api/admin/reports/{reportId}/status` | 관리자 | 신고 상태 설정 |

## 2. 회원 접수

두 `POST` 모두 `Idempotency-Key`가 필요하고 설명은 공백 제거 후 10~2000자, `evidenceUrl`은 선택적 HTTPS URL이며 최대 2048자다. HTML은 텍스트로 취급하고 스크립트·제어 문자를 안전하게 거부하거나 정규화한다.

### 2.1 제보

```json
{
  "targetType": "RESTAURANT",
  "candidate": { "name": "새 맛집", "roadAddress": "서울특별시 ..." },
  "description": "신규 등록을 제안합니다.",
  "evidenceUrl": "https://example.com/evidence"
}
```

`targetType`별 `candidate` 필수값은 다음과 같다.

| targetType | candidate |
|---|---|
| `RESTAURANT` | `name`, `roadAddress` |
| `CREATOR` | `channelUrl` |
| `VIDEO` | `videoUrl` |
| `VISIT_RELATIONSHIP` | `restaurantId`, `creatorId`, `videoId` |

서버는 타입별 필드를 정규화해 중복 판정용 대상 키를 만들며 클라이언트가 대상 키를 지정하지 않는다.

### 2.2 신고

```json
{
  "targetType": "RESTAURANT",
  "targetId": "01K4RESTAURANT00000000001",
  "reportType": "CLOSED",
  "description": "폐업 안내를 확인했습니다.",
  "evidenceUrl": "https://example.com/evidence"
}
```

`targetType`은 `RESTAURANT`, `CREATOR`, `VIDEO`, `VISIT_RELATIONSHIP`이며 `reportType`은 `ERROR`, `CLOSED`, `UNAVAILABLE`, `WRONG_RELATIONSHIP`, `INAPPROPRIATE_CONTENT`다. 대상 유형과 맞지 않는 신고 유형은 `400 INVALID_FIELD_VALUE`다.

접수 성공은 `201 Created`와 `RECEIVED` 자원을 반환한다. 같은 회원·정규화 대상·제보 대상 유형 또는 신고 유형의 열린 요청이 있으면 `409 DUPLICATE_OPEN_SUBMISSION` 또는 `DUPLICATE_OPEN_REPORT`와 기존 요청의 최소 `resource`를 반환한다. 제보·신고 합산 Asia/Seoul 하루 5건 초과는 `429 DAILY_REQUEST_LIMIT_EXCEEDED`다.

## 3. 회원 조회

목록은 `page`, `size`, 선택적 `status`를 받고 `createdAt` 내림차순, ID 오름차순이다. 상세와 목록은 `requestId`, 대상, 설명, 근거 URL, 상태, `memberReason`, 생성·수정 시각만 제공한다. 관리자 내부 메모·관리자 ID·감사 로그는 제외한다.

타 회원 또는 없는 요청은 각각 `404 SUBMISSION_NOT_FOUND`, `404 REPORT_NOT_FOUND`다.

## 4. 관리자 조회·상태 전이

관리 목록은 `page`, `size`, 선택적 `status`, `targetType`을 받고 `createdAt` 오름차순, ID 오름차순으로 오래된 요청부터 반환한다. 상세에는 회원 요청 필드와 `memberId`, 관리자 검토 기록을 포함한다.

```json
{
  "status": "REJECTED",
  "memberReason": "공개된 근거로 사실을 확인할 수 없습니다.",
  "internalNote": "확인한 출처와 판단 근거",
  "result": null
}
```

- `IN_REVIEW`, `ACCEPTED`에는 `memberReason`이 선택이며 `REJECTED`, `COMPLETED`에는 공백 제거 후 1~1000자의 `memberReason`이 필수다.
- `COMPLETED` 요청에는 실제 조치 결과인 `result`가 필수다. 형식은 `{ "actionType": "CREATED|UPDATED|HIDDEN", "targetType": "RESTAURANT|CREATOR|VIDEO|VISIT_RELATIONSHIP", "targetId": "..." }`이며 서버가 현재 데이터와 조치 완료 여부를 검증한다. 그 밖의 목표 상태에서 `result`는 `null`이다.
- 목표 상태가 이미 현재 상태면 최초 사유·결과·`updatedAt`과 알림을 유지한 채 `200`을 반환한다.
- 허용되지 않는 상태 전이는 `409 INVALID_STATUS_TRANSITION`이다. 실제 원본 데이터 등록·정정·수동 비공개가 끝나지 않은 `ACCEPTED` 요청의 `COMPLETED` 전환은 `409 SOURCE_ACTION_NOT_COMPLETED`다.
- 성공한 상태 변경, 사유와 내부 메모는 관리자 감사 이력에 기록한다. 알림 대상 상태 전이와 알림 생성은 같은 DB 트랜잭션이다.

## 5. 기능 오류

| HTTP | 코드 | 조건 |
|---:|---|---|
| 404 | `SUBMISSION_NOT_FOUND` / `REPORT_NOT_FOUND` | 요청 없음 또는 회원 소유 아님 |
| 404 | `PARTICIPATION_TARGET_NOT_FOUND` | 신고 대상 없음 |
| 409 | `DUPLICATE_OPEN_SUBMISSION` / `DUPLICATE_OPEN_REPORT` | 같은 열린 요청 존재 |
| 409 | `INVALID_STATUS_TRANSITION` | 허용되지 않은 상태 전이 |
| 409 | `SOURCE_ACTION_NOT_COMPLETED` | 실제 데이터 조치 확인 전 완료 요청 |
| 429 | `DAILY_REQUEST_LIMIT_EXCEEDED` | 제보·신고 합산 일일 한도 초과 |

---
id: API-ADMIN-AIEXTRACT-001
title: 관리자 AI 영상 추출 API
status: Accepted
owner: 김인안
reviewers:
  - 박진영
related_documents:
  - ../README.md
  - ../../../01-requirements/functional-requirements.md
  - ../../../01-requirements/business-rules.md
  - ../../../01-requirements/non-functional-requirements.md
  - ../../../04-product/prd/admin/ai-video-information-extraction.md
  - ../../../04-product/user-flows/third-expansion-user-flows.md
  - ../../data/third-expansion-ai-video-data-contract.md
  - ../../../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../../../07-adr/integration/ext-003-ai-extraction-async-reliability.md
  - ../../../08-planning/third-expansion-evaluation-strategy.md
  - ../common/authentication-contract.md
  - ../common/error-contract.md
  - ../common/pagination-contract.md
  - ../common/response-contract.md
---

# 관리자 AI 영상 추출 API

## 1. 범위와 공통 계약

이 문서는 [PRD-ADMIN-002](../../../04-product/prd/admin/ai-video-information-extraction.md)의 관리자 신규 영상 추가, YouTube Webhook 접수, AI 추출 작업 조회, 채널 감시 설정·상태 조회와 자동 등록·예외 보정 API를 정의한다. AI 결과는 자동 검증을 통과하면 관리자 승인 없이 정식 Entity와 `VisitTag`를 생성·공개하고, 모호·실패·중복 결과만 보류한다.

- 관리자 API는 `/api/admin` 아래에 두고 JWT Bearer와 `ADMIN` 권한을 요구한다.
- YouTube Webhook은 `/api/webhooks/youtube` 아래의 외부 수신 경계이며 관리자 JWT를 요구하지 않는다. Webhook은 작업 접수만 하고 AI·정식 등록을 실행하지 않는다.
- 관리자 요청과 Webhook 요청은 같은 영상·입력·Provider·Model·Prompt·Schema 버전 멱등성 키로 수렴한다.
- 성공 응답은 `data` 래퍼 없이 자원 자체를 반환하고, 목록은 `{ "items": [...], "page": {...} }`를 사용한다.
- 오류는 [공통 오류 계약](../common/error-contract.md)의 `traceId`와 오류 코드를 사용한다.
- 실제 Google Gemini API 호출, 원본 영상 다운로드, 전체 자막 저장은 이 API 요청 수명에 포함하지 않는다.

## 2. 상태와 공통 자원

### 2.1 작업 상태

| 필드 | 값 | 의미 |
|---|---|---|
| `executionStatus` | `QUEUED` | 작업이 접수되어 Worker 실행을 기다림 |
| `executionStatus` | `RUNNING` | Worker가 lease를 보유하고 처리 중 |
| `executionStatus` | `SUCCEEDED` | 출력 Schema 검증 성공 |
| `executionStatus` | `FAILED` | 제한된 재시도 뒤 실행 실패 또는 입력 접근 실패 |
| `resultCompleteness` | `COMPLETE` | 검수 대상 필드가 모두 추출됨 |
| `resultCompleteness` | `PARTIAL` | 작업은 성공했지만 일부 필드가 `UNKNOWN` |
| `reviewStatus` | `AUTO_CONFIRMED` | 자동 검증과 정식 등록·공개 완료 |
| `reviewStatus` | `AUTO_BLOCKED` | 모호·근거 부족·외부 충돌로 자동 보류 |
| `reviewStatus` | `AUTO_REJECTED` | 입력·정책·중복 검증 실패로 자동 거부 |
| `reviewStatus` | `MANUAL_OVERRIDE` | 관리자의 사후 보정·롤백 결과 |

`SUCCEEDED`와 `PARTIAL`은 실행 상태와 결과 완전성을 각각 표현한다. `AUTO_CONFIRMED`는 자동 검증과 기존 정식 등록 명령이 성공했다는 의미이며, 관리자의 사전 승인을 뜻하지 않는다.

### 2.2 작업 응답 공통 필드

```json
{
  "jobId": "opaque-job-id",
  "source": "WEBHOOK",
  "youtube": {
    "channelId": "opaque-channel-id",
    "videoId": "opaque-video-id",
    "videoUrl": "https://www.youtube.com/watch?v=..."
  },
  "executionStatus": "QUEUED",
  "resultCompleteness": null,
  "reviewStatus": null,
  "provider": "GOOGLE_GEMINI",
  "modelVersion": "gemini-3.5-flash-lite",
  "promptVersion": "P7",
  "schemaVersion": "S1",
  "attemptCount": 0,
  "createdAt": "2026-08-10T12:00:00+09:00",
  "startedAt": null,
  "finishedAt": null
}
```

`provider`는 `GOOGLE_GEMINI`, `modelVersion`은 `gemini-3.5-flash-lite`로 고정한다. quota·장애 시 다른 모델로 자동 전환하지 않고 작업 실패와 관리자 수동 등록 fallback을 사용한다. 입력 원문, Gemini 응답 전문, 비밀정보와 전체 자막은 응답에 포함하지 않는다.

## 3. 관리자 API

### 3.1 `POST /api/admin/ai/video-extractions` 신규 영상 추가·추출 요청

관리자가 초기 데이터 적립, Webhook 누락 보완 또는 수동 신규 영상 추가를 요청한다.

#### 요청

```json
{
  "videoUrl": "https://www.youtube.com/watch?v=...",
  "supplementText": "선택적 자막·전사·검수 메모",
  "idempotencyKey": "client-generated-key"
}
```

- `videoUrl`: 필수, 공개 YouTube 동영상 URL만 허용한다.
- `supplementText`: 선택, trim 후 최대 20,000자. 전체 자막을 자동으로 수집했다는 의미가 아니다. 비동기 Worker 복구를 위해 암호화된 임시 입력으로 저장할 수 있으며 작업 종료 후 24시간 이내 삭제한다. 식당명·메뉴·주소·Kakao 장소 URL은 보완 텍스트의 SHA-256과 문자 범위가 일치하는 `TEXT_RANGE` 후보로 사용할 수 있지만, 실제 방문 근거는 보완 텍스트만으로 확정하지 않고 영상 `TIMESTAMP`를 요구한다. 모든 값은 기존 Kakao·YouTube·Visit 검증을 그대로 통과해야 한다.
- `idempotencyKey`: 선택. 동일 URL·입력·버전 조합은 서버 멱등성 키로 다시 수렴한다.

#### 응답

- 신규 접수: `202 Accepted`, 작업 자원
- 동일 작업 수렴: `200 OK`, 기존 작업 자원과 `reused: true`
- URL 형식·공개 영상 조건 위반: `400 Bad Request`
- 동일 URL이 정식 등록 상태와 충돌해 자동 병합할 수 없는 경우: `409 Conflict`
- Gemini 호출은 이 요청 안에서 수행하지 않는다.

### 3.2 `GET /api/admin/ai/video-extractions` 작업 목록

| Query | 필수 | 허용값·기본값 |
|---|---:|---|
| `executionStatus` | 아니요 | `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED` |
| `source` | 아니요 | `WEBHOOK`, `ADMIN` |
| `reviewStatus` | 아니요 | `AUTO_CONFIRMED`, `AUTO_BLOCKED`, `AUTO_REJECTED`, `MANUAL_OVERRIDE` |
| `page` | 아니요 | 1-base, 기본 1 |
| `size` | 아니요 | 10·20·50, 기본 20 |

응답은 최신 생성 시각과 작업 ID 오름차순으로 안정 정렬한다. 목록에는 작업 ID, 영상 식별 정보, 유입 경로, 실행 상태, 결과 완전성, 자동 등록 상태, 버전, 시각만 포함하고 입력 원문은 포함하지 않는다.

### 3.3 `GET /api/admin/ai/video-extractions/{jobId}` 작업·후보 상세

작업 상태와 후보가 있으면 필드별 결과를 반환한다.

```json
{
  "jobId": "opaque-job-id",
  "source": "WEBHOOK",
  "executionStatus": "SUCCEEDED",
  "resultCompleteness": "PARTIAL",
  "reviewStatus": "AUTO_BLOCKED",
  "candidates": [
    {
      "field": "restaurantName",
      "value": "후보 맛집명",
      "confidence": 0.82,
      "evidence": {
        "type": "TIMESTAMP",
        "startMs": 42000,
        "endMs": 49000
      }
    },
    {
      "candidateTagId": "opaque-candidate-tag-id",
      "field": "tag",
      "tagType": "MENU",
      "rawLabel": "냉면",
      "normalizedCode": "MENU_NAENGMYEON",
      "label": "냉면",
      "confidence": 0.93,
      "evidence": {
        "type": "TIMESTAMP",
        "startMs": 42000,
        "endMs": 49000
      }
    }
  ],
  "missingFields": ["visitEvidence"],
  "error": null
}
```

- `evidence.type`은 `TIMESTAMP`(`startMs`, `endMs`), `TEXT_RANGE`(`startOffset`, `endOffset`, `sourceHash`), `UNKNOWN` 후보를 사용한다.
- `UNKNOWN` 또는 근거 없는 값은 정식 등록 확정 대상이 될 수 없다.
- **같은 `field`가 `candidates`에 여러 번 나타날 수 있다.** 한 영상에서 장소를 하나로 판정할 수 없으면 `BR-AIEXTRACT-001`에 따라 후보를 확정하지 않고 모두 남기기 때문이다. 클라이언트는 `field`를 키로 후보를 색인하지 않는다.
- 실패 응답은 `error.category`, `retryable`, `attemptCount`만 제공하며 외부 응답 전문은 제공하지 않는다.

### 3.4 `POST /api/admin/ai/video-extractions/{jobId}/retry` 수동 재시도

`FAILED` 또는 `SUCCEEDED/PARTIAL` 작업에 관리자가 새로운 보완 텍스트를 제출해 새 버전 작업을 요청한다. 이전 작업의 임시 입력은 재사용하지 않는다.

```json
{
  "supplementText": "추가로 확인한 텍스트",
  "reason": "Gemini 영상 입력에서 방문 근거가 누락됨"
}
```

- 응답은 `202 Accepted`와 새 작업 ID를 반환한다.
- 이전 후보를 덮어쓰지 않고 새 입력 해시·버전 후보를 만든다.
- `reason`은 trim 후 1,000자 이내 필수 값이며 새 작업의 `retry_reason`으로 보존한다.
- 무료 quota 소진·결제 연결 요구·설정 미검증 상태에서는 `429` 또는 공통 오류 계약의 제공자 차단 오류를 반환하고 수동 등록 fallback을 안내한다.

### 3.5 `POST /api/admin/ai/video-extractions/{jobId}/review` 사후 보정·롤백

이 API의 `review`는 정상 등록을 승인하는 API가 아니라 `AUTO_BLOCKED` 결과의 사후 보정과 이미 공개된 `AUTO_CONFIRMED` 결과의 롤백을 위한 관리자 API다. 요청 가능한 `decision`은 `CONFIRM`(차단 결과를 수동 보정해 등록), `DISCARD`(후보 폐기), `ROLLBACK`(자동 등록 결과 비공개·관계 해제)이며, 모든 변경은 `MANUAL_OVERRIDE` 이력으로 남긴다.

```json
{
  "decision": "CONFIRM",
  "reason": "Kakao 장소와 영상 timestamp 근거를 확인함",
  "expectedReviewStatus": "AUTO_BLOCKED",
  "tagDecisions": [
    {
      "candidateTagId": "opaque-candidate-tag-id",
      "decision": "MANUAL_OVERRIDE",
      "tagCode": null
    }
  ]
}
```

- `CONFIRM`은 정상 자동 등록을 시작하는 명령이 아니라, `AUTO_BLOCKED` 결과를 관리자가 사후 보정해 등록하는 경우에만 사용한다.
- 필수 필드에 후보가 둘 이상 남아 있으면 `CONFIRM`을 `422 AIEXTRACT_VALIDATION_CONFLICT`로 거절한다. 서버가 후보 중 하나를 임의로 고르지 않기 때문이며, 외부 검증을 시작하기 전에 거절하고 정식 저장은 0건이다. 이 경우 관리자는 후보를 확인해 관리자 등록 API로 등록하거나 `DISCARD`한다.
- `tagDecisions`는 자동 태그 판단 또는 관리자 사후 보정의 append-only 이력으로 저장한다.
- `UNKNOWN` 근거의 AI 후보는 자동 등록하지 않고, `TIMESTAMP` 또는 `TEXT_RANGE` 근거가 있는 태그만 `AI_AUTO_CONFIRMED` `VisitTag` 연결 대상이 된다.
- 자동 확정된 태그는 정식 `Visit` 등록 성공과 함께 `VisitTag`로 연결하며, 검색 API는 그 연결만 사용한다.
- `DISCARD`는 자동 등록 결과를 비공개·롤백하는 사후 조치로 사용한다.
- 자동 검증 실패 시 `AUTO_BLOCKED` 또는 `AUTO_REJECTED`로 유지하고 정식 Entity 저장은 0건이다.
- 동시 검수 충돌은 `409 Conflict`로 처리하고 최신 상태 재조회를 요구한다.

### 3.6 `GET /api/admin/ai/youtube-channel-watches/{creatorId}` 채널 감시 상태 조회

- `Authorization: Bearer <access-token>`과 `ADMIN` 권한을 요구한다.
- 대상은 외부 YouTube 채널 식별자가 확인된 Creator로 한정한다. Creator가 존재하지 않거나 외부 채널 식별자가 없으면 `404 CREATOR_NOT_FOUND`를 반환한다. 등록 후 Creator가 비공개·삭제 상태 또는 외부 이용 불가로 바뀌어도 기존 감시 상태는 조회할 수 있다. 감시 활성화(`enabled=true`)만 공개·외부 이용 가능 검증을 요구한다.
- 감시 설정 행이 없어도 오류로 처리하지 않고 다음 자원 표현을 `200 OK`로 반환한다.

```json
{
  "enabled": false,
  "subscriptionStatus": "INACTIVE",
  "lastNotificationAt": null,
  "lastRenewedAt": null,
  "lastErrorCategory": null,
  "lastErrorAt": null
}
```

- `subscriptionStatus=UNKNOWN`은 `enabled=true` 활성화 의도를 저장했지만 외부 구독 challenge가 아직 성공하지 않은 상태이며, 이때 Webhook을 수락하지 않는다.
- `subscriptionStatus=ACTIVE`는 challenge 성공 후 구독을 수락한 상태이며, 이때만 Webhook을 수락한다. `INACTIVE`와 `RENEWAL_FAILED`도 신규 Webhook을 수락하지 않는다.
- `lastErrorCategory`는 저장된 오류 범주만 반환하며, Token 원문·Token 해시·Hub 원문과 외부 응답 전문은 반환하지 않는다. 오류가 저장되지 않았으면 `null`이다.
- `lastErrorAt`은 마지막 구독 처리 오류가 기록된 시각이며, 오류가 저장되지 않았으면 `null`이다. 오류 시각과 범주는 challenge 성공 시 함께 초기화한다.
- `PUT`의 응답은 이 GET과 같은 자원 표현을 사용하며, 활성화·해지 결과는 이후 GET에서 조회할 수 있다.

### 3.7 `PUT /api/admin/ai/youtube-channel-watches/{creatorId}` 채널 감시 설정

```json
{
  "enabled": true
}
```

- 검증된 Creator의 YouTube 채널만 활성화할 수 있다.
- 응답에는 `enabled`, `subscriptionStatus`, `lastNotificationAt`, `lastRenewedAt`, `lastErrorCategory`, `lastErrorAt`을 포함한다.
- `enabled=true`는 감시 활성화 의도만 저장하며, 외부 구독 challenge가 성공하기 전까지 `subscriptionStatus=UNKNOWN`으로 반환하고 Webhook을 수락하지 않는다. challenge 성공 시 `ACTIVE`와 `lastRenewedAt`을 기록한다.
- `enabled=true` 요청은 `hub.mode=subscribe`와 발급한 검증 Token을 YouTube PubSubHubbub 구독 요청에 전달한다. 구독 확인 요청이 성공하기 전에는 계속 `UNKNOWN`으로 둔다.
- 이미 `ACTIVE`이고 검증 Token 해시가 유효한 채널에 대한 중복 `enabled=true` 요청은 기존 상태·Token을 유지하고 외부 재구독을 요청하지 않는다.
- Creator 등록만으로 자동 활성화하지 않는다.
- `enabled=false`는 신규 Webhook 접수를 중지하고 `subscriptionStatus=INACTIVE`로 저장한다. 이미 접수된 작업·후보·정식 데이터는 삭제하지 않는다.

## 4. YouTube Webhook API

| API ID | Method | Path | 설명 |
|---|---|---|---|
| API-ADMIN-AIEXTRACT-WEBHOOK-001 | GET | `/api/webhooks/youtube/channel-updates` | YouTube 구독 확인 |
| API-ADMIN-AIEXTRACT-WEBHOOK-002 | POST | `/api/webhooks/youtube/channel-updates` | 신규 영상 Atom 알림 접수 |

### 4.1 API-ADMIN-AIEXTRACT-WEBHOOK-001 `GET /api/webhooks/youtube/channel-updates` 구독 확인

YouTube 구독 확인 요청의 `hub.challenge`를 검증한 뒤 동일 값을 `200 OK` 본문으로 반환한다. 등록되지 않은 채널 또는 검증 Token이 맞지 않으면 `404` 또는 `403`으로 응답하고 작업을 만들지 않는다.

### 4.2 API-ADMIN-AIEXTRACT-WEBHOOK-002 `POST /api/webhooks/youtube/channel-updates` 신규 영상 알림

- `Content-Type: application/atom+xml`을 허용한다.
- YouTube 구독 시 협상한 공용 `hub.secret`으로 raw payload의 HMAC을 검증한다. `X-Hub-Signature-256: sha256=<hex>`를 우선 사용하고, 호환을 위해 `X-Hub-Signature: sha1=<hex>`도 허용한다.
- 서명 검증은 XML 파싱과 작업 접수보다 먼저 수행하며, 비밀값이 없거나 헤더가 누락·불일치하면 `403 AIEXTRACT_WEBHOOK_SIGNATURE_INVALID`으로 거부한다.
- Atom Payload에서 채널 ID와 영상 ID를 읽고, 활성화된 감시 채널인지 확인한다.
- 동일 영상의 반복 알림은 같은 작업으로 수렴한다.
- 처리 순서: 크기 제한 → HMAC 서명 검증 → Atom 형식·채널·영상 식별 검증 → 작업 등록 → `204 No Content` 응답
- 처리기 안에서 Gemini, Kakao, 정식 등록 API를 호출하지 않는다.
- 알림이 유효하지 않으면 작업을 만들지 않고 오류 범주만 기록한다.

Webhook 수신 경로는 공개 인터넷 진입점이므로 Nginx·Spring Security 라우팅, 요청 크기 제한, 검증 Token 관리와 traceId 기록을 별도 운영 계약으로 연결한다. 원본 XML 전체와 입력 원문은 일반 로그에 남기지 않는다.

## 5. 오류 계약

| 오류 코드 | HTTP | 상황 |
|---|---:|---|
| `AIEXTRACT_INVALID_VIDEO_URL` | 400 | YouTube URL 형식·호스트·식별자 오류 |
| `AIEXTRACT_DUPLICATE_CONFLICT` | 409 | 기존 정식 데이터 또는 동시 검수 충돌 |
| `AIEXTRACT_JOB_NOT_FOUND` | 404 | 작업 ID 없음 또는 접근 불가 |
| `CREATOR_NOT_FOUND` | 404 | Creator 없음·비공개·삭제 또는 외부 이용 불가 |
| `AIEXTRACT_RETRY_BLOCKED` | 409 | 상태상 재시도 불가 |
| `AIEXTRACT_PROVIDER_BLOCKED` | 429 | Gemini Free Tier quota 소진·결제 연결 요구·무료 정책 미검증 |
| `AIEXTRACT_WEBHOOK_REJECTED` | 403 | 구독 채널·검증 Token 불일치 |
| `AIEXTRACT_WEBHOOK_SIGNATURE_INVALID` | 403 | Webhook HMAC 비밀값·헤더 누락 또는 서명 불일치 |
| `AIEXTRACT_VALIDATION_CONFLICT` | 422 | 자동 검증 중 기존 Kakao·YouTube·Visit 검증 실패 |

모든 오류는 서버 생성 `traceId`를 포함하며 입력 원문·Gemini 응답·비밀정보를 메시지에 포함하지 않는다.

## 6. API 완료 조건

- [ ] 관리자 신규 추가·목록·상세·재시도·자동 결과 조회·예외 보정·채널 감시 API의 권한·상태·오류 계약이 승인된다.
- [ ] Webhook 확인·알림의 중복·잘못된 채널·잘못된 Token·대형 Payload·AI 호출 격리가 검증된다.
- [ ] `WEBHOOK`·`ADMIN` 요청이 하나의 멱등 작업으로 수렴하고, 동시 검수에서 정식 저장 중복이 0건이다.
- [ ] Gemini 접근 실패·부분 추출·quota 차단과 관리자 텍스트 fallback의 응답이 계약 테스트로 고정된다.
- [ ] 태그 후보의 허용 코드·근거·자동 판단과 사후 보정·`VisitTag` 연결이 계약 테스트로 고정된다.
- [ ] [데이터 계약](../../data/third-expansion-ai-video-data-contract.md), API 추적표, 후보·Worker 테스트와 연결된다.

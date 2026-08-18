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

이 문서는 [PRD-ADMIN-002](../../../04-product/prd/admin/ai-video-information-extraction.md)의 관리자 신규 영상 추가, YouTube Webhook 접수, AI 추출 작업 조회, 채널 감시 설정·상태 조회와 자동 등록·예외 보정 API를 정의한다. AI 결과는 자동 검증을 통과하면 관리자 승인 없이 정식 Entity와 `VisitTag`를 생성·공개하고, 모호·실패·중복 결과만 보류한다. 판정과 등록의 단위는 작업 전체가 아니라 `BR-AIEXTRACT-001`의 장소 단위 등록 단위이며, 장소 동일성(`BR-AIEXTRACT-009`)과 대표 음식 카테고리(`BR-AIEXTRACT-010`)는 관리자 입력 없이 시스템이 결정한다. 이 API는 관리자에게 Kakao 장소 URL이나 음식 카테고리 선택을 요구하지 않는다.

`registrationUnits`·`candidateTruncated`·`manualOverrideType` 응답 필드, 등록 단위 일괄 등록 API(3.6절), `review`의 `unitId`·`supplements`·`ADJUST_CATEGORY`, 작업 최상위 `reviewStatus` 요약 규칙, `recoveryPaths`, `AIEXTRACT_UNIT_ID_REQUIRED`·`AIEXTRACT_UNIT_NOT_FOUND`·`AIEXTRACT_CONCURRENT_REQUEST_CONFLICT` 오류 코드는 `합의 대기` 상태다. 합의는 [PR #226](https://github.com/team-youngkk/masit-on/pull/226)의 소유자 승인으로 갈음하며, 승인 전에는 구현 계약으로 사용하지 않는다. 승인 후 병합 직전 커밋에서 이 표시를 제거한다. 절차는 [ADR-AI-001 1절](../../../07-adr/integration/ai-001-video-extraction-candidate-boundary.md)에 있다. 그 밖의 절은 종전대로 Accepted다.

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
| `reviewStatus` | `AUTO_CONFIRMED` | 자동 검증과 정식 등록·공개 완료. 등록 단위 값은 그 단위의 등록 성공을 뜻하고, 작업 최상위 값은 아래 요약 규칙을 따르므로 모든 단위 성공을 보장하지 않는다 |
| `reviewStatus` | `AUTO_BLOCKED` | 모호·근거 부족·외부 충돌로 자동 보류 |
| `reviewStatus` | `AUTO_REJECTED` | 입력·정책 검증 실패로 자동 거부. 복구 경로가 없는 종결 상태다 |
| `reviewStatus` | `MANUAL_OVERRIDE` | 관리자 개입 결과. 등록 유지(사후 보정 등록·카테고리 보정)·롤백 완료·폐기 완료 세 하위 상태를 가지며 `manualOverrideType`으로 구분한다 |

`SUCCEEDED`와 `PARTIAL`은 실행 상태와 결과 완전성을 각각 표현한다. `AUTO_CONFIRMED`는 자동 검증과 기존 정식 등록 명령이 성공했다는 의미이며, 관리자의 사전 승인을 뜻하지 않는다.

중복은 종결이 아니라 복구 가능한 보류다. 같은 맛집·방문 관계가 이미 존재하는 `DUPLICATE_CONFLICT`는 `AUTO_BLOCKED`로 귀결하며 `AUTO_REJECTED`로 매핑하지 않는다. `BR-AIEXTRACT-011`의 예외 전환 대상이지만, 관리자가 할 수 있는 것은 기존 등록 결과를 확인하는 것(`EXISTING_RESOURCE`)뿐이다. `CONFIRM`·`ADJUST_CATEGORY`로 사후 보정하지 않으며 재추출·재실행·수동 등록 경로도 없다.

#### 작업 최상위 `reviewStatus` 요약 규칙

작업 최상위 값은 등록 단위 판정의 요약이다. 권위 있는 값은 `registrationUnits[].reviewStatus`이며, 최상위 값은 다음 우선순위로 결정한다.

| 순위 | 조건 | 최상위 `reviewStatus` |
|---:|---|---|
| 1 | 후보 Snapshot이 아직 없다. 즉 자동 판정 전이다 | `null` |
| 2 | Snapshot은 있으나 등록 단위가 하나도 없다 | Snapshot 자체 판정값. 후보 부족으로 차단이면 `AUTO_BLOCKED`, 정책 위반이면 `AUTO_REJECTED` |
| 3 | `MANUAL_OVERRIDE` 단위가 하나라도 있다(등록 유지·롤백 완료·폐기 완료 세 하위 상태 모두 포함) | `MANUAL_OVERRIDE` |
| 4 | `AUTO_BLOCKED` 단위가 하나라도 있다 | `AUTO_BLOCKED` |
| 5 | `AUTO_CONFIRMED` 단위가 하나라도 있다 | `AUTO_CONFIRMED` |
| 6 | 그 밖의 경우, 즉 모든 단위가 `AUTO_REJECTED`다 | `AUTO_REJECTED` |

위에서부터 먼저 만족하는 조건 하나를 적용하며, 6순위가 나머지를 모두 받으므로 등록 단위 상태의 어떤 조합에서도 값이 하나로 결정된다. 조합별 결과는 다음과 같다.

| 등록 단위 조합 | 최상위 값 | 이유 |
|---|---|---|
| 확정 + 차단 | `AUTO_BLOCKED` | 처리할 예외가 남았다 |
| 확정 + 거부 | `AUTO_CONFIRMED` | 거부는 종결이라 남은 작업이 없고 등록은 일어났다 |
| 차단 + 거부 | `AUTO_BLOCKED` | 처리할 예외가 남았다 |
| 확정 + 차단 + 거부 | `AUTO_BLOCKED` | 같은 이유 |
| `MANUAL_OVERRIDE` 단위(등록 유지·롤백 완료·폐기 완료 어느 하위 상태든) 포함 어떤 조합 | `MANUAL_OVERRIDE` | 관리자 개입 사실이 가장 우선한다 |

최상위 `AUTO_CONFIRMED`는 "모든 단위 성공"이 아니라 **"처리할 예외가 남지 않았고 등록된 단위가 있다"**는 뜻이다. 확정 + 거부 혼합도 여기에 해당한다. 모든 단위가 성공했는지 확인하려면 `registrationUnits`를 읽는다.

`AUTO_BLOCKED`가 `AUTO_CONFIRMED`보다 우선하는 이유는 처리할 예외가 남아 있음을 알리기 위해서다. 반대로 `AUTO_REJECTED`는 복구 경로가 없는 종결이므로 확정된 등록이 있으면 그쪽이 우선한다. 클라이언트는 어느 경우에도 최상위 값만으로 등록 성공 여부를 판단하지 않고 `registrationUnits`를 함께 읽는다. 새 Enum 값은 추가하지 않는다. 계약 테스트는 위 다섯 조합을 모두 고정한다.

`resultCompleteness`와는 독립이다. `resultCompleteness`는 AI 추출 결과의 필드 완전성이고 `reviewStatus`는 등록 판정 결과다. `COMPLETE` 결과가 `AUTO_BLOCKED`일 수 있고, `PARTIAL` 결과의 일부 등록 단위가 `AUTO_CONFIRMED`일 수 있다.

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
  "candidateTruncated": false,
  "registrationUnits": [
    {
      "unitId": "opaque-registration-unit-id",
      "restaurantName": "후보 맛집명",
      "reviewStatus": "AUTO_CONFIRMED",
      "manualOverrideType": null,
      "blockReason": null,
      "registeredRestaurantId": "opaque-restaurant-id",
      "registeredCreatorId": "opaque-creator-id",
      "registeredVideoId": "opaque-video-id",
      "registeredVisitId": "opaque-visit-id",
      "reusedResources": ["creator", "video"],
      "placeDecision": {
        "kakaoPlaceUrl": "https://place.map.kakao.com/example",
        "roadAddress": "서울특별시 영등포구 도림로131길 17",
        "matchedBy": "NAME_AND_DISTRICT"
      },
      "categoryDecision": {
        "foodCategoryName": "일식",
        "resolvedBy": "KAKAO_PLACE_CATEGORY"
      }
    },
    {
      "unitId": "opaque-registration-unit-id-2",
      "restaurantName": "다른 후보 맛집명",
      "reviewStatus": "AUTO_BLOCKED",
      "manualOverrideType": null,
      "blockReason": "PLACE_AMBIGUOUS",
      "registeredRestaurantId": null,
      "registeredCreatorId": null,
      "registeredVideoId": null,
      "registeredVisitId": null,
      "reusedResources": [],
      "placeDecision": null,
      "categoryDecision": null
    },
    {
      "unitId": "opaque-registration-unit-id-3",
      "restaurantName": "롤백된 맛집명",
      "reviewStatus": "MANUAL_OVERRIDE",
      "manualOverrideType": "ROLLED_BACK",
      "blockReason": null,
      "registeredRestaurantId": null,
      "registeredCreatorId": null,
      "registeredVideoId": null,
      "registeredVisitId": null,
      "reusedResources": [],
      "placeDecision": null,
      "categoryDecision": null
    },
    {
      "unitId": "opaque-registration-unit-id-4",
      "restaurantName": "폐기된 맛집명",
      "reviewStatus": "MANUAL_OVERRIDE",
      "manualOverrideType": "DISCARDED",
      "blockReason": null,
      "registeredRestaurantId": null,
      "registeredCreatorId": null,
      "registeredVideoId": null,
      "registeredVisitId": null,
      "reusedResources": [],
      "placeDecision": null,
      "categoryDecision": null
    }
  ],
  "error": null
}
```

`manualOverrideType`은 `reviewStatus`가 `MANUAL_OVERRIDE`일 때만 값이 있고, 그 밖의 상태에서는 `null`이다.

| `manualOverrideType` | 의미 | 등록 결과 식별자 4종 |
|---|---|---|
| `null` | `MANUAL_OVERRIDE`가 아니거나, 등록 유지 상태(사후 보정 등록 완료·카테고리 보정) | 등록 유지 상태면 모두 존재, 그 밖은 `MANUAL_OVERRIDE`가 아니므로 해당 없음 |
| `ROLLED_BACK` | 관리자 롤백 완료. 종결 상태 | 모두 `null` |
| `DISCARDED` | 관리자 폐기 완료. 종결 상태 | 모두 `null` |

등록 유지 상태(사후 보정 등록 또는 카테고리 보정 완료)는 `manualOverrideType`이 `null`이면서 `reviewStatus`가 `MANUAL_OVERRIDE`이고 등록 결과 식별자가 모두 존재하는 조합으로 판별한다. 롤백 완료·폐기 완료와 달리 별도 값을 두지 않은 이유는 등록 결과 존재 여부만으로 이미 구분되기 때문이다.

- `evidence.type`은 `TIMESTAMP`(`startMs`, `endMs`), `TEXT_RANGE`(`startOffset`, `endOffset`, `sourceHash`), `UNKNOWN` 후보를 사용한다.
- `UNKNOWN` 또는 근거 없는 값은 정식 등록 확정 대상이 될 수 없다.
- **같은 `field`가 `candidates`에 여러 번 나타날 수 있다.** 한 영상에 장소가 여러 곳 등장하는 것은 정상이므로 `BR-AIEXTRACT-001`에 따라 후보를 모두 남긴다. 클라이언트는 `field`를 키로 후보를 색인하지 않는다.
- `candidateTruncated`가 `true`이면 후보 수 상한 때문에 일부 장소가 후보에서 생략됐다는 뜻이다. 클라이언트는 이 작업의 등록 결과가 영상의 모든 맛집을 덮지 않는다고 표시해야 한다. 후보 수가 상한과 같으면 모델이 표시하지 않았어도 서버가 `true`로 판단한다.
- `registrationUnits`는 `BR-AIEXTRACT-001`의 장소 단위 등록 단위별 판정 결과다. 후보가 없거나 등록 단위를 구성하지 못한 작업은 빈 배열이다. 작업 최상위 `reviewStatus`는 등록 단위 판정의 요약이며, 단위별 결과는 `registrationUnits[].reviewStatus`가 권위 있는 값이다.
- 등록 결과 식별자 4종은 함께 존재하거나 함께 `null`이다. `AUTO_CONFIRMED`와 등록 유지 `MANUAL_OVERRIDE`(`manualOverrideType=null`)에서만 값이 있고, `AUTO_BLOCKED`·`AUTO_REJECTED`·롤백 완료·폐기 완료 `MANUAL_OVERRIDE`에서는 모두 `null`이며 `reusedResources`는 빈 배열이다. 이 규칙은 데이터 계약 5.1절의 상태·컬럼 조합표와 같다.
- `blockReason`은 `AUTO_BLOCKED`·`AUTO_REJECTED`일 때만 값이 있다. 장소 판정은 `PLACE_NOT_FOUND`·`PLACE_AMBIGUOUS`, 카테고리 판정은 `CATEGORY_UNRESOLVED`, 그 밖에는 기존 검증 실패 사유 코드를 사용한다.
- `placeDecision.matchedBy`와 `categoryDecision.resolvedBy`는 자동 판정 근거다. `matchedBy`는 `NAME_AND_DISTRICT`, `resolvedBy`는 `KAKAO_PLACE_CATEGORY` 또는 `MENU_EXPRESSION`이다. 관리자 사후 보정 결과는 `MANUAL_OVERRIDE`로 표시한다.
- `registeredRestaurantId`, `kakaoPlaceUrl` 등 모든 식별자는 불투명 문자열이며 클라이언트는 생성 규칙을 검증하지 않는다.
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

이 API의 `review`는 정상 등록을 승인하는 API가 아니라 자동 판정 결과를 사후에 보정·롤백하는 관리자 API다. 모든 변경은 `MANUAL_OVERRIDE` 이력으로 남긴다.

| `decision` | 허용 상태 | 효과 |
|---|---|---|
| `CONFIRM` | `AUTO_BLOCKED` | 보충 입력으로 판정을 보정해 등록한다 |
| `DISCARD` | `AUTO_BLOCKED` | 등록 단위를 폐기한다 |
| `ROLLBACK` | `AUTO_CONFIRMED`, 등록 완료 `MANUAL_OVERRIDE` | 등록 결과를 비공개·관계 해제한다 |
| `ADJUST_CATEGORY` | `AUTO_CONFIRMED`, 등록 완료 `MANUAL_OVERRIDE` | 등록을 유지한 채 대표 음식 카테고리만 바꾼다 |

허용 상태가 아닌 등록 단위에 대한 요청은 `422 AIEXTRACT_VALIDATION_CONFLICT`로 거절하고 정식 데이터는 변경하지 않는다. `AUTO_REJECTED`, 롤백 완료 `MANUAL_OVERRIDE`, 폐기 완료 `MANUAL_OVERRIDE`는 어떤 `decision`도 허용하지 않는 종결 상태이며 새 작업 재추출만 가능하다.

`unitId` 처리 규칙은 네 `decision`에 모두 같게 적용한다. 3.5절 아래의 등록 단위 수별 처리표가 그 규칙이며 `ADJUST_CATEGORY`도 예외가 아니다.

장소 예외를 보정하는 `CONFIRM` 요청이다.

```json
{
  "decision": "CONFIRM",
  "unitId": "opaque-registration-unit-id",
  "reason": "Kakao 장소와 영상 timestamp 근거를 확인함",
  "expectedReviewStatus": "AUTO_BLOCKED",
  "supplements": {
    "kakaoPlaceUrl": "https://place.map.kakao.com/example"
  },
  "tagDecisions": [
    {
      "candidateTagId": "opaque-candidate-tag-id",
      "decision": "MANUAL_OVERRIDE",
      "tagCode": null
    }
  ]
}
```

카테고리 예외를 보정하는 `CONFIRM` 요청은 다른 키를 보낸다.

```json
{
  "decision": "CONFIRM",
  "unitId": "opaque-registration-unit-id",
  "reason": "Kakao 분류가 비어 있어 메뉴 표현으로 확인함",
  "expectedReviewStatus": "AUTO_BLOCKED",
  "supplements": {
    "foodCategoryId": "opaque-food-category-id"
  }
}
```

등록 완료 결과의 카테고리만 바꾸는 요청이다. 공개 상태와 나머지 등록 결과는 그대로 둔다.

```json
{
  "decision": "ADJUST_CATEGORY",
  "unitId": "opaque-registration-unit-id",
  "reason": "Kakao 분류가 실제 업종과 달라 보정함",
  "expectedReviewStatus": "AUTO_CONFIRMED",
  "supplements": {
    "foodCategoryId": "opaque-food-category-id"
  }
}
```

`supplements`는 `CONFIRM`과 `ADJUST_CATEGORY`에서만 사용한다. `CONFIRM`에서는 3.6절 예외 전환 응답의 `requiredSupplements`에 대응하고, `ADJUST_CATEGORY`에서는 `foodCategoryId`만 필수다.

| 필드 | 타입 | 필수 조건 | 설명 |
|---|---|---|---|
| `supplements.kakaoPlaceUrl` | string | `blockReason`이 `PLACE_NOT_FOUND`·`PLACE_AMBIGUOUS`이면 필수 | 관리자가 확인한 Kakao 장소 URL |
| `supplements.foodCategoryId` | string | `blockReason`이 `CATEGORY_UNRESOLVED`이거나 `decision`이 `ADJUST_CATEGORY`이면 필수 | 공통 기준정보 10개 값 중 하나의 식별자 |

- **요구하지 않은 키는 값이 `null`이어도 보내지 않는다.** `null`을 미전송으로 취급하지 않고 키 존재만으로 판정한다. 직렬화 단계에서 `null` 필드를 자동으로 넣는 클라이언트는 그 필드를 제외하도록 설정해야 한다.
- `requiredSupplements`가 요구하지 않은 필드를 보내면 `400 INVALID_FIELD_VALUE`로 거절한다. 관리자가 자동 판정 결과를 임의로 덮어쓰지 못하게 하기 위해서다.
- `requiredSupplements`가 요구한 필드가 없으면 `400 MISSING_REQUIRED_FIELD`로 거절하고 정식 저장은 0건이다.
- `kakaoPlaceUrl`은 기존 수동 등록 경로와 같은 Kakao 장소 동일성 검증을 다시 통과해야 한다. 검증 실패는 `422 AIEXTRACT_VALIDATION_CONFLICT`이며 `blockReason`을 그대로 유지한다.
- `foodCategoryId`는 활성 기준정보 값이어야 한다. 비활성·미존재 값은 `400 INVALID_FIELD_VALUE`다.
- `VISIT_EVIDENCE_REQUIRED`·`DUPLICATE_CONFLICT`·`EXTERNAL_SERVICE_ERROR`는 보충 입력으로 복구할 수 없다. 이 사유의 `CONFIRM`은 `422 AIEXTRACT_VALIDATION_CONFLICT`로 거절한다. `VISIT_EVIDENCE_REQUIRED`·`EXTERNAL_SERVICE_ERROR`는 재추출·재실행 또는 수동 등록으로 안내하고, `DUPLICATE_CONFLICT`는 이미 등록된 자원 확인(`EXISTING_RESOURCE`)만 안내하며 재추출·재실행·수동 등록 경로는 없다.
- 보충 입력으로 등록에 성공하면 등록 단위는 `MANUAL_OVERRIDE`가 되고, 사용한 보충값과 제출자를 감사 이력에 남긴다.
- `ADJUST_CATEGORY`는 `registered_restaurant_id`가 가리키는 맛집의 대표 카테고리와 `category_decision`을 바꾼다. `resolvedBy`는 `MANUAL_OVERRIDE`가 되고 등록 단위 상태는 `MANUAL_OVERRIDE`로 전환하되 등록 결과 컬럼과 공개 상태는 유지한다. 이전 카테고리 값과 제출자는 append-only 감사 이력에 남긴다.
- 계약 테스트는 `TST-E3-AI-007`에 매핑한다.

- `CONFIRM`은 정상 자동 등록을 시작하는 명령이 아니라, `AUTO_BLOCKED` 결과를 관리자가 사후 보정해 등록하는 경우에만 사용한다.
- 네 `decision`(`CONFIRM`·`DISCARD`·`ROLLBACK`·`ADJUST_CATEGORY`)은 모두 등록 단위를 대상으로 하며 같은 `unitId` 처리 규칙을 따른다. 규칙은 작업이 가진 등록 단위 수에 따라 다음과 같다.

| 등록 단위 수 | `unitId` 생략 | `unitId` 지정 |
|---:|---|---|
| 0개 | `422 AIEXTRACT_VALIDATION_CONFLICT`. 대상이 없어 처리할 수 없고 정식 저장은 0건이다 | 같음 |
| 1개 | 그 단위를 대상으로 한다 | 그 단위와 일치해야 한다 |
| 2개 이상 | `400 AIEXTRACT_UNIT_ID_REQUIRED` | 지정한 단위를 대상으로 한다 |

- 지정한 `unitId`가 이 작업의 등록 단위가 아니면 `404 AIEXTRACT_UNIT_NOT_FOUND`로 응답한다. 작업 자체가 없거나 접근할 수 없는 `AIEXTRACT_JOB_NOT_FOUND`와 구분한다. 전자는 `unitId`를 다시 확인해야 하고, 후자는 작업 목록으로 돌아가야 한다. 이 응답은 그 식별자가 다른 작업에 존재하는지 여부를 알려주지 않는다.
- `AIEXTRACT_UNIT_ID_REQUIRED`는 후보 데이터 부족을 뜻하는 `blockReason`의 `MISSING_REQUIRED_FIELD`와 구분한다. 전자는 요청 파라미터를 다시 보내야 하고, 후자는 후보 데이터를 보완해야 한다.
- 대상 등록 단위의 필수 필드에 후보가 둘 이상 남아 어느 값으로 등록할지 확정할 수 없으면 `CONFIRM`을 `422 AIEXTRACT_VALIDATION_CONFLICT`로 거절한다. 서버가 후보 중 하나를 임의로 고르지 않기 때문이며, 외부 검증을 시작하기 전에 거절하고 정식 저장은 0건이다. 이 경우 관리자는 후보를 확인해 관리자 등록 API로 등록하거나 `DISCARD`한다.
- `ROLLBACK`은 지정한 등록 단위의 자동 등록 결과만 되돌리고 같은 작업의 다른 등록 단위는 변경하지 않는다.
- `tagDecisions`는 자동 태그 판단 또는 관리자 사후 보정의 append-only 이력으로 저장한다.
- `UNKNOWN` 근거의 AI 후보는 자동 등록하지 않고, `TIMESTAMP` 또는 `TEXT_RANGE` 근거가 있는 태그만 `AI_AUTO_CONFIRMED` `VisitTag` 연결 대상이 된다.
- 자동 확정된 태그는 정식 `Visit` 등록 성공과 함께 `VisitTag`로 연결하며, 검색 API는 그 연결만 사용한다.
- `DISCARD`는 등록되지 않은 `AUTO_BLOCKED` 등록 단위를 더 다루지 않겠다고 선언하는 종결 조치다. 자동 등록 결과를 되돌리는 것은 `ROLLBACK`이며 둘을 섞어 쓰지 않는다. 폐기한 등록 단위는 `MANUAL_OVERRIDE`가 되고 `discarded_at`이 채워지며, 등록 결과 컬럼은 계속 `NULL`이다. 이후 어떤 `decision`도 등록 API도 허용하지 않는다.
- 자동 검증 실패 시 `AUTO_BLOCKED` 또는 `AUTO_REJECTED`로 유지하고 정식 Entity 저장은 0건이다.
- 같은 등록 단위에 대한 동시 `review` 요청은 `409 AIEXTRACT_CONCURRENT_REQUEST_CONFLICT`로 처리하고 최신 상태 재조회를 요구한다.

### 3.6 `POST /api/admin/ai/video-extractions/{jobId}/registration-units/{unitId}/registration` 등록 단위 일괄 등록

관리자가 아직 등록되지 않은 등록 단위의 등록을 실행한다. 한 번의 요청으로 맛집·유튜버·영상·방문 관계 4종을 등록하고 결과를 반환한다. 관리자는 장소 후보·주소 힌트·Kakao 장소 URL·음식 카테고리를 제출하지 않으며, 요청 본문은 비어 있다.

Worker 자동 등록과 같은 판정 규칙(`BR-AIEXTRACT-009`·`BR-AIEXTRACT-010`)을 사용한다. 실행 주체만 다르고 판정 기준은 같다.

#### 등록 단위 상태별 허용 범위

| 등록 단위 상태 | 요청 처리 | 저장 효과 |
|---|---|---|
| `AUTO_BLOCKED` | 허용. 판정을 다시 실행한다 | 차단 사유가 해소됐으면 등록, 아니면 예외 전환 응답과 저장 0건 |
| `AUTO_CONFIRMED` | 멱등. `200 OK`와 기존 결과를 반환한다 | 없음 |
| `MANUAL_OVERRIDE` · 등록 완료 | 멱등. `200 OK`와 기존 결과를 반환한다 | 없음 |
| `MANUAL_OVERRIDE` · 롤백 완료 | `422 AIEXTRACT_VALIDATION_CONFLICT`로 거절한다 | 없음 |
| `MANUAL_OVERRIDE` · 폐기 완료 | `422 AIEXTRACT_VALIDATION_CONFLICT`로 거절한다 | 없음 |
| `AUTO_REJECTED` | `422 AIEXTRACT_VALIDATION_CONFLICT`로 거절한다 | 없음 |

- `AUTO_REJECTED`는 입력·정책 검증 실패로 끝난 종결 상태다. 이 엔드포인트로 되살리면 종결 판정을 우회하게 되므로 허용하지 않는다. 새 작업으로 다시 추출한다.
- 롤백 완료 `MANUAL_OVERRIDE`는 관리자가 의도적으로 되돌린 결과다. 같은 경로로 다시 등록하면 롤백 의도를 무효로 만들 수 있어 거절한다. `review`의 어떤 `decision`도 이 상태를 대상으로 허용하지 않으므로, 다시 등록하려면 새 작업으로 재추출하거나 기존 수동 등록을 사용한다.
- 멱등 응답은 새 Entity를 만들지 않고 `ai_registration_unit`에 저장된 기존 결과를 그대로 반환한다.

#### 응답 `200 OK`

```json
{
  "unitId": "opaque-registration-unit-id",
  "reviewStatus": "AUTO_CONFIRMED",
  "restaurantId": "opaque-restaurant-id",
  "creatorId": "opaque-creator-id",
  "videoId": "opaque-video-id",
  "visitId": "opaque-visit-id",
  "reusedResources": ["creator", "video"],
  "placeDecision": {
    "kakaoPlaceUrl": "https://place.map.kakao.com/example",
    "roadAddress": "서울특별시 영등포구 도림로131길 17",
    "matchedBy": "NAME_AND_DISTRICT"
  },
  "categoryDecision": {
    "foodCategoryName": "일식",
    "resolvedBy": "KAKAO_PLACE_CATEGORY"
  }
}
```

- `reusedResources`는 새로 만들지 않고 기존 식별자를 재사용한 자원 목록이다. 허용값은 `creator`와 `video`뿐이다. 유튜버·영상 재사용은 정상 경로이며 예외가 아니다. 맛집과 방문 관계는 이미 존재하면 `DUPLICATE_CONFLICT`로 차단되어 등록 자체가 일어나지 않으므로 재사용 대상이 될 수 없다.
- 응답의 네 식별자와 `reusedResources`는 `ai_registration_unit`의 `registered_restaurant_id`, `registered_creator_id`, `registered_video_id`, `registered_visit_id`, `reused_resources` 컬럼에서 읽는다. 재요청 시 같은 값을 그대로 재구성할 수 있어야 하므로 별도 감사 이력이 아니라 이 테이블이 응답의 소스다.
- 4종 등록의 원자 경계는 등록 단위 하나다. 중간 실패 시 이 등록 단위의 정식 저장은 0건이고, 같은 작업의 다른 등록 단위는 변경하지 않는다.
- 외부 조회·검증은 DB 트랜잭션 밖에서 수행하고, 검증 통과 후 4종을 하나의 트랜잭션으로 저장한다.
- 재요청과 상태별 허용 범위는 위 표를 따른다.
- 같은 등록 단위에 대한 동시 요청은 `409 AIEXTRACT_CONCURRENT_REQUEST_CONFLICT`로 처리한다. 이 코드는 동시성 충돌이며, 같은 맛집·방문 관계가 이미 존재한다는 업무 중복을 뜻하는 `blockReason`의 `DUPLICATE_CONFLICT`와 다르다. 전자는 잠시 후 재시도하면 되고, 후자는 이미 등록된 자원을 확인해야 한다.

#### 예외 전환 응답 `422 AIEXTRACT_VALIDATION_CONFLICT`

`BR-AIEXTRACT-011`이 정의한 예외 사유에 해당하면 정식 저장 0건으로 끝내고 필요한 보충 입력만 알린다. 그 밖의 사유로 관리자 입력을 요구하지 않는다.

```json
{
  "code": "AIEXTRACT_VALIDATION_CONFLICT",
  "blockReason": "PLACE_AMBIGUOUS",
  "recoveryPaths": ["SUPPLEMENT", "MANUAL_REGISTRATION"],
  "requiredSupplements": ["kakaoPlaceUrl"],
  "traceId": "opaque-trace-id"
}
```

`AIEXTRACT_VALIDATION_CONFLICT`는 "검증 충돌" 하나를 뜻하며 복구 가능 여부를 구분하지 않는다. 클라이언트는 `recoveryPaths`로 화면에 노출할 동작을 결정한다. **배열이며 첫 원소가 주 경로다.** 한 차단 사유가 여러 복구 동작을 허용하기 때문에 단일 값으로 두지 않는다.

| 값 | 의미 | 클라이언트 동작 |
|---|---|---|
| `SUPPLEMENT` | `CONFIRM` 보충 입력으로 복구 | 보조 입력란. `requiredSupplements`가 필요한 필드를 지정한다 |
| `REEXTRACT` | 보완 텍스트 재추출로 복구 | 재추출 버튼. 값 입력란을 두지 않는다 |
| `MANUAL_REGISTRATION` | 기존 수동 등록으로 전환 | 수동 등록 화면 연결 |
| `EXISTING_RESOURCE` | 이미 등록된 자원이 있어 새 등록이 불필요 | 기존 맛집·방문 관계로 이동 |
| `RETRY` | 일시 오류. 같은 요청 재실행으로 복구 | 재실행 버튼 |

차단 사유와 거절 상황별 매핑을 고정한다. 화면은 이 배열에 없는 동작을 노출하지 않는다. **`DISCARD`는 이 배열과 무관한 예외다.** `decision` 표(3.5절)가 정의한 대로 `DISCARD`는 `recoveryPaths` 내용과 관계없이 `AUTO_BLOCKED`의 일곱 `blockReason` 모두에서 공통으로 허용하는 종결 동작이며, 화면은 배열이 안내하는 동작에 `DISCARD`를 항상 추가로 노출한다. 등록 단위 0개 거절·`AUTO_REJECTED` 거절·롤백 완료·폐기 완료 네 거절 상황은 `AUTO_BLOCKED`가 아니므로 `DISCARD` 대상이 아니다.

| 상황 | `recoveryPaths` | `requiredSupplements` |
|---|---|---|
| `PLACE_NOT_FOUND` | `["SUPPLEMENT", "MANUAL_REGISTRATION"]` | `["kakaoPlaceUrl"]` |
| `PLACE_AMBIGUOUS` | `["SUPPLEMENT", "MANUAL_REGISTRATION"]` | `["kakaoPlaceUrl"]` |
| `CATEGORY_UNRESOLVED` | `["SUPPLEMENT", "MANUAL_REGISTRATION"]` | `["foodCategoryId"]` |
| `MISSING_REQUIRED_FIELD` | `["REEXTRACT", "MANUAL_REGISTRATION"]` | `[]` |
| `VISIT_EVIDENCE_REQUIRED` | `["REEXTRACT", "MANUAL_REGISTRATION"]` | `[]` |
| `DUPLICATE_CONFLICT` | `["EXISTING_RESOURCE"]` | `[]` |
| `EXTERNAL_SERVICE_ERROR` | `["RETRY", "MANUAL_REGISTRATION"]` | `[]` |
| 등록 단위 0개 거절 | `[]` | `[]` |
| `AUTO_REJECTED` 거절 | `[]` | `[]` |
| 롤백 완료 `MANUAL_OVERRIDE` 거절 | `[]` | `[]` |
| 폐기 완료 `MANUAL_OVERRIDE` 거절 | `[]` | `[]` |

- 빈 배열은 이 경로로 복구할 수 없다는 뜻이다. 화면은 거절 사유만 표시하고 새 작업 재추출을 안내한다.
- `requiredSupplements`는 `recoveryPaths`에 `SUPPLEMENT`가 있을 때만 비어 있지 않다.
- `MANUAL_REGISTRATION`은 어느 예외에서든 관리자가 기존 수동 등록으로 우회할 수 있다는 뜻이며, 그 경로는 이 API가 아니라 기존 관리자 등록 API를 쓴다.
- `DISCARD`는 `recoveryPaths` 배열에 나타나지 않지만, 3.5절 `decision` 표가 정의한 대로 `AUTO_BLOCKED` 등록 단위 전체에서 공통으로 허용하는 종결 동작이다. 화면은 배열이 안내하는 동작과 별개로 `DISCARD`를 항상 함께 노출한다.

보충 입력은 **판정 선택만 받고 후보 값 생성은 받지 않는다.** Kakao 장소와 카테고리는 관리자가 외부 기준정보 중 하나를 고르는 것이라 AI 근거를 대체하지 않지만, 맛집명·주소·방문 근거는 관리자가 값을 만들어 넣는 순간 영상 근거 없는 데이터가 등록된다. 그래서 후자는 보충 입력 대상이 아니고 재추출 또는 기존 수동 등록으로 처리한다.

| `blockReason` | 의미 | 복구 경로 | `requiredSupplements` |
|---|---|---|---|
| `PLACE_NOT_FOUND` | 조건을 만족하는 Kakao 장소가 없다 | `CONFIRM` 보충 입력 | `kakaoPlaceUrl` |
| `PLACE_AMBIGUOUS` | 조건을 만족하는 장소가 둘 이상이다 | `CONFIRM` 보충 입력 | `kakaoPlaceUrl` |
| `CATEGORY_UNRESOLVED` | 카테고리 근거를 찾지 못했다 | `CONFIRM` 보충 입력 | `foodCategoryId` |
| `MISSING_REQUIRED_FIELD` | 등록 단위에 맛집명·주소 등 필수 후보가 없다 | 보완 텍스트 재추출 또는 기존 수동 등록 | 빈 배열 |
| `VISIT_EVIDENCE_REQUIRED` | 방문 근거가 `UNKNOWN`이거나 영상 `TIMESTAMP`가 아니다 | 재추출 또는 기존 수동 등록. 방문 근거는 보완 텍스트 주장으로 확정하지 않는다 | 빈 배열 |
| `DUPLICATE_CONFLICT` | 같은 맛집·방문 관계가 이미 존재한다 | `EXISTING_RESOURCE`. 이미 등록된 자원으로 이동해 확인한다 | 빈 배열 |
| `EXTERNAL_SERVICE_ERROR` | Kakao·YouTube 조회 실패·시간 초과 | 등록 재실행 또는 기존 수동 등록 | 빈 배열 |

이 표의 복구 경로 열은 3.6절 `recoveryPaths` 배열과 같은 의미다. `MISSING_REQUIRED_FIELD`·`VISIT_EVIDENCE_REQUIRED`·`EXTERNAL_SERVICE_ERROR`는 재추출·재실행 뒤에도 관리자가 기존 수동 등록으로 우회할 수 있다. `DUPLICATE_CONFLICT`는 `EXISTING_RESOURCE`만 가지며 재추출·재실행·수동 등록 우회 경로가 없다.

- `requiredSupplements`가 빈 배열이면 `CONFIRM`으로 복구할 수 없다는 뜻이다. 이 상태에서 보낸 `CONFIRM`은 `422 AIEXTRACT_VALIDATION_CONFLICT`로 거절하고 정식 저장은 0건이며, 응답의 복구 경로를 안내한다.
- 부족한 필드 이름은 작업 상세의 `missingFields`로 확인한다. 이 응답은 관리자가 채워 넣을 대상이 아니므로 `requiredSupplements`에 싣지 않는다.
- 보충 입력은 기존 `POST /api/admin/ai/video-extractions/{jobId}/review`의 `CONFIRM`으로 제출한다. 이 API는 보충 입력을 받지 않는다.
- 관리자가 제출한 보충 입력도 기존 Kakao·YouTube·Visit 검증을 우회하지 않는다.

### 3.7 `GET /api/admin/ai/youtube-channel-watches` 채널 감시 목록 조회

- `Authorization: Bearer <access-token>`과 `ADMIN` 권한을 요구한다.
- `page`는 1-base이며 `size`는 `10`, `20`, `50`만 허용한다. 기본값은 각각 `1`, `20`이다.
- 공개·외부 이용 가능 상태의 YouTube 채널이 연결된 Creator와 기존 감시 행을 함께 반환한다. 따라서 Creator가 비공개·삭제·외부 이용 불가로 바뀐 기존 감시 행도 목록에서 확인하고 중지할 수 있다.
- 목록 조회는 외부 YouTube API를 호출하지 않고 Creator와 감시 저장소의 현재 상태만 조합한다.

```json
{
  "items": [
    {
      "creatorId": "opaque-creator-id",
      "channelName": "맛집 채널",
      "publiclyVisible": true,
      "externallyAvailable": true,
      "status": {
        "enabled": false,
        "subscriptionStatus": "INACTIVE",
        "lastNotificationAt": null,
        "lastRenewedAt": null,
        "lastErrorCategory": null,
        "lastErrorAt": null
      }
    }
  ],
  "page": { "number": 1, "size": 20, "totalElements": 1, "totalPages": 1, "hasNext": false }
}
```

### 3.8 `GET /api/admin/ai/youtube-channel-watches/{creatorId}` 채널 감시 상태 조회

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

### 3.9 `PUT /api/admin/ai/youtube-channel-watches/{creatorId}` 채널 감시 설정

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
| `AIEXTRACT_DUPLICATE_CONFLICT` | 409 | 3.1절 신규 영상 접수 시 동일 URL이 기존 정식 등록 상태와 충돌해 자동 병합할 수 없음. 등록 단위의 업무 중복은 `blockReason`의 `DUPLICATE_CONFLICT`, 등록 단위 동시 요청은 `AIEXTRACT_CONCURRENT_REQUEST_CONFLICT`를 쓰며 이 코드와 다르다 |
| `AIEXTRACT_JOB_NOT_FOUND` | 404 | 작업 ID 없음 또는 접근 불가 |
| `AIEXTRACT_UNIT_NOT_FOUND` | 404 | 작업은 유효하지만 지정한 `unitId`가 그 작업의 등록 단위가 아님 |
| `CREATOR_NOT_FOUND` | 404 | Creator 없음·비공개·삭제·외부 이용 불가 또는 감시 활성화 대상이 아님 |
| `AIEXTRACT_RETRY_BLOCKED` | 409 | 상태상 재시도 불가 |
| `AIEXTRACT_PROVIDER_BLOCKED` | 429 | Gemini Free Tier quota 소진·결제 연결 요구·무료 정책 미검증 |
| `AIEXTRACT_WEBHOOK_REJECTED` | 403 | 구독 채널·검증 Token 불일치 |
| `AIEXTRACT_WEBHOOK_SIGNATURE_INVALID` | 403 | Webhook HMAC 비밀값·헤더 누락 또는 서명 불일치 |
| `AIEXTRACT_VALIDATION_CONFLICT` | 422 | 검증 충돌. 복구 가능 여부는 코드가 아니라 응답의 `recoveryPaths`로 구분한다 |
| `AIEXTRACT_UNIT_ID_REQUIRED` | 400 | 등록 단위가 둘 이상인 작업의 `review` 요청에 `unitId`가 없음 |
| `AIEXTRACT_CONCURRENT_REQUEST_CONFLICT` | 409 | 같은 등록 단위에 대한 동시 요청. 업무 중복을 뜻하는 `blockReason`의 `DUPLICATE_CONFLICT`와 다름 |

모든 오류는 서버 생성 `traceId`를 포함하며 입력 원문·Gemini 응답·비밀정보를 메시지에 포함하지 않는다.

## 6. API 완료 조건

- [ ] 관리자 신규 추가·목록·상세·재시도·자동 결과 조회·예외 보정·채널 감시 API의 권한·상태·오류 계약이 승인된다.
- [ ] Webhook 확인·알림의 중복·잘못된 채널·잘못된 Token·대형 Payload·AI 호출 격리가 검증된다.
- [ ] `WEBHOOK`·`ADMIN` 요청이 하나의 멱등 작업으로 수렴하고, 동시 검수에서 정식 저장 중복이 0건이다.
- [ ] Gemini 접근 실패·부분 추출·quota 차단과 관리자 텍스트 fallback의 응답이 계약 테스트로 고정된다.
- [ ] 태그 후보의 허용 코드·근거·자동 판단과 사후 보정·`VisitTag` 연결이 계약 테스트로 고정된다.
- [ ] 다장소 영상의 `registrationUnits` 응답과 단위별 `reviewStatus`·`blockReason`·`placeDecision`·`categoryDecision`이 계약 테스트로 고정된다.
- [ ] 등록 단위가 둘 이상인 작업의 `review` 요청에서 `unitId` 누락 거절과 단위별 `ROLLBACK` 격리가 검증된다.
- [ ] 등록 단위 일괄 등록의 4종 결과·자원 재사용·재요청 멱등·동시 요청 충돌과 예외 전환 `blockReason`·`requiredSupplements`가 계약 테스트로 고정된다.
- [ ] 일괄 등록 중간 실패에서 해당 등록 단위 정식 저장 0건과 외부 호출 중 트랜잭션 미개방이 검증된다.
- [ ] [데이터 계약](../../data/third-expansion-ai-video-data-contract.md), API 추적표, 후보·Worker 테스트와 연결된다.

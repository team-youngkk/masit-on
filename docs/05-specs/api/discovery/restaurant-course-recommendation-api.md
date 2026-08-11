---
id: API-DISCOVERY-COURSE-001
title: 맛집 코스 추천 API
status: Accepted
related_prd:
  - PRD-DISCOVERY-006
  - PRD-DISCOVERY-001
workstream: WS-16
owner: 이우람
reviewers:
  - 양성훈
related_requirements:
  - FR-COURSE-001
  - FR-COURSE-002
  - FR-COURSE-003
related_business_rules:
  - BR-COURSE-001
  - BR-COURSE-002
  - BR-COURSE-003
  - BR-COURSE-004
related_nfr:
  - NFR-PRIVACY-006
  - NFR-COST-001
  - NFR-EXTERNAL-005
  - NFR-PERFORMANCE-007
  - NFR-AVAILABILITY-003
  - NFR-OBSERVABILITY-005
  - NFR-TEST-006
related_documents:
  - ../../../04-product/prd/discovery/restaurant-course-recommendation.md
  - ../../../04-product/prd/discovery/restaurant-discovery.md
  - ../../../01-requirements/functional-requirements.md
  - ../../../01-requirements/business-rules.md
  - ../../../01-requirements/non-functional-requirements.md
  - ../../../01-requirements/requirements-review.md
  - ../../../02-analysis/third-expansion-workstreams.md
  - ../../../02-analysis/third-expansion-domain-boundaries.md
  - ../../../07-adr/integration/route-001-kakao-mobility-course-routing.md
  - ../../../08-planning/third-expansion-evaluation-strategy.md
  - ../common/response-contract.md
  - ../common/error-contract.md
  - ../common/identifier-contract.md
---

# 맛집 코스 추천 API

## 1. 목적과 경계

사용자가 직접 선택한 공개 맛집 2~5개의 좌표를 이용해 자동차 이동 순서와 경로 정보를 반환한다. 시스템이 맛집을 고르거나 현재 위치·실시간 교통·영업 여부를 판단하지 않는다.

- 첫 번째 입력 맛집은 출발점으로 고정한다.
- 경로 결과는 요청 시점 응답이며 초기에는 저장·공유·조회 ID를 제공하지 않는다.
- 외부 경로 계산이 완전하지 않으면 거리·시간을 추정하거나 정상 코스로 표시하지 않는다.
- Kakao Mobility 자동차 길찾기 `/v1/directions`와 REST API Key를 사용한다. quota 연결 전에는 호출하지 않으며, 결과 TTL은 5분이고 서버 캐시는 사용하지 않는다.

## 2. 접근 권한

인증 없이 공개 접근한다. 입력은 공개 맛집 식별자만 사용하며 현재 위치·회원 식별자·선택 이력은 받거나 저장하지 않는다.

## 3. API 요약

| API ID | Method | Path | 설명 |
|---|---|---|---|
| API-DISCOVERY-COURSE-001 | POST | `/api/restaurants/course-routes` | 선택 맛집의 자동차 순서·경로 조회 |

이 API는 저장된 `Course` 자원을 생성하지 않는다. 동일 요청을 다시 계산하려면 클라이언트가 같은 입력으로 새 POST 요청을 보낸다.

## 4. API-DISCOVERY-COURSE-001 코스 경로 조회

- Method: `POST`
- Path: `/api/restaurants/course-routes`
- 인증: 없음
- 권한: 일반 공개 조회
- 성공 상태: `200 OK`
- 외부 호출: 정상 요청당 Kakao Mobility 경로 계산 최대 1회

### Request Body

```json
{
  "restaurantIds": [
    "restaurant-id-1",
    "restaurant-id-2",
    "restaurant-id-3"
  ]
}
```

| 필드 | 타입 | 필수 | 설명 | 검증 규칙 |
|---|---|---:|---|---|
| `restaurantIds` | array[string] | 예 | 사용자가 선택한 맛집 식별자 순서 | 2~5개, 중복 불가 |
| `restaurantIds[]` | string | 예 | 불투명 맛집 식별자 | 빈 값·배열 중첩·잘못된 형식 불가 |

첫 번째 식별자는 출발점으로 고정한다. 별도 출발지·도착지·현재 위치·이동 수단 필드는 받지 않는다.

### Request Validation

| 조건 | 결과 |
|---|---|
| 2개 미만 또는 5개 초과 | `400 INVALID_COURSE_SIZE` |
| 동일 식별자 반복 | `400 DUPLICATE_RESTAURANT_IN_COURSE` |
| 존재하지 않는 맛집 | `404 RESTAURANT_NOT_FOUND` |
| 비공개·삭제·비활성 맛집 포함 | `422 RESTAURANT_NOT_PUBLIC` |
| 위도·경도 중 하나라도 없거나 범위 오류 | `422 RESTAURANT_COORDINATE_REQUIRED` |

좌표가 없는 맛집을 조용히 제외하지 않고 전체 요청을 거부한다. 입력 검증 오류는 공통 오류 계약의 `errors`로 표현하며, 기존 자원 참조가 아니므로 `resource`를 사용하지 않는다.

### Success Response

```json
{
  "status": "SUCCEEDED",
  "restaurants": [
    {
      "sequence": 1,
      "restaurantId": "restaurant-id-1",
      "name": "출발 맛집",
      "role": "START"
    },
    {
      "sequence": 2,
      "restaurantId": "restaurant-id-3",
      "name": "두 번째 맛집",
      "role": "WAYPOINT"
    },
    {
      "sequence": 3,
      "restaurantId": "restaurant-id-2",
      "name": "마지막 맛집",
      "role": "DESTINATION"
    }
  ],
  "segments": [
    {
      "fromRestaurantId": "restaurant-id-1",
      "toRestaurantId": "restaurant-id-3",
      "distanceMeters": 4200,
      "durationSeconds": 780
    },
    {
      "fromRestaurantId": "restaurant-id-3",
      "toRestaurantId": "restaurant-id-2",
      "distanceMeters": 3100,
      "durationSeconds": 600
    }
  ],
  "totalDistanceMeters": 7300,
  "totalDurationSeconds": 1380,
  "generatedAt": "2026-08-10T12:00:00+09:00",
  "expiresAt": "2026-08-10T12:05:00+09:00"
}
```

`restaurants`의 첫 항목은 항상 입력 배열의 첫 식별자다. 나머지는 좌표 직선거리 최근접 이웃 순서로 계산하고 동률은 Restaurant ID 오름차순으로 정렬한다.

### Response Field Definitions

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `status` | enum | 예 | 성공 시 `SUCCEEDED` |
| `restaurants` | array | 예 | 제안된 방문 순서와 공개 표시 정보 |
| `restaurants[].sequence` | integer | 예 | 1부터 시작하는 방문 순서 |
| `restaurants[].restaurantId` | string | 예 | 맛집 식별자 |
| `restaurants[].name` | string | 예 | 공개 맛집 이름 |
| `restaurants[].role` | enum | 예 | `START`, `WAYPOINT`, `DESTINATION` |
| `segments` | array | 예 | 인접 방문지 사이 실제 경로 구간 |
| `segments[].fromRestaurantId` | string | 예 | 출발 맛집 식별자 |
| `segments[].toRestaurantId` | string | 예 | 도착 맛집 식별자 |
| `segments[].distanceMeters` | integer | 예 | 외부 제공 실제 경로 거리 |
| `segments[].durationSeconds` | integer | 예 | 외부 제공 예상 소요 시간 |
| `totalDistanceMeters` | integer | 예 | 구간 실제 거리 합계 |
| `totalDurationSeconds` | integer | 예 | 구간 예상 시간 합계 |
| `generatedAt` | date-time | 예 | 응답 생성 시각 |
| `expiresAt` | date-time | 예 | 생성 시각 5분 뒤의 재조회 만료 시각 |

응답에는 좌표, Kakao 원문 응답, API Key, 현재 위치와 개인 식별자를 포함하지 않는다. 모든 식별자는 구조를 해석하지 않는 문자열이다.

## 5. Distance and Expiry Rules

- `totalDistanceMeters`가 30,000을 초과하면 정상 코스를 반환하지 않고 `422 COURSE_DISTANCE_LIMIT_EXCEEDED`를 반환한다.
- 경로 결과는 영구 저장하지 않는다. `expiresAt` 이후 클라이언트는 같은 POST를 다시 요청한다.
- 만료된 결과를 정상 결과처럼 재사용하거나 서버가 이전 거리·시간을 추정해 반환하지 않는다.
- 초기에는 캐시와 GET `/api/restaurants/course-routes/{courseId}`를 제공하지 않는다.

## 6. External Failure and Error Cases

| 오류 코드 | HTTP 상태 | 발생 조건 |
|---|---:|---|
| `INVALID_REQUEST` | 400 | JSON 구조를 해석할 수 없음 |
| `INVALID_COURSE_SIZE` | 400 | 선택 맛집 수가 2~5 범위를 벗어남 |
| `DUPLICATE_RESTAURANT_IN_COURSE` | 400 | 동일 맛집 식별자 반복 |
| `INVALID_IDENTIFIER` | 400 | 맛집 식별자 형식 오류 |
| `RESTAURANT_NOT_FOUND` | 404 | 선택한 맛집 없음 |
| `RESTAURANT_NOT_PUBLIC` | 422 | 비공개·삭제·비활성 맛집 포함 |
| `RESTAURANT_COORDINATE_REQUIRED` | 422 | 좌표 누락 또는 좌표 범위 오류 |
| `COURSE_DISTANCE_LIMIT_EXCEEDED` | 422 | 실제 경로 합계 30km 초과 |
| `COURSE_ROUTE_PARTIAL_FAILURE` | 502 | 일부 구간만 계산됨. 정상 거리·시간을 반환하지 않음 |
| `COURSE_ROUTE_PROVIDER_UNAVAILABLE` | 502 | timeout·provider의 429·5xx·quota 차단 등 외부 경로 실패 |
| `COURSE_ROUTE_RATE_LIMITED` | 429 | 서비스 자체 요청 제한 초과 |
| `INTERNAL_SERVER_ERROR` | 500 | 예상하지 못한 내부 오류 |

외부·부분 실패의 오류 본문은 선택 맛집의 최소 표시 정보, 입력 순서, 실패 범주와 재시도 안내만 제공한다. 계산되지 않은 구간의 거리·시간·전체 합계는 제공하지 않는다.

이때 공통 오류 봉투의 `details`를 사용하며 `resource`는 생략한다.

```json
{
  "code": "COURSE_ROUTE_PARTIAL_FAILURE",
  "message": "일부 구간의 경로 계산에 실패했습니다.",
  "errors": [],
  "details": {
    "selectedRestaurants": [
      {
        "restaurantId": "restaurant-id-1",
        "name": "출발 맛집",
        "inputOrder": 1
      },
      {
        "restaurantId": "restaurant-id-2",
        "name": "도착 맛집",
        "inputOrder": 2
      }
    ],
    "failureCategory": "PARTIAL",
    "retryGuidance": {
      "action": "RESELECT_OR_RETRY",
      "message": "선택 맛집을 바꾸거나 잠시 후 다시 조회해 주세요."
    }
  },
  "traceId": "01K123ABC456DEF789GHJKMNPQ"
}
```

`details.selectedRestaurants`는 요청 배열 순서의 최소 표시 정보만 담는다. `failureCategory`의 공개 값은 `PARTIAL`, `PROVIDER_UNAVAILABLE`, `SERVICE_RATE_LIMIT`이며, Adapter 내부의 `TIMEOUT`, `SCHEMA`, `PROVIDER_BLOCKED` 같은 세부 범주는 외부에 노출하지 않고 `PROVIDER_UNAVAILABLE`로 통합한다.

## 7. 비용·보안·운영 계약

- 요청당 외부 Mobility 호출은 최대 1회다.
- Free Tier quota 초과 전 hard stop하며 유료 호출은 항상 금지한다. 무료 quota·계약 확인이 없으면 호출하지 않는다.
- 연결 1초·응답 4초·전체 5초 timeout과 재시도 0회를 적용한다. 429·5xx·quota 오류는 실패 상태로 반환한다.
- 외부 장애는 코스 API에 격리하고 기존 목록·상세·자연어 탐색에 전파하지 않는다.
- 로그에는 좌표 원문, 외부 응답, API Key, 현재 위치와 회원 식별자를 남기지 않는다.
- 내부 입력 검증·응답 조합 p95 500ms, 외부 호출 포함 정상 또는 명시적 실패 5초 이내를 목표로 한다.

## 8. API 완료 조건

- [ ] FR-COURSE-001~003과 BR-COURSE-001~004의 정상·개수·좌표·출발점·30km·만료·부분 실패 계약 테스트가 있다.
- [ ] 정상 요청당 외부 호출 최대 1회와 quota hard stop을 검증한다.
- [ ] 위치·선택 이력·경로 결과 저장 0건과 외부 전송 필드 최소화를 검증한다.
- [ ] Kakao Mobility 운영 계정 인증·quota 연결과 `/v1/directions` WireMock 계약을 검증한다.
- [ ] 50명·20 RPS와 200명·80 RPS에서 기존 탐색 격리와 응답 시간을 검증한다.

---

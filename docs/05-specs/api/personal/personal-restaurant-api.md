---
id: API-PERSONAL-001
title: 개인 맛집 관리 API
status: draft
related_prd:
  - PRD-PERSONAL-001
  - PRD-ACCOUNT-001
workstream: WS-06
owner: 박진영
reviewers:
  - 김인안
related_requirements:
  - FR-FAVORITE-001
  - FR-FAVORITE-002
  - FR-FAVORITE-003
  - FR-FAVORITE-004
  - FR-RECENT-001
  - FR-RECENT-002
  - FR-RECENT-003
  - FR-MEMBER-004
related_business_rules:
  - BR-FAVORITE-001
  - BR-FAVORITE-002
  - BR-FAVORITE-003
  - BR-FAVORITE-004
  - BR-RECENT-001
  - BR-RECENT-002
  - BR-RECENT-003
  - BR-RECENT-004
  - BR-RECENT-005
  - BR-MEMBER-004
related_nfr:
  - NFR-PERFORMANCE-004
  - NFR-PERFORMANCE-005
  - NFR-SECURITY-004
  - NFR-RELIABILITY-001
  - NFR-RELIABILITY-003
  - NFR-COMPATIBILITY-001
  - NFR-TEST-004
  - NFR-PRIVACY-003
  - NFR-PRIVACY-004
related_documents:
  - ../../../04-product/prd/personal/personal-restaurant-management.md
  - ../../../04-product/prd/account/member-authentication.md
  - ../detail/restaurant-detail-api.md
  - ../discovery/restaurant-discovery-api.md
  - ../common/identifier-contract.md
  - ../common/response-contract.md
  - ../common/error-contract.md
  - ../common/pagination-contract.md
  - ../common/date-time-contract.md
  - ../../data/lifecycle-rules.md
  - ../../../01-requirements/functional-requirements.md
  - ../../../01-requirements/business-rules.md
  - ../../../02-analysis/first-expansion-workstreams.md
---

# 개인 맛집 관리 API

## 1. 문서 목적

로그인 회원의 찜 추가·해제·상태 확인, 찜 목록 조회, 최근 본 맛집 목록 조회와 최근 기록 개별 삭제에 대한 외부 HTTP 계약을 정의한다. 최근 기록 생성은 공개 맛집 상세 조회 성공 뒤 서버 내부에서만 수행하며 별도 공개 쓰기 API를 만들지 않는다.

## 2. 적용 범위

`/api/me` 아래의 본인 고정 개인화 경로만 포함한다. 소셜 로그인, 다른 회원 식별자 입력, 비로그인 로컬 찜·최근 기록, 배치 찜 상태 조회, 전체 최근 기록 일괄 삭제는 제외한다.

## 3. 접근 권한

모든 `/api/me/**` 경로는 일반 회원 Access Token을 요구한다. 현재 회원은 인증된 Principal로만 결정하며 `memberId`, `userId` 같은 다른 회원 식별자 입력을 받지 않는다.

- 인증 정보가 없거나 만료·변조·폐기·잘못된 audience의 Token이면 `401 AUTHENTICATION_REQUIRED`
- 관리자 JWT처럼 회원 audience가 아닌 인증 정보는 `401 AUTHENTICATION_REQUIRED`
- 오류 응답 본문은 [공통 오류 계약](../common/error-contract.md)을 따르며 항상 `traceId`를 포함
- 모든 성공·오류 응답에 `Cache-Control: private, no-store`를 적용하고 CDN·공유 캐시 저장을 금지

## 4. API 요약

| API ID | Method | Path | 설명 |
|---|---|---|---|
| [API-PERSONAL-001](personal-restaurant-api.md#api-personal-001-맛집-찜-추가) | PUT | `/api/me/favorites/{restaurantId}` | 현재 회원의 맛집 찜 추가 |
| [API-PERSONAL-002](personal-restaurant-api.md#api-personal-002-맛집-찜-해제) | DELETE | `/api/me/favorites/{restaurantId}` | 현재 회원의 맛집 찜 해제 |
| [API-PERSONAL-003](personal-restaurant-api.md#api-personal-003-맛집별-현재-회원-찜-상태-조회) | GET | `/api/me/favorites/{restaurantId}` | 현재 회원의 맛집 찜 상태 조회 |
| [API-PERSONAL-004](personal-restaurant-api.md#api-personal-004-찜-목록-조회) | GET | `/api/me/favorites` | 현재 회원의 찜 목록 조회 |
| [API-PERSONAL-005](personal-restaurant-api.md#api-personal-005-최근-본-맛집-목록-조회) | GET | `/api/me/recent-restaurants` | 현재 회원의 최근 본 맛집 목록 조회 |
| [API-PERSONAL-006](personal-restaurant-api.md#api-personal-006-최근-본-맛집-개별-삭제) | DELETE | `/api/me/recent-restaurants/{restaurantId}` | 현재 회원의 최근 기록 한 건 삭제 |

찜 상태는 `GET /api/me/favorites/{restaurantId}`를 선택한다. 현재 범위에서 가장 작은 명시적 본인 API이며, 회원별 상태를 기존 공개 맛집 목록·상세 응답에 섞지 않아 공개 캐시와 인증 경계를 유지한다. 목록 화면의 대량 상태 조합을 위한 배치 API는 현재 범위에 추가하지 않는다.

## 5. 최근 기록 생성 연동 규칙

최근 본 맛집은 별도 공개 쓰기 API 없이 [맛집 상세 API](../detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회)의 `200 OK` 성공 뒤 서버 내부 부수효과로 기록한다.

- 로그인 회원이 `GET /api/restaurants/{restaurantId}`를 성공하면 현재 회원과 맛집의 최근 기록을 upsert한다.
- 같은 회원·맛집 조합은 중복 생성하지 않고 `lastViewedAt`만 갱신한다.
- 기록 저장 실패는 공개 맛집 상세 응답을 실패시키지 않는다.
- 비로그인 조회, `404`, `500`, 비공개·삭제 맛집 조회는 기록하지 않는다.

## 6. 맛집 찜 추가

### API-PERSONAL-001 맛집 찜 추가

- Method: `PUT`
- Path: `/api/me/favorites/{restaurantId}`
- 인증: 회원 Access Token
- 권한: 현재 로그인 회원 본인
- 관련 PRD: [PRD-PERSONAL-001](../../../04-product/prd/personal/personal-restaurant-management.md)
- 관련 요구사항: [FR-FAVORITE-001](../../../01-requirements/functional-requirements.md#fr-favorite-001-맛집-찜-추가)
- 설명: 현재 회원이 공개 맛집을 찜 상태로 만든다.

#### Path Parameters

| 이름 | 타입 | 필수 | 설명 | 검증 규칙 |
|---|---|---:|---|---|
| `restaurantId` | Identifier | 예 | 맛집 식별자 | 공통 식별자 계약 적용 |

#### Request Rules

- 요청 본문은 없다.
- 현재 회원과 `restaurantId`의 논리 찜은 최대 한 건만 존재한다.
- 존재하지 않거나 비공개·삭제된 맛집에는 새 찜을 추가할 수 없다.
- 중복·동시 요청도 최종 상태를 찜으로 수렴시킨다.

#### Success Response

- 상태: `200 OK`

```json
{
  "restaurantId": "restaurant-id",
  "favorited": true
}
```

#### Response Field Definitions

| 필드 | 타입 | 필수 | 설명 | 빈 값 규칙 |
|---|---|---:|---|---|
| `restaurantId` | Identifier | 예 | 요청한 맛집 식별자 | `null`·빈 값 불가 |
| `favorited` | boolean | 예 | 현재 회원의 최종 찜 상태 | 이 API에서는 항상 `true` |

#### Error Cases

| 오류 코드 | HTTP 상태 | 발생 조건 |
|---|---:|---|
| `INVALID_IDENTIFIER` | 400 | `restaurantId` 형식 오류 |
| `AUTHENTICATION_REQUIRED` | 401 | 인증 없음·무효 또는 회원 audience가 아닌 Token |
| `RESTAURANT_NOT_FOUND` | 404 | 맛집 없음, 비공개 또는 삭제 |
| `INTERNAL_SERVER_ERROR` | 500 | 예상하지 못한 내부 오류 |

## 7. 맛집 찜 해제

### API-PERSONAL-002 맛집 찜 해제

- Method: `DELETE`
- Path: `/api/me/favorites/{restaurantId}`
- 인증: 회원 Access Token
- 권한: 현재 로그인 회원 본인
- 관련 PRD: [PRD-PERSONAL-001](../../../04-product/prd/personal/personal-restaurant-management.md)
- 관련 요구사항: [FR-FAVORITE-002](../../../01-requirements/functional-requirements.md#fr-favorite-002-맛집-찜-해제)
- 설명: 현재 회원의 찜 관계를 해제 상태로 만든다.

#### Path Parameters

| 이름 | 타입 | 필수 | 설명 | 검증 규칙 |
|---|---|---:|---|---|
| `restaurantId` | Identifier | 예 | 맛집 식별자 | 공통 식별자 계약 적용 |

#### Request Rules

- 요청 본문은 없다.
- 현재 공개 여부와 무관하게 현재 회원과 `restaurantId`의 찜 관계만 정리한다.
- 이미 해제됐거나 관계가 없더라도 새 오류를 만들지 않고 미찜 상태를 유지한다.
- 다른 회원의 찜 관계 존재 여부는 응답으로 노출하지 않는다.

#### Success Response

- 상태: `200 OK`

```json
{
  "restaurantId": "restaurant-id",
  "favorited": false
}
```

#### Response Field Definitions

| 필드 | 타입 | 필수 | 설명 | 빈 값 규칙 |
|---|---|---:|---|---|
| `restaurantId` | Identifier | 예 | 요청한 맛집 식별자 | `null`·빈 값 불가 |
| `favorited` | boolean | 예 | 현재 회원의 최종 찜 상태 | 이 API에서는 항상 `false` |

#### Error Cases

| 오류 코드 | HTTP 상태 | 발생 조건 |
|---|---:|---|
| `INVALID_IDENTIFIER` | 400 | `restaurantId` 형식 오류 |
| `AUTHENTICATION_REQUIRED` | 401 | 인증 없음·무효 또는 회원 audience가 아닌 Token |
| `INTERNAL_SERVER_ERROR` | 500 | 예상하지 못한 내부 오류 |

## 8. 맛집별 현재 회원 찜 상태 조회

### API-PERSONAL-003 맛집별 현재 회원 찜 상태 조회

- Method: `GET`
- Path: `/api/me/favorites/{restaurantId}`
- 인증: 회원 Access Token
- 권한: 현재 로그인 회원 본인
- 관련 PRD: [PRD-PERSONAL-001](../../../04-product/prd/personal/personal-restaurant-management.md)
- 관련 요구사항: [FR-FAVORITE-003](../../../01-requirements/functional-requirements.md#fr-favorite-003-맛집별-현재-회원-찜-상태-확인)
- 설명: 공개 맛집 하나에 대한 현재 회원의 찜 여부를 반환한다.

#### Path Parameters

| 이름 | 타입 | 필수 | 설명 | 검증 규칙 |
|---|---|---:|---|---|
| `restaurantId` | Identifier | 예 | 맛집 식별자 | 공통 식별자 계약 적용 |

#### Request Rules

- 상태 조회 대상은 현재 공개 조회 가능한 맛집만 허용한다.
- 현재 회원의 관계가 있으면 `true`, 없으면 `false`다.
- 개인화 저장소 장애 시 다른 회원 상태를 추정해 반환하지 않고 요청 자체를 실패시킨다.

#### Success Response

- 상태: `200 OK`

```json
{
  "restaurantId": "restaurant-id",
  "favorited": false
}
```

#### Response Field Definitions

| 필드 | 타입 | 필수 | 설명 | 빈 값 규칙 |
|---|---|---:|---|---|
| `restaurantId` | Identifier | 예 | 요청한 맛집 식별자 | `null`·빈 값 불가 |
| `favorited` | boolean | 예 | 현재 회원의 찜 여부 | 생략 불가 |

#### Error Cases

| 오류 코드 | HTTP 상태 | 발생 조건 |
|---|---:|---|
| `INVALID_IDENTIFIER` | 400 | `restaurantId` 형식 오류 |
| `AUTHENTICATION_REQUIRED` | 401 | 인증 없음·무효 또는 회원 audience가 아닌 Token |
| `RESTAURANT_NOT_FOUND` | 404 | 맛집 없음, 비공개 또는 삭제 |
| `INTERNAL_SERVER_ERROR` | 500 | 개인화 상태 확인 실패 또는 예상하지 못한 내부 오류 |

## 9. 찜 목록 조회

### API-PERSONAL-004 찜 목록 조회

- Method: `GET`
- Path: `/api/me/favorites`
- 인증: 회원 Access Token
- 권한: 현재 로그인 회원 본인
- 관련 PRD: [PRD-PERSONAL-001](../../../04-product/prd/personal/personal-restaurant-management.md)
- 관련 요구사항: [FR-FAVORITE-004](../../../01-requirements/functional-requirements.md#fr-favorite-004-찜-목록-조회)
- 설명: 현재 회원의 공개 맛집 찜을 최신 찜 순으로 페이지 조회한다.

#### Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 설명 | 검증 규칙 |
|---|---|---:|---|---|---|
| `page` | integer | 아니요 | `1` | 요청 페이지 | 1 이상 |
| `size` | integer | 아니요 | `20` | 페이지 크기 | `10`, `20`, `50`만 허용 |

#### Request Rules

- 비공개 맛집의 찜은 보존하되 목록에서 숨긴다.
- 삭제된 맛집의 찜과 탈퇴 회원의 찜은 조회 전에 정리돼야 한다.
- 정렬은 `favoritedAt` 내림차순, 같은 시각은 `restaurant.id` 오름차순의 안정 정렬을 사용한다.
- 범위 밖의 유효 페이지는 `200`과 빈 목록이다.

#### Success Response

- 상태: `200 OK`

```json
{
  "items": [
    {
      "restaurant": {
        "id": "restaurant-id",
        "name": "맛집 이름",
        "district": "마포구",
        "category": "한식"
      },
      "favoritedAt": "2026-07-29T10:15:30Z"
    }
  ],
  "page": {
    "number": 1,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

#### Response Field Definitions

| 필드 | 타입 | 필수 | 설명 | 빈 값 규칙 |
|---|---|---:|---|---|
| `items` | array | 예 | 현재 회원의 공개 맛집 찜 목록 | 결과가 없으면 `[]` |
| `items[].restaurant` | object | 예 | 목록 표시용 맛집 요약 | `null` 불가 |
| `items[].restaurant.id` | Identifier | 예 | 맛집 식별자 | `null`·빈 값 불가 |
| `items[].restaurant.name` | string | 예 | 등록된 맛집 이름 | 빈 문자열 불가 |
| `items[].restaurant.district` | string | 예 | 서울특별시 자치구 | 빈 문자열 불가 |
| `items[].restaurant.category` | string | 예 | 대표 음식 카테고리 1개 | `null` 불가 |
| `items[].favoritedAt` | string | 예 | 현재 찜 생성 시각 | RFC 3339, `null` 불가 |
| `page` | object | 예 | 공통 페이지 정보 | 생략 불가 |

#### Error Cases

| 오류 코드 | HTTP 상태 | 발생 조건 |
|---|---:|---|
| `AUTHENTICATION_REQUIRED` | 401 | 인증 없음·무효 또는 회원 audience가 아닌 Token |
| `INVALID_FIELD_VALUE` | 400 | `page` 또는 `size` 검증 실패 |
| `INTERNAL_SERVER_ERROR` | 500 | 예상하지 못한 내부 오류 |

## 10. 최근 본 맛집 목록 조회

### API-PERSONAL-005 최근 본 맛집 목록 조회

- Method: `GET`
- Path: `/api/me/recent-restaurants`
- 인증: 회원 Access Token
- 권한: 현재 로그인 회원 본인
- 관련 PRD: [PRD-PERSONAL-001](../../../04-product/prd/personal/personal-restaurant-management.md)
- 관련 요구사항: [FR-RECENT-002](../../../01-requirements/functional-requirements.md#fr-recent-002-최근-본-맛집-목록-조회)
- 설명: 현재 회원의 유효한 최근 본 맛집을 최신 조회 순으로 페이지 조회한다.

#### Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 설명 | 검증 규칙 |
|---|---|---:|---|---|---|
| `page` | integer | 아니요 | `1` | 요청 페이지 | 1 이상 |
| `size` | integer | 아니요 | `20` | 페이지 크기 | `10`, `20`, `50`만 허용 |

#### Request Rules

- 목록 구성 전에 마지막 조회 후 30일이 지난 기록과 최신 50개를 초과한 기록을 정리한다.
- 비공개·삭제 맛집 기록은 목록에서 제외한다.
- 정렬은 `lastViewedAt` 내림차순, 같은 시각은 `restaurant.id` 오름차순의 안정 정렬을 사용한다.
- 범위 밖의 유효 페이지는 `200`과 빈 목록이다.

#### Success Response

- 상태: `200 OK`

```json
{
  "items": [
    {
      "restaurant": {
        "id": "restaurant-id",
        "name": "맛집 이름",
        "district": "마포구",
        "category": "한식"
      },
      "lastViewedAt": "2026-07-29T10:15:30Z"
    }
  ],
  "page": {
    "number": 1,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

#### Response Field Definitions

| 필드 | 타입 | 필수 | 설명 | 빈 값 규칙 |
|---|---|---:|---|---|
| `items` | array | 예 | 현재 회원의 유효한 최근 본 맛집 목록 | 결과가 없으면 `[]` |
| `items[].restaurant` | object | 예 | 목록 표시용 맛집 요약 | `null` 불가 |
| `items[].restaurant.id` | Identifier | 예 | 맛집 식별자 | `null`·빈 값 불가 |
| `items[].restaurant.name` | string | 예 | 등록된 맛집 이름 | 빈 문자열 불가 |
| `items[].restaurant.district` | string | 예 | 서울특별시 자치구 | 빈 문자열 불가 |
| `items[].restaurant.category` | string | 예 | 대표 음식 카테고리 1개 | `null` 불가 |
| `items[].lastViewedAt` | string | 예 | 현재 회원의 마지막 조회 시각 | RFC 3339, `null` 불가 |
| `page` | object | 예 | 공통 페이지 정보 | 생략 불가 |

#### Error Cases

| 오류 코드 | HTTP 상태 | 발생 조건 |
|---|---:|---|
| `AUTHENTICATION_REQUIRED` | 401 | 인증 없음·무효 또는 회원 audience가 아닌 Token |
| `INVALID_FIELD_VALUE` | 400 | `page` 또는 `size` 검증 실패 |
| `INTERNAL_SERVER_ERROR` | 500 | 예상하지 못한 내부 오류 |

## 11. 최근 본 맛집 개별 삭제

### API-PERSONAL-006 최근 본 맛집 개별 삭제

- Method: `DELETE`
- Path: `/api/me/recent-restaurants/{restaurantId}`
- 인증: 회원 Access Token
- 권한: 현재 로그인 회원 본인
- 관련 PRD: [PRD-PERSONAL-001](../../../04-product/prd/personal/personal-restaurant-management.md)
- 관련 요구사항: [FR-RECENT-003](../../../01-requirements/functional-requirements.md#fr-recent-003-최근-본-맛집-개별-삭제)
- 설명: 현재 회원의 최근 본 기록 한 건을 맛집 식별자로 멱등 삭제한다.

#### Path Parameters

| 이름 | 타입 | 필수 | 설명 | 검증 규칙 |
|---|---|---:|---|---|
| `restaurantId` | Identifier | 예 | 맛집 식별자 | 공통 식별자 계약 적용 |

#### Request Rules

- 요청 본문은 없다.
- 현재 회원과 `restaurantId`의 최근 기록만 삭제 대상으로 본다.
- 다른 회원 식별자 입력을 받지 않는다.
- 이미 없거나 반복 삭제한 경우에도 삭제 완료 상태를 유지한다.
- 비공개·삭제 맛집에 남아 있는 현재 회원의 기록도 직접 삭제할 수 있다.

#### Success Response

- 상태: `200 OK`

```json
{
  "restaurantId": "restaurant-id",
  "recorded": false
}
```

#### Response Field Definitions

| 필드 | 타입 | 필수 | 설명 | 빈 값 규칙 |
|---|---|---:|---|---|
| `restaurantId` | Identifier | 예 | 요청한 맛집 식별자 | `null`·빈 값 불가 |
| `recorded` | boolean | 예 | 현재 회원의 최근 기록 존재 여부 | 이 API에서는 항상 `false` |

#### Error Cases

| 오류 코드 | HTTP 상태 | 발생 조건 |
|---|---:|---|
| `INVALID_IDENTIFIER` | 400 | `restaurantId` 형식 오류 |
| `AUTHENTICATION_REQUIRED` | 401 | 인증 없음·무효 또는 회원 audience가 아닌 Token |
| `INTERNAL_SERVER_ERROR` | 500 | 예상하지 못한 내부 오류 |

## 12. 공통 목록 모델과 경계

- 목록 응답은 [응답 계약](../common/response-contract.md)과 [페이지네이션 계약](../common/pagination-contract.md)을 따른다.
- `page`는 1-base이며 `size`는 `10`, `20`, `50`만 허용한다.
- 두 목록 모두 빈 목록과 범위 밖 유효 페이지를 정상 `200`으로 반환한다.
- 두 목록의 맛집 요약은 개인화 화면에 필요한 최소 필드만 반환하며, 전화번호·주소·방문 콘텐츠 전체는 포함하지 않는다.

## 13. 오류 응답

[공통 오류 계약](../common/error-contract.md)을 따른다.

- 개인화 기능은 본인 고정 경로만 제공하므로 다른 회원 자원을 지정하는 전용 오류 코드를 두지 않는다.
- 비공개·삭제 맛집의 목록 노출은 숨기고, 공개 단일 자원 전제가 필요한 상태 조회·찜 추가만 `RESTAURANT_NOT_FOUND`를 사용한다.
- 개인화 저장소 장애는 다른 회원 상태를 추정해 대체하지 않고 실패로 응답한다.

## 14. 예외 및 경계 상황

| 상황 | 응답 |
|---|---|
| 이미 찜한 맛집에 `PUT /api/me/favorites/{restaurantId}` 반복 | `200`, 같은 본문으로 `favorited: true` |
| 이미 해제한 맛집에 `DELETE /api/me/favorites/{restaurantId}` 반복 | `200`, 같은 본문으로 `favorited: false` |
| 존재하지 않는 관계에 `DELETE /api/me/recent-restaurants/{restaurantId}` 반복 | `200`, 같은 본문으로 `recorded: false` |
| 비공개 맛집 찜 관계 | 목록에서는 숨김, 공개 상태 조회는 `404`, 삭제 요청은 현재 회원 관계만 정리 |
| 삭제 맛집 찜 관계 | 조회 전에 정리되며 목록에 노출되지 않음 |
| 비공개·삭제 맛집 최근 기록 | 목록에서는 숨김, 개별 삭제는 허용 |
| 최근 기록 저장 실패 | 공개 맛집 상세는 기존 성공을 유지하고 최근 목록에 즉시 반영되지 않을 수 있음 |
| 인증 저장소 또는 개인화 저장소 장애 | 해당 개인화 API는 실패하고 공개 맛집 목록·상세 기본 조회는 계속 동작 |

## 15. 관련 요구사항 및 규칙

- 주 책임: [PRD-PERSONAL-001](../../../04-product/prd/personal/personal-restaurant-management.md), [WS-06](../../../02-analysis/first-expansion-workstreams.md#5-ws-06-개인-맛집-관리), 박진영
- 인증 협업: [PRD-ACCOUNT-001](../../../04-product/prd/account/member-authentication.md)
- 요구사항: [FR-FAVORITE-001](../../../01-requirements/functional-requirements.md#fr-favorite-001-맛집-찜-추가)~[FR-FAVORITE-004](../../../01-requirements/functional-requirements.md#fr-favorite-004-찜-목록-조회), [FR-RECENT-001](../../../01-requirements/functional-requirements.md#fr-recent-001-최근-본-맛집-기록), [FR-RECENT-002](../../../01-requirements/functional-requirements.md#fr-recent-002-최근-본-맛집-목록-조회), [FR-RECENT-003](../../../01-requirements/functional-requirements.md#fr-recent-003-최근-본-맛집-개별-삭제), [FR-MEMBER-004](../../../01-requirements/functional-requirements.md#fr-member-004-회원-탈퇴)
- 규칙: [BR-FAVORITE-001](../../../01-requirements/business-rules.md#br-favorite-001-회원별-찜의-고유성과-멱등성)~[BR-FAVORITE-004](../../../01-requirements/business-rules.md#br-favorite-004-맛집-상태에-따른-찜-보존과-정리), [BR-RECENT-001](../../../01-requirements/business-rules.md#br-recent-001-최근-본-맛집의-기록과-갱신)~[BR-RECENT-005](../../../01-requirements/business-rules.md#br-recent-005-최근-기록-개별-삭제의-멱등성과-소유권), [BR-MEMBER-004](../../../01-requirements/business-rules.md#br-member-004-회원-탈퇴와-재가입)

## 16. 확정 사항

- 본인 고정 개인화 경로는 모두 `/api/me` 아래에 둔다.
- 찜 추가와 해제는 각각 `PUT`, `DELETE`로 같은 자원 경로를 공유하고 둘 다 `200` 멱등 응답을 사용한다.
- 찜 상태의 최소 명시적 조회는 `GET /api/me/favorites/{restaurantId}` 하나로 확정하며, 목록·상세 공개 API에 회원별 필드를 직접 추가하지 않는다.
- 최근 기록 생성은 공개 상세 성공 뒤 서버 내부 부수효과로만 수행하고 공개 쓰기 API를 추가하지 않는다.
- 찜·최근 목록의 안정 정렬 보조 기준은 동일 시각에서 `restaurant.id` 오름차순으로 확정한다.

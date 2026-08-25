---
id: API-DISCOVERY-001
title: 맛집 탐색 API
status: draft
related_prd:
  - PRD-DISCOVERY-001
  - PRD-DISCOVERY-002
workstream: WS-01
owner: 양성훈
reviewers:
  - 이우람
related_requirements:
  - FR-RESTAURANT-001
  - FR-RESTAURANT-002
  - FR-RESTAURANT-003
  - FR-RESTAURANT-004
  - FR-RESTAURANT-005
  - FR-RESTAURANT-006
  - FR-RESTAURANT-007
  - FR-CREATOR-001
related_business_rules:
  - BR-SEARCH-001
  - BR-SEARCH-002
  - BR-SEARCH-003
  - BR-SEARCH-004
  - BR-SEARCH-005
  - BR-SEARCH-006
  - BR-SEARCH-007
  - BR-SEARCH-008
  - BR-SEARCH-009
related_nfr:
  - NFR-PERFORMANCE-001
  - NFR-PERFORMANCE-002
  - NFR-PERFORMANCE-004
  - NFR-SECURITY-002
  - NFR-SECURITY-003
  - NFR-RELIABILITY-001
  - NFR-RELIABILITY-003
  - NFR-COMPATIBILITY-002
  - NFR-COMPATIBILITY-003
  - NFR-TEST-001
  - NFR-TEST-002
related_documents:
  - ../../../04-product/prd/discovery/restaurant-discovery.md
  - ../../../04-product/prd/discovery/creator-discovery.md
  - creator-discovery-api.md
  - ../common/identifier-contract.md
  - ../common/response-contract.md
  - ../common/error-contract.md
  - ../common/pagination-contract.md
  - ../common/filtering-contract.md
  - ../../data/data-model.md
  - ../../data/relationship-rules.md
  - ../../../01-requirements/functional-requirements.md
  - ../../../02-analysis/mvp-workstreams.md
  - ../../../01-requirements/business-rules.md
---

# 맛집 탐색 API

## 1. 문서 목적

로그인하지 않은 일반 사용자가 공개 맛집을 이름, 서울특별시 자치구, 대표 음식 카테고리와 유튜버 조건으로 탐색하는 외부 계약을 정의한다.

## 2. 적용 범위

목록, 이름 검색, 단일 값 필터의 AND 조합, 안정 정렬, 페이지네이션과 목록용 방문 채널 표시를 포함한다. 추천·지도·복수 값 필터·유튜버 상세는 제외한다.

## 3. 접근 권한

인증 없이 공개 접근한다. 공개 맛집과 공개·유효 관계의 표시 정보만 반환한다.

## 4. API 요약

| API ID | Method | Path | 설명 |
|---|---|---|---|
| [API-DISCOVERY-001](restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) | GET | `/api/restaurants` | 맛집 목록 및 조건 검색 |
| [API-DISCOVERY-002](restaurant-discovery-api.md#api-discovery-002-공개-맛집-필터-선택지) | GET | `/api/restaurants/filter-options` | 공개 맛집이 사용하는 지역·음식 종류 선택지 |

`/api/restaurants`를 선택한다. `/api`는 화면과 백엔드를 구분하고 검색과 필터는 목록 조회의 조건이므로 `/api/restaurant-discovery`나 `/api/search/restaurants`처럼 별도 동사·기능 경로로 분리하지 않는다.

## 5. 맛집 목록 및 조건 검색

### API-DISCOVERY-001 맛집 목록 및 조건 검색

- Method: `GET`
- Path: `/api/restaurants`
- 인증: 없음
- 권한: 일반 공개 조회
- 관련 PRD: [PRD-DISCOVERY-001](../../../04-product/prd/discovery/restaurant-discovery.md), [PRD-DISCOVERY-002](../../../04-product/prd/discovery/creator-discovery.md)
- 관련 요구사항: [FR-RESTAURANT-001](../../../01-requirements/functional-requirements.md#fr-restaurant-001-맛집-목록-조회)~[FR-RESTAURANT-007](../../../01-requirements/functional-requirements.md#fr-restaurant-007-기본-정렬-적용), [FR-CREATOR-001](../../../01-requirements/functional-requirements.md#fr-creator-001-유튜버-기준-방문-맛집-조회)
- 설명: 지정한 조건을 모두 만족하는 공개 맛집을 고유하게 정렬해 페이지로 반환한다.

#### Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 설명 | 검증 규칙 |
|---|---|---:|---|---|---|
| `query` | string | 아니요 | 없음 | 맛집 이름 부분 일치 검색어 | 앞뒤 공백 제거, 공백뿐이면 조건 미적용, 최대 100자 |
| `district` | string | 아니요 | 없음 | 서울특별시 자치구 1개 | 서울 자치구 이름만 허용, 반복 불가 |
| `category` | string | 아니요 | 없음 | 대표 음식 카테고리 1개 | 공통 계약의 10개 값만 허용, 반복 불가 |
| `creatorId` | Identifier | 아니요 | 없음 | 유튜버 1명의 식별자 | 공개 유튜버만 허용, 반복 불가 |
| `tag` | string | 아니요 | 없음 | 활성 관리자 확정 태그 코드 1개 | 공개·유효 Visit에 연결된 활성 태그만 허용, 반복 불가 |
| `page` | integer | 아니요 | `1` | 요청 페이지 | 1 이상. 첫 페이지는 1 |
| `size` | integer | 아니요 | `21` | 페이지 크기 | `10`, `20`, `21`, `50`만 허용 |

#### Request Rules

- 검색과 서로 다른 필터는 AND로 적용하고 미지정 조건은 적용하지 않는다.
- `creatorId` 조건의 유효 관계 판정은 [WS-03](../../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) 계약을 따르며 최종 조합·정렬·페이지는 [WS-01](../../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)이 수행한다.
- 비공개·삭제 맛집과 무효 관계는 제외한다. 영상 관계가 없는 공개 맛집은 유튜버 조건이 없을 때 포함한다.
- `tag` 조건은 공개·유효 Visit에 연결된 확정 태그가 있는 맛집만 포함하며, 같은 맛집에 여러 관계가 있어도 한 번만 반환한다.
- 같은 맛집에 여러 관계가 있어도 한 번만 반환한다.
- 검색·필터 변경 시 클라이언트는 첫 페이지를 요청한다.

#### Success Response

- 상태: `200 OK`
- 본문:

```json
{
  "items": [
    {
      "id": "restaurant-id",
      "name": "맛집 이름",
      "district": "마포구",
      "category": "한식",
      "visitedBy": [
        {
          "id": "creator-id",
          "channelName": "채널명"
        }
      ],
      "remainingVisitedByCount": 0
    }
  ],
  "page": {
    "number": 1,
    "size": 21,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

모든 식별자는 구조를 해석하지 않는 JSON 문자열이다.

#### Response Field Definitions

| 필드 | 타입 | 필수 | 설명 | 빈 값 규칙 |
|---|---|---:|---|---|
| `items` | array | 예 | 고유 맛집 목록 | 결과가 없으면 `[]` |
| `items[].id` | Identifier | 예 | 맛집 식별자 | `null`·빈 값 불가 |
| `items[].name` | string | 예 | 등록된 맛집 이름 | 빈 문자열 불가 |
| `items[].district` | string | 예 | 서울특별시 자치구 | 빈 문자열 불가 |
| `items[].category` | string | 예 | 대표 음식 카테고리 1개 | `null` 불가 |
| `items[].visitedBy` | array | 예 | 채널명 오름차순, 중복 제거한 방문 유튜버 최대 3명 | 없으면 `[]` |
| `items[].visitedBy[].id` | Identifier | 예 | 유튜버 식별자 | `null` 불가 |
| `items[].visitedBy[].channelName` | string | 예 | 현재 YouTube 채널명 | 빈 문자열 불가 |
| `items[].remainingVisitedByCount` | integer | 예 | 3명을 제외한 나머지 공개 방문 유튜버 수 | 없으면 `0` |
| `page` | object | 예 | 공통 페이지 정보 | 생략 불가 |

#### Error Cases

| 오류 코드 | HTTP 상태 | 발생 조건 |
|---|---:|---|
| `INVALID_REQUEST` | 400 | 지원하지 않는 쿼리 파라미터 |
| `INVALID_FIELD_VALUE` | 400 | 자치구·카테고리·유튜버·태그·페이지·크기가 유효하지 않거나, 같은 필터를 반복·배열·쉼표 목록 등 복수 값 형식으로 전달함([필터링 계약](../common/filtering-contract.md) 2절) |
| `INVALID_IDENTIFIER` | 400 | `creatorId` 형식이 잘못됨 |
| `INTERNAL_SERVER_ERROR` | 500 | 예상하지 못한 내부 오류 |

### API-DISCOVERY-002 공개 맛집 필터 선택지

- Method: `GET`
- Path: `/api/restaurants/filter-options`
- 인증: 없음
- 권한: 일반 공개 조회
- 설명: 공개·활성 맛집이 실제 사용하는 활성 지역과 음식 종류만 중복 없이 정렬해 반환한다.

#### Query Parameters

쿼리 파라미터를 받지 않는다. 파라미터가 전달되면 `400 INVALID_REQUEST`를 반환한다.

#### Success Response

- 상태: `200 OK`
- 본문:

```json
{
  "districts": ["마포구", "강남구"],
  "categories": ["한식", "일식"]
}
```

`districts`는 지역 기준 순서, `categories`는 음식 종류 기준 순서로 반환한다. 공개·활성 맛집이 없으면 두 배열 모두 빈 배열이다.

## 6. 검색·필터 조합 규칙

`query`, `district`, `category`, `creatorId`, `tag`는 모두 선택이며 지정한 조건을 모두 만족해야 한다. 같은 종류의 복수 값은 지원하지 않는다. 유효한 조건의 무결과는 `200`과 빈 목록이다. 여러 태그의 AND 조합은 자연어 탐색 API의 `filters.tags`를 사용한다.

## 7. 정렬 및 페이지네이션

클라이언트 정렬 입력은 없다. 이름 오름차순, 같은 이름은 전체 도로명주소 오름차순으로 안정 정렬한다. 페이지는 1부터 시작하고 크기 21이 기본이며 10·20·21·50만 허용한다. 범위 밖의 유효 페이지는 빈 목록이다.

## 8. 응답 모델

목록은 상세 주소·전화번호·영상 전체를 포함하지 않는다. 방문 유튜버 표시용으로 최대 3명의 식별자·채널명과 나머지 수만 제공하며 프론트엔드는 `remainingVisitedByCount > 0`이면 `외 N명`을 표시할 수 있다.

## 9. 오류 응답

[공통 오류 계약](../common/error-contract.md)을 따른다. 빈 결과와 범위 밖 유효 페이지는 오류가 아니다.

## 10. 예제

`GET /api/restaurants?query=식당&district=마포구&category=한식&creatorId=creator-id&tag=MENU_NAENGMYEON&page=1&size=21`

이 요청은 네 탐색 조건을 모두 만족하는 공개 맛집의 첫 페이지를 요청한다.

## 11. 관련 요구사항 및 규칙

- 주 책임: [PRD-DISCOVERY-001](../../../04-product/prd/discovery/restaurant-discovery.md), [WS-01](../../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색), 양성훈
- 관계 조건 협업: [PRD-DISCOVERY-002](../../../04-product/prd/discovery/creator-discovery.md), [WS-03](../../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색), 이우람
- 요구사항: [FR-RESTAURANT-001](../../../01-requirements/functional-requirements.md#fr-restaurant-001-맛집-목록-조회)~[FR-RESTAURANT-007](../../../01-requirements/functional-requirements.md#fr-restaurant-007-기본-정렬-적용), [FR-CREATOR-001](../../../01-requirements/functional-requirements.md#fr-creator-001-유튜버-기준-방문-맛집-조회)
- 규칙: [BR-SEARCH-001](../../../01-requirements/business-rules.md#br-search-001-검색-대상과-일치-기준)~[BR-SEARCH-009](../../../01-requirements/business-rules.md#br-search-009-기본-정렬) 중 001~009 전체, 공개 범위 규칙

## 12. 확정 사항

- 페이지 번호는 1부터 시작한다.
- 검색어는 앞뒤 공백 제거 후 최대 100자다.
- 응답 시간 목표는 일반 목록 p95 500ms, 모든 필터 조합 p95 800ms 이하로 확정하며 외부 서비스·사용자 네트워크 지연은 제외한다.

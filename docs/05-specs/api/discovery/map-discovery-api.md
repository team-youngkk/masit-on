---
id: API-MAP-001
title: 지도 맛집 마커 조회 API
status: approved
last_reviewed: 2026-08-03
owner: 양성훈
related_documents:
  - ../../../01-requirements/functional-requirements.md
  - ../../../01-requirements/business-rules.md
  - ../../../04-product/prd/discovery/map-discovery.md
  - ../../../07-adr/integration/map-001-map-bounds-search.md
  - ../../../08-planning/expansion-1-task-breakdown.md
---

# 지도 맛집 마커 조회 API

## 1. 문서 목적

URL 탐색 조건에 맞는 공개 맛집의 위치·요약을 지도 마커와 대체 목록에 제공한다. Kakao 지도 뷰포트와 SDK 타입은 프론트엔드 내부 상태이며 API 요청·응답에 포함하지 않는다.

이 계약은 2026-08-03 승인됐고 이전 bounds 기반 구현은 [E1-T11](../../../08-planning/expansion-1-task-breakdown.md)에서 이 계약으로 교체했다.

## 2. API-MAP-001 지도 맛집 마커 조회

- Method: `GET`
- Path: `/api/restaurants/map-points`
- 인증: 없음

### Query Parameters

| 이름 | 타입 | 필수 | 설명 | 규칙 |
|---|---|---:|---|---|
| `query` | string | 아니요 | 맛집 이름 검색 | 앞뒤 공백 제거, 공백뿐이면 미적용, 최대 100자 |
| `district` | string | 아니요 | 서울 자치구 | 기존 탐색 계약의 단일 값 |
| `category` | string | 아니요 | 대표 음식 카테고리 | 기존 탐색 계약의 단일 값 |
| `creatorId` | Identifier | 아니요 | 방문 유튜버 | 공개 유튜버 한 명, 반복 불가 |

`south`, `west`, `north`, `east`는 지원하지 않는다. 지도 뷰포트는 서버 조회 조건이 아니며 알 수 없는 쿼리, 배열·반복 값과 쉼표 목록은 `400`이다. 지정한 네 탐색 조건은 모두 AND로 적용한다.

### Success Response

- 상태: `200 OK`

```json
{
  "resultStatus": "AVAILABLE",
  "limit": 200,
  "items": [
    {
      "id": "restaurant-id",
      "name": "맛집 이름",
      "category": "한식",
      "addressSummary": "서울특별시 마포구",
      "coordinate": {
        "latitude": 37.5665,
        "longitude": 126.978
      }
    }
  ]
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `resultStatus` | string | 예 | `AVAILABLE` 또는 `TOO_MANY_RESULTS` |
| `limit` | integer | 예 | 최대 결과 `200` |
| `items` | array | 예 | 탐색 조건에 맞는 고유 공개 맛집 |
| `items[].id` | Identifier | 예 | 맛집 식별자 |
| `items[].name` | string | 예 | 맛집 이름 |
| `items[].category` | string | 예 | 대표 음식 카테고리 |
| `items[].addressSummary` | string | 예 | 주소 요약 |
| `items[].coordinate.latitude` | number | 예 | WGS84 위도 |
| `items[].coordinate.longitude` | number | 예 | WGS84 경도 |

결과가 200개 이하면 `AVAILABLE`과 전체 결과를 반환한다. 200개를 초과하면 임의 일부를 반환하지 않는다.

```json
{
  "resultStatus": "TOO_MANY_RESULTS",
  "limit": 200,
  "items": []
}
```

빈 조건 결과는 `AVAILABLE`과 빈 `items`다. 페이지네이션하지 않는다.

## 3. 정렬·중복·공개 규칙

- 이름 오름차순, 같은 이름은 맛집 ID 오름차순으로 안정 정렬한다.
- 같은 맛집에 여러 Visit 관계가 있어도 한 번만 반환한다.
- 공개·활성 상태이고 유효한 WGS84 좌표 쌍이 있는 맛집만 포함한다.
- 좌표 없음·비공개·삭제 맛집은 지도에서 제외하되 일반 목록·상세 계약을 변경하지 않는다.
- 지도 이동·확대·축소는 이 API를 호출하는 사유가 아니다.

## 4. 호출·오류 계약

서버는 신뢰된 클라이언트 요청 출처 기준 초당 최대 4회를 허용한다. 초과 시 `429 RATE_LIMIT_EXCEEDED`와 `Retry-After`를 반환한다. 이 제한은 최초 진입·탐색 조건 변경·명시적 재시도 요청을 보호하며 지도 이동 호출을 전제로 하지 않는다.

| HTTP | 코드 | 조건 |
|---:|---|---|
| 400 | `INVALID_REQUEST` | `south`·`west`·`north`·`east`를 포함한 알 수 없는 쿼리 |
| 400 | `INVALID_FIELD_VALUE` | 검색·필터 값 오류 또는 반복·배열·쉼표 목록 |
| 400 | `INVALID_IDENTIFIER` | `creatorId` 형식 오류 |
| 429 | `RATE_LIMIT_EXCEEDED` | 초당 조회 제한 초과 |

모든 오류 응답은 공통 오류 계약과 `traceId`를 따른다.

## 5. 프론트엔드 조회 계약

- Query Key는 `query`, `district`, `category`, `creatorId`만 포함한다.
- 지도 뷰포트와 중심 좌표는 Query Key·URL·API 요청·분석 이벤트에 포함하지 않는다.
- 지도 이동만으로 invalidate·refetch하지 않는다.
- 필터 변경 또는 명시적 오류 재시도 때만 새 조회를 수행한다.
- 조회 실패·429에서는 마지막 정상 결과와 선택 상태를 유지한다.

## 6. 완료 검증

- bounds 없이 요청이 성공하고 bounds를 보내면 알 수 없는 쿼리로 거부되는지 검증한다.
- 네 탐색 조건의 AND·중복 제거·안정 정렬을 검증한다.
- 결과 200개는 전체 반환, 201개는 `TOO_MANY_RESULTS`와 빈 배열인지 검증한다.
- 좌표 없는·비공개·삭제 맛집 제외와 일반 목록·상세 격리를 검증한다.
- 지도 이동 전후 네트워크 요청이 0건 추가되고 마커·대체 목록·선택이 유지되는지 브라우저에서 검증한다.
- 요청·응답·서버 로그·분석 이벤트에 지도 뷰포트와 사용자 현재 위치가 없는지 검증한다.

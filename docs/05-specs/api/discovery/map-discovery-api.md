---
id: API-MAP-001
title: 지도 기반 맛집 탐색 API
status: draft
related_prd:
  - PRD-DISCOVERY-003
workstream: WS-07
owner: 양성훈
reviewers:
  - 박진영
  - 이우람
related_requirements:
  - FR-MAP-001
  - FR-MAP-002
  - FR-RESTAURANT-005
related_business_rules:
  - BR-MAP-001
  - BR-MAP-002
  - BR-MAP-003
  - BR-MAP-004
  - BR-MAP-005
related_nfr:
  - NFR-PERFORMANCE-005
  - NFR-EXTERNAL-004
  - NFR-COMPATIBILITY-004
  - NFR-TEST-004
  - NFR-PRIVACY-004
related_documents:
  - ../../../04-product/prd/discovery/map-discovery.md
  - restaurant-discovery-api.md
  - ../common/identifier-contract.md
  - ../common/response-contract.md
  - ../common/error-contract.md
  - ../common/filtering-contract.md
  - ../common/coordinate-contract.md
  - ../../../02-analysis/first-expansion-workstreams.md
  - ../../../06-architecture/security-boundary.md
---

# 지도 기반 맛집 탐색 API

## 1. 문서 목적

현재 지도 화면 영역과 기존 맛집 탐색 조건에 맞는 공개 맛집의 위치·요약을 제공한다. Kakao Maps SDK의 렌더링·마커 선택은 프론트엔드 책임이며 이 API는 SDK 타입을 반환하지 않는다.

## 2. 접근 권한과 경로

인증 없이 공개 접근한다. 사용자 현재 위치, 위치 권한, 기기 식별자와 반경은 요청하지 않는다.

| API ID | Method | Path | 설명 |
|---|---|---|---|
| [API-MAP-001](map-discovery-api.md#api-map-001-지도-영역-맛집-조회) | GET | `/api/restaurants/map-points` | 지도 영역·탐색 조건에 맞는 맛집 위치 조회 |

`map-points`는 Restaurant가 소유한 좌표 조회 표현이다. 일반 페이지 목록과 응답 상한·필드가 다르므로 `GET /api/restaurants`에 `view` 분기를 추가하지 않는다.

## 3. 좌표 계약

- [좌표 공통 계약](../common/coordinate-contract.md)을 적용한다.
- 좌표계는 WGS84 십진수 위도·경도다.
- 위도는 JSON number와 쿼리 decimal로 `-90` 이상 `90` 이하, 경도는 `-180` 이상 `180` 이하다.
- 소수 자릿수나 문자열 포맷을 식별 의미로 사용하지 않는다.
- 영역은 남쪽·서쪽·북쪽·동쪽 경계로 표현하고 경계선 위 좌표를 포함한다.
- `south < north`, `west < east`여야 한다. 날짜변경선을 가로지르는 `west > east` 영역은 1차 확장에서 지원하지 않는다.
- 서비스 데이터는 서울특별시로 제한하지만 화면 영역 자체가 서울 경계 안에 완전히 포함될 필요는 없다.

## 4. 지도 영역 맛집 조회

### API-MAP-001 지도 영역 맛집 조회

- Method: `GET`
- Path: `/api/restaurants/map-points`
- 인증: 없음
- 권한: 일반 공개 조회

### Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 설명 | 규칙 |
|---|---|---:|---|---|---|
| `south` | decimal | 예 | 없음 | 남쪽 위도 | `-90`~`90` |
| `west` | decimal | 예 | 없음 | 서쪽 경도 | `-180`~`180` |
| `north` | decimal | 예 | 없음 | 북쪽 위도 | `-90`~`90`, `south`보다 큼 |
| `east` | decimal | 예 | 없음 | 동쪽 경도 | `-180`~`180`, `west`보다 큼 |
| `query` | string | 아니요 | 없음 | 맛집 이름 검색 | 앞뒤 공백 제거, 공백뿐이면 미적용, 최대 100자 |
| `district` | string | 아니요 | 없음 | 서울 자치구 | 기존 탐색 계약의 단일 값 |
| `category` | string | 아니요 | 없음 | 대표 음식 카테고리 | 기존 탐색 계약의 단일 값 |
| `creatorId` | Identifier | 아니요 | 없음 | 방문 유튜버 | 공개 유튜버 한 명, 반복 불가 |

지원하지 않는 쿼리, 배열·반복 값과 쉼표 목록은 `400`이다. 영역과 지정한 탐색 조건은 모두 AND로 적용한다.

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
      "addressSummary": "서울특별시 마포구 월드컵로 1",
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
| `limit` | integer | 예 | 한 번에 표시 가능한 최대 결과 `200` |
| `items` | array | 예 | 영역과 조건에 맞는 고유 공개 맛집 |
| `items[].id` | Identifier | 예 | 맛집 식별자 |
| `items[].name` | string | 예 | 맛집 이름 |
| `items[].category` | string | 예 | 대표 음식 카테고리 |
| `items[].addressSummary` | string | 예 | 저장된 전체 도로명주소 |
| `items[].coordinate.latitude` | number | 예 | WGS84 위도 |
| `items[].coordinate.longitude` | number | 예 | WGS84 경도 |

결과가 200개 이하면 `AVAILABLE`과 전체 결과를 반환한다. 200개를 초과하면 임의 일부를 반환하지 않고 다음처럼 응답한다.

```json
{
  "resultStatus": "TOO_MANY_RESULTS",
  "limit": 200,
  "items": []
}
```

빈 영역은 `AVAILABLE`과 빈 `items`다. 지도 결과는 페이지네이션하지 않는다.

## 5. 정렬·중복·공개 규칙

- 결과가 200개 이하일 때 이름 오름차순, 같은 이름은 맛집 ID 오름차순으로 안정 정렬한다.
- 같은 맛집에 여러 Visit 관계가 있어도 한 번만 반환한다.
- 공개 상태이고 유효 좌표가 있는 맛집만 포함한다.
- 좌표 없음·범위 오류·비공개·삭제 맛집은 지도에서 제외하되 일반 목록·상세의 존재 여부를 변경하지 않는다.
- 사용자 요청 처리 중 Kakao Local API나 YouTube API를 호출하지 않는다.

## 6. 호출 제한

클라이언트는 지도 이동 종료 뒤 300ms debounce를 적용한다. 서버는 클라이언트 요청 출처 기준 초당 최대 4회를 허용한다. 초과 시 `429 RATE_LIMIT_EXCEEDED`, 공통 오류 본문과 재시도 가능 시점을 초 단위 `Retry-After` 헤더로 반환한다. 전달 헤더의 신뢰 범위는 인프라 보안 설정을 따른다.

## 7. 오류

| HTTP | 코드 | 조건 |
|---:|---|---|
| 400 | `MISSING_REQUIRED_FIELD` | 네 영역 값 중 하나 누락 |
| 400 | `INVALID_FIELD_VALUE` | 좌표 범위·순서, 검색·필터 값 오류 |
| 400 | `INVALID_IDENTIFIER` | `creatorId` 형식 오류 |
| 429 | `RATE_LIMIT_EXCEEDED` | 초당 조회 제한 초과 |
| 500 | `INTERNAL_SERVER_ERROR` | 예상하지 못한 내부 오류 |

모든 오류는 [공통 오류 계약](../common/error-contract.md)에 따라 `traceId`를 포함한다. Kakao Maps SDK 로딩 오류는 브라우저 오류 상태이며 이 API의 `502`로 변환하지 않는다.

## 8. 완료 검증

- 영역 밖 제외, 네 경계 포함, 좌표 범위·역전 영역을 검증한다.
- 기존 네 탐색 조건의 AND·중복 제거와 빈 결과를 검증한다.
- 결과 200개는 전체 반환, 201개는 `TOO_MANY_RESULTS`와 빈 배열임을 검증한다.
- 좌표 없는·비공개·삭제 맛집 제외와 일반 목록·상세 격리를 검증한다.
- 초당 4회 경계와 `429`·`Retry-After`, 모든 오류의 `traceId`를 검증한다.
- 요청·응답·로그에 사용자 현재 위치가 없음을 검증한다.

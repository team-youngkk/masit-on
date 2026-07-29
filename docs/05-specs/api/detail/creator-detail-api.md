---
id: API-CREATOR-DETAIL-001
title: 유튜버 상세 API
status: draft
related_prd:
  - PRD-DETAIL-002
workstream: WS-08
owner: 이우람
reviewers:
  - 박진영
  - 양성훈
related_requirements:
  - FR-CREATOR-004
  - FR-CREATOR-005
  - FR-CREATOR-006
related_business_rules:
  - BR-CREATOR-008
  - BR-CREATOR-009
  - BR-CREATOR-010
  - BR-CREATOR-011
  - BR-CREATOR-012
  - BR-VISIT-005
related_nfr:
  - NFR-PERFORMANCE-005
  - NFR-RELIABILITY-001
  - NFR-EXTERNAL-001
  - NFR-EXTERNAL-002
  - NFR-TEST-004
related_documents:
  - ../../../04-product/prd/detail/creator-detail.md
  - ../discovery/creator-discovery-api.md
  - restaurant-detail-api.md
  - ../common/identifier-contract.md
  - ../common/response-contract.md
  - ../common/error-contract.md
  - ../common/pagination-contract.md
  - ../../../02-analysis/first-expansion-workstreams.md
  - ../../../06-architecture/query-composition.md
---

# 유튜버 상세 API

## 1. 문서 목적

공개·외부 이용 가능한 유튜버의 저장된 채널 정보와 유효 Visit 관계에 기반한 방문 맛집·근거 영상을 제공한다. 세 조회는 사용자 화면의 독립 로딩·페이지 상태를 유지하기 위해 분리한다.

## 2. 접근 권한과 API 요약

모두 인증 없이 공개 접근한다.

| API ID | Method | Path | 설명 |
|---|---|---|---|
| [API-CREATOR-DETAIL-001](creator-detail-api.md#api-creator-detail-001-유튜버-기본-상세-조회) | GET | `/api/creators/{creatorId}` | 저장된 채널 표시 정보 |
| [API-CREATOR-DETAIL-002](creator-detail-api.md#api-creator-detail-002-유튜버-방문-맛집-조회) | GET | `/api/creators/{creatorId}/restaurants` | 고유 방문 맛집 페이지 |
| [API-CREATOR-DETAIL-003](creator-detail-api.md#api-creator-detail-003-유튜버-근거-영상-조회) | GET | `/api/creators/{creatorId}/videos` | 고유 근거 영상 페이지 |

한 연결 목록의 페이지 이동이 다른 목록을 다시 요청하거나 페이지 상태를 바꾸지 않는다.

## 3. 공통 공개·오류 규칙

- 유튜버 없음·비공개·삭제·외부 이용 불가는 세 API 모두 `404 CREATOR_NOT_FOUND`다.
- 사용자 조회 중 YouTube API를 호출하지 않고 마지막으로 확인해 저장한 정보만 반환한다.
- 연결 목록은 Visit 관계와 대상 자원이 모두 공개·유효할 때만 포함한다.
- 제외된 관계·맛집·영상의 존재나 상태는 응답으로 설명하지 않는다.
- `creatorId` 형식 오류는 `400 INVALID_IDENTIFIER`다.
- 모든 오류는 공통 오류 본문과 서버 생성 `traceId`를 포함한다.

## 4. 유튜버 기본 상세 조회

### API-CREATOR-DETAIL-001 유튜버 기본 상세 조회

- Method: `GET`
- Path: `/api/creators/{creatorId}`
- 인증: 없음
- 성공: `200 OK`

```json
{
  "id": "creator-id",
  "channelName": "채널명",
  "profileImageUrl": "https://example.com/profile.jpg",
  "description": null,
  "handle": "@channel-handle",
  "channelUrl": "https://www.youtube.com/channel/example"
}
```

| 필드 | 타입 | 필수 | 빈 값 규칙 |
|---|---|---:|---|
| `id` | Identifier | 예 | `null` 불가 |
| `channelName` | string | 예 | 빈 문자열 불가 |
| `profileImageUrl` | string 또는 null | 예 | 미등록이면 `null` |
| `description` | string 또는 null | 예 | 미등록이면 `null` |
| `handle` | string 또는 null | 예 | 미등록이면 `null` |
| `channelUrl` | string | 예 | 빈 문자열·`null` 불가 |

선택 문자열을 생략하거나 빈 문자열로 반환하지 않는다. 저장된 외부 링크의 실시간 성공은 보장하지 않는다.

## 5. 유튜버 방문 맛집 조회

### API-CREATOR-DETAIL-002 유튜버 방문 맛집 조회

- Method: `GET`
- Path: `/api/creators/{creatorId}/restaurants`
- 인증: 없음
- Query: 공통 `page`·`size`
- 성공: `200 OK`

```json
{
  "items": [
    {
      "id": "restaurant-id",
      "name": "맛집 이름",
      "district": "마포구",
      "category": "한식"
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

같은 맛집의 유효 관계가 여러 개면 맛집은 한 번만 제공한다. 각 맛집에서 가장 최근에 생성된 유효 관계 시각 내림차순으로 정렬하고, 시각이 같으면 맛집 ID 오름차순을 적용한다. 관계 시각은 외부 응답에 노출하지 않는다.

## 6. 유튜버 근거 영상 조회

### API-CREATOR-DETAIL-003 유튜버 근거 영상 조회

- Method: `GET`
- Path: `/api/creators/{creatorId}/videos`
- 인증: 없음
- Query: 공통 `page`·`size`
- 성공: `200 OK`

```json
{
  "items": [
    {
      "id": "video-id",
      "title": "영상 제목",
      "thumbnailUrl": "https://i.ytimg.com/example.jpg",
      "sourceUrl": "https://www.youtube.com/watch?v=example"
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

같은 영상의 유효 관계가 여러 개면 영상은 한 번만 제공한다. 각 영상에서 가장 최근에 생성된 유효 관계 시각 내림차순으로 정렬하고, 시각이 같으면 영상 ID 오름차순을 적용한다.

## 7. 페이지 계약

두 연결 목록은 각각 1-base 페이지와 `size=10|20|50`, 기본 20을 사용한다. 범위 밖 유효 페이지는 `200`과 빈 `items`, 실제 전체 개수·페이지 수를 반환한다. 잘못된 값은 `400 INVALID_FIELD_VALUE`다. 클라이언트 정렬 입력은 받지 않는다.

## 8. 연결 항목 필드

### 방문 맛집

| 필드 | 타입 | 필수 | 의미 |
|---|---|---:|---|
| `id` | Identifier | 예 | 맛집 식별자 |
| `name` | string | 예 | 맛집 이름 |
| `district` | string | 예 | 서울 자치구 |
| `category` | string | 예 | 대표 음식 카테고리 |

### 근거 영상

| 필드 | 타입 | 필수 | 의미 |
|---|---|---:|---|
| `id` | Identifier | 예 | 영상 식별자 |
| `title` | string | 예 | 저장된 영상 제목 |
| `thumbnailUrl` | string | 예 | 저장된 썸네일 URL |
| `sourceUrl` | string | 예 | YouTube 원본 링크 |

## 9. 오류 및 경계

| HTTP | 코드 | 조건 |
|---:|---|---|
| 400 | `INVALID_IDENTIFIER` | 유튜버 식별자 형식 오류 |
| 400 | `INVALID_FIELD_VALUE` | 페이지·크기 오류 |
| 404 | `CREATOR_NOT_FOUND` | 없음·비공개·삭제·외부 이용 불가 |
| 500 | `INTERNAL_SERVER_ERROR` | 기본 정보 또는 요청 목록 제공 실패 |

유효 연결 결과가 없으면 `200`과 빈 페이지다. 한 연결 자원의 비공개·삭제·외부 이용 불가는 그 항목만 제외한다. 내부 저장소의 일시적 실패를 정상 빈 목록으로 숨기지 않는다.

## 10. 완료 검증

- 세 API의 공개 접근과 동일 `CREATOR_NOT_FOUND` 경계를 검증한다.
- 선택 표시 정보의 `null`, 필수 필드 비어 있지 않음을 검증한다.
- 맛집별·영상별 중복 제거, 최신 유효 관계 정렬과 ID tie-breaker를 검증한다.
- 두 목록의 페이지 상태가 독립적이고 빈·첫·마지막·범위 밖 페이지가 공통 계약과 일치하는지 검증한다.
- 비공개·삭제·무효 관계와 대상 제외, 사용자 조회 중 YouTube 호출 0건을 검증한다.
- 모든 오류의 `traceId`를 검증한다.

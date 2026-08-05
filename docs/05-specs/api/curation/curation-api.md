---
id: API-CURATION-001
title: 큐레이션 API
status: approved
related_prd:
  - PRD-CURATION-001
workstream: WS-11
owner: 김인안
reviewers:
  - 양성훈
related_requirements:
  - FR-CURATION-001
  - FR-CURATION-002
  - FR-CURATION-003
  - FR-CURATION-004
related_business_rules:
  - BR-CURATION-001
  - BR-CURATION-002
  - BR-CURATION-003
  - BR-CURATION-004
related_nfr:
  - NFR-PERFORMANCE-006
  - NFR-OBSERVABILITY-004
  - NFR-TEST-005
related_documents:
  - ../../../04-product/prd/curation/admin-curation.md
  - ../common/second-expansion-contract.md
  - ../common/identifier-contract.md
  - ../../data/second-expansion-data-contract.md
---

# 큐레이션 API

## 1. API 목록

| API ID | Method | Path | 접근 | 설명 |
|---|---|---|---|---|
| API-CURATION-001 | GET | `/api/curations` | 공개 | 게시 큐레이션 목록 |
| API-CURATION-002 | GET | `/api/curations/{curationId}` | 공개 | 게시 큐레이션 상세 |
| API-CURATION-003 | POST | `/api/admin/curations` | 관리자 | 초안 생성 |
| API-CURATION-004 | GET | `/api/admin/curations` | 관리자 | 관리 목록 |
| API-CURATION-005 | GET | `/api/admin/curations/{curationId}` | 관리자 | 관리 상세 |
| API-CURATION-006 | PATCH | `/api/admin/curations/{curationId}` | 관리자 | 제목·설명 수정 |
| API-CURATION-007 | PUT | `/api/admin/curations/{curationId}/restaurants` | 관리자 | 맛집 구성·순서 교체 |
| API-CURATION-008 | PUT | `/api/admin/curations/{curationId}/publication` | 관리자 | 게시 상태 설정 |
| API-CURATION-009 | PUT | `/api/admin/curations/main-order` | 관리자 | 메인 게시 순서 교체 |

삭제·예약 게시·자동 추천 API는 제공하지 않는다.

## 2. 공개 조회

`GET /api/curations`는 `PUBLISHED` 큐레이션을 저장된 메인 순서로 최대 5개 반환한다. 상세도 `PUBLISHED`만 조회할 수 있고 그 외는 `404 CURATION_NOT_FOUND`다. 포함 맛집이 전부 숨겨져도 게시 상태를 암묵적으로 바꾸지 않으며 빈 `items`로 반환한다.

```json
{
  "items": [{
    "curationId": "01K4CURATION000000000001",
    "title": "비 오는 날 국물 맛집",
    "description": "관리자가 고른 목록",
    "items": [{ "restaurantId": "01K4RESTAURANT00000000001", "name": "맛집", "roadAddress": "서울특별시 ..." }],
    "publishedAt": "2026-08-03T10:00:00+09:00",
    "updatedAt": "2026-08-03T10:00:00+09:00"
  }]
}
```

## 3. 관리자 생성·편집

생성은 `Idempotency-Key`와 아래 본문을 받아 항상 `DRAFT`로 `201 Created`한다.

```json
{ "title": "비 오는 날 국물 맛집", "description": "관리자가 고른 목록" }
```

`title`은 공백 제거 후 1~100자, `description`은 공백 제거 후 0~1000자다. `PATCH`는 둘 중 하나 이상을 받아 목표값을 설정하며 `200 OK`다.

구성 교체는 배열 순서가 표시 순서인 완전 교체 계약이다.

```json
{ "restaurantIds": ["01K4RESTAURANT00000000001", "01K4RESTAURANT00000000002"] }
```

0~20개의 중복 없는 공개·활성 Restaurant만 허용한다. 전체 검증과 저장을 한 트랜잭션에서 수행하며 실패하면 기존 공개 구성을 유지한다.

관리 응답은 각 관계를 다음처럼 표현해 공개 상태 변경을 경고한다.

```json
{
  "restaurantId": "01K4RESTAURANT00000000001",
  "position": 1,
  "name": "맛집",
  "availability": "PRIVATE",
  "warning": "공개 조회에서 숨김"
}
```

관리 목록의 각 항목은 구성 수와 공개 조회에서 숨겨질 맛집의 포함 여부를 요약한다.
관리자 계정 식별자는 응답에 포함하지 않는다.

```json
{
  "curationId": "01K4CURATION000000000001",
  "title": "비 오는 날 국물 맛집",
  "description": "관리자가 고른 목록",
  "status": "DRAFT",
  "mainPosition": null,
  "restaurantCount": 2,
  "hasHiddenRestaurants": true,
  "publishedAt": null,
  "updatedAt": "2026-08-03T10:00:00+09:00"
}
```

관리 상세와 생성·편집·구성·게시 변경의 성공 응답은 같은 기본 필드와 관계 `items`를
반환한다. `mainPosition`은 `DRAFT`이면 `null`, `PUBLISHED`이면 1~5다.

```json
{
  "curationId": "01K4CURATION000000000001",
  "title": "비 오는 날 국물 맛집",
  "description": "관리자가 고른 목록",
  "status": "DRAFT",
  "mainPosition": null,
  "publishedAt": null,
  "updatedAt": "2026-08-03T10:00:00+09:00",
  "items": [{
    "restaurantId": "01K4RESTAURANT00000000001",
    "position": 1,
    "name": "맛집",
    "availability": "PRIVATE",
    "warning": "공개 조회에서 숨김"
  }]
}
```

## 4. 게시와 메인 순서

게시 상태는 아래 목표 상태를 설정한다.

```json
{ "status": "PUBLISHED" }
```

허용값은 `DRAFT`, `PUBLISHED`다. 처음 `PUBLISHED`로 설정하면 현재 메인 순서의 마지막에 추가한다. 메인 게시 수가 5개면 `409 PUBLISHED_CURATION_LIMIT_EXCEEDED`다. 게시 중단은 `DRAFT` 설정이며 메인 순서에서 함께 제거한다.

`PUT /api/admin/curations/main-order`는 현재 게시 중인 큐레이션 전체(최대 5개)의 순서를 완전 교체한다.

```json
{ "curationIds": ["01K4CURATION000000000001", "01K4CURATION000000000002"] }
```

누락·중복·`DRAFT` ID가 있으면 전체 요청을 `409 INVALID_MAIN_CURATION_ORDER`로 거부한다. 성공한 관리자 변경은 `200 OK`이며 다음 공개 조회부터 반영하고 감사 이력을 남긴다.

## 5. 목록과 오류

관리 목록은 `GET /api/admin/curations?page=1&size=20&status=PUBLISHED`이며 `status`는 선택적 `DRAFT`, `PUBLISHED`다. 정렬은 `updatedAt` 내림차순, ID 오름차순이다.

| HTTP | 코드 | 조건 |
|---:|---|---|
| 404 | `CURATION_NOT_FOUND` | 큐레이션 없음 또는 공개 상세이지만 비게시 |
| 404 | `RESTAURANT_NOT_FOUND` | 구성 추가 대상 없음 또는 공개·활성 아님 |
| 409 | `DUPLICATE_CURATION_RESTAURANT` | 구성 ID 중복 |
| 409 | `CURATION_RESTAURANT_LIMIT_EXCEEDED` | 구성 20개 초과 |
| 409 | `PUBLISHED_CURATION_LIMIT_EXCEEDED` | 게시 5개 초과 |
| 409 | `INVALID_MAIN_CURATION_ORDER` | 게시 전체 순서와 요청 불일치 |

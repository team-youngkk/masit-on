---
id: API-COLLECTION-001
title: 개인 컬렉션 API
status: approved
related_prd:
  - PRD-COLLECTION-001
workstream: WS-09
owner: 박진영
reviewers:
  - 김인안
related_requirements:
  - FR-COLLECTION-001
  - FR-COLLECTION-002
  - FR-COLLECTION-003
  - FR-COLLECTION-004
  - FR-COLLECTION-005
  - FR-COLLECTION-006
related_business_rules:
  - BR-COLLECTION-001
  - BR-COLLECTION-002
  - BR-COLLECTION-003
  - BR-COLLECTION-004
  - BR-COLLECTION-005
related_nfr:
  - NFR-PRIVACY-005
  - NFR-TEST-005
related_documents:
  - ../../../04-product/prd/personal/personal-collection.md
  - ../common/second-expansion-contract.md
  - ../../../02-analysis/second-expansion-workstreams.md
  - ../../data/second-expansion-data-contract.md
---

# 개인 컬렉션 API

## 1. 경계와 API 목록

모든 경로는 회원 인증이 필요한 본인 전용 `/api/me` 자원이다. 요청에 회원 식별자를 받지 않으며 컬렉션 수동 정렬 API는 제공하지 않는다.

| API ID | Method | Path | 설명 |
|---|---|---|---|
| API-COLLECTION-001 | POST | `/api/me/collections` | 컬렉션 생성 |
| API-COLLECTION-002 | GET | `/api/me/collections` | 내 컬렉션 목록 |
| API-COLLECTION-003 | GET | `/api/me/collections/{collectionId}` | 컬렉션과 맛집 조회 |
| API-COLLECTION-004 | PATCH | `/api/me/collections/{collectionId}` | 컬렉션 이름 변경 |
| API-COLLECTION-005 | DELETE | `/api/me/collections/{collectionId}` | 컬렉션 삭제 |
| API-COLLECTION-006 | PUT | `/api/me/collections/{collectionId}/restaurants/{restaurantId}` | 맛집 추가 |
| API-COLLECTION-007 | DELETE | `/api/me/collections/{collectionId}/restaurants/{restaurantId}` | 맛집 제거 |
| API-COLLECTION-008 | GET | `/api/me/collection-options?restaurantId={restaurantId}` | 맛집 문맥의 컬렉션 추가 옵션 조회 |

## 2. 생성·수정

생성은 `Idempotency-Key`가 필요하다.

```json
{ "name": "가족과 갈 곳" }
```

`name`은 앞뒤 공백 제거 후 1~50자이며 빈 문자열은 허용하지 않는다. 생성은 `201 Created`, 이름 변경은 같은 본문의 `PATCH`와 `200 OK`를 사용한다.

```json
{
  "collectionId": "01K4COLLECTION000000000001",
  "name": "가족과 갈 곳",
  "restaurantCount": 0,
  "createdAt": "2026-08-03T10:00:00+09:00",
  "updatedAt": "2026-08-03T10:00:00+09:00"
}
```

회원당 20개 상한은 `409 COLLECTION_LIMIT_EXCEEDED`다. 이름 중복은 허용한다. 이름 변경 성공 시 `updatedAt`을 갱신하고 같은 이름 재설정도 `200`으로 멱등 처리한다.

## 3. 조회

`GET /api/me/collections`는 페이지 없이 최근 수정 시각 내림차순, 컬렉션 ID 오름차순으로 최대 20개를 반환한다.

```json
{ "items": [{ "collectionId": "01K4COLLECTION000000000001", "name": "가족과 갈 곳", "restaurantCount": 3, "updatedAt": "2026-08-03T10:00:00+09:00" }] }
```

상세 조회는 `page`, `size`를 받고 공개·활성 맛집만 추가 시각 내림차순, 맛집 ID 오름차순으로 반환한다. `restaurantCount`는 현재 조회 가능한 공개·활성 항목 수다.

```json
{
  "collectionId": "01K4COLLECTION000000000001",
  "name": "가족과 갈 곳",
  "restaurantCount": 1,
  "updatedAt": "2026-08-03T10:00:00+09:00",
  "items": [{ "restaurantId": "01K4RESTAURANT00000000001", "name": "맛집", "roadAddress": "서울특별시 ...", "addedAt": "2026-08-03T09:00:00+09:00" }],
  "page": { "number": 1, "size": 20, "totalElements": 1, "totalPages": 1, "hasNext": false }
}
```

### 3.1 맛집 문맥의 컬렉션 추가 옵션

`GET /api/me/collection-options?restaurantId={restaurantId}`는 공개 맛집 상세에서 현재 회원의
컬렉션별 추가 상태를 조회한다. `restaurantId`는 한 번만 지정하는 필수 식별자이며, 맛집이 없거나
공개·활성 상태가 아니면 `404 RESTAURANT_NOT_FOUND`를 반환한다. 응답 정렬은 컬렉션 목록과 같은
최근 수정 시각 내림차순, 컬렉션 ID 오름차순이다.

```json
{
  "items": [
    {
      "collectionId": "01K4COLLECTION000000000001",
      "name": "가족과 갈 곳",
      "restaurantCount": 3,
      "additionStatus": "AVAILABLE"
    }
  ]
}
```

`restaurantCount`는 공개·활성 관계 수다. 서버는 비공개 관계를 포함한 실제 관계 수를 100개 상한
판정에만 사용하며 응답에 노출하지 않는다. `additionStatus`는 다음 세 값만 사용한다.

| 값 | 의미 |
|---|---|
| `AVAILABLE` | 현재 맛집을 추가할 수 있음 |
| `ALREADY_INCLUDED` | 현재 맛집이 이미 포함되어 추가 요청을 만들지 않음 |
| `LIMIT_REACHED` | 실제 관계가 100개에 도달해 추가할 수 없음 |

현재 맛집이 이미 포함되어 있고 컬렉션도 상한에 도달한 경우 `ALREADY_INCLUDED`를 우선한다.
화면은 `ALREADY_INCLUDED`와 `LIMIT_REACHED`의 추가 요청을 비활성화한다. 맛집 추가 성공뿐 아니라
상한 충돌이나 맛집 공개 상태 변경으로 실패한 뒤에도 이 API를 다시 조회해 서버 상태와 동기화한다.

## 4. 맛집 추가·제거와 삭제

- 맛집 추가는 본문 없는 `PUT`이다. 최초 추가와 반복 추가 모두 현재 관계를 `200 OK`로 반환한다.
- 추가 가능한 맛집은 공개·활성 상태뿐이다. 컬렉션당 100개 상한은 `409 COLLECTION_RESTAURANT_LIMIT_EXCEEDED`다.
- 맛집 제거와 컬렉션 삭제는 `204 No Content`다. 반복 호출과 타 회원 소유 식별자에도 아무 자원을 바꾸지 않고 `204`를 반환한다.
- 조회·수정·추가에서 컬렉션이 없거나 본인 소유가 아니면 `404 COLLECTION_NOT_FOUND`다.

```json
{ "collectionId": "01K4COLLECTION000000000001", "restaurantId": "01K4RESTAURANT00000000001", "addedAt": "2026-08-03T09:00:00+09:00" }
```

## 5. 기능 오류

| HTTP | 코드 | 조건 |
|---:|---|---|
| 404 | `COLLECTION_NOT_FOUND` | 조회·수정·추가 대상 없음 또는 본인 소유 아님 |
| 404 | `RESTAURANT_NOT_FOUND` | 맛집 없음 또는 공개·활성 아님 |
| 409 | `COLLECTION_LIMIT_EXCEEDED` | 회원 컬렉션 20개 도달 |
| 409 | `COLLECTION_RESTAURANT_LIMIT_EXCEEDED` | 컬렉션 맛집 100개 도달 |

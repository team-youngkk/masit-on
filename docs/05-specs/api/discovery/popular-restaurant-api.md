---
id: API-POPULAR-001
title: 인기 맛집 API
status: draft
related_prd:
  - PRD-DISCOVERY-004
workstream: WS-10
owner: 양성훈
reviewers:
  - 박진영
related_requirements:
  - FR-POPULAR-001
related_business_rules:
  - BR-POPULAR-001
  - BR-POPULAR-002
  - BR-POPULAR-003
related_nfr:
  - NFR-PERFORMANCE-006
  - NFR-TEST-005
related_documents:
  - ../../../04-product/prd/discovery/popular-restaurants.md
  - ../common/second-expansion-contract.md
  - ../../data/second-expansion-data-contract.md
  - ../../../07-adr/data/data-011-popular-restaurant-request-time-aggregation.md
---

# 인기 맛집 API

## API-POPULAR-001 인기 맛집 조회

`GET /api/restaurants/popular`는 인증 없이 현재 찜이 1건 이상인 공개·활성 맛집 상위 20개를 반환한다. 페이지·기간·정렬·신호 선택 쿼리는 받지 않는다.

```json
{
  "items": [
    {
      "rank": 1,
      "restaurantId": "01K4RESTAURANT00000000001",
      "name": "맛집",
      "roadAddress": "서울특별시 ...",
      "category": "한식",
      "favoriteCount": 42
    }
  ]
}
```

- 정렬은 `favoriteCount` 내림차순, `restaurantId` 오름차순이며 `rank`는 1부터 결과 순서대로 부여한다.
- `favoriteCount`는 전체 기간의 현재 찜 관계 수다. 회원 식별자, 상세·최근 조회 기록, 비로그인 상세 조회 이벤트와 개인별 찜 여부는 포함하지 않는다.
- 조건에 맞는 맛집이 없으면 `200 OK`와 `{ "items": [] }`다.
- 매 요청은 커밋된 현재 찜 관계와 Restaurant 공개 상태를 사용한다. 배치·캐시·Snapshot API는 두지 않는다.

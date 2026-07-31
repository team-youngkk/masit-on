---
related_documents:
  - ../README.md
  - restaurant-discovery-api.md
  - creator-discovery-api.md
  - ../../../04-product/prd/discovery/README.md
  - ../../../02-analysis/mvp-workstreams.md
---

# 탐색 API

탐색 영역은 두 PRD와 Workstream의 책임 경계를 유지한다.

- [WS-01](../../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)은 `GET /api/restaurants`의 이름·지역·카테고리·유튜버 조건 AND 조합, 최종 목록, 정렬과 페이지를 소유한다.
- [WS-03](../../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)은 공개 유튜버 최소 선택 목록과 `creatorId`에 해당하는 유효 방문 맛집 판정 의미를 소유한다.

유튜버별 탐색은 별도 사용자용 맛집 목록 엔드포인트를 만들지 않는다. `creatorId`를 `GET /api/restaurants`에 전달하며, 유튜버 상세 페이지도 제공하지 않는다.

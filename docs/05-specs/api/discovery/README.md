---
related_documents:
  - ../README.md
  - restaurant-discovery-api.md
  - natural-language-restaurant-discovery-api.md
  - restaurant-course-recommendation-api.md
  - creator-discovery-api.md
  - popular-restaurant-api.md
  - ../../../04-product/prd/discovery/README.md
  - ../../../02-analysis/mvp-workstreams.md
  - ../../../02-analysis/third-expansion-workstreams.md
  - ../../../02-analysis/third-expansion-domain-boundaries.md
  - ../../../04-product/prd/discovery/natural-language-restaurant-discovery.md
  - ../../../04-product/prd/discovery/restaurant-course-recommendation.md
  - ../../../07-adr/architecture/arch-005-natural-language-filter-interpretation.md
  - ../../../07-adr/integration/route-001-kakao-mobility-course-routing.md
---

# 탐색 API

탐색 영역은 각 PRD와 Workstream의 책임 경계를 유지한다.

- [WS-01](../../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)은 `GET /api/restaurants`의 이름·지역·카테고리·유튜버·단일 태그 조건 AND 조합, 최종 목록, 정렬과 페이지를 소유한다.
- [WS-03](../../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)은 공개 유튜버 최소 선택 목록과 `creatorId`에 해당하는 유효 방문 맛집 판정 의미를 소유한다.
- [WS-10](../../../02-analysis/second-expansion-workstreams.md#5-ws-10-인기-맛집)은 [인기 맛집 API](popular-restaurant-api.md)의 현재 찜 수 집계와 안정 정렬을 소유한다.
- [WS-14](../../../02-analysis/third-expansion-workstreams.md#5-ws-14-자연어-맛집-탐색)은 [자연어 맛집 탐색 API](natural-language-restaurant-discovery-api.md)의 조건·확정 태그 해석과 기존 목록 API 조합을 소유한다.
- [WS-16](../../../02-analysis/third-expansion-workstreams.md#7-ws-16-맛집-코스-추천)은 [맛집 코스 추천 API](restaurant-course-recommendation-api.md)의 후보 검증·Route Provider 호출·비저장 결과를 소유한다.

유튜버별 탐색은 별도 사용자용 맛집 목록 엔드포인트를 만들지 않는다. `creatorId`를 `GET /api/restaurants`에 전달한다. 인기 맛집은 일반 검색 정렬 옵션으로 섞지 않고 별도 고정 목록 경로를 사용한다.

자연어 탐색은 `POST /api/restaurants/natural-language-search`로 기존 조건을 해석하며, 구조화 필터만 사용하는 `GET /api/restaurants`를 대체하지 않는다. 코스 추천은 `POST /api/restaurants/course-routes`로 선택 맛집의 일회성 자동차 경로를 반환하고 결과를 저장하지 않는다.

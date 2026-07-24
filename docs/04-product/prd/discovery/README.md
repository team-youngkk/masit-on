---
related_documents:
  1: ../00-product-overview.md
  2: restaurant-discovery.md
  3: creator-discovery.md
  4: ../../../01-requirements/functional-requirements.md
  5: ../../../01-requirements/business-rules.md
  6: ../../../05-specs/api/discovery/README.md
  7: ../../../02-analysis/mvp-workstreams.md
  8: ../detail/restaurant-detail.md
  9: ../admin/admin-data-management.md
  10: ../../traceability.md
---

# 탐색 PRD

## 1. 영역 목적

일반 사용자가 계정 없이 공개 맛집을 탐색하고 원하는 조건으로 후보를 좁히는 제품 가치를 관리한다.

## 2. 포함 기능

- 맛집 목록, 이름 검색, 지역·음식 카테고리 조건, 페이지와 기본 정렬
- 유효 방문 관계에 근거한 유튜버 조건
- 서로 다른 탐색 조건의 AND 조합과 빈 결과 처리

## 3. 제외 기능

- 맛집 상세와 방문 콘텐츠 표시
- 관리자 데이터 등록
- 지도·추천·자연어 검색·복수 값 필터·유튜버 상세

## 4. 하위 PRD

| PRD | 주 Workstream | 최종 책임자 |
|---|---|---|
| [#2 맛집 탐색](restaurant-discovery.md) | [#7 WS-01](../../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | 양성훈 |
| [#3 유튜버 기반 탐색](creator-discovery.md) | [#7 WS-03](../../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | 이우람 |

## 5. PRD 간 경계

[#7 WS-03](../../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)은 선택한 유튜버와 공개·유효 방문 관계를 판정해 고유 맛집 식별 결과를 제공한다. [#7 WS-01](../../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)은 이 결과를 이름·지역·카테고리 조건과 결합하고 정렬·페이지를 적용해 최종 목록을 완성한다. 관계 판정 규칙을 [#7 WS-01](../../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)에 복제하지 않는다.

## 6. 공통 요구사항 및 규칙

- 기준 요구사항: [#4 FR-RESTAURANT-001](../../../01-requirements/functional-requirements.md#fr-restaurant-001-맛집-목록-조회)~[#4 FR-RESTAURANT-007](../../../01-requirements/functional-requirements.md#fr-restaurant-007-기본-정렬-적용), [#4 FR-CREATOR-001](../../../01-requirements/functional-requirements.md#fr-creator-001-유튜버-기준-방문-맛집-조회), [#4 FR-CREATOR-003](../../../01-requirements/functional-requirements.md#fr-creator-003-유튜버-필터-선택-목록-조회)
- 공통 탐색 규칙: [#5 BR-SEARCH-001](../../../01-requirements/business-rules.md#br-search-001-검색-대상과-일치-기준)~[#5 BR-SEARCH-009](../../../01-requirements/business-rules.md#br-search-009-기본-정렬)
- 공개 정책: [#5 BR-PUBLICATION-001](../../../01-requirements/business-rules.md#br-publication-001-일반-사용자-공개-범위), [#5 BR-PUBLICATION-003](../../../01-requirements/business-rules.md#br-publication-003-맛집-상태와-연결-정보-노출)~[#5 BR-PUBLICATION-006](../../../01-requirements/business-rules.md#br-publication-006-관계-상태와-맛집-기본-조회)
- 원문은 [#4 기능 요구사항](../../../01-requirements/functional-requirements.md), [#5 비즈니스 규칙](../../../01-requirements/business-rules.md)과 [#1 제품 개요](../00-product-overview.md)를 따른다.

## 7. 책임 Workstream

[#7 WS-01](../../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) 양성훈이 최종 목록 계약을, [#7 WS-03](../../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) 이우람이 Visit 관계 판정 계약을 소유한다. 기본 리뷰어는 각각 이우람과 양성훈이며, 등록 데이터 반영 변경에는 [#7 WS-04](../../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 김인안이 참여한다.

## 8. 변경 영향

유튜버 관계 의미·공개 상태 변경은 두 PRD와 [#8 맛집 상세 PRD](../detail/restaurant-detail.md), [#9 관리자 PRD](../admin/admin-data-management.md)를 함께 검토한다. 검색 조건·페이지·정렬 변경은 맛집 탐색 PRD와 [#10 추적성 문서](../../traceability.md)를 갱신한다.

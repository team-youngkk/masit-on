---
related_documents:
  - ../00-product-overview.md
  - restaurant-discovery.md
  - creator-discovery.md
  - popular-restaurants.md
  - natural-language-restaurant-discovery.md
  - restaurant-course-recommendation.md
  - ../../../01-requirements/functional-requirements.md
  - ../../../01-requirements/business-rules.md
  - ../../../05-specs/api/discovery/README.md
  - ../../../02-analysis/mvp-workstreams.md
  - ../../../02-analysis/third-expansion-workstreams.md
  - ../detail/restaurant-detail.md
  - ../admin/admin-data-management.md
  - ../../traceability.md
---

# 탐색 PRD

## 1. 영역 목적

일반 사용자가 계정 없이 공개 맛집을 조건, 인기 신호 또는 관리자 편집 주제로 탐색하는 제품 가치를 관리한다.

## 2. 포함 기능

- 맛집 목록, 이름 검색, 지역·음식 카테고리 조건, 페이지와 기본 정렬
- 유효 방문 관계에 근거한 유튜버 조건
- 서로 다른 탐색 조건의 AND 조합, 자연어 조건 해석과 빈 결과 처리
- 현재 찜 수 기반 인기 맛집과 관리자 편집형 공개 큐레이션
- 사용자가 선택한 맛집의 자동차 이동 순서와 경로

## 3. 제외 기능

- 맛집 상세와 방문 콘텐츠 표시
- 관리자 데이터 등록
- 자동 맛집 추천·임베딩 검색·챗봇·복수 값 필터·유튜버 상세

## 4. 하위 PRD

| PRD | 주 Workstream | 최종 책임자 |
|---|---|---|
| [맛집 탐색](restaurant-discovery.md) | [WS-01](../../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | 양성훈 |
| [유튜버 기반 탐색](creator-discovery.md) | [WS-03](../../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | 이우람 |
| [인기 맛집](popular-restaurants.md) | [WS-10](../../../02-analysis/second-expansion-workstreams.md#5-ws-10-인기-맛집) | 양성훈 |
| [자연어 맛집 탐색](natural-language-restaurant-discovery.md) | [WS-14](../../../02-analysis/third-expansion-workstreams.md#5-ws-14-자연어-맛집-탐색) | 양성훈 |
| [맛집 코스 추천](restaurant-course-recommendation.md) | [WS-16](../../../02-analysis/third-expansion-workstreams.md#7-ws-16-맛집-코스-추천) | 이우람 |

## 5. PRD 간 경계

[WS-03](../../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)은 선택한 유튜버와 공개·유효 방문 관계를 판정해 고유 맛집 식별 결과를 제공한다. [WS-01](../../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)은 이 결과를 이름·지역·카테고리 조건과 결합하고 정렬·페이지를 적용해 최종 목록을 완성한다. 관계 판정 규칙을 [WS-01](../../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)에 복제하지 않는다.

인기와 큐레이션은 공개 탐색 진입면을 공유하지만, 인기 산정은 찜 데이터 계약에 의존하고 큐레이션 생성은 관리자 권한을 요구한다. 따라서 [인기 맛집 PRD](popular-restaurants.md)와 [관리자 큐레이션 PRD](../curation/admin-curation.md), WS-10과 WS-11로 분리한다.

자연어 맛집 탐색은 기존 조건 해석 뒤 목록 계약을 재사용하고, 맛집 코스 추천은 사용자가 선택한 공개·좌표 보유 맛집을 외부 경로 계산으로 조합한다. 두 기능은 화면 진입면을 공유할 수 있지만 입력·비용·장애·완료 기준이 달라 별도 PRD로 유지한다.

## 6. 공통 요구사항 및 규칙

- 기준 요구사항: [FR-RESTAURANT-001](../../../01-requirements/functional-requirements.md#fr-restaurant-001-맛집-목록-조회)~[FR-RESTAURANT-007](../../../01-requirements/functional-requirements.md#fr-restaurant-007-기본-정렬-적용), [FR-CREATOR-001](../../../01-requirements/functional-requirements.md#fr-creator-001-유튜버-기준-방문-맛집-조회), [FR-CREATOR-003](../../../01-requirements/functional-requirements.md#fr-creator-003-유튜버-필터-선택-목록-조회)
- 공통 탐색 규칙: [BR-SEARCH-001](../../../01-requirements/business-rules.md#br-search-001-검색-대상과-일치-기준)~[BR-SEARCH-009](../../../01-requirements/business-rules.md#br-search-009-기본-정렬)
- 공개 정책: [BR-PUBLICATION-001](../../../01-requirements/business-rules.md#br-publication-001-일반-사용자-공개-범위), [BR-PUBLICATION-003](../../../01-requirements/business-rules.md#br-publication-003-맛집-상태와-연결-정보-노출)~[BR-PUBLICATION-006](../../../01-requirements/business-rules.md#br-publication-006-관계-상태와-맛집-기본-조회)
- 3차 확장: [FR-NLSEARCH-001](../../../01-requirements/functional-requirements.md#fr-nlsearch-001-자연어-검색-요청과-결과-조회)~[FR-NLSEARCH-004](../../../01-requirements/functional-requirements.md#fr-nlsearch-004-확정-태그-조건과-결과-조회), [FR-COURSE-001](../../../01-requirements/functional-requirements.md#fr-course-001-코스-후보-입력)~[FR-COURSE-003](../../../01-requirements/functional-requirements.md#fr-course-003-외부-경로-실패-시-대체-결과)
- 원문은 [기능 요구사항](../../../01-requirements/functional-requirements.md), [비즈니스 규칙](../../../01-requirements/business-rules.md)과 [제품 개요](../00-product-overview.md)를 따른다.

## 7. 책임 Workstream

[WS-01](../../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) 양성훈이 최종 목록 계약을, [WS-03](../../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) 이우람이 Visit 관계 판정 계약을 소유한다. 기본 리뷰어는 각각 이우람과 양성훈이며, 등록 데이터 반영 변경에는 [WS-04](../../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 김인안이 참여한다.

3차 확장 자연어 탐색은 [WS-14](../../../02-analysis/third-expansion-workstreams.md#5-ws-14-자연어-맛집-탐색) 양성훈이, 코스 추천은 [WS-16](../../../02-analysis/third-expansion-workstreams.md#7-ws-16-맛집-코스-추천) 이우람이 최종 책임을 맡는다. 기본 리뷰어는 각각 이우람과 양성훈이다.

## 8. 변경 영향

유튜버 관계 의미·공개 상태 변경은 두 PRD와 [맛집 상세 PRD](../detail/restaurant-detail.md), [관리자 PRD](../admin/admin-data-management.md)를 함께 검토한다. 검색 조건·페이지·정렬 변경은 맛집 탐색 PRD와 [추적성 문서](../../traceability.md)를 갱신한다.

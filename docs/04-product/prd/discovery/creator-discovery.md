---
id: PRD-DISCOVERY-002
title: 유튜버 기반 탐색
status: draft
workstream: WS-03
owner: 이우람
reviewers:
  - 양성훈
related_requirements:
  - FR-CREATOR-001
  - FR-CREATOR-003
related_business_rules:
  - BR-CREATOR-001
  - BR-CREATOR-004
  - BR-CREATOR-005
  - BR-CREATOR-007
  - BR-VIDEO-005
  - BR-VIDEO-009
  - BR-VISIT-001
  - BR-VISIT-002
  - BR-VISIT-003
  - BR-VISIT-004
  - BR-VISIT-005
  - BR-VISIT-006
  - BR-VISIT-007
  - BR-SEARCH-003
  - BR-SEARCH-004
  - BR-SEARCH-005
  - BR-SEARCH-006
  - BR-SEARCH-007
related_nfr:
  - NFR-PERFORMANCE-002
  - NFR-INTEGRITY-004
  - NFR-RELIABILITY-001
  - NFR-RELIABILITY-003
  - NFR-TEST-001
  - NFR-TEST-002
  - NFR-MAINTAINABILITY-001
  - NFR-MAINTAINABILITY-002
related_documents:
  - ../00-product-overview.md
  - README.md
  - restaurant-discovery.md
  - ../../../01-requirements/functional-requirements.md
  - ../../../01-requirements/business-rules.md
  - ../../../02-analysis/mvp-workstreams.md
  - ../../../05-specs/api/discovery/creator-discovery-api.md
  - ../../../05-specs/api/discovery/restaurant-discovery-api.md
  - ../../../07-adr/platform/web-003-routing-boundary.md
  - ../../../05-specs/data/relationship-rules.md
  - ../../../01-requirements/non-functional-requirements.md
  - ../../traceability.md
---

# 유튜버 기반 탐색 PRD

## 1. 문서 정보

특정 YouTube 채널 단위 유튜버의 유효 방문 맛집을 판정하는 독립 기능을 정의한다. 별도 화면을 만들지 않고 `/restaurants`에서 `GET /api/creators`의 선택 목록과 `GET /api/restaurants?creatorId=...`를 사용한다. [WS-03](../../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)이 관계 판정을 소유하고 [WS-01](../../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)이 최종 목록 조합을 소유한다.

## 2. 기능 개요

사용자는 유튜버 1명을 조건으로 선택해 공개 영상으로 실제 방문이 검증된 공개 맛집만 탐색한다.

## 3. 문제 및 사용자 요구

사용자는 특정 유튜버가 방문한 맛집을 찾기 위해 채널의 영상을 하나씩 확인해야 한다. 단순 언급이나 무효·비공개 콘텐츠가 아니라 실제 방문 근거가 유효한 맛집만 보고 싶다.

## 4. 목표

- 선택한 유튜버의 유효 방문 관계를 일관되게 판정한다.
- 같은 맛집에 여러 근거가 있어도 고유 맛집 결과를 제공한다.
- [WS-01](../../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)이 다른 탐색 조건과 결합할 수 있는 명확한 결과를 제공한다.

## 5. 비목표

유튜버 상세, 선택 목록 검색·페이지네이션, 구독자·인기·추천 정보, 복수 유튜버 선택과 근거 없는 추정 방문은 목표가 아니다.

## 6. 대상 사용자

- 특정 유튜버의 방문 맛집을 찾는 일반 사용자

## 7. 전제 조건

- 유튜버는 YouTube 채널 단위로 식별한다.
- 맛집·유튜버·영상·방문 관계가 존재하며 공개·유효 조건을 충족해야 한다.
- 영상 게시 채널과 관계의 유튜버가 일치해야 한다.

## 8. 핵심 사용자 흐름

- 시작 조건: 사용자가 탐색 화면에서 유튜버 1명을 선택한다.
- 사용자 행동: 해당 유튜버 조건으로 맛집 탐색을 요청한다.
- 시스템 동작: 유튜버·영상·관계·맛집 상태, 실제 방문 근거와 채널 일치를 검증하고 고유 맛집을 산출해 [WS-01](../../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)에 제공한다.
- 성공 결과: [WS-01](../../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)의 최종 목록에서 해당 유튜버가 유효하게 방문한 공개 맛집을 확인한다.
- 빈 결과 또는 실패 처리: 유효 관계가 없으면 빈 결과를 제공하고 존재하지 않거나 공개되지 않은 유튜버는 노출 가능한 결과를 제공하지 않는다.

## 9. 기능 범위

### 포함 범위

- 공개 유튜버 식별자와 현재 채널명의 최소 선택 목록
- 유튜버 1명 기준 실제 방문 맛집 판정
- 네 대상의 공개·유효 상태, 근거 영상과 게시 채널 일치 확인
- 같은 맛집의 중복 제거와 [WS-01](../../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)용 맛집 식별 결과

### 제외 범위

- 최종 이름·지역·카테고리 조건 조합, 페이지와 정렬
- 유튜버 상세·목록·추천과 복수 선택

### 후속 확장

- 유튜버 상세는 기존 2차 확장 범위로, 추천은 후속 범위 변경 대상으로 검토한다.

## 10. 제품 요구사항

| PRD 요구사항 | 제품 동작 | 관련 기능 요구사항 | 중요도 | 상태 |
|---|---|---|---|---|
| PR-CREATOR-001 | 사용자는 특정 유튜버의 유효 방문 관계에 연결된 공개 맛집을 고유하게 탐색한다. | [FR-CREATOR-001](../../../01-requirements/functional-requirements.md#fr-creator-001-유튜버-기준-방문-맛집-조회) | Must | 확정 |
| PR-CREATOR-002 | 사용자는 공개 유튜버의 식별자와 현재 채널명으로 구성된 최소 선택 목록을 조회한다. | [FR-CREATOR-003](../../../01-requirements/functional-requirements.md#fr-creator-003-유튜버-필터-선택-목록-조회) | Must | 확정 |

## 11. 비즈니스 규칙

- 유튜버 관리 단위·표시·채널 일치와 이용 불가 처리는 [BR-CREATOR-001](../../../01-requirements/business-rules.md#br-creator-001-유튜버-정보의-의미), [BR-CREATOR-004](../../../01-requirements/business-rules.md#br-creator-004-유튜버-표시-정보), [BR-CREATOR-005](../../../01-requirements/business-rules.md#br-creator-005-방문-관계의-유튜버-일치), [BR-CREATOR-007](../../../01-requirements/business-rules.md#br-creator-007-채널-이용-불가-처리)을 따른다.
- 근거 영상·방문 관계의 구성, 중복과 조회 유효성은 [BR-VIDEO-005](../../../01-requirements/business-rules.md#br-video-005-실제-방문-근거), [BR-VIDEO-009](../../../01-requirements/business-rules.md#br-video-009-영상-이용-불가-처리), [BR-VISIT-001](../../../01-requirements/business-rules.md#br-visit-001-방문-관계의-구성)~[BR-VISIT-007](../../../01-requirements/business-rules.md#br-visit-007-등록-완료와-검증-상태)을 따른다.
- 단일 선택, 고유성, 빈 결과와 방문 근거는 [BR-SEARCH-003](../../../01-requirements/business-rules.md#br-search-003-필터-종류와-단일-선택)~[BR-SEARCH-007](../../../01-requirements/business-rules.md#br-search-007-유튜버-필터의-방문-근거)을 따른다.
- 공개 상태 우선순위는 [BR-PUBLICATION-001](../../../01-requirements/business-rules.md#br-publication-001-일반-사용자-공개-범위), [BR-PUBLICATION-003](../../../01-requirements/business-rules.md#br-publication-003-맛집-상태와-연결-정보-노출)~[BR-PUBLICATION-006](../../../01-requirements/business-rules.md#br-publication-006-관계-상태와-맛집-기본-조회)을 따른다.

## 12. 예외 및 경계 상황

| 상황 | 기대 결과 |
|---|---|
| 유효 방문 관계 없음 | 빈 결과를 정상 제공한다. |
| 같은 맛집에 여러 유효 관계 | 맛집을 한 번만 산출한다. |
| 근거 영상 없음 또는 게시 채널 불일치 | 관계를 결과에서 제외한다. |
| 유튜버·영상·관계·맛집이 비공개 또는 삭제됨 | 관련 맛집을 결과에서 제외한다. |
| 존재하지 않거나 공개되지 않은 유튜버 | 사용자에게 관련 데이터가 노출되지 않는다. |

## 13. 품질 요구사항

조건 조합 성능은 [NFR-PERFORMANCE-002](../../../01-requirements/non-functional-requirements.md#nfr-performance-002-검색필터-조합-응답-시간), 외부 링크와 내부 판정 분리는 [NFR-INTEGRITY-004](../../../01-requirements/non-functional-requirements.md#nfr-integrity-004-외부-링크와-내부-데이터-분리)를 따른다. 오류·테스트·책임 경계는 메타데이터의 관련 NFR로 검증하며 목표 성능 수치는 팀 결정이 필요하다.

## 14. 의존성

- 선행 정책: 채널 동일성, 방문 관계 유효성, 공개 상태
- 데이터 의존성: [WS-04](../../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록)가 등록한 유튜버·영상·맛집·관계
- 다른 기능 PRD: [PRD-DISCOVERY-001](restaurant-discovery.md)이 최종 조건 결합과 목록을 담당
- 다른 Workstream: [WS-01](../../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) 조합, [WS-02](../../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) 공통 관계 판정, [WS-04](../../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 관계 등록
- 공통 API 계약: 유튜버·맛집 식별자와 관계 판정 결과 의미

[WS-02](../../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)와 동일 정책을 공유하되 서로의 구현을 선행 호출하지 않는다.

## 15. Workstream 및 책임자

- 주 Workstream: [WS-03](../../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) 유튜버 기반 탐색
- 최종 책임자: 이우람
- 기본 리뷰어: 양성훈
- 협업: 박진영(상세 관계 정책), 김인안(관계 등록)

## 16. 성공 기준

- 사용자가 선택한 유튜버의 실제 방문이 검증된 공개 맛집만 확인한다.
- 무효·비공개 관계와 중복 맛집이 결과에 포함되지 않는다.
- 결과가 [WS-01](../../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)의 다른 조건과 정확히 결합된다.

## 17. 완료 기준

- [FR-CREATOR-001](../../../01-requirements/functional-requirements.md#fr-creator-001-유튜버-기준-방문-맛집-조회), [FR-CREATOR-003](../../../01-requirements/functional-requirements.md#fr-creator-003-유튜버-필터-선택-목록-조회)과 관련 규칙의 구현·자동화 테스트가 완료된다.
- [WS-02](../../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)와 동일 관계 정책, [WS-01](../../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) 실제 조합 계약, [WS-04](../../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 등록 결과를 통합 검증한다.
- API 계약, 문서와 추적성이 실제 동작과 일치한다.

## 18. 리스크

- 선택 목록과 관계 판정을 함께 제공하면서 세 Workstream 통합 지원 부담이 크다.
- 판정 로직을 [WS-01](../../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)과 공유 구현하면 순환 의존이 생길 수 있다.
- 유튜버 선택 목록은 [FR-CREATOR-003](../../../01-requirements/functional-requirements.md#fr-creator-003-유튜버-필터-선택-목록-조회)의 최소 필드·비페이지 계약을 유지해야 하며 유튜버 상세 기능으로 확장하지 않아야 한다.

## 19. 관련 문서

- [전체 제품 PRD](../00-product-overview.md)
- [맛집 탐색 PRD](restaurant-discovery.md)
- [기능 요구사항](../../../01-requirements/functional-requirements.md)
- [비즈니스 규칙](../../../01-requirements/business-rules.md)
- [MVP Workstream](../../../02-analysis/mvp-workstreams.md)
- [추적성](../../traceability.md)

## 20. 확정 성능 기준

- 초기 기준 데이터와 정상 부하 50명·20 RPS에서 조건 조합 p95 800ms 이하와 서버 오류율 1% 미만을 검증한다.

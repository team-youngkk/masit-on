---
related_documents:
  - ../00-overview/scope.md
  - prd/00-product-overview.md
  - traceability.md
  - ../05-specs/README.md
  - prd/discovery/README.md
  - prd/discovery/restaurant-discovery.md
  - ../02-analysis/mvp-workstreams.md
  - prd/discovery/creator-discovery.md
  - prd/detail/restaurant-detail.md
  - prd/admin/admin-data-management.md
  - ../00-overview/service-overview.md
  - ../01-requirements/functional-requirements.md
  - ../01-requirements/business-rules.md
  - ../01-requirements/non-functional-requirements.md
  - ../03-team/ownership.md
  - wireframes/README.md
---

# 맛잇온 제품 문서

## 1. 디렉터리 목적

이 디렉터리는 맛잇온 1차 MVP의 제품 방향과 기능별 사용자 가치를 계층적으로 관리한다. 범위의 기준은 [프로젝트 범위](../00-overview/scope.md)이며, PRD는 구현 방식이 아니라 사용자 문제, 제품 동작, 성공 기준과 책임 경계를 설명한다.

## 2. PRD 계층 구조

- [제품 개요 PRD](prd/00-product-overview.md)는 제품 문제, 전체 MVP 범위, 공통 원칙과 품질 목표를 관리한다.
- 기능 PRD는 하나의 사용자 가치 또는 관리자 업무 흐름을 관리하고 관련 요구사항·규칙 ID를 참조한다.
- [추적성 문서](traceability.md)는 요구사항, 규칙, NFR, Workstream과 담당자의 연결을 관리한다.
- 기능 PRD가 둘 이상인 탐색 영역만 별도 [영역 README](prd/discovery/README.md)를 둔다.

## 3. 문서 목록

| 문서 | 기능 가치 | 주 Workstream | 최종 책임자 |
|---|---|---|---|
| [제품 개요](prd/00-product-overview.md) | 제품 전체 방향과 공통 정책 | 전체 | 이우람(변경 조율) |
| [맛집 탐색](prd/discovery/restaurant-discovery.md) | 이름·지역·음식 종류 등 조건으로 맛집 찾기 | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | 양성훈 |
| [유튜버 기반 탐색](prd/discovery/creator-discovery.md) | 특정 유튜버의 실제 방문 맛집 찾기 | [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | 이우람 |
| [맛집 상세 및 콘텐츠 조회](prd/detail/restaurant-detail.md) | 맛집 기본 정보와 방문 콘텐츠를 한 흐름에서 확인 | [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | 박진영 |
| [관리자 데이터 등록](prd/admin/admin-data-management.md) | 검증된 기본 데이터와 방문 관계를 순서대로 등록 | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 김인안 |
| [PRD 추적성](traceability.md) | 기준 문서와 PRD의 매핑 및 변경 영향 | 전체 | 김인안(PRD 조율) |
| [와이어프레임 적용 기준](wireframes/README.md) | 단계별 화면 참조와 1차 MVP UI 적용·제외 기준 | 전체 | 양성훈·김인안 |

유튜버 조건은 독립된 관계 판정과 [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) 책임이 있으므로 별도 PRD로 둔다. 상세와 방문 콘텐츠는 한 화면의 완결된 흐름이며 [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)가 최종 조합을 책임하므로 통합한다. 기본 데이터와 방문 관계 등록은 하나의 [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록)가 선행 순서부터 조회 반영까지 책임하므로 하나의 관리자 PRD로 관리한다.

## 4. 문서별 책임 범위

- 상위 PRD는 공통 범위·원칙·품질 목표를 소유하며 기능별 세부 동작을 중복하지 않는다.
- 기능 PRD는 해당 흐름의 문제, 목표, 범위, 제품 동작, 예외, 성공·완료 기준을 소유한다.
- 기능 요구사항과 비즈니스 규칙은 각각 원문에서 정의하고 PRD에서는 ID와 제품 관점 요약만 유지한다.
- API 필드·경로, 데이터 구조, 클래스, 패키지와 기술 선택은 후속 명세·설계 문서가 소유한다.
- 각 기능 PRD의 Workstream 담당자가 내용을 작성하고 지정 리뷰어와 영향받는 담당자가 검토한다.

## 5. 문서 참조 순서

1. [서비스 개요](../00-overview/service-overview.md)와 [프로젝트 범위](../00-overview/scope.md)
2. [제품 개요 PRD](prd/00-product-overview.md)
3. 작업 대상 기능 PRD
4. [기능 요구사항](../01-requirements/functional-requirements.md), [비즈니스 규칙](../01-requirements/business-rules.md), [비기능 요구사항](../01-requirements/non-functional-requirements.md)
5. [Workstream](../02-analysis/mvp-workstreams.md), [소유권](../03-team/ownership.md)과 후속 API·설계 문서

## 6. 변경 규칙

### 제품 전체 범위 변경

1. [scope.md](../00-overview/scope.md)를 수정한다.
2. [00-product-overview.md](prd/00-product-overview.md)를 수정한다.
3. 영향 기능 PRD를 수정한다.
4. 기능 요구사항과 Workstream을 수정한다.
5. [traceability.md](traceability.md)를 갱신한다.
6. 역할과 일정 영향을 검토한다.

### 기능 내부 변경

1. 해당 기능 PRD를 수정한다.
2. 관련 기능 요구사항과 비즈니스 규칙을 검토한다.
3. API 계약과 테스트 영향을 확인한다.
4. [traceability.md](traceability.md)를 갱신한다.
5. 다른 PRD에 영향이 있으면 관련 담당자의 리뷰를 받는다.

### 기술 구현 변경

사용자 동작과 제품 범위가 변하지 않으면 PRD를 수정하지 않고 API 계약, 데이터 모델, 아키텍처 또는 ADR만 수정한다. 기술 변경으로 사용자 동작이 달라질 때만 PRD도 함께 수정한다.

## 7. 신규 기능 PRD 생성 기준

독립된 사용자·관리자 목표와 시작·완료 흐름이 있고, 별도 책임자·비즈니스 규칙·완료 조건을 가지며 독립 검증 가능한 경우에만 새 PRD를 만든다. 동일 흐름에서 항상 함께 쓰이고 책임자가 같으며 분리 시 중복만 늘어나는 작은 기능은 기존 PRD에 통합한다. 엔티티 이름만으로 분리하지 않으며, 새 PRD를 만들기 전 [scope.md](../00-overview/scope.md), Workstream, 주 요구사항과 최종 책임자가 모두 확정되었는지 확인한다.

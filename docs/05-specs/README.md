---
related_documents:
  - ../04-product/README.md
  - api/README.md
  - api-traceability.md
  - data/README.md
  - data/data-traceability.md
  - api-review.md
  - ../00-overview/scope.md
  - ../02-analysis/mvp-workstreams.md
  - ../02-analysis/second-expansion-workstreams.md
  - data/second-expansion-data-contract.md
---

# 맛잇온 명세 문서

## 1. 디렉터리 목적

`docs/05-specs/`는 확정된 MVP와 확장 단계의 제품 동작을 구현 가능한 API·데이터 계약으로 구체화한다. 이 진입 문서는 내부 클래스나 프레임워크 구현을 정의하지 않는다.

## 2. 명세 문서 구성

- `api/`: 클라이언트와 서버 사이의 공통·기능별 API 계약
- [api-traceability.md](api-traceability.md): PRD, 요구사항, 규칙, Workstream과 API의 연결
- [api-review.md](api-review.md): 계약 확정을 막거나 후속 결정이 필요한 항목
- [data/README.md](data/README.md): 논리·물리 데이터 명세 진입점
- [2차 확장 데이터 계약](data/second-expansion-data-contract.md): 2차 확장의 소유권·제약·생명주기·인덱스·V3 계획

PRD는 사용자 동작과 제품 범위를, API 계약은 클라이언트와 서버 사이의 외부 인터페이스를, 데이터 모델은 내부 저장 구조와 정합성을 정의한다.

## 3. 문서 참조 순서

충돌 시 [scope.md](../00-overview/scope.md), 확정된 기능 요구사항·비즈니스 규칙, 기능 PRD, [mvp-workstreams.md](../02-analysis/mvp-workstreams.md), 역할·소유권 문서, 미확정 검토 항목 순으로 적용한다. API를 사용할 때는 이 문서, [api/README.md](api/README.md), `api/common/`, 해당 기능 API 문서 순으로 읽는다.

## 4. 변경 원칙

사용자 동작이나 범위가 바뀌면 [scope.md](../00-overview/scope.md)와 PRD를 먼저 검토하고 요구사항·규칙, API 계약, 추적성, 데이터 모델 순으로 영향을 반영한다. API 계약만 바뀌면 관련 PRD·요구사항 영향, Workstream 리뷰, 프론트엔드 계약, 구현·테스트, 추적성을 함께 갱신한다. 내부 구현만 바뀌고 외부 동작이 유지되면 API 문서를 수정하지 않는다.

## 5. PRD·API·데이터 모델의 관계

PRD의 기능 경계가 API 문서 경계를 결정한다. API는 요청·응답·오류처럼 외부에서 관찰 가능한 동작만 고정한다. 데이터 모델은 그 계약을 만족하는 내부 구조를 후속 단계에서 결정하며 API 필드나 경로로부터 테이블·관계를 역설계해 확정하지 않는다.

## 6. 미확정 사항 처리

기준 문서에서 확정되지 않은 정책은 예시 값으로 확정하지 않는다. 프론트엔드 연동을 막는 항목은 [api-review.md](api-review.md)에 중요도, 결정 질문, 영향과 결정 시점을 기록하며, 확정 후 관련 계약과 추적성을 함께 갱신한다.

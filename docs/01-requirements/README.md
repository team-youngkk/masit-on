---
related_documents:
  - functional-requirements.md
  - business-rules.md
  - non-functional-requirements.md
  - requirements-review.md
  - ../00-overview/scope.md
  - ../00-overview/glossary.md
  - ../02-analysis/domain-boundaries.md
  - ../04-product/traceability.md
  - ../05-specs/api-traceability.md
---

# 요구사항

## 1. 목적

이 디렉터리는 [MVP 범위](../00-overview/scope.md) 안에서 **무엇이 동작해야 하고 어떤 기준으로 판정하는지**를 검증 가능한 형태로 정의한다. PRD, API 계약, 데이터 모델, 테스트 시나리오와 구현 Task는 여기의 요구사항 ID를 추적 기준으로 사용한다.

구현 방식, 데이터 구조, API 경로와 기술 선택은 이 디렉터리에서 정하지 않는다. 그건 [명세](../05-specs/)와 [ADR](../07-adr/adr-index.md)의 몫이다.

## 2. 문서 읽기 순서

1. [기능 요구사항](functional-requirements.md): 기능별 정상 결과, 예외와 경계 조건 (`FR-*` ID)
2. [비즈니스 규칙](business-rules.md): 여러 기능에 공통 적용되는 등록·관계·공개·중복 판단 기준
3. [비기능 요구사항](non-functional-requirements.md): 성능·보안·정합성·안정성·관측성·호환성·테스트 품질 기준
4. [요구사항 검토 결과](requirements-review.md): 작성 중 나온 미결정 사항의 합의 기록

`requirements-review.md`는 결론이 이미 앞의 세 문서에 반영돼 있다. 규칙의 **근거와 경위**를 확인할 때만 열면 된다.

## 3. 문서별 역할

| 문서 | 답하는 질문 | 다루지 않는 내용 |
|---|---|---|
| `functional-requirements.md` | 이 기능은 어떤 입력에 어떤 결과를 내는가? | 여러 기능에 걸친 공통 판단 기준 |
| `business-rules.md` | 등록·관계·공개·중복을 어떤 기준으로 판정하는가? | 개별 화면·API의 동작 |
| `non-functional-requirements.md` | 얼마나 빠르고 안전하고 견고해야 하는가? | 기능 범위와 업무 규칙 |
| `requirements-review.md` | 이 결정은 왜 이렇게 정해졌는가? | 아직 확정되지 않은 신규 결정 |

## 4. 기능 요구사항 ID

`FR-{도메인}-{번호}` 형식을 사용하며 도메인은 `RESTAURANT`, `CREATOR`, `VIDEO`, `VISIT`, `ADMIN`이다. 구현·테스트·PR은 이 ID로 근거를 밝힌다. 대응 관계는 추적표를 사용한다.

- [제품 추적표](../04-product/traceability.md) — 요구사항 ↔ PRD
- [API 추적표](../05-specs/api-traceability.md) — 요구사항 ↔ API
- [데이터 추적표](../05-specs/data/data-traceability.md) — 요구사항 ↔ 테이블

## 5. 변경 절차

1. 사용자 동작이나 범위가 바뀌면 [scope.md](../00-overview/scope.md)를 먼저 검토한다.
2. 기능 요구사항과 비즈니스 규칙을 수정한다.
3. 영향받는 PRD, API 계약, 데이터 명세를 같은 PR에서 갱신한다.
4. 추적표를 갱신한다.
5. 소유자 리뷰를 받는다. 요구사항별 소유자는 [ownership.md](../03-team/ownership.md)에 있다.

확정되지 않은 항목은 임의로 해석하지 않고 각 문서의 `검토 필요` 절에 남긴다.

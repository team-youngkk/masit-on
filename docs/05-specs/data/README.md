---
related_documents:
  - ../README.md
  - ../../01-requirements/business-rules.md
  - ../api/README.md
  - data-model.md
  - entity-definitions.md
  - relationship-rules.md
  - lifecycle-rules.md
  - constraints.md
  - data-traceability.md
  - data-review.md
  - physical-data-model.md
  - table-definitions.md
  - constraint-mapping.md
  - index-strategy.md
  - migration-plan.md
  - seed-data-plan.md
  - ../diagrams/erd-spec.md
  - ../../00-overview/scope.md
  - ../../07-adr/security/auth-003-confirmation-token.md
  - ../../07-adr/data/data-007-uuid-v4-identifiers.md
  - ../../07-adr/data/data-008-publication-lifecycle-soft-delete.md
---

# 맛잇온 데이터 명세

## 1. 디렉터리 목적

이 디렉터리는 맛잇온 1차 MVP가 관리하는 데이터 개념, 소유권, 관계, 제약과 생명주기를 논리 수준에서 정의하고, 이를 PostgreSQL에 구현할 물리 스키마까지 연결한다. API DTO를 그대로 저장 구조로 옮기지 않으며, 조회 응답의 조합·축약·파생 값과 영속 데이터를 구분한다.

## 2. 데이터 모델 문서 구성

| 문서 | 책임 |
|---|---|
| [data-model.md](data-model.md) | 전체 모델, 소유권, 생성·조회 흐름 |
| [entity-definitions.md](entity-definitions.md) | 데이터 개념별 식별·속성·상태·규칙 |
| [relationship-rules.md](relationship-rules.md) | 관계, 카디널리티, Visit 모델 선택 |
| [constraints.md](constraints.md) | 필수값·유일성·참조·원자성 제약 |
| [lifecycle-rules.md](lifecycle-rules.md) | 공개·외부 상태·삭제를 구분한 생명주기 |
| [data-traceability.md](data-traceability.md) | PRD·요구사항·규칙·API·Workstream 추적 |
| [data-review.md](data-review.md) | 해결된 결정, 미확정 사항과 ADR 후보 |
| [physical-data-model.md](physical-data-model.md) | PostgreSQL 물리 모델과 구현 컨벤션 |
| [table-definitions.md](table-definitions.md) | 테이블·컬럼·SQL 타입·PK·FK 정의 |
| [constraint-mapping.md](constraint-mapping.md) | 논리 규칙과 DB·애플리케이션 제약 매핑 |
| [index-strategy.md](index-strategy.md) | 공개 조회·검색·관계 조회 인덱스 전략 |
| [migration-plan.md](migration-plan.md) | Flyway 파일 순서·배포·복구 계획 |
| [seed-data-plan.md](seed-data-plan.md) | Region·FoodCategory 기준 데이터 계획 |
| [../diagrams/erd-spec.md](../diagrams/erd-spec.md) | ERD 포함 범위와 표기 명세 |
| `../diagrams/erd.mmd` | 논리 모델의 Mermaid 시각화 |

## 3. 논리 모델과 물리 모델의 구분

`data-model.md`부터 `data-review.md`와 논리 ERD까지는 구현 기술에 독립적인 논리 데이터 모델이다. 실제 테이블명, 컬럼명, SQL 자료형, 내부 식별자 타입, 인덱스와 Flyway 순서는 `physical-data-model.md`부터 `seed-data-plan.md`까지의 물리 설계 문서가 정의한다. 논리 규칙과 물리 제약이 충돌하면 상위 요구사항을 확인하고 두 계층 문서를 함께 수정한다.

UUID 내부 식별자와 논리 삭제 정책은 각각 [ADR-DATA-007](../../07-adr/data/data-007-uuid-v4-identifiers.md), [ADR-DATA-008](../../07-adr/data/data-008-publication-lifecycle-soft-delete.md)로 확정됐다.

검증 미리보기의 확인 Token은 PostgreSQL 단기 기술 테이블로 확정됐지만 핵심 도메인 엔티티나 논리 ERD에는 포함하지 않는다. Token 해시·관리자·자원 종류·후보 스키마 버전·JSONB Snapshot과 결과 상태만 저장하며 세부 정책은 [ADR-AUTH-003](../../07-adr/security/auth-003-confirmation-token.md)을 따른다. 저장 위치가 미정인 로그인 제한 카운터 같은 기술 아티팩트도 핵심 엔티티로 만들지 않는다.

## 4. 문서 참조 순서

충돌 시 [scope.md](../../00-overview/scope.md) → 확정 비즈니스 규칙 → 확정 기능 요구사항 → 확정 API 계약 → 기능 PRD → 도메인·Workstream → 검토 항목 순으로 판단한다. 이 디렉터리에서는 전체 모델, 엔티티, 관계, 제약, 생명주기, 추적성, 검토 문서 순으로 읽고 ERD는 마지막에 확인한다.

## 5. 데이터 모델 변경 절차

1. 변경 근거가 되는 범위·요구사항·비즈니스 규칙의 상태를 확인한다.
2. 영향받는 데이터 소유 도메인과 Workstream을 확인한다.
3. 엔티티·관계·제약·생명주기와 API 요청·응답 영향을 함께 수정한다.
4. [data-traceability.md](data-traceability.md)와 [data-review.md](data-review.md)를 갱신한다.
5. 장기 구조 또는 기술 선택이면 ADR을 작성한 뒤 물리 모델과 ERD를 동기화한다.

## 6. PRD·API·ADR과의 관계

PRD와 요구사항은 사용자 결과와 업무 규칙의 근거이고, API는 외부 요청·응답 계약이다. 데이터 모델은 그 결과를 일관되게 지원하는 내부 개념을 정의한다. API의 `remainingVisitedByCount`, `contentStatus`, 페이지 메타데이터처럼 계산 가능한 값은 저장하지 않는다. 데이터베이스 제품, 식별자 전략, 논리 삭제 구현, 동시성 구현과 마이그레이션 도구는 ADR 또는 물리 설계 대상이다.

## 7. 미확정 사항 처리

확정되지 않은 필드나 구현 방식은 임의로 필수화하지 않고 `검토 필요` 또는 `후속 설계에서 결정`으로 표시한다. 문서 충돌과 논리 모델을 막는 질문은 [data-review.md](data-review.md)에 중요도, 영향, 선택지와 결정 시점을 기록한다. 현재 명세의 Open Questions와 Assumptions는 해당 검토 문서가 단일 목록 역할을 한다.

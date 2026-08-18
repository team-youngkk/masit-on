---
id: ADR-DATA-008
title: 공개 상태와 논리 삭제 생명주기 분리
status: Accepted
decision_date: 2026-07-27
owners:
  - 박진영
related_requirements:
  - NFR-INTEGRITY-001
  - NFR-INTEGRITY-003
related_documents:
  - ../../01-requirements/business-rules.md
  - ../../05-specs/data/lifecycle-rules.md
  - ../../05-specs/data/physical-data-model.md
  - ../../05-specs/data/table-definitions.md
  - ../../05-specs/data/constraint-mapping.md
  - ../../05-specs/data/data-review.md
  - data-001-postgresql.md
  - data-004-flyway.md
supersedes: []
superseded_by: null
---

# ADR-DATA-008 공개 상태와 논리 삭제 생명주기 분리

## 1. 상태

Accepted

## 2. 결정 요약

Restaurant·Creator·Video·Visit에 `publication_status(PUBLIC/PRIVATE)`와 `lifecycle_status(ACTIVE/DELETED)`를 독립 컬럼으로 두고 `deleted_at`을 결합한다. 참조 보존을 위해 MVP 핵심 데이터는 물리 삭제하지 않으며 모든 FK는 `ON DELETE RESTRICT`를 사용한다.

## 3. 배경

공개 여부, 외부 리소스 가용성과 삭제 여부는 서로 다른 질문이다. 현재 수정·삭제 API는 없지만 잘못된 데이터와 외부 이용 불가 콘텐츠를 공개 조회에서 제외하고 관계·중복 이력은 보존해야 한다.

## 4. 결정 문제

공개 전환, 삭제·복구, 참조 보존과 공개 조회 판정을 어떤 상태와 제약으로 일관되게 표현할 것인가.

## 5. 고려한 선택지

- 단일 상태 enum
- 공개 상태와 `deleted_at`만 사용
- 공개·생명주기 독립 상태와 `deleted_at`
- 이력·보관 전용 테이블

## 6. 결정

- 공개 상태는 `PUBLIC`, `PRIVATE`다.
- 생명주기는 `ACTIVE`, `DELETED`다.
- `DELETED`는 반드시 `PRIVATE`이고 `deleted_at`이 존재한다.
- Creator·Video 외부 가용성은 `AVAILABLE`, `UNAVAILABLE`로 별도 관리한다.
- 삭제 행도 고유성 판단에 포함하며 같은 업무 키 재등록은 새 행 생성이 아니라 검증 후 복구로 처리한다.
- 핵심 FK의 cascade와 물리 삭제를 사용하지 않는다.

## 7. 선택 근거

비공개 정정과 삭제를 구분하면서도 참조·중복 이력을 보존한다. 외부 채널·영상 삭제가 Restaurant 또는 Visit 행 삭제로 전파되지 않는 상위 규칙을 직접 지원하며, partial 공개 인덱스로 읽기 경로를 최적화할 수 있다.

## 8. 트레이드오프

모든 공개 조회가 상태 조건을 일관되게 적용해야 하고, 물리 행 수는 줄지 않는다. MVP 상태 변경은 운영 감사 로그에 남지만 구조화된 DB 변경 이력이 없어 장기 이력 조회·복구 감사에는 한계가 있다. 보존 기간과 개인정보 삭제 요구가 생기면 별도 purge·이력 전략이 필요하다.

## 9. 적용 범위

Restaurant·Creator·Video·Visit와 그 공개 조회·운영 정정에 적용한다. Region·FoodCategory는 `active`, 통합 `MemberAccount`는 `status`와 `role`, 확인 Token은 자체 상태·보관 정책을 사용한다.

## 10. 강제 규칙

삭제 전환은 `PRIVATE`, `DELETED`, `deleted_at`을 한 트랜잭션에서 설정한다. 복구는 재검증 후 상태와 시각을 함께 갱신한다. 공개 조회는 공통 유효성 조건을 사용한다.

## 11. 금지 사항

FK cascade 삭제, 외부 이용 불가를 이유로 한 핵심 행 자동 삭제, 삭제 행을 제외한 업무 키 재등록, publication과 lifecycle을 하나의 값으로 합치는 행위를 금지한다.

## 12. 구현 및 운영 영향

공통 상태 enum, CHECK 제약, partial index, Query Projection의 공통 공개 조건과 운영 정정 절차가 필요하다. MVP에는 상태 변경 API와 변경 사유 이력을 추가하지 않는다.

## 13. 검증 방법

상태 조합 CHECK, 삭제·복구 원자성, 삭제 행 중복 방지, FK 보존, Restaurant·Creator·Video·Visit 상태 조합별 공개 조회 제외를 통합 테스트한다.

## 14. 재검토 조건

법적 삭제, 보존 기간, 변경자·사유 감사, 대량 purge, 별도 archive 저장소 또는 관리자 수정·삭제 API가 범위에 들어올 때 재검토한다.

## 15. 관련 문서

- [데이터 생명주기](../../05-specs/data/lifecycle-rules.md)
- [물리 데이터 모델](../../05-specs/data/physical-data-model.md)

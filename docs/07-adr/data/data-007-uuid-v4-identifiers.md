---
id: ADR-DATA-007
title: 애플리케이션 생성 UUID v4 내부 식별자
status: Accepted
decision_date: 2026-07-27
owners:
  - 박진영
related_requirements:
  - NFR-INTEGRITY-001
  - NFR-MAINTAINABILITY-002
related_documents:
  - ../../05-specs/api/common/identifier-contract.md
  - ../../05-specs/data/physical-data-model.md
  - ../../05-specs/data/table-definitions.md
  - ../../05-specs/data/data-review.md
  - data-001-postgresql.md
  - data-003-spring-data-jpa.md
supersedes: []
superseded_by: null
---

# ADR-DATA-007 애플리케이션 생성 UUID v4 내부 식별자

## 1. 상태

Accepted

## 2. 결정 요약

PostgreSQL 영속 엔티티의 내부 식별자는 애플리케이션이 저장 전에 생성한 UUID v4를 사용하고 DB에는 native `uuid`로 저장한다. API는 UUID 구조를 계약으로 노출하지 않고 기존 식별자 계약대로 불투명 JSON 문자열로 반환한다.

## 3. 배경

논리 모델은 외부 제공자 ID와 분리된 내부 ID를 요구하지만 생성 위치와 SQL 타입을 정하지 않았다. Restaurant·Creator·Video·Visit 식별자는 API와 여러 FK에서 사용되며, 확인 Token의 결과 자원 ID도 같은 형식을 사용한다.

## 4. 결정 문제

단일 PostgreSQL과 Spring Data JPA 환경에서 내부 ID를 어디서 어떤 형식으로 만들고 외부 계약과 어떻게 분리할 것인가.

## 5. 고려한 선택지

- DB `bigint identity`
- DB 함수로 생성하는 UUID
- 애플리케이션 생성 UUID v4
- 시간 정렬 UUID

## 6. 결정

- `region`, `food_category`, `admin_account`, `restaurant`, `creator`, `video`, `visit`, `confirmation_token` PK는 `uuid`다.
- 런타임 Entity는 INSERT 전에 UUID v4를 생성하고 DB default에 의존하지 않는다.
- Region·FoodCategory seed는 환경 간 동일한 고정 UUID를 사용한다.
- API와 클라이언트는 UUID 형식·버전·생성 규칙을 검증하지 않는다.

## 7. 선택 근거

PostgreSQL 확장과 INSERT 후 키 조회 없이 애플리케이션·테스트에서 ID를 미리 사용할 수 있다. 숫자 순번을 외부에 드러내지 않으며 Java와 PostgreSQL이 모두 native UUID를 지원한다. MVP 규모에서는 UUID v4의 무작위 B-tree 삽입 비용보다 구현 단순성과 환경 독립성이 크다.

## 8. 트레이드오프

`bigint`보다 PK·FK 인덱스가 크고 삽입 지역성이 낮다. UUID 문자열은 사람이 읽기 어렵다. 실제 인덱스 크기나 쓰기 병목이 확인되면 시간 정렬 UUID 또는 다른 키 전략을 새 ADR과 마이그레이션 계획으로 검토한다.

## 9. 적용 범위

PostgreSQL의 내부 PK·FK와 API 식별자 변환에 적용한다. Kakao·YouTube 외부 ID, JWT ID, Redis Refresh Token ID와 확인 Token 원문에는 적용하지 않는다.

## 10. 강제 규칙

JPA 저장 전에 ID를 부여하고, FK는 PostgreSQL `uuid`를 사용하며, Entity ID를 외부 제공자 ID와 겸용하지 않는다.

## 11. 금지 사항

도메인별 임의 키 타입 혼용, UUID 문자열을 `varchar`로 저장, DB sequence를 API 업무 ID로 노출, UUID 버전을 API 계약으로 고정하는 행위를 금지한다.

## 12. 구현 및 운영 영향

공통 ID 변환과 잘못된 UUID의 `INVALID_IDENTIFIER` 매핑이 필요하다. UUID PK의 실제 인덱스 크기와 캐시 적중률은 성능 테스트에서 확인한다.

## 13. 검증 방법

JPA round-trip, UUID API 문자열 변환, 잘못된 식별자 400, FK·고유 제약, 고정 seed ID와 서로 다른 환경의 재현성을 Testcontainers로 검증한다.

## 14. 재검토 조건

대량 쓰기에서 UUID v4 인덱스 지역성이 측정된 병목이 되거나, 외부 연동·데이터 병합에 다른 식별 체계가 필요할 때 재검토한다.

## 15. 관련 문서

- [물리 데이터 모델](../../05-specs/data/physical-data-model.md)
- [식별자 API 계약](../../05-specs/api/common/identifier-contract.md)

---
id: ADR-DATA-003
title: Spring Data JPA 기본 데이터 접근
status: Accepted
decision_date: 2026-07-27
owners:
  - 박진영
related_requirements:
  - NFR-INTEGRITY-001
  - NFR-MAINTAINABILITY-001
  - NFR-MAINTAINABILITY-002
related_documents:
  - ../../05-specs/data/data-model.md
  - ../../05-specs/data/relationship-rules.md
  - ../../05-specs/data/constraints.md
  - ../architecture/arch-001-domain-monolith.md
  - data-001-postgresql.md
  - ../../03-team/roles.md
  - ../../00-overview/scope.md
  - ../adr-backlog.md
  - ../../01-requirements/non-functional-requirements.md
supersedes: []
superseded_by: null
---

# ADR-DATA-003 Spring Data JPA 기본 데이터 접근

## 1. 상태

Accepted

## 2. 결정 요약

엔티티 중심 CRUD와 트랜잭션의 기본 데이터 접근은 Spring Data JPA를 사용한다.

## 3. 배경

4명의 백엔드 개발자가 각자 자신의 Workstream에 속한 엔티티와 Repository를 처음부터 끝까지 담당한다([roles.md](../../03-team/roles.md) 원칙). 개인별 PostgreSQL·SQL 숙련도는 확인되지 않았으므로([roles.md](../../03-team/roles.md)), Restaurant/Visit/Creator/Video 각 엔티티마다 CRUD·페이지네이션·트랜잭션 경계 코드를 매번 손으로 작성하게 하면 팀원 간 구현 편차와 반복 실수(트랜잭션 누락, 페이지네이션 오프바이원 등)가 커질 위험이 있다. 반대로 모든 조회를 QueryDSL 같은 동적 쿼리 도구로 처음부터 통일하면 학습 곡선이 생겨 MVP 기간 내 4인이 독립적으로 개발 가능해야 한다는 제약([scope.md](../../00-overview/scope.md) 6번)에 부담이 된다.

## 4. 결정 문제

PostgreSQL 관계형 모델의 기본 ORM·Repository 체계를 무엇으로 표준화할 것인가.

## 5. 고려한 선택지

- **Spring Data JPA**: 인터페이스 선언만으로 기본 CRUD·페이지네이션(`Pageable`)·트랜잭션 경계를 얻을 수 있어, 팀원 4명이 각자의 엔티티에 대해 동일한 패턴으로 Repository를 작성할 수 있다. 숙련도 차이가 있어도 기본 제공 메서드 범위 안에서는 구현 편차가 생기기 어렵다.
- **JDBC·SQL 직접 구현**: 생성되는 쿼리를 완전히 통제할 수 있지만, Restaurant-Visit-Creator-Video 조인 조회마다 매핑 코드를 직접 작성해야 하므로 4명이 각자 비슷한 매핑·페이지네이션 코드를 중복 작성하게 되고, 리뷰에서 잡아야 할 반복 실수 지점(페이지 경계, NULL 처리, 트랜잭션 커밋 시점)이 늘어난다. 소규모 MVP 팀이 감당하기엔 반복 비용이 크다.
- **QueryDSL을 모든 조회에 선제 적용**: 복잡한 동적 검색·필터 조건에는 유리하지만, 기본 CRUD까지 QueryDSL로 통일하면 모든 팀원이 Q타입 생성·빌드 설정·문법을 먼저 익혀야 한다. [adr-backlog.md](../adr-backlog.md)에서 QueryDSL은 검색 기능 고도화가 실제로 필요해졌을 때 검토할 Conditional 항목으로 분류되어 있으므로, 지금 전면 도입하는 것은 아직 입증되지 않은 필요를 위해 선행 투자하는 것이라 시기상조로 판단한다.

## 6. 결정

Spring Data JPA를 기본으로 사용하고 복합 동적 쿼리는 필요성이 입증될 때 별도 결정한다.

## 7. 선택 근거

Spring Data JPA의 선언적 Repository 패턴은 4명이 서로 다른 엔티티를 독립적으로 개발하면서도 CRUD·페이지네이션·트랜잭션 경계를 같은 방식으로 구현하게 만드는 가장 낮은 진입 장벽의 선택지다. 팀원별 실제 역량이 확인되지 않은 지금 시점에는, 개인 역량에 덜 의존하고 프레임워크가 정한 안전한 기본값(트랜잭션 전파, 영속성 컨텍스트 생명주기)을 따르는 편이 일관성 확보에 유리하다. 동적 쿼리 도구는 지금 필요성이 입증되지 않았으므로 도입을 늦추고, 실제로 검색·필터 요구가 복잡해지는 시점에 별도 결정([ADR-SEARCH-001](../adr-backlog.md#adr-search-001-querydsl-도입), Backlog)으로 넘긴다.

## 8. 트레이드오프

생산성과 팀 간 구현 일관성을 얻는 대신, JPA의 지연 로딩·영속성 컨텍스트 동작을 이해하지 못하면 N+1 조회나 의도치 않은 대량 연관 로딩이 발생하기 쉽다. 이는 Restaurant-Visit-Creator-Video처럼 조인이 잦은 도메인에서 특히 성능에 직접 영향을 주며, 확정된 성능 기준([RV-NFR-004](../../01-requirements/non-functional-requirements.md#rv-nfr-004-목표-응답-시간과-허용-오류율): 일반 조회 p95 500ms, 검색·필터 p95 800ms)을 못 지키는 원인이 될 수 있다. 팀원별 JPA 숙련도가 확인되지 않았으므로 이 위험은 실제로 발생할 가능성이 낮지 않다고 보고, 통합 테스트에서 쿼리 수를 직접 확인하는 절차(13번)로 조기에 발견하는 방식으로 완화한다. 또한 엔티티를 API 응답에 직접 노출하지 않도록 강제 규칙(10, 11번)을 둬 영속성 계층의 내부 구조가 외부 계약으로 새어나가는 것을 막는다.

## 9. 적용 범위

전체 MVP Repository, 엔티티 매핑과 트랜잭션 경계에 적용한다.

## 10. 강제 규칙

도메인 규칙을 Repository에 넣지 않고 조회 성능과 트랜잭션 경계를 통합 테스트로 확인한다.

## 11. 금지 사항

엔티티의 API 직접 노출, 무제한 연관 로딩, 근거 없는 QueryDSL·다른 ORM 혼용을 금지한다.

## 12. 구현 및 운영 영향

목록·검색 조회는 기본 페이지 크기 20, 허용 크기 10/20/50([RV-NFR-003](../../01-requirements/non-functional-requirements.md#rv-nfr-003-페이지-크기))을 `Pageable`로 강제하고 그 밖의 값은 잘못된 요청으로 거부한다. 연결 풀 크기는 정상 부하 50명·20 RPS와 최대 부하 200명·80 RPS 성능 테스트 결과로 조정하며, 개발 단계에서는 SQL 로그를 활성화해 생성되는 쿼리와 N+1 여부를 팀원이 직접 확인할 수 있게 한다. 조인이 잦은 조회는 인덱스와 실행 계획을 리뷰 대상에 포함한다.

## 13. 검증 방법

PostgreSQL Testcontainers로 CRUD, 제약 위반, 동시 등록 트랜잭션 충돌과 롤백을 검증한다. 조인 조회는 실행되는 쿼리 수를 테스트에서 직접 단언(assert)해 N+1이 없는지 확인하고, 목록·검색 API는 페이지 크기 10/20/50과 기본값 20이 [RV-NFR-003](../../01-requirements/non-functional-requirements.md#rv-nfr-003-페이지-크기)대로 동작하는지, 그 밖의 값이 잘못된 요청으로 거부되는지 경계값 테스트로 확인한다. 성능은 [RV-NFR-004](../../01-requirements/non-functional-requirements.md#rv-nfr-004-목표-응답-시간과-허용-오류율)(일반 조회 p95 500ms, 검색·필터 p95 800ms, 오류율 1% 미만)를 통과 기준으로 삼는다.

## 14. 재검토 조건

복합 조회나 대량 처리에서 반복적으로 한계가 측정될 때 범위별 대안을 검토한다.

## 15. 관련 문서

- [데이터 모델](../../05-specs/data/data-model.md)
- [QueryDSL Backlog](../adr-backlog.md#adr-search-001-querydsl-도입)

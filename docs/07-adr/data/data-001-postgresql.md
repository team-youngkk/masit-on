---
id: ADR-DATA-001
title: PostgreSQL 17.10 주 데이터베이스
status: Accepted
decision_date: 검토 필요
owners:
  - 박진영
related_requirements:
  1: NFR-INTEGRITY-001
  2: NFR-INTEGRITY-002
  3: NFR-INTEGRITY-003
related_documents:
  1: ../../05-specs/data/data-model.md
  2: ../../05-specs/data/entity-definitions.md
  3: ../../05-specs/data/constraints.md
  4: ../../06-architecture/technology-policy.md
  5: data-002-database-placement.md
  6: data-003-spring-data-jpa.md
  7: data-004-flyway.md
  8: ../../01-requirements/non-functional-requirements.md
  9: ../../00-overview/scope.md
  10: ../../03-team/roles.md
  11: ../adr-backlog.md
supersedes: []
superseded_by: null
---

# ADR-DATA-001 PostgreSQL 17.10 주 데이터베이스

## 1. 상태

Accepted

## 2. 결정 요약

1차 MVP의 영속 관계형 데이터베이스는 PostgreSQL 17.10을 사용한다.

## 3. 배경

맛잇온의 핵심 도메인은 Restaurant, Visit, Creator, Video 네 엔티티이며 "어떤 크리에이터가 어떤 영상에서 어떤 맛집을 방문했는가"를 조회하는 것이 서비스 가치의 중심이다. 즉 대부분의 화면(목록, 상세, 검색·필터)이 여러 엔티티를 조인해서 응답을 구성하며, 관리자가 수동으로 등록하는 값들 사이의 참조 무결성(존재하지 않는 Creator를 가리키는 Video 금지 등)과 등록 트랜잭션의 원자성이 데이터 신뢰성의 최소 조건이다. 데이터 규모 자체는 크지 않지만(맛집·영상 수는 [#8 RV-NFR-002](../../01-requirements/non-functional-requirements.md#rv-nfr-002-초기-데이터-규모)/[#8 RV-NFR-014](../../01-requirements/non-functional-requirements.md#rv-nfr-014-초기-예상-맛집-수)/[#8 RV-NFR-015](../../01-requirements/non-functional-requirements.md#rv-nfr-015-초기-예상-영상-수) 미확정) 관계의 밀도가 높은 소규모 정형 데이터라는 점이 이 결정의 핵심 배경이다.

## 4. 결정 문제

MVP의 권위 있는 관계형 데이터를 저장할 주 데이터베이스 엔진과 버전은 무엇인가.

## 5. 고려한 선택지

- **PostgreSQL 17.10**: 조인·외래키·유니크 제약·트랜잭션을 1급으로 지원하는 오픈소스 RDBMS. Amazon RDS 관리형 옵션이 있어 150,000원 예산 안에서 백업([#8 RV-NFR-010](../../01-requirements/non-functional-requirements.md#rv-nfr-010-백업-주기와-복구-범위): 일 1회 스냅샷, 7일 보관, RPO 24시간)까지 포함해 운영할 수 있다.
- **PostgreSQL 최신 자동 추종(버전 미고정)**: 항상 최신 마이너/메이저를 따라가는 방식. 팀원 4명 각자가 로컬 Docker와 배포 환경에서 서로 다른 시점에 이미지를 받으면 버전이 어긋날 수 있고, 개인별 기술 역량이 확인되지 않은 상태에서 마이너 버전 차이로 인한 동작 차이를 각자 디버깅하게 만들 위험이 있다. MVP 기간 내 4명이 독립적으로 개발 가능해야 한다는 [#9 scope.md](../../00-overview/scope.md)의 범위 경계 원칙(6번)과 어긋난다.
- **다른 관계형 DB(MySQL 등) 또는 문서형/NoSQL(MongoDB 등)**: MySQL 계열은 기능상 큰 차이는 없지만 이 프로젝트에 고유한 이점이 없어 전환 근거가 약하다. 문서형 DB는 Restaurant-Visit-Creator-Video처럼 다대다·다대일 관계가 얽힌 조인 중심 조회를, 애플리케이션 레벨에서 여러 번 조회하거나 비정규화로 흡수해야 하므로 오히려 4인 팀이 각자 독립 워크스트림([#10 roles.md](../../03-team/roles.md), 담당자별 엔티티/Repository 소유)을 유지하며 정합성을 맞추기가 더 어려워진다. 관계형 제약을 DB가 대신 검증해 주는 이점을 포기하는 셈이라 이 프로젝트 규모에는 맞지 않는다.

## 6. 결정

PostgreSQL 17.10을 주 데이터베이스로 사용한다.

## 7. 선택 근거

버전을 17.10으로 고정한 이유는 (1) 개발 Docker와 운영 RDS([#5 ADR-DATA-002](data-002-database-placement.md))의 엔진 버전을 정확히 맞춰 환경 차이로 인한 버그를 줄이고, (2) 팀원 4명의 PostgreSQL 숙련도가 확인되지 않은 상태에서 각자 다른 시점에 이미지를 받아도 동일한 동작을 보장하기 위함이다. 엔진으로 PostgreSQL을 택한 이유는 도메인이 조인이 많은 소규모 관계형 데이터이고, 외래키·유니크 제약·트랜잭션 원자성을 애플리케이션 코드가 아니라 DB가 강제할 수 있어 4명이 독립적으로 각자의 엔티티를 개발하더라도 전체 데이터 정합성이 깨지기 어렵기 때문이다. 이는 새 기술을 배우는 오버헤드보다, 검증 안 된 팀 역량으로도 안전한 실패 모드를 가진 기술을 택한다는 판단이다.

## 8. 트레이드오프

강한 정합성과 재현 가능한 환경을 얻는 대신, 이후 데이터/트래픽이 크게 늘어나 수평 분산이나 다른 엔진 전환이 필요해지면 마이그레이션 비용이 크다(전체 스키마·쿼리·운영 절차 재작성). 다만 MVP 시점에는 예상 데이터 규모([#8 RV-NFR-002](../../01-requirements/non-functional-requirements.md#rv-nfr-002-초기-데이터-규모)/[#8 RV-NFR-014](../../01-requirements/non-functional-requirements.md#rv-nfr-014-초기-예상-맛집-수)/[#8 RV-NFR-015](../../01-requirements/non-functional-requirements.md#rv-nfr-015-초기-예상-영상-수))가 크지 않을 것으로 보이므로 이 위험은 현재 시점에는 낮게 평가하며, 규모가 실제로 병목이 되었을 때 재검토하는 것으로 위험을 이연한다(14번 항목). 또한 PostGIS·pgvector 같은 확장 기능은 지금 활성화하지 않으므로(11번 금지 사항, [#11 adr-backlog.md](../adr-backlog.md) Conditional 항목) 위치 검색·추천 고도화가 필요해지면 별도 ADR로 확장 여부를 재결정해야 하는 부담이 남는다.

## 9. 적용 범위

MVP의 Restaurant, Creator, Video, Visit와 관리자 인증에 필요한 영속 데이터에 적용한다.

## 10. 강제 규칙

개발·운영 엔진 버전을 17.10으로 맞추고 고유성·참조 무결성을 DB 제약과 애플리케이션 검증으로 함께 보장한다.

## 11. 금지 사항

`latest`, RDS 기본 버전, 별도 공간·벡터 DB 또는 PostGIS·pgvector 선제 활성화를 금지한다.

## 12. 구현 및 운영 영향

운영 배치는 단일 EC2(Nginx + Spring Boot)와 별도의 Amazon RDS for PostgreSQL 17.10([#5 ADR-DATA-002](data-002-database-placement.md))이며, 백업은 일 1회 자동 스냅샷·7일 보관·RPO 24시간([#8 RV-NFR-010](../../01-requirements/non-functional-requirements.md#rv-nfr-010-백업-주기와-복구-범위))을 기준으로 한다. 장애 시 ALB/ASG 자동 복구는 도입하지 않고 운영자가 수동으로 인스턴스를 재기동·교체하므로, DB 연결 재시도와 커넥션 풀 설정도 이 수동 복구 시나리오를 전제로 설계한다. 용량·연결 수 설정은 예상 동시 사용자 수([#8 RV-NFR-001](../../01-requirements/non-functional-requirements.md#rv-nfr-001-목표-동시-사용자-수) 미확정)가 정해지는 대로 조정한다.

## 13. 검증 방법

Testcontainers로 PostgreSQL 17.10 컨테이너를 띄워 외래키·유니크 제약 위반, 동시 등록 트랜잭션 충돌, 롤백 시나리오를 검증한다. 응답 성능은 확정된 성능 기준([#8 RV-NFR-004](../../01-requirements/non-functional-requirements.md#rv-nfr-004-목표-응답-시간과-허용-오류율): 일반 조회 p95 500ms, 검색·필터 p95 800ms, 관리자 등록 p95 1초, 오류율 1% 미만)을 통과 기준으로 삼고, 조인이 많은 목록·검색 조회에서 이 기준을 충족하는지를 실제 17.10 엔진 기준으로 확인한다. 개발 Docker 이미지와 운영 RDS의 엔진 버전이 17.10으로 동일한지는 배포 파이프라인에서 대조한다.

## 14. 재검토 조건

지원 종료, 규모·기능 요구로 현재 엔진이 검증된 병목이 되거나 확장 기능이 승인될 때 재검토한다.

## 15. 관련 문서

- [#1 데이터 모델](../../05-specs/data/data-model.md)
- [#3 데이터 제약](../../05-specs/data/constraints.md)

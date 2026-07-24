---
id: ADR-DATA-002
title: 개발 Docker와 운영 RDS 데이터베이스 분리
status: Accepted
decision_date: 검토 필요
owners:
  - 이우람
related_requirements:
  - NFR-DEPLOYMENT-001
  - NFR-PRIVACY-002
related_documents:
  - ../../01-requirements/non-functional-requirements.md
  - ../../03-team/ownership.md
  - ../../06-architecture/technology-policy.md
  - data-001-postgresql.md
  - ../platform/runtime-001-docker.md
  - ../security/sec-001-secrets-workload-identity.md
  - ../adr-traceability.md
  - ../../00-overview/scope.md
supersedes: []
superseded_by: null
---

# ADR-DATA-002 개발 Docker와 운영 RDS 데이터베이스 분리

## 1. 상태

Accepted

## 2. 결정 요약

개발은 Docker PostgreSQL 17.10, 운영은 Amazon RDS for PostgreSQL 17.10을 사용하며 접속 정보와 데이터는 분리한다.

## 3. 배경

초기 월 인프라 예산은 150,000원([adr-traceability.md](../adr-traceability.md))이며 운영 배포는 단일 EC2 인스턴스(Nginx + Spring Boot, ALB/ASG 미도입, 장애 시 수동 재기동)로 결정되어 있다([technology-policy.md](../../06-architecture/technology-policy.md) 13절, 2026-07-24). 이 예산 범위 안에서 관리형 백업([RV-NFR-010](../../01-requirements/non-functional-requirements.md#rv-nfr-010-백업-주기와-복구-범위): 일 1회 스냅샷, 7일 보관, RPO 24시간)을 직접 구현하지 않고 확보하려면 운영 DB는 자체 구축 서버가 아닌 관리형 서비스가 유리하다. 한편 팀원 4명이 각자 로컬에서 독립적으로 개발·테스트를 반복해야 하므로([scope.md](../../00-overview/scope.md) 범위 경계 원칙 6번) 개발 환경은 비용이 들지 않고 즉시 재현 가능해야 한다. 이 둘의 요구가 다르기 때문에 환경별 배치 분리가 필요하다.

## 4. 결정 문제

같은 DB 엔진을 개발과 운영에 어떤 배치로 제공하고 환경 경계를 어떻게 강제할 것인가.

## 5. 고려한 선택지

- **개발 Docker / 운영 RDS**: 개발은 팀원 각자 로컬에서 무료로 즉시 기동·초기화·삭제가 가능하고, 운영은 RDS의 자동 스냅샷 기능으로 [RV-NFR-010](../../01-requirements/non-functional-requirements.md#rv-nfr-010-백업-주기와-복구-범위)의 백업 요구를 별도 구현 없이 충족한다. RDS 요금은 인스턴스 등급을 최소화하면 150,000원 예산 안에서 운용 가능한 범위다.
- **모든 환경 RDS 공유**: 개발 단계부터 RDS를 쓰면 환경 차이는 사라지지만, 팀원 4명이 각자 개발 중 스키마를 실험하거나 데이터를 초기화하는 일이 잦은 MVP 초기에는 공유 인스턴스에 대한 동시 접근 충돌 위험이 커지고, 무엇보다 인스턴스를 상시 띄워두는 비용이 150,000원 예산에서 개발용으로 소모되어 운영 여유가 줄어든다. 4인이 독립적으로 개발 가능해야 한다는 원칙과도 맞지 않는다.
- **개발·운영 서로 다른 엔진 또는 버전**: 예를 들어 개발은 최신 PostgreSQL 이미지를, 운영은 RDS 기본 제공 버전을 그대로 쓰는 방식. 구성이 간단해 보이지만 마이너 버전 차이로 인한 SQL 함수·기본값 동작 차이를 배포 시점에야 발견할 위험이 있고, 이는 팀원별 PostgreSQL 숙련도가 확인되지 않은 상태에서 디버깅 부담을 키운다. [ADR-DATA-001](data-001-postgresql.md)에서 17.10으로 버전을 고정한 목적 자체가 이런 환경 간 불일치를 막는 것이므로 이 대안은 채택하지 않는다.

## 6. 결정

환경별 배치는 분리하되 PostgreSQL 17.10과 Flyway 스키마 기준은 일치시킨다.

## 7. 선택 근거

개발 Docker는 팀원 각자가 비용 없이 반복 초기화할 수 있어 4인 독립 개발 요건을 충족하고, 운영 RDS는 150,000원 예산 안에서 자동 스냅샷·패치 관리 같은 운영 부담을 관리형 서비스에 위임해 [RV-NFR-010](../../01-requirements/non-functional-requirements.md#rv-nfr-010-백업-주기와-복구-범위)의 백업 요구를 별도 구현 없이 만족시킨다. 두 환경의 엔진 버전을 17.10으로 강제 일치시킨 이유는, 배치는 다르더라도 엔진 동작이 달라지면 개발에서 통과한 마이그레이션·쿼리가 운영에서 실패할 수 있고, 이를 사전에 검증할 여력이 4인 소규모 팀에는 크지 않기 때문이다.

## 8. 트레이드오프

가장 실질적인 위험은 환경 드리프트다 — 로컬 Docker와 운영 RDS는 네트워크 구성, 파라미터 그룹, 확장 설치 권한, 커넥션 제한이 서로 다르며, 개발에서 문제없던 쿼리나 마이그레이션이 운영에서만 실패할 수 있다. 이를 엔진 버전을 17.10으로 양쪽 다 고정해 최소화하지만(SQL 문법·기본 함수 동작 차이는 없앨 수 있음), RDS 고유의 네트워크·IAM 권한·파라미터 그룹 차이는 버전 고정만으로는 없어지지 않으므로 배포 전 스테이징 검증이 여전히 필요하다. 비용 측면에서는 RDS가 EC2에 자체 PostgreSQL을 설치하는 것보다 인스턴스 요금이 더 들지만, 자동 백업·패치·장애 복구를 자체 구현하지 않아도 되는 대가로 받아들인다. 150,000원 예산 안에서 이 비용을 감당하기 위해 RDS 인스턴스는 최소 등급으로 시작하고, 트래픽이 늘어 예산 초과가 예상되면 등급 조정 여부를 재검토한다.

## 9. 적용 범위

개발·테스트·운영 DB 설정, 프로파일, 비밀값과 배포 검증에 적용한다.

## 10. 강제 규칙

운영 접속 정보는 비밀값으로 주입하고 개발은 운영 엔드포인트에 연결하지 않는다.

## 11. 금지 사항

개발 설정의 RDS 주소·운영 자격 증명, 운영 설정의 `localhost`·Docker 서비스명, 엔진 버전 불일치를 금지한다.

## 12. 구현 및 운영 영향

운영 RDS는 사설 서브넷에 두고 단일 EC2 애플리케이션에서만 접근을 허용한다. 백업은 [RV-NFR-010](../../01-requirements/non-functional-requirements.md#rv-nfr-010-백업-주기와-복구-범위)에 따라 RDS 자동 스냅샷을 일 1회, 7일 보관으로 설정하고 RPO 24시간을 목표로 한다. 장애 시 자동 페일오버(Multi-AZ 등)는 예산상 도입하지 않으며, EC2와 마찬가지로 운영자가 수동으로 상태를 확인하고 필요하면 스냅샷에서 복구하는 절차를 따른다. 로그는 14일 보관 정책과 별도로 DB 자체의 슬로우 쿼리·연결 로그는 RDS 파라미터 그룹에서 활성화 여부를 정한다.

## 13. 검증 방법

배포 파이프라인에서 개발·운영 설정 파일을 스캔해 개발 프로파일에 RDS 엔드포인트나 운영 자격 증명이, 운영 프로파일에 `localhost`나 Docker 서비스명이 섞이지 않았는지 확인한다. 두 환경의 PostgreSQL 엔진 버전이 17.10으로 동일한지 배포 전 대조하고, 운영 RDS는 사설 서브넷 바깥에서 접근이 불가능한지 네트워크 테스트로 확인한다. 백업 검증은 [RV-NFR-010](../../01-requirements/non-functional-requirements.md#rv-nfr-010-백업-주기와-복구-범위) 기준(일 1회 스냅샷, 7일 보관)대로 스냅샷이 실제로 생성되는지와, 스냅샷으로부터의 복구 절차가 RPO 24시간 이내에 완료되는지를 최소 1회 복구 훈련으로 확인한다.

## 14. 재검토 조건

운영 플랫폼 변경, RDS 지원·비용 문제 또는 승인된 배포 토폴로지 변경 시 재검토한다.

## 15. 관련 문서

- [기술 정책](../../06-architecture/technology-policy.md)
- [NFR](../../01-requirements/non-functional-requirements.md)

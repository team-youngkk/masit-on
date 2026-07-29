---
id: ADR-RUNTIME-001
title: Docker 기반 실행 환경
status: Accepted
decision_date: 2026-07-27
owners:
  - 이우람
related_requirements:
  - NFR-DEPLOYMENT-001
  - NFR-DEPLOYMENT-002
  - NFR-MAINTAINABILITY-003
related_documents:
  - ../../01-requirements/non-functional-requirements.md
  - ../../03-team/ownership.md
  - ../../06-architecture/technology-policy.md
  - lang-001-java-21-runtime.md
  - build-001-gradle-groovy.md
  - ../data/data-002-database-placement.md
  - ci-001-github-actions-quality-gate.md
  - deploy-002-validation-deployment-before-expansion.md
  - ../security/sec-001-secrets-workload-identity.md
  - ../adr-backlog.md
  - ../adr-traceability.md
  - web-003-routing-boundary.md
  - ../../02-analysis/mvp-workstreams.md
supersedes: []
superseded_by: null
---

# ADR-RUNTIME-001 Docker 기반 실행 환경

## 1. 상태

Accepted

## 2. 결정 요약

애플리케이션과 개발 의존 서비스의 재현 가능한 실행·배포 산출물은 Docker를 사용한다.

## 3. 배경

[ADR-DEPLOY-002](deploy-002-validation-deployment-before-expansion.md)에 따라 MVP 구현은 로컬 Docker 환경에서 통합 실행하고, M2부터 단일 EC2 인스턴스 위에 Nginx, Next.js와 Spring Boot를 함께 운영한다. 따라서 현재 단계의 우선 목적은 네 명의 개발자가 같은 PostgreSQL·Redis·애플리케이션 실행 환경을 재현하는 것이며, 동일 이미지를 운영 배포 산출물로 이어갈 수 있게 유지한다.

## 4. 결정 문제

애플리케이션과 로컬 PostgreSQL·Redis 실행 환경을 어떤 단위로 재현할 것인가. 단일 EC2 인스턴스라는 제한된 배포 대상 위에 애플리케이션을 어떤 형태의 산출물로 올릴지, 그리고 팀원 각자의 로컬 개발 의존 서비스(PostgreSQL, Redis)를 어떻게 실행할지를 함께 결정해야 한다.

## 5. 고려한 선택지

- **Docker 이미지·컨테이너**: 애플리케이션을 이미지로 빌드하고, 로컬 개발용 PostgreSQL 17.10·Redis 8.8도 컨테이너로 실행한다. 운영 EC2에는 ECR에서 받은 이미지를 컨테이너로 실행한다([technology-policy.md](../../06-architecture/technology-policy.md) 13장).
- **서버별 수동 런타임 설치**: EC2에 JDK·PostgreSQL 등을 직접 설치하고 배포마다 아티팩트만 교체하는 방식. 단일 인스턴스·수동 복구 체계에서는 장애 시 운영자가 인스턴스를 재기동하거나 교체해야 하는데, 수동 설치된 런타임은 그 재현 절차 자체가 문서 밖 암묵 지식이 되기 쉽다. 4인 중 누가 장애 대응을 하더라도 같은 결과를 내야 하므로, 설치 절차가 사람 기억에 의존하는 방식은 이 배포 구조와 맞지 않는다.
- **복잡한 오케스트레이션 플랫폼(Kubernetes 등) 선제 도입**: 단일 EC2 인스턴스·ALB/ASG조차 아직 도입하지 않은 배포 규모에서 오케스트레이션 플랫폼을 먼저 들이는 것은 운영 복잡도와 학습 비용이 15만 원 예산·4인 캡스톤 일정에 비해 지나치다. [technology-policy.md](../../06-architecture/technology-policy.md) 13장이 ALB·ASG조차 확장 단계로 미룬 상황에서 그보다 무거운 오케스트레이션을 먼저 쓰는 것은 범위에 맞지 않는다.

## 6. 결정

애플리케이션 이미지를 빌드하고 개발 PostgreSQL 17.10·Redis 8.8을 Docker로 실행한다. EC2·RDS·운영 Redis 구성은 M2부터 적용한다.

## 7. 선택 근거

단일 EC2·수동 복구라는 제한된 운영 체계에서는 "장애가 나면 무엇을 어떻게 다시 띄우는가"가 사람의 기억이 아니라 이미지 하나로 정의되어야 한다. Docker 이미지는 애플리케이션과 그 실행에 필요한 런타임 설정을 하나의 태그로 묶어 두므로, 운영자가 인스턴스를 재기동하거나 교체할 때 참조할 대상이 명확하다. 로컬 개발 의존 서비스(PostgreSQL·Redis)도 Docker로 통일하면, 팀원 4명이 각자 다른 OS·설치 방식으로 DB를 띄우면서 생기는 버전 차이(예: PostgreSQL 마이너 차이로 인한 SQL 동작 차이)를 배제할 수 있다. 15만 원 예산 안에서는 관리형 컨테이너 오케스트레이션 서비스 자체를 쓰기 어려우므로, EC2 한 대 위에서 직접 컨테이너를 실행하는 지금의 형태가 예산 제약과도 맞는다.

## 8. 트레이드오프

Docker를 쓰면 이미지 빌드·스캔·저장·정리라는 새로운 운영 작업이 생긴다. 단일 EC2·운영자 1명(CloudWatch 알림 수신자 1명, [technology-policy.md](../../06-architecture/technology-policy.md) 13장) 체계에서는 이 작업을 전담할 인력이 따로 없으므로, 이미지 관리가 소홀해지면 오래된 이미지가 EC2 디스크나 ECR 용량을 잠식하거나, 태그 관리가 흐트러져 "지금 운영 중인 이미지가 정확히 어떤 커밋인지" 알기 어려워질 위험이 있다. 이 위험은 11장의 금지 사항(임의 `latest` 사용 금지)과 13장의 검증 절차(태그·digest 대조)로 낮추고, 로그 14일 보관·백업 일 1회 스냅샷·7일 보관이라는 이미 결정된 운영 기준(2026-07-24 결정)과 같은 수준의 최소 운영 루틴으로 이미지 정리도 함께 관리한다. 실제 위험의 크기는 "이미지 누적으로 인한 디스크·비용 증가" 정도이며, 애플리케이션 자체의 가용성을 위협하는 수준은 아니다.

## 9. 적용 범위

MVP와 확장 단계에서는 백엔드·프론트엔드 이미지와 로컬 PostgreSQL·Redis 컨테이너에 적용한다. M2부터 단일 EC2의 Nginx·애플리케이션 컨테이너에도 적용한다.

## 10. 강제 규칙

명시 태그를 고정하고 최소 이미지, 비루트 실행, Healthcheck와 환경 외부 주입을 사용한다. 로컬 Docker Healthcheck는 `/internal/health/live`와 `/internal/health/ready`를 사용한다. ECR digest, CloudWatch Agent와 운영 배포 Smoke Test는 M2부터 적용한다.

## 11. 금지 사항

`latest`, 이미지 내 비밀값, 운영 설정의 Docker 서비스명, 개발 컨테이너의 운영 리소스 연결을 금지한다. 운영 비밀값은 Parameter Store SecureString([ADR-SEC-001](../security/sec-001-secrets-workload-identity.md))으로 주입하며 이미지에 굽지 않는다.

## 12. 구현 및 운영 영향

이미지 빌드·스캔·저장·정리, 프로세스 신호와 상태 확인이 필요하다. 단일 EC2·수동 복구 체계이므로 운영자가 EC2에 접속해 컨테이너를 재기동하는 절차, 그리고 그 절차에서 참조할 이미지 태그 목록(ECR)을 최신 상태로 유지하는 것이 실질적인 운영 부담이다. 이는 CloudWatch 알림을 받는 담당자 1명(2026-07-24 결정)의 대응 절차와 함께 문서화한다.

## 13. 검증 방법

깨끗한 이미지 빌드, 버전·비밀·취약점 검사와 컨테이너 기반 Smoke Test를 실행한다. 구체적으로는 CI에서 클린 빌드 컨텍스트로 이미지를 빌드해 성공 여부를 확인하고, 이미지 안에 평문 비밀값이나 `.env` 파일이 포함되지 않았는지 검사하며, 베이스 이미지가 명시 태그(운영 확정 시 digest)를 쓰는지 대조한다. 컨테이너 내부에서 `/internal/health/live`, PostgreSQL을 포함한 `/internal/health/ready`가 정상 응답하는지, 애플리케이션 시작 로그에 오류가 없는지를 Smoke Test로 확인한다. `/internal/**`이 인터넷 Nginx 경로에서 차단되는지도 배포 후 검사한다. p95 500ms/800ms/1초와 오류율 1% 기준은 운영 동급 단일 EC2, 기준 데이터 100%, WireMock 외부 Stub 환경의 별도 성능·부하 테스트에서 검증한다.

## 14. 재검토 조건

운영 플랫폼 전환 또는 컨테이너가 검증된 비용·성능 병목이 될 때 재검토한다. 트래픽이 늘어 [technology-policy.md](../../06-architecture/technology-policy.md) 13장이 예고한 확장 단계(ALB 도입, 다중 인스턴스)로 넘어가거나, 단일 EC2 인스턴스 운영 비용이 15만 원 예산을 초과하는 것이 확인되면 배포 토폴로지와 함께 이 ADR도 재검토한다.

## 15. 관련 문서

- [기술 정책](../../06-architecture/technology-policy.md)
- [NFR](../../01-requirements/non-functional-requirements.md)

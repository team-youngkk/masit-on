---
id: ADR-OBS-002
title: CloudWatch 제거 후 로컬 운영 관측 경계
status: Accepted
decision_date: 2026-09-03
owners:
  - 이우람
related_requirements:
  - NFR-AVAILABILITY-001
  - NFR-OBSERVABILITY-001
  - NFR-OBSERVABILITY-002
  - NFR-OBSERVABILITY-003
  - RV-NFR-009
  - RV-NFR-013
related_documents:
  - ../../01-requirements/non-functional-requirements.md
  - ../../06-architecture/technology-policy.md
  - ../adr-traceability.md
  - obs-001-logging-observability.md
  - ../../../infra/production/README.md
  - ../../08-planning/observability-retirement-runbook.md
supersedes:
  - ADR-OBS-001
superseded_by: null
---

# ADR-OBS-002 CloudWatch 제거 후 로컬 운영 관측 경계

## 1. 상태

Accepted

## 2. 결정 요약

SLF4J·Logback과 Spring Boot Actuator는 모든 단계에서 유지한다. 운영 환경에서는 CloudWatch Agent, CloudWatch Logs, custom metric, metric alarm과 CloudWatch 알람을 전달하는 Slack notifier를 사용하지 않는다. 운영자는 인스턴스 내부의 health endpoint와 로컬 로그로 장애를 확인한다.

## 3. 배경

현재 운영은 단일 EC2와 수동 복구를 전제로 한다. 2026년 8월 실측 비용에서 CloudWatch `MetricMonitorUsage`가 발생했고, 중앙 수집·알림을 유지하는 비용보다 현재 단계의 수동 운영 경계를 우선하기로 했다. 애플리케이션의 상태 판정과 로그 생성 자체는 CloudWatch와 독립적으로 동작하므로, 외부 수집 경로만 제거한다.

## 4. 결정 내용

- 애플리케이션의 구조화 로그, 요청 상관관계, 오류 분류와 Actuator health group은 유지한다.
- `/internal/health/live`, `/internal/health/ready`, `/internal/health/dependencies`는 기존처럼 인스턴스 내부·배포 smoke 용도로 사용한다. 외부 Nginx 차단과 loopback 경계도 유지한다.
- Docker 컨테이너 로그는 `json-file` `max-size=10m`, `max-file=3`으로 로컬 저장량을 제한하고, Nginx access/error 로그도 로컬에 남긴다.
- CloudWatch Agent 설치, `aws cloudwatch put-metric-data`, CloudWatch Logs 권한, CloudWatch metric alarm과 외부 Slack 운영 알림은 배포·Terraform에서 제거한다.
- 운영 장애 확인은 운영자가 health endpoint와 로컬 로그를 점검하는 방식으로 수행한다. 인스턴스 교체·재기동 시 로컬 로그가 유실될 수 있다는 점을 수용한다.
- 중앙 관측이나 외부 알림을 다시 도입할 때는 예상 비용, 보존 기간, 수신자, 장애·복구 절차를 포함한 새 ADR을 작성한다.

## 5. 영향과 트레이드오프

- 장점: Agent·custom metric·alarm·로그 수집에 따른 비용과 IAM 권한, 배포 복잡도가 줄어든다.
- 단점: 인스턴스 외부에서 과거 로그를 검색하거나 자동 알림을 받을 수 없다. 운영자는 SSH로 접속해 health와 로그를 확인해야 한다.
- 보안: CloudWatch 전용 `PutMetricData`와 Logs 권한을 제거하고, SSM·KMS·S3 secret·ECR 등 애플리케이션 실행에 필요한 권한은 유지한다.
- 복구: 배포 후 smoke와 애플리케이션 내부 health 검증은 유지하므로, CloudWatch 제거가 Docker healthcheck·배포 rollback·`/internal/**` 외부 차단을 바꾸지 않는다.

## 6. 검증 기준

- CI 배포 bundle과 bootstrap/hook에 CloudWatch Agent·custom metric 자산이 포함되지 않는다.
- Terraform에 CloudWatch alarm이 없고 EC2 runtime IAM에 CloudWatch·Logs 전용 권한이 없다.
- 배포 계약 테스트가 삭제된 경로의 재도입을 실패시키며 Docker Hub digest·SSH 단일 EC2·Redis endpoint 계약은 계속 통과한다.
- 운영자는 배포 후 `/internal/health/*`와 로컬 로그를 통해 애플리케이션·PostgreSQL·Redis 상태를 확인한다.

## 7. 관련 문서

- [RV-NFR-009 로그 보관 기간](../../01-requirements/non-functional-requirements.md#rv-nfr-009-로그-보관-기간)
- [RV-NFR-013 운영 알림 기준](../../01-requirements/non-functional-requirements.md#rv-nfr-013-운영-알림-기준)
- [운영 단일 EC2 인프라](../../../infra/production/README.md)
- [기술 정책](../../06-architecture/technology-policy.md)

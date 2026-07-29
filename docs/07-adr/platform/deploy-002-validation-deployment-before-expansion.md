---
id: ADR-DEPLOY-002
title: 초기 운영 배포 선행과 확장 단계별 인프라 반영
status: Accepted
decision_date: 2026-07-28
owners:
  - 이우람
related_requirements:
  - NFR-AVAILABILITY-001
  - NFR-AVAILABILITY-002
  - NFR-DEPLOYMENT-001
  - NFR-DEPLOYMENT-002
  - NFR-DEPLOYMENT-003
  - NFR-DEPLOYMENT-004
related_documents:
  - ../../00-overview/scope.md
  - ../../00-overview/service-overview.md
  - ../../01-requirements/non-functional-requirements.md
  - ../../02-analysis/mvp-workstreams.md
  - ../../06-architecture/technology-policy.md
  - ci-001-github-actions-quality-gate.md
  - runtime-001-docker.md
  - ../quality/obs-001-logging-observability.md
  - ../security/sec-001-secrets-workload-identity.md
  - ../adr-backlog.md
  - ../adr-index.md
  - ../adr-traceability.md
supersedes:
  - ADR-DEPLOY-001
superseded_by: null
---

# ADR-DEPLOY-002 초기 운영 배포 선행과 확장 단계별 인프라 반영

## 1. 상태

Accepted

## 2. 결정 요약

MVP 검증을 위한 **초기 운영 배포**(M2)를 다음 확장 단계 착수 전에 최초 운영 환경으로 먼저 수행한다. 초기에는 검증 참여자에게만 제한 공개하고, 검증을 통과한 같은 환경을 계속 운영한다. 이후 확장에서는 각 단계에 필요한 인프라 변경을 기능 변경과 함께 반영하고 검증한다. 3차 확장까지는 초기 운영 배포의 단일 인스턴스·수동 복구 구성을 유지하고, ALB·Blue-Green 무중단 배포는 3차 확장 이후 **배포 고도화** 단계에서 도입을 검토한다(착수 시점 합의는 3.1절, 실제 착수는 비용·일정 영향 검토 통과 후).

## 2.1. 배포 단계 명칭

이 ADR 이후 배포 단계는 다음 세 이름으로만 표기한다. `최종 배포`는 더 이상 사용하지 않는다.

| 단계 | 범위 | 인프라 |
|---|---|---|
| MVP | 기능 구현과 로컬 Docker 통합 검증 | AWS 미사용 |
| 초기 운영 배포 (M2) | 최초 운영 환경 제한 공개, 1~3차 확장까지 유지 | 단일 EC2, Nginx, 수동 복구 |
| 배포 고도화 | 3차 확장 이후 무중단 배포 전환 검토 (착수 시점 합의, 영향 검토 미완) | ALB, Blue-Green, 다중 인스턴스 |

## 3. 배경

[ADR-DEPLOY-001](deploy-001-release-sequencing.md)은 모든 확장 단계가 끝난 뒤 AWS 운영 배포를 한 번 수행하도록 결정했다. 이후 팀은 도메인·HTTPS·Nginx 리버스 프록시·운영 외부 API 키 설정을 포함하는 [M2 — MVP 검증 배포 완료](https://github.com/team-youngkk/masit-on/milestone/2)를 다음 확장 단계보다 먼저 수행하기로 승인했다.

이는 ADR-DEPLOY-001 7절의 재검토 조건 중 "팀이 별도 스테이징 배포를 승인"한 경우에 해당한다. 실제 공개 환경에서 MVP를 검증한 뒤 확장을 시작하고, 확장 과정에서 생기는 인프라 요구를 해당 단계에서 함께 확인할 필요가 있다.

### 3.1. 배포 고도화 착수 시점 합의 (2026-07-28)

팀 4인(이우람·양성훈·박진영·김인안) 전원이 3차 확장 이후를 배포 고도화 착수 시점으로 삼고 Blue-Green 무중단 배포를 목표 방식으로 하기로 합의했다. 기존 문서는 ALB·ASG·Blue-Green을 "트래픽·가용성 운영 근거가 생길 때 재검토할 확장 경로"로만 두었으므로, 이 합의는 착수 시점을 앞당겨 고정한다.

**합의 범위는 착수 시점과 목표 방식뿐이다.** [scope.md](../../00-overview/scope.md) 7절이 요구하는 다음 두 선행 검토는 수행하지 않았다.

- 3항 개발 비용·데이터·외부 연동·운영 영향 — ALB·다중 인스턴스 요금이 초기 월 인프라 예산 목표 150,000원에 미치는 영향을 산정하지 않았다.
- 4항 일정 영향 — 고도화 작업이 4차 이후 기능 일정에 미치는 영향을 확인하지 않았다.

따라서 이 합의는 착수 시점을 정한 것이고, **실제 착수는 위 두 검토를 통과한 뒤에 시작한다.** 검토 결과 단일 인스턴스 구성이 충분하거나 비용이 예산을 초과하면 착수 시점 합의를 철회한다. 이 문서는 합의된 사실만 기록하며 고도화 수행을 계약상 의무로 만들지 않는다.

다음은 확정하지 않으며 착수 시점의 별도 ADR로 결정한다.

- ALB·ASG의 구체적 토폴로지와 전환 절차
- 고도화 단계의 인프라 비용과 예산 반영 범위
- 무중단 배포 자동화의 상세 범위
- Nginx의 경로 라우팅 책임을 ALB가 대체할지 여부

## 4. 결정

- M2 초기 운영 배포에서 최초 운영 환경을 먼저 배포하고 검증 참여자에게 제한 공개한다.
- 초기 운영 배포 검증을 통과한 환경을 별도 재구축 없이 같은 운영 환경으로 계속 사용한다.
- 초기 운영 배포부터 EC2·ECR·RDS·CloudWatch와 운영 비밀정보 설정을 활성화한다.
- 초기 운영 배포에서 도메인·HTTPS·Nginx 리버스 프록시와 운영 외부 API 키를 설정하고 검증한다.
- 이후 각 확장 단계에서는 기능에 필요한 인프라 변경을 같은 단계에 반영하고 검증한다.
- GitHub Actions의 빌드·자동화 테스트 품질 게이트와 로컬 Docker 통합 검증은 초기 운영 배포 및 이후 확장 단계에서도 계속 유지한다.
- 단일 EC2, GitHub Actions → ECR → EC2, 운영 RDS와 CloudWatch라는 기존 기술 선택은 유지한다. 이번 결정은 적용 순서를 변경한다.
- 3차 확장까지는 단일 인스턴스와 수동 복구를 유지한다. ALB·Blue-Green 무중단 배포와 ASG 다중 인스턴스는 3차 확장 이후 배포 고도화 단계에서 도입을 검토한다. 착수 시점은 3.1절에서 합의했고, 실제 착수는 비용·일정 영향 검토를 통과한 뒤에 시작한다.

## 5. 영향

- ADR-DEPLOY-001이 최종 배포로 미뤘던 EC2·ECR·RDS·CloudWatch·도메인·HTTPS·Nginx·운영 외부 API 키 설정이 초기 운영 배포 시점부터 배포 범위가 된다.
- AWS 배포 경로는 확장 완료 뒤 한 번에 검증하지 않고 초기 운영 배포에서 먼저 검증한 뒤 단계별로 유지·변경한다.
- 로컬 Docker 환경은 개발과 통합 검증의 재현 가능한 기준으로 계속 사용한다.
- GitHub Actions 품질 게이트는 배포 순서와 관계없이 모든 배포 후보에 계속 적용한다.
- 초기 운영 배포 이후 확장 단계는 이미 운영 중인 환경을 기준으로 기능과 인프라 변경을 함께 검증한다.
- [NFR-AVAILABILITY-002](../../01-requirements/non-functional-requirements.md#nfr-availability-002-초기-운영-배포-가용성과-수동-복구)의 단일 인스턴스·수동 복구 기준은 초기 운영 배포부터 배포 고도화 전까지 적용된다. [NFR-AVAILABILITY-001](../../01-requirements/non-functional-requirements.md#nfr-availability-001-상태-확인과-장애-구분)의 `/internal/**` 차단 요구는 로컬 검증에서 초기 운영 배포로 적용 시점이 앞당겨진다.

## 6. 검증

- M2 초기 운영 배포 체크리스트에서 도메인·HTTPS·Nginx 리버스 프록시·운영 외부 API 키 설정을 확인한다.
- EC2에서 ECR 이미지를 사용해 애플리케이션을 실행하고 RDS·CloudWatch 연동을 검증한다.
- 공개 경로와 `/api/**` 라우팅, 외부에서 차단해야 하는 `/internal/**` 경계를 확인한다.
- 배포 전 GitHub Actions 품질 게이트와 로컬 Docker 통합 검증이 계속 통과하는지 확인한다.
- 각 확장 단계의 인프라 변경이 해당 단계의 기능 검증 및 복구 절차에 반영됐는지 확인한다.

## 7. 재검토 조건

검증 환경과 운영 환경을 분리하기로 결정하거나, 단일 EC2 토폴로지가 3차 확장 이전에 가용성·성능 요구를 충족하지 못하거나, 확장 단계의 인프라 변경을 독립 배포 주기로 분리할 필요가 생기거나, 월 인프라 비용이 승인된 예산을 초과하면 배포 순서와 환경 구성을 재검토한다.

3차 확장 이후 배포 고도화는 3.1절에서 착수 시점을 합의한 항목이며 아직 도입이 확정된 단계가 아니다. 비용·일정 영향 검토 결과 단일 인스턴스 구성이 충분하거나 비용이 예산 목표를 초과하면 착수 시점 합의를 철회한다. ALB·Blue-Green·ASG 다중 인스턴스의 구체적 토폴로지·전환 절차·비용은 착수 시점의 별도 ADR로 결정한다.

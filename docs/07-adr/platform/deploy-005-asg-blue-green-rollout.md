---
id: ADR-DEPLOY-005
title: ASG 기반 Blue-Green 운영 배포
status: Proposed
decision_date: 2026-08-18
owners:
  - 이우람
related_requirements:
  - NFR-AVAILABILITY-001
  - NFR-AVAILABILITY-002
  - NFR-DEPLOYMENT-002
  - NFR-DEPLOYMENT-003
  - NFR-DEPLOYMENT-004
  - NFR-SECURITY-001
  - NFR-SECURITY-003
related_documents:
  - ../../01-requirements/non-functional-requirements.md
  - ../../06-architecture/security-boundary.md
  - ../../08-planning/deployment-hardening-impact-review.md
  - deploy-002-validation-deployment-before-expansion.md
  - runtime-001-docker.md
  - ci-001-github-actions-quality-gate.md
supersedes: []
superseded_by: null
---

# ADR-DEPLOY-005 ASG 기반 Blue-Green 운영 배포

## 1. 상태

Proposed. 배포 고도화 구현의 목표 토폴로지와 전환 안전장치를 고정하기 위한 제안이다. 실제 운영 자원 전환과 기존 단일 EC2 폐기는 별도 승인과 리허설을 통과한 뒤 수행한다.

## 2. 결정 문제

현재 단일 EC2의 수동 배포를 유지하면서도 배포 중 중단과 인스턴스 장애 영향을 줄이는 운영 토폴로지와 자동 전환 경계를 정한다.

## 3. 제안 결정

```text
Internet
  -> ALB (ACM TLS)
  -> active target group
  -> blue 또는 green ASG
  -> 각 EC2의 Nginx
  -> loopback Spring Boot / Next.js
  -> 공유 RDS PostgreSQL 및 사설 Redis
```

- ALB는 TLS 종단, 상태 확인과 target group 전환을 담당한다. Nginx는 애플리케이션 경로 라우팅, 검증 세션 gate와 오류 응답 경계를 계속 담당한다.
- Blue는 Terraform이 관리하는 원본 ASG와 target group이다. Green은 CodeDeploy가 원본 ASG를 기준으로 생성하는 교체 환경과 target group이며, 배포 시 새 색상에서 이미지를 기동하고 health·smoke를 통과한 뒤 listener를 전환한다. 성공 후에도 blue를 즉시 복귀할 수 있도록 두 색상을 관찰 기간 동안 유지하고, 유휴 green 정리는 별도 runbook으로 수행한다.
- ASG는 상시 `min=1`, `desired=1`, `max=2`를 기본 운영값으로 둔다. 배포 전환 중에는 blue와 green이 동시에 존재할 수 있으며, 기존 색상은 관찰 기간 뒤 축소한다.
- Redis는 앱 인스턴스에 동거시키지 않고 사설 전용 인스턴스로 분리한다. 세션·Refresh Token·rate-limit 상태는 색상 전환 사이에 공유되어야 한다.
- 운영 배포는 GitHub Actions의 기존 build/test/ECR digest 검증과 `production` 승인 게이트를 유지하고, 승인 후 CodeDeploy가 green 생성·검증·전환을 수행하도록 확장한다.
- `/internal/**`은 ALB·Nginx의 인터넷 경계에서 계속 차단한다. ALB health check는 비밀정보를 반환하지 않는 별도 readiness 경로 또는 인스턴스 내부 검증 경로로 구성한다.
- Flyway 변경은 blue와 green이 동시에 실행할 수 있도록 expand → 애플리케이션 전환 → 별도 contract migration 순서를 따른다. 전환 실패 시 데이터 스키마를 자동 rollback하지 않는다.

## 4. 선택지

| 선택지 | 판단 | 이유 |
|---|---|---|
| 단일 EC2 + SSM 수동 배포 | 기존 유지 | 비용과 구성은 단순하지만 배포 중 중단과 인스턴스 장애를 해결하지 못한다. |
| ALB + 단일 앱 인스턴스 Blue-Green | 부분 대안 | 배포 중 중단은 줄이지만 인스턴스 장애 자동 복구는 제공하지 않는다. |
| ALB + Blue/Green ASG + CodeDeploy | 제안 채택 | 배포 전 검증, listener 전환, 실패 시 기존 색상 유지와 인스턴스 교체를 하나의 운영 경계로 묶는다. |

## 5. 보안·운영 규칙

- 인터넷에서 앱 인스턴스로 직접 접근하지 못하고 ALB 보안 그룹에서만 Nginx 포트로 접근한다.
- Nginx가 신뢰하는 `X-Forwarded-For`·`X-Forwarded-Proto`는 ALB 보안 그룹으로 제한된 경로에서만 사용한다.
- 앱 ASG는 RDS와 Redis에 필요한 포트만 접근하고, Redis는 앱 ASG 보안 그룹에서만 접근한다.
- launch template와 CodeDeploy hook은 ECR digest와 Parameter Store 경로를 사용하며 비밀값을 이미지·user data·로그에 기록하지 않는다.
- green health 실패 시 listener는 blue를 유지하고 green만 폐기한다. 전환 후 오류율·지연·readiness alarm이 임계값을 넘으면 listener를 blue로 되돌린다.
- 기존 단일 EC2는 새 환경에서 배포·복구·비용을 확인하기 전까지 제거하지 않는다.

## 6. 검증

- Terraform `fmt`·`validate`·saved plan에서 기존 RDS와 기존 운영 인스턴스가 교체·삭제 대상이 아님을 확인한다.
- blue와 green 각각에서 공개 API, 관리자 인증, 회원 refresh, webhook, deep link, `/internal/**` 외부 차단을 확인한다.
- green readiness와 Nginx smoke가 통과하기 전 listener가 바뀌지 않는지 확인한다.
- 의도적 health 실패와 전환 후 오류율 상승을 주입해 blue 유지·복귀와 green 폐기를 확인한다.
- Redis 재기동과 앱 인스턴스 교체 뒤 세션·Refresh Token·rate-limit 상태가 유지되는지 확인한다.
- expand 단계가 아닌 destructive migration이 배포 gate에서 차단되는지 확인한다.
- 비용은 실제 청구와 대조하고 ASG·ALB·Redis 전용 인스턴스의 상시 비용을 별도 기록한다.

## 7. 남은 승인·재검토 항목

- 이 ADR을 Accepted로 전환할 때 Redis 사설 접근 경로, ALB health 경로, CodeDeploy hook, ASG 용량과 비용 상한을 확정한다.
- 인증 owner는 ALB→Nginx proxy header와 쿠키 세션 경계를 검토한다.
- 데이터 owner는 모든 운영 migration의 expand/contract 호환성을 검토한다.
- 실제 운영 전환은 기존 단일 EC2 rollback 경로, 관찰 기간, 비용 알림과 담당자 승인을 포함한 별도 runbook을 통과해야 한다.

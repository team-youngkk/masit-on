---
id: ADR-DEPLOY-005
title: ASG 기반 Blue-Green 운영 배포
status: Superseded
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
superseded_by: ADR-DEPLOY-007
---

# ADR-DEPLOY-005 ASG 기반 Blue-Green 운영 배포

## 1. 상태

Superseded in the deployment alarm boundary by [ADR-DEPLOY-007](deploy-007-codedeploy-health-alarm-boundary.md). Blue-Green 토폴로지와 그 밖의 전환 안전장치는 계속 유효하며, 실제 운영 자원 전환과 기존 단일 EC2 폐기는 별도 승인과 리허설을 통과한 뒤 수행한다. 이 ADR의 CloudWatch Agent·custom metric·deployment alarm 상세는 [ADR-OBS-002](../quality/obs-002-local-operations-without-cloudwatch.md)에 따라 2026-09-03 현재 운영 계약이 아니며, ASG를 다시 활성화할 때 별도 관측성 ADR로 재검토해야 한다.

## 2. 결정 문제

현재 단일 EC2의 수동 배포를 유지하면서도 배포 중 중단과 인스턴스 장애 영향을 줄이는 운영 토폴로지와 자동 전환 경계를 정한다.

## 3. 제안 결정

```text
Internet
  -> ALB (ACM TLS)
  -> active target group
  -> original 또는 replacement ASG
  -> 각 EC2의 Nginx
  -> loopback Spring Boot / Next.js
  -> 공유 RDS PostgreSQL 및 사설 subnet 전용 Redis
```

- ALB는 TLS 종단과 상태 확인을 담당하며, 하나의 target group을 계속 가리킨다. Nginx는 애플리케이션 경로 라우팅, 검증 세션 gate와 오류 응답 경계를 계속 담당한다.
- Blue는 Terraform이 관리하는 원본 ASG와 target group이다. EC2/On-Premises CodeDeploy는 원본 ASG를 복사해 replacement ASG를 만들고, 같은 target group에 replacement 인스턴스를 등록한 뒤 original 인스턴스를 해제한다. listener 전환이나 별도 green target group은 사용하지 않는다. 최초 seeding에서는 `codedeploy_termination_enabled=false`로 original을 유지하고, deployment group의 원본 ASG가 replacement로 갱신된 것을 확인한 뒤에만 `true`로 전환한다. 전환 후 성공한 배포는 관찰 대기 시간(기본 15분)이 지나면 CodeDeploy가 original 인스턴스와 ASG를 종료하며, 실패·중단된 배포가 남긴 환경은 별도 runbook으로 수행한다.
- ASG는 상시 `min=1`, `desired=1`, `max=2`를 기본 운영값으로 둔다. 배포 전환 중에는 original과 replacement가 동시에 존재할 수 있으며, 기존 환경은 관찰 기간 뒤 축소한다.
- Redis는 Blue-Green 전환 사이의 상태 공유를 위해 앱 인스턴스와 분리한 사설 subnet 전용 인스턴스로 운영한다. 2026-08-18 김인안·이우람 owner 재합의로 [ADR-DATA-005](../data/data-005-redis-refresh-token.md) 6절의 운영 배치를 개정했으며, 이 ADR과 함께 Accepted 운영 계약으로 취급한다.
- 운영 배포는 GitHub Actions의 기존 build/test/ECR digest 검증, Terraform `terraform-contract` 렌더링 게이트와 `production` 승인 게이트를 유지하고, 승인 후 CodeDeploy가 replacement 환경을 생성·검증·전환하도록 확장한다. 배포 ID는 실행별 S3 pointer에 먼저 보존해 취소 cleanup이 원격 deployment를 중단할 수 있어야 한다.
- `/internal/**`은 ALB·Nginx의 인터넷 경계에서 계속 차단한다. ALB health check는 비밀정보를 반환하지 않는 별도 readiness 경로 또는 인스턴스 내부 검증 경로로 구성한다.
- Flyway 변경은 blue와 green이 동시에 실행할 수 있도록 expand → 애플리케이션 전환 → 별도 contract migration 순서를 따른다. 전환 실패 시 데이터 스키마를 자동 rollback하지 않는다.

## 4. 선택지

| 선택지 | 판단 | 이유 |
|---|---|---|
| 단일 EC2 + SSM 수동 배포 | 기존 유지 | 비용과 구성은 단순하지만 배포 중 중단과 인스턴스 장애를 해결하지 못한다. |
| ALB + 단일 앱 인스턴스 Blue-Green | 부분 대안 | 배포 중 중단은 줄이지만 인스턴스 장애 자동 복구는 제공하지 않는다. |
| ALB + ASG replacement + CodeDeploy | 제안 채택 | 배포 전 검증, 단일 target group의 인스턴스 교체, 실패 시 기존 인스턴스 유지와 복구를 하나의 운영 경계로 묶는다. |

## 5. 보안·운영 규칙

- 인터넷에서 앱 인스턴스로 직접 접근하지 못하고 ALB 보안 그룹에서만 Nginx 포트로 접근한다.
- Nginx가 신뢰하는 `X-Forwarded-For`·`X-Forwarded-Proto`는 ALB 보안 그룹으로 제한된 경로에서만 사용한다.
- 앱 ASG는 RDS와 Redis에 필요한 포트만 접근하고, Redis는 앱 ASG 보안 그룹에서만 접근한다.
- launch template와 CodeDeploy hook은 ECR digest와 Parameter Store 경로를 사용하며 비밀값을 이미지·user data·로그에 기록하지 않는다.
- replacement health 실패 시 CodeDeploy가 original 인스턴스를 유지하고 replacement를 폐기한다. 배포 후 오류율·지연·readiness alarm이 임계값을 넘으면 deployment를 중지하고 original ASG membership를 복구 대상으로 삼는다. listener rollback은 사용하지 않는다.
- **ALB health check는 readiness만 반영하고 readiness에는 Redis가 없다.** 따라서 Redis 장애는 target health로 드러나지 않고, 공개 GET은 `200`이지만 인증은 [ADR-AUTH-007](../security/auth-007-unified-account-rbac-session.md) 12절대로 fail-closed가 되는 구간이 생긴다. 이 상태는 **배포 게이트로 감지하고 트래픽 경로로는 감지하지 않는다.** Redis는 fleet 전체가 인스턴스 하나를 공유하므로 readiness에 넣으면 모든 target이 동시에 unhealthy가 되어 Redis와 무관한 공개 탐색까지 전면 중단된다. 감지를 얻고 가용성을 잃는 교환이므로 채택하지 않는다.
- (역사적 설계) 감지 경로는 `masiton/health` 네임스페이스의 `FleetDependencyRedis` 지표였다. `health-metrics.sh`와 deployment alarm은 ADR-OBS-002에 따라 현재 배포에서 제거됐으며, ASG 재개 시 별도 관측성 ADR의 범위로 다시 결정한다.
- (역사적 설계) ALB의 `UnHealthyHostCount`와 5xx·지연·Redis deployment alarm 기준은 당시 Blue-Green 리허설을 위해 기록해 둔 값이다. 현재 단일 EC2 배포에는 적용하지 않는다.
- (역사적 설계) 최초 seeding의 `initial_alarm_seeding`·`deployment_alarms_enabled`·`deployment_auto_rollback_enabled`·`redis_recovery_mode` 조합은 현재 Terraform 운영 경로의 입력 계약이 아니다. ASG를 다시 활성화할 때 새 ADR과 plan 검증을 함께 추가한다.
- 기존 단일 EC2는 새 환경에서 배포·복구·비용을 확인하기 전까지 제거하지 않는다.

## 6. 검증

- Terraform `fmt`·`validate`·saved plan에서 기존 RDS와 기존 운영 인스턴스가 교체·삭제 대상이 아님을 확인한다.
- original과 replacement 인스턴스에서 공개 API, 관리자 인증, 회원 refresh, webhook, deep link, `/internal/**` 외부 차단을 확인한다.
- replacement readiness와 Nginx smoke가 통과하기 전 original target instance가 target group에서 해제되지 않는지 확인한다.
- 의도적 health 실패와 배포 후 오류율 상승을 주입해 original 유지·replacement 폐기와 ASG membership 복구를 확인한다.
- Redis 재기동과 앱 인스턴스 교체 뒤 세션·Refresh Token·rate-limit 상태가 유지되는지 확인한다.
- (역사적 검증 기록) 교체 환경의 `masiton-health-metrics.timer`, `FleetDependencyRedis`, 최초 seeding과 deployment alarm 전이 검증은 CloudWatch 관측성 경로가 운영 계약이었던 시점의 절차다. 현재는 실행하지 않으며 ASG 재개 시 새 관측성 ADR에서 검증 항목을 정한다.
- expand 단계가 아닌 destructive migration이 배포 gate에서 차단되는지 확인한다.
- 비용은 실제 청구와 대조하고 ASG·ALB·Redis 전용 인스턴스의 상시 비용을 별도 기록한다.

## 7. 남은 승인·재검토 항목

- Redis 사설 접근 경로, ALB health 경로, CodeDeploy hook, ASG 용량과 비용 상한은 이 ADR의 구현 계약으로 확정한다. 실제 AWS 적용과 리허설 결과는 별도 운영 증거로 남긴다.
- 인증 owner는 ALB→Nginx proxy header와 쿠키 세션 경계를 검토한다.
- 데이터 owner는 모든 운영 migration의 expand/contract 호환성을 검토한다.
- 실제 운영 전환은 기존 단일 EC2 rollback 경로, 관찰 기간, 비용 알림과 담당자 승인을 포함한 별도 runbook을 통과해야 한다.

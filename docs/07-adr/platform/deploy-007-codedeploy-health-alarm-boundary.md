---
id: ADR-DEPLOY-007
title: CodeDeploy 전환 중 ALB 호스트 상태 알람과 배포 게이트 분리
status: Accepted
decision_date: 2026-08-24
owners:
  - 이우람
related_requirements:
  - NFR-AVAILABILITY-001
  - NFR-AVAILABILITY-002
  - NFR-DEPLOYMENT-002
  - NFR-DEPLOYMENT-003
  - NFR-DEPLOYMENT-004
related_documents:
  - ../../01-requirements/non-functional-requirements.md
  - deploy-005-asg-blue-green-rollout.md
  - deploy-006-public-release-without-validation-gate.md
  - ../../08-planning/blue-green-cleanup-runbook.md
  - ../../08-planning/redis-recovery-runbook.md
supersedes:
  - ADR-DEPLOY-005 (deployment alarm boundary only)
superseded_by: null
---

# ADR-DEPLOY-007 CodeDeploy 전환 중 ALB 호스트 상태 알람과 배포 게이트 분리

## 1. 상태

Accepted. 2026-08-24 배포에서 같은 target group의 replacement 등록·기존 target
draining 중 `UnHealthyHostCount`가 ALARM↔OK로 반복되고 `ALARM_ACTIVE`로 배포를
중단한 운영 증적을 반영한다. [ADR-DEPLOY-005](deploy-005-asg-blue-green-rollout.md)의
Blue-Green 토폴로지와 나머지 장애 게이트는 유지하고, 배포 알람 경계만 이 ADR이
대체한다.

## 2. 결정

- `${name_prefix}-blue-unhealthy-host` CloudWatch alarm 리소스는 운영 관측용으로
  유지한다. metric, threshold, missing-data 정책과 ALB target group health check는
  변경하지 않는다.
- `UnHealthyHostCount` alarm은 CodeDeploy deployment group의 alarm 목록에서
  제외한다. CodeDeploy가 같은 target group을 전환하는 동안 target 등록·deregistering
  자체가 일시적인 비정상 host로 관측될 수 있기 때문이다.
- target 5xx, target latency, Redis dependency 및 Redis memory alarm은 기존처럼
  CodeDeploy 배포 게이트로 유지한다. Redis 복구 모드에서는 승인된 범위에서 Redis
  alarm 두 개만 일시 제외한다.
- ALB의 `/_masiton/alb-health` → `/internal/health/ready` 확인과 CodeDeploy의
  `AllowTraffic` 단계는 그대로 두어 replacement가 실제 트래픽을 받을 수 있는지
  검증한다. 이 ADR은 ALB health check나 트래픽 전환 검증을 완화하지 않는다.
- 자동 rollback과 `ignore_poll_alarm_failure=false`는 유지한다. 관측용
  `blue-unhealthy-host`가 ALARM이어도 배포를 자동 중단하지 않으므로, 운영자는
  target health와 target 5xx·latency를 함께 확인해 실제 장애를 판단한다.

## 3. 영향과 검증

Terraform의 `local.deployment_alarm_names`에는 `blue_unhealthy`를 넣지 않고,
`aws_cloudwatch_metric_alarm.blue_unhealthy` 리소스는 계속 선언한다. 배포 후에는
`aws deploy get-deployment-group`의 `alarmConfiguration`에서 해당 alarm이 빠지고,
CloudWatch alarm 자체·ALB target health·CodeDeploy `AllowTraffic` 검증이 남아 있는지
확인한다. 실제 target health 실패는 `AllowTraffic`, target 5xx 또는 latency 게이트로
실패해야 하며, 전환 중 일시적인 deregistration만으로 새 배포가 중단되어서는 안 된다.

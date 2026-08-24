---
id: ADR-DEPLOY-005
title: ASG 기반 Blue-Green 운영 배포
status: Accepted
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

Accepted. 배포 고도화 구현의 목표 토폴로지와 전환 안전장치를 고정한다. 실제 운영 자원 전환과 기존 단일 EC2 폐기는 별도 승인과 리허설을 통과한 뒤 수행한다.

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
- 감지 경로는 `masiton/health` 네임스페이스의 `FleetDependencyRedis` 지표다. `health-metrics.sh`가 1분 주기로 `Environment=asg` 차원으로 올리고, `Minimum`이 연속 3회 `1` 미만이면 deployment alarm이 된다. 이 alarm은 CodeDeploy deployment group의 alarm 목록에 포함되므로 **Redis가 끊긴 상태에서는 새 배포가 시작되지 않고 진행 중인 배포는 자동 rollback된다.** 이미 서비스 중인 트래픽은 끊지 않는다.
- ALB의 `UnHealthyHostCount` 배포 알람도 1분 주기 3회 연속 비정상일 때만 중단한다. replacement target 등록·draining 중 발생하는 단일 datapoint를 지속적인 장애와 구분하기 위한 운영 기준이며, 2026-08-24의 ALARM↔OK 반복 이력으로 근거를 보강했다. 5xx·지연·Redis 알람의 기준은 변경하지 않는다.
- 최초 seeding에서만 `initial_alarm_seeding=true`, `deployment_alarms_enabled=false`, `deployment_auto_rollback_enabled=false`, `redis_recovery_mode=false`를 같은 명령행에 명시해 deployment alarm과 자동 rollback을 제외한다. 네 입력은 앱 없는 seed ASG에 known-good revision을 올리는 단 한 번의 명시적 조합이며, Terraform precondition은 이 조합 외의 alarm·자동 rollback 비활성화와 `initial_alarm_seeding=false`·`deployment_auto_rollback_enabled=false`를 거부한다. 따라서 Redis 복구를 포함한 정상 운영에서는 alarm과 자동 rollback을 켜고, 지표 수집 중단도 감지 경로 장애이므로 deployment alarm의 결측을 `breaching`으로 처리해 새 배포를 막는다.
- 기존 단일 EC2는 새 환경에서 배포·복구·비용을 확인하기 전까지 제거하지 않는다.

## 6. 검증

- Terraform `fmt`·`validate`·saved plan에서 기존 RDS와 기존 운영 인스턴스가 교체·삭제 대상이 아님을 확인한다.
- original과 replacement 인스턴스에서 공개 API, 관리자 인증, 회원 refresh, webhook, deep link, `/internal/**` 외부 차단을 확인한다.
- replacement readiness와 Nginx smoke가 통과하기 전 original target instance가 target group에서 해제되지 않는지 확인한다.
- 의도적 health 실패와 배포 후 오류율 상승을 주입해 original 유지·replacement 폐기와 ASG membership 복구를 확인한다.
- Redis 재기동과 앱 인스턴스 교체 뒤 세션·Refresh Token·rate-limit 상태가 유지되는지 확인한다.
- 교체 환경 인스턴스에서 `masiton-health-metrics.timer`가 활성이고 `FleetDependencyRedis`가 실제로 올라오는지 확인한다. 최초 seeding은 `terraform plan -var="initial_alarm_seeding=true" -var="deployment_alarms_enabled=false" -var="deployment_auto_rollback_enabled=false"` 명령으로만 실행하고, 성공 직후 `initial_alarm_seeding=false`·`deployment_alarms_enabled=true`·`deployment_auto_rollback_enabled=true`로 복원한다. 정상 운영에서 Redis를 의도적으로 끊거나 지표 수집을 중단해 deployment alarm이 `ALARM`으로 전이하는지 확인한다.
- expand 단계가 아닌 destructive migration이 배포 gate에서 차단되는지 확인한다.
- 비용은 실제 청구와 대조하고 ASG·ALB·Redis 전용 인스턴스의 상시 비용을 별도 기록한다.

## 7. 남은 승인·재검토 항목

- Redis 사설 접근 경로, ALB health 경로, CodeDeploy hook, ASG 용량과 비용 상한은 이 ADR의 구현 계약으로 확정한다. 실제 AWS 적용과 리허설 결과는 별도 운영 증거로 남긴다.
- 인증 owner는 ALB→Nginx proxy header와 쿠키 세션 경계를 검토한다.
- 데이터 owner는 모든 운영 migration의 expand/contract 호환성을 검토한다.
- 실제 운영 전환은 기존 단일 EC2 rollback 경로, 관찰 기간, 비용 알림과 담당자 승인을 포함한 별도 runbook을 통과해야 한다.

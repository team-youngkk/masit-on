---
related_documents:
  - ../08-planning/redis-recovery-runbook.md
  - ../../infra/production/terraform/codedeploy.tf
  - ../../infra/production/terraform/monitoring.tf
  - ../../src/test/java/com/masiton/deployment/RuntimeDeploymentContractTest.java
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# Issue #284 리뷰 후속: Redis 복구 모드의 ALB 보호 선행 조건

## 1. 개요

| 항목 | 내용 |
|---|---|
| 추적 이슈 | [Issue #284](https://github.com/team-youngkk/masit-on/issues/284) |
| PR | 현재 브랜치에 연결된 GitHub PR 없음 |
| 처리 일자 | 2026-08-22 |
| 범위 | 최종 리뷰 지적 1건: Redis 복구 모드와 배포 알람 게이트 비활성화의 동시 사용 차단 |
| 주 문제 유형 | 인프라·배포 |
| 기존 기록 | `docs/troubleshooting/`와 Redis 장애 복구 런북을 확인했다. 기존 기록은 Redis 알람·용량 감시와 break-glass 절차를 다루지만, 두 Terraform 입력의 잘못된 조합을 plan 단계에서 거부하는 기계적 가드는 없었다. |

## 2. 리뷰 지적 처리 결과

| 지적 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| 최종 리뷰 지적 | `redis_recovery_mode=true`일 때 `deployment_alarms_enabled=false`를 허용하면 ALB 5xx·latency·unhealthy-host 보호까지 꺼질 수 있음 | 인프라·배포 | 수정 필요 | CodeDeploy deployment group의 Terraform `lifecycle.precondition`으로 조합을 plan 단계에서 거부하고 집중 계약 테스트를 추가 | `RuntimeDeploymentContractTest` 통과, `git diff --check` 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 기존에는 없음. 잘못된 입력 조합이 별도 오류 없이 허용될 수 있었다.
- 발생 환경: `fix/issue-284-redis-recovery-observability` 브랜치의 운영 Terraform 모듈.
- 재현 조건: `redis_recovery_mode=true`와 `deployment_alarms_enabled=false`를 동시에 전달한다.
- 실제 결과: Redis alarm 두 개뿐 아니라 CodeDeploy deployment group의 전체 alarm 게이트가 비활성화될 수 있다.
- 기대 결과: Redis 복구 모드는 Redis alarm 목록만 제외하고, ALB 5xx·latency·unhealthy-host 보호와 polling 실패 차단을 유지해야 한다.
- 영향 범위: Redis 장애 복구 배포 중 ALB 오류율·지연·비정상 호스트를 감지하는 fail-closed 배포 보호.

## 4. 근본 원인

Terraform이 `redis_recovery_mode`와 `deployment_alarms_enabled`를 독립적인 boolean 입력으로만 선언하고 두 변수 사이의 금지 조합을 검증하지 않았다. 따라서 기존 로컬 alarm 목록은 Redis 복구 모드에서 Redis alarm만 제외했지만, 별도로 `deployment_alarms_enabled=false`를 주면 CodeDeploy의 전체 alarm 게이트가 꺼질 수 있었다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| Redis 장애 복구 런북 및 monitoring/codeDeploy Terraform 확인 | 복구 명령은 두 변수 모두 `true`로 사용하고, ALB 3개 alarm과 `ignore_poll_alarm_failure=false`를 유지하도록 명시한다 | 런북 의미는 유지하고 입력 경계만 코드로 보강 |
| 기존 `RuntimeDeploymentContractTest` 확인 | Redis alarm 제외·fail-closed·런북 명령은 검증하지만 잘못된 변수 조합 거부는 검증하지 않는다 | 동일 계약 테스트 클래스에 집중 테스트 추가 |
| Terraform CLI 확인 | 현재 실행 환경에 `terraform` 명령이 없다 | `fmt`·`validate`·실제 invalid plan 실행은 미실행으로 보고 |

## 6. 최종 해결

- `aws_codedeploy_deployment_group.app`에 다음 선행 조건을 추가했다: `!var.redis_recovery_mode || var.deployment_alarms_enabled`.
- 조건이 거짓이면 Terraform plan 단계에서 ALB 5xx·latency·unhealthy-host 보호를 유지해야 한다는 오류를 반환하도록 했다.
- 기존 Redis 복구 런북의 `redis_recovery_mode=true`·`deployment_alarms_enabled=true` 절차와 정상 fail-closed 감시 의미는 변경하지 않았다.
- 변경 파일: `infra/production/terraform/codedeploy.tf`, `src/test/java/com/masiton/deployment/RuntimeDeploymentContractTest.java`, 이 기록과 인덱스.

선택 이유는 AWS 자원 생성 전에 Terraform 자체가 잘못된 운영 입력을 차단하고, `deployment_alarms_enabled=false`·`deployment_auto_rollback_enabled=false`를 사용하는 최초 seeding 경로는 기존처럼 `redis_recovery_mode=false`에서 계속 허용하기 위해서다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew test --tests com.masiton.deployment.RuntimeDeploymentContractTest --no-daemon --console=plain` | 통과 | precondition 문자열과 기존 Redis 복구·fail-closed 계약 테스트 통과 |
| `git diff --check` | 통과 | whitespace 오류 없음 |
| `terraform fmt -check -recursive` | 미실행 | 환경에 Terraform CLI가 없음 |
| `terraform validate` 및 invalid 조합 plan | 미실행 | 환경에 Terraform CLI가 없음 |

## 8. 재발 방지 및 다음 확인

- Terraform precondition과 집중 계약 테스트로 `redis_recovery_mode=true`·`deployment_alarms_enabled=false` 조합을 plan 전에 차단한다.
- Terraform CLI가 설치된 CI의 기존 `terraform-contract` job에서 HCL 포맷·검증을 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 잘못된 Terraform 입력 조합의 plan 차단 | 기계적 차단 0건 | `redis_recovery_mode=true`, `deployment_alarms_enabled=false` invalid plan 시도 | 배포 후 확인 필요 | 로컬 Terraform CLI 부재로 실제 plan 수치는 미측정 | Terraform CLI가 있는 CI/운영 사전 검증 시 확인 |

## 10. 남은 사항

- GitHub PR이 없어 리뷰 스레드 답글·resolve 처리는 수행하지 않았다.
- Terraform CLI 부재로 `fmt`, `validate`, 실제 invalid plan 결과는 CI에서 확인해야 한다.

## 후속 PR

- [PR #289 Redis local fallback의 SSM port 오염](pr-289-redis-recovery-observability-review.md)에서 같은 운영 배포 경계의 후속 리뷰를 처리했다. 공유 Redis 모드가 아닐 때 SSM port가 단일 EC2 fallback으로 유입되던 문제를 세 producer와 회귀 테스트에서 보완했다.

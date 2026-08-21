---
related_documents:
  - ../../infra/production/README.md
  - ../../infra/production/terraform/monitoring.tf
  - ../../infra/production/terraform/codedeploy.tf
  - post-cutover-runtime-baseline.md
  - ../07-adr/data/data-005-redis-refresh-token.md
---

# 전용 Redis 장애 복구 runbook

이 문서는 전용 Redis 장애로 CodeDeploy 배포 게이트가 복구 배포까지 막을 때의
유일한 break-glass 진입점이다. 평상시에는 이 절차를 사용하지 않는다. 정상 감시는
`FleetDependencyRedis`·`RedisMemoryUtilizationPercent`의 결측을 포함해 fail-closed로
동작해야 하며, Terraform의 missing-data 정책과 CodeDeploy의 alarm polling 정책을
완화하지 않는다.

## 1. 정상 계약과 중단 기준

- 공개 맛집 탐색 GET은 DB와 ALB가 정상인 동안 계속 응답할 수 있다.
- 회원 로그인·토큰 재발급·세션·rate-limit은 Redis 장애 때 우회하지 않고
  fail-closed한다. 인증 성공이나 rate-limit 무력화를 복구 조건으로 삼지 않는다.
- Redis 연결 알람, 용량 알람, 지표 결측은 새 배포를 막고 진행 중인 배포는
  CodeDeploy rollback 대상으로 삼는다.
- `treat_missing_data = "breaching"`, `ignore_poll_alarm_failure = false`,
  `DEPLOYMENT_STOP_ON_ALARM`은 이 runbook으로도 바꾸지 않는다.

다음 중 하나라도 확인되면 즉시 중단한다. 복구 배포를 시작하지 않았으면 게이트를
끄지 않고 장애 대응으로 전환한다. 복구 배포를 시작했다면 추가 배포를 시도하지 않고
해당 배포의 자동 rollback 또는 known-good revision 복귀 결과를 확인한다.

- 공개 탐색이 예상과 다르게 동작한다.
- Redis 장애 중 회원 인증이 성공하거나 rate-limit이 우회된다.
- replacement target health 또는 앱 dependency health가 회복되지 않는다.
- plan에 alarm enabled 외 변경·삭제·교체가 포함된다.
- 승인 유효기간이 만료되거나 승인자 중 한 명이라도 확인되지 않는다.

## 2. break-glass 승인 계약

1. 장애 시각, 현재 deployment ID, 두 Redis 알람의 상태, 공개 탐색 응답, 로그인·토큰
   재발급의 fail-closed 결과를 운영 기록에 남긴다. Redis에 직접 접근할 수 있으면
   전용 Redis의 AUTH PING, `INFO memory`, AOF 상태와 앱 dependency health도 먼저
   기록한다.
2. 서로 다른 운영 담당자 2명이 같은 기록을 확인하고 승인한다. 한 사람이 두 승인란을
   채우거나 사후 승인을 대체할 수 없다.
3. 두 승인 시각부터 30분을 break-glass 유효기간으로 한다. 만료 뒤에는 이미 승인된
   plan·apply를 이어서 실행하지 말고 두 사람의 재승인을 받는다. 유효기간을 늘리기
   위해 alarm을 다시 끄는 것은 허용하지 않는다.
4. 유효기간 안에서 복구 목적의 CodeDeploy 배포는 **최대 한 번**만 실행한다. 실패한
   배포를 재시도하거나 두 번째 수동 배포를 만들지 않는다. 해당 배포에 연결된
   CodeDeploy 자동 rollback은 같은 복구 시도의 일부이며, 별도 복구 배포가 아니다.

## 3. 게이트의 일시적 해제와 한 번의 복구 배포

Terraform 작업 디렉터리는 `infra/production/terraform`이다. 먼저 현재 state를
갱신하고 plan을 저장한다. `deployment_alarms_enabled=false`는 명령행에서만 넘긴다.
`terraform.tfvars`, Terraform 리소스의 `treat_missing_data`, alarm 목록과
`ignore_poll_alarm_failure`를 수정하지 않는다.

```powershell
terraform plan -var="deployment_alarms_enabled=false" -out=redis-break-glass.tfplan
terraform show -no-color redis-break-glass.tfplan
terraform apply redis-break-glass.tfplan
```

plan에는 CodeDeploy deployment group의 alarm enabled 값에 대한 일시적 변경만 있어야
한다. 승인 기록에 plan 요약, 승인자 2명, 유효기간 만료 시각, 복구 revision과
deployment ID를 함께 적는다.

게이트가 꺼진 동안 known-good 또는 복구용 revision으로 CodeDeploy 배포를 **한 번만**
실행한다. 배포가 실패하거나 중단되면 자동 rollback 결과를 확인하고, rollback이
확정되지 않으면 수동 장애 대응으로 전환한다. 이 절차 안에서 두 번째 `create-deployment`
또는 재시도는 금지한다.

## 4. 복구 확인과 즉시 게이트 복원

복구 배포가 끝나면 다음을 모두 확인한다.

- 첫 `FleetDependencyRedis=1`과 유효한 Redis 용량 지표 3종이 수집된다.
- `masiton-prod-fleet-dependency-redis`와
  `masiton-prod-redis-memory-utilization`이 `OK`로 전환된다.
- 앱 dependency health, 공개 탐색, 회원 인증·토큰 재발급 smoke가 정상 계약을
  회복한다. Redis가 불안정하면 인증이 성공해서는 안 된다.

조건을 모두 충족한 즉시 같은 Terraform 작업 디렉터리에서 정상 게이트를 복원한다.
복구 확인이 실패하거나 유효기간이 끝났으면 break-glass를 연장하지 말고 이 단계로
넘어가지 않는다.

```powershell
terraform plan -var="deployment_alarms_enabled=true" -out=redis-break-glass-restore.tfplan
terraform show -no-color redis-break-glass-restore.tfplan
terraform apply redis-break-glass-restore.tfplan
aws deploy get-deployment-group `
  --application-name <application-name> `
  --deployment-group-name <deployment-group-name> `
  --query 'deploymentGroupInfo.alarmConfiguration.{Enabled:enabled,IgnorePollFailure:ignorePollAlarmFailure,Alarms:alarms}'
```

최종 출력에서 `Enabled=true`, `IgnorePollFailure=false`, 두 Redis alarm이 목록에
있는 것을 확인하고 승인 기록을 닫는다. 복원 apply가 실패하면 게이트가 복원됐다고
간주하지 말고 추가 배포 없이 수동 장애 대응으로 전환한다.

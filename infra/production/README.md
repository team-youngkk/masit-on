---
related_documents:
  - ../../docs/07-adr/platform/deploy-005-asg-blue-green-rollout.md
  - ../../docs/08-planning/deployment-hardening-impact-review.md
  - ../../docs/07-adr/quality/perf-003-isolated-performance-terraform.md
---

# 운영 ASG·Blue-Green Terraform

`terraform/`은 기존 VPC·subnet·AMI·RDS·Redis를 입력으로 받아 운영 ALB, 원본 ASG, CodeDeploy Blue-Green deployment group을 선언한다. 기존 단일 EC2·RDS·Redis를 자동으로 import하거나 삭제하지 않는다.

CodeDeploy가 원본 ASG를 기준으로 replacement 환경을 만들기 때문에 Terraform에는 원본 ASG와 하나의 target group을 둔다. EC2/On-Premises 배포에서는 replacement 인스턴스를 같은 target group에 등록하고 original 인스턴스를 해제한다. listener나 별도 green target group은 사용하지 않는다. `codedeploy_termination_enabled=false`인 최초 seeding에서는 original을 유지하고, replacement ASG 전환을 확인한 뒤에만 별도 apply로 자동 종료를 활성화한다.

이 문서와 Terraform 레이어는 Accepted [ADR-DEPLOY-005](../../docs/07-adr/platform/deploy-005-asg-blue-green-rollout.md)의 배포 고도화 구현이다. 전용 Redis 배치는 2026-08-18 owner 재합의로 개정된 Accepted [ADR-DATA-005](../../docs/07-adr/data/data-005-redis-refresh-token.md) 6절을 따른다. 실제 운영 apply·데이터 이전·CodeDeploy 전환은 별도 runbook과 승인·리허설을 통과한 뒤 수행한다.

## Redis 장애 복구 진입점

전용 Redis 장애로 CodeDeploy 게이트가 복구 배포까지 막히면 [Redis 장애 복구 runbook](../../docs/08-planning/redis-recovery-runbook.md)을 유일한 break-glass 진입점으로 사용한다. 정상 감시는 fail-closed로 유지하며, 운영 담당자 2인의 승인·30분 유효기간·복구 목적의 단 한 번 배포·즉시 게이트 복원 계약을 지킨다. `treat_missing_data = "breaching"`, `ignore_poll_alarm_failure = false`와 회원 인증의 fail-closed 경계는 완화하지 않는다. `deployment_alarms_enabled=false`는 Redis 복구에 사용할 수 없고, 최초 seeding 명령에서만 `initial_alarm_seeding=true`·`deployment_alarms_enabled=false`·`deployment_auto_rollback_enabled=false`를 함께 허용한다.

## 적용 전 필수 확인

- CodeDeploy Agent가 AMI 또는 launch template `user_data`에서 설치·기동된다.
- 인스턴스 IAM role이 ECR, Parameter Store, KMS, SSM Agent에 필요한 최소 권한을 가진다.
- GitHub Actions OIDC role이 CodeDeploy revision을 올리고 실행별 deployment ID pointer를 기록·조회할 S3 prefix 권한과 지정 CodeDeploy application/deployment group의 `CreateDeployment`·상태 조회·`StopDeployment` 권한을 가진다. 배포 취소 cleanup은 이 pointer를 직접 읽어 수행한다.
- RDS 보안 그룹의 ingress를 이 모듈이 관리할지 `manage_rds_ingress_rule`로 명시한다. **새 ASG가 기존 RDS에 접근하려면 이 값이 `true`여야 한다.** 끄면 SG ingress에서 drop되어 backend가 Flyway 연결 timeout으로 기동에 실패한다. Redis ingress는 전용 Redis를 소유하는 `../terraform-redis` 레이어가 관리하므로 `manage_redis_ingress_rule`은 `false`로 둔다.
- ACM을 연결한 ALB는 Nginx 포트 `443`으로 재암호화해 전달하고 Spring Boot `8080`에 직접 연결하지 않는다. 현재 승인된 운영 예시는 public app subnet(`app_subnet_is_private=false`)이며, private 모드를 선택하면 `0.0.0.0/0 -> NAT gateway` 경로가 필수다. CodeDeploy·SSM·ECR·S3 VPC endpoint는 별도 보조 경로로 관리하고, endpoint-only private 토폴로지는 현재 postcondition에서 지원하지 않는다.
- `/_masiton/alb-health`는 backend readiness status만 반영하는 비민감 ALB health 응답이며 `/internal/**` 경계를 외부에 노출하지 않는다.
- `user_data`·AMI·Parameter Store에 실제 비밀값을 기록하지 않는다. `REQUIRE_SHARED_REDIS=true`, 공유 Redis endpoint, ALB subnet CIDR를 `/etc/masiton/deployment.env`에 설정해야 한다.
- revision bucket은 이 모듈이 versioning·SSE·Public Access Block·TLS-only policy로 관리한다. GitHub Actions role은 OIDC trust와 `id-token: write`를 별도로 유지하고, 이 모듈에 role 이름을 넘기면 revision·deployment ID pointer prefix의 S3 Put/Get과 지정 CodeDeploy 앱/그룹의 생성·상태 조회·중단 권한을 추가한다.
- Replacement ASG는 `COPY_AUTO_SCALING_GROUP`으로 CodeDeploy가 생성하므로 Terraform state가 소유하지 않는다. 최초 apply·최초 배포에서는 `codedeploy_termination_enabled=false`를 유지해 `KEEP_ALIVE`로 seeding한다. 배포가 성공한 뒤 CodeDeploy deployment group의 `autoscaling_groups`가 Terraform 소유 `${name_prefix}-blue-asg`가 아닌 replacement ASG를 가리키고, replacement의 target health·known-good revision을 확인하면 [정리 runbook](../../docs/08-planning/blue-green-cleanup-runbook.md)의 명령으로 Terraform seed ASG를 desired capacity 0으로 축소한다. 이 ASG는 `ignore_changes = [desired_capacity]` 대상이므로 tfvars만 바꾸지 말고 AWS CLI로 실행 중인 값을 먼저 낮춘다. seed ASG의 healthy target이 남아 있지 않은 것을 확인한 뒤에만 `codedeploy_termination_enabled=true`로 바꿔 별도 plan/apply한다. 이후 성공한 배포는 기본 15분 대기 후 CodeDeploy가 original 인스턴스와 ASG를 자동 정리한다. 실패·중단 배포와 `KEEP_ALIVE` 시기에 누적된 환경만 runbook의 수동 대상이다. Terraform plan에서 replacement ASG를 관리 대상으로 추가하지 않는다.
- 최초 apply에서는 Route53 alias가 기본으로 생성되지 않는다. blue에서 known-good revision을 기동하고 ALB health·외부 smoke를 확인한 뒤 `initial_blue_verified=true`를 별도 plan으로 승인해 DNS를 연결한다.
- 자동 rollback은 CodeDeploy deployment failure/stop-on-alarm 이벤트에 대해 켜져 있다. ALB health가 backend readiness를 반영하고, target 5xx·latency·`blue_unhealthy` CloudWatch alarm을 이 모듈에서 deployment group에 연결한다.

최초 전환은 다음 순서로 진행한다. `codedeploy_termination_enabled=false`로 apply하고, 앱 없는 seed ASG라서 alarm 게이트를 꺼야 한다면 다음 명령으로만 최초 known-good revision을 한 번 배포한다: `terraform plan -var="initial_alarm_seeding=true" -var="deployment_alarms_enabled=false" -var="deployment_auto_rollback_enabled=false"`. 배포 직후 `initial_alarm_seeding=false`·`deployment_alarms_enabled=true`·`deployment_auto_rollback_enabled=true`로 정상 게이트와 자동 rollback을 복원한다. 그 뒤 `aws deploy get-deployment-group`으로 deployment group의 원본 ASG가 replacement로 갱신됐는지 확인한다. Terraform seed ASG(`${name_prefix}-blue-asg`)가 계속 원본으로 남아 있으면 `true`로 바꾸지 않는다. replacement ASG와 target health를 확인하고 [정리 runbook](../../docs/08-planning/blue-green-cleanup-runbook.md)의 AWS CLI로 seed ASG를 desired capacity 0으로 축소한 뒤, seed에 healthy target이 남아 있지 않은 것을 재확인한다. 그 후에만 `codedeploy_termination_enabled=true`로 별도 plan/apply한다. 이 순서를 건너뛰면 `TERMINATE`가 Terraform state 소유 seed ASG를 삭제할 수 있다.

## 검증

```powershell
terraform init -backend=false
terraform fmt -check -recursive
terraform validate
```

실제 적용은 원격 state와 locking을 먼저 준비한 뒤 수행한다.

```powershell
terraform init `
  -backend-config=backend.hcl
terraform plan -out=production.tfplan
terraform show -no-color production.tfplan
```

`apply`는 plan에서 기존 운영 자원에 `replace`·`destroy`가 없는 것을 확인하고 별도 승인한 뒤 실행한다. 이 저장소 작업에서는 실제 AWS `apply`를 실행하지 않는다.


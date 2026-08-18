---
related_documents:
  - ../../docs/07-adr/platform/deploy-005-asg-blue-green-rollout.md
  - ../../docs/08-planning/deployment-hardening-impact-review.md
  - ../../docs/07-adr/quality/perf-003-isolated-performance-terraform.md
---

# 운영 ASG·Blue-Green Terraform

`terraform/`은 기존 VPC·subnet·AMI·RDS·Redis를 입력으로 받아 운영 ALB, 원본 ASG, CodeDeploy Blue-Green deployment group을 선언한다. 기존 단일 EC2·RDS·Redis를 자동으로 import하거나 삭제하지 않는다.

CodeDeploy가 원본 ASG를 기준으로 replacement 환경을 만들기 때문에 Terraform에는 원본 ASG와 하나의 target group을 둔다. EC2/On-Premises 배포에서는 replacement 인스턴스를 같은 target group에 등록하고 original 인스턴스를 해제한다. listener나 별도 green target group은 사용하지 않으며, 배포 검증·관찰 뒤 유휴 replacement ASG를 삭제하는 절차를 별도로 수행해야 한다.

이 문서와 Terraform 레이어는 [ADR-DEPLOY-005](../../docs/07-adr/platform/deploy-005-asg-blue-green-rollout.md) Proposed 구현안이다. 특히 전용 Redis 배치는 Accepted [ADR-DATA-005](../../docs/07-adr/data/data-005-redis-refresh-token.md) 6절과 충돌하므로 owner 재합의와 ADR Accepted 전환 전에는 운영 적용하지 않는다.

## 적용 전 필수 확인

- CodeDeploy Agent가 AMI 또는 launch template `user_data`에서 설치·기동된다.
- 인스턴스 IAM role이 ECR, Parameter Store, KMS, SSM Agent에 필요한 최소 권한을 가진다.
- GitHub Actions OIDC role이 CodeDeploy revision을 올리고 실행별 deployment ID pointer를 기록·조회할 S3 prefix 권한과 지정 CodeDeploy application/deployment group의 `CreateDeployment`·상태 조회·`StopDeployment` 권한을 가진다. 배포 취소 cleanup은 이 pointer를 직접 읽어 수행한다.
- RDS 보안 그룹의 ingress를 이 모듈이 관리할지 `manage_rds_ingress_rule`로 명시한다. **새 ASG가 기존 RDS에 접근하려면 이 값이 `true`여야 한다.** 끄면 SG ingress에서 drop되어 backend가 Flyway 연결 timeout으로 기동에 실패한다. Redis ingress는 전용 Redis를 소유하는 `../terraform-redis` 레이어가 관리하므로 `manage_redis_ingress_rule`은 `false`로 둔다.
- ACM을 연결한 ALB는 Nginx 포트 `443`으로 재암호화해 전달하고 Spring Boot `8080`에 직접 연결하지 않는다. 앱 private subnet은 NAT 또는 CodeDeploy·SSM·ECR·S3 VPC endpoint 경로를 가져야 한다.
- `/_masiton/alb-health`는 backend readiness status만 반영하는 비민감 ALB health 응답이며 `/internal/**` 경계를 외부에 노출하지 않는다.
- `user_data`·AMI·Parameter Store에 실제 비밀값을 기록하지 않는다. `REQUIRE_SHARED_REDIS=true`, 공유 Redis endpoint, ALB subnet CIDR를 `/etc/masiton/deployment.env`에 설정해야 한다.
- revision bucket은 이 모듈이 versioning·SSE·Public Access Block·TLS-only policy로 관리한다. GitHub Actions role은 OIDC trust와 `id-token: write`를 별도로 유지하고, 이 모듈에 role 이름을 넘기면 revision·deployment ID pointer prefix의 S3 Put/Get과 지정 CodeDeploy 앱/그룹의 생성·상태 조회·중단 권한을 추가한다.
- Replacement ASG는 `COPY_AUTO_SCALING_GROUP`으로 CodeDeploy가 생성하므로 Terraform state가 소유하지 않는다. `KEEP_ALIVE` 정책으로 original 인스턴스를 관찰 기간 동안 남기고, 승인된 관찰 시간이 끝난 뒤 CodeDeploy가 만든 유휴 replacement ASG를 runbook으로 정리한다. Terraform plan에서 이 교체 환경을 관리 대상으로 추가하지 않는다.
- 최초 apply에서는 Route53 alias가 기본으로 생성되지 않는다. blue에서 known-good revision을 기동하고 ALB health·외부 smoke를 확인한 뒤 `initial_blue_verified=true`를 별도 plan으로 승인해 DNS를 연결한다.
- 자동 rollback은 CodeDeploy deployment failure/stop-on-alarm 이벤트에 대해 켜져 있다. ALB health가 backend readiness를 반영하고, target 5xx·latency·blue/green unhealthy host CloudWatch alarm도 이 모듈에서 deployment group에 연결한다.

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

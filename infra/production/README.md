# 운영 단일 EC2 인프라

`terraform/`은 기존 VPC와 public subnet을 입력으로 받아 직접 서비스할 앱 EC2 한 대, EIP, 앱 security group, Route53 A record, CloudWatch health alarm을 준비한다. 현재는 첫 plan에서 기존 ALB·ASG·CodeDeploy 경로를 파괴하지 않도록 legacy 리소스도 함께 선언한다. 직접 경로의 state import·smoke·cutover를 확인한 뒤, 별도 plan에서 legacy 리소스를 정리한다.

PostgreSQL은 RDS에서 별도 EC2로 전환 중이며, 이 레이어는 PostgreSQL EC2를 생성하지 않는다. 앱 SG의 `database_security_group_id`에 PostgreSQL EC2 SG를 넣고, `/masiton/db/url`의 JDBC URL을 PostgreSQL EC2 endpoint로 바꾸는 것이 데이터 전환 작업이다. 전환 전까지는 `rds_security_group_id`와 기존 RDS ingress rule도 유지한다.

전용 Redis는 `terraform-redis/`의 private EC2를 사용한다. 앱은 `/masiton/redis/host`·`/masiton/redis/port`를 통해 Redis endpoint를 읽고 `REQUIRE_SHARED_REDIS=true`로 endpoint 누락을 fail-closed 처리한다. Redis 비밀번호는 현재 재기동 때마다 SSM Parameter Store에서 렌더링하므로, 안전한 대체 비밀 주입 경로가 마련되기 전에는 Redis SSM interface endpoint를 삭제하지 않는다.

## 배포 경계

- GitHub Actions는 `environment: production` 승인 뒤 기본적으로 기존 CodeDeploy Blue-Green으로 배포한다. 단일 EC2 전환 검증용 SSM Run Command는 `workflow_dispatch`에서 `deployment_target=ssm`을 명시한 경우에만 실행하며, 저장소 변수 `PRODUCTION_INSTANCE_ID`와 다른 instance ID는 거부한다.
- 배포 명령은 커밋 SHA와 함께 스크립트·Nginx·systemd 산출물을 전달하고 `app-deploy.sh`의 health check와 rollback 결과를 그대로 반환한다.
- 앱 EC2의 IAM role은 런타임 SSM/ECR/ACM/CloudWatch 권한을 갖고, GitHub Actions role은 legacy CodeDeploy 권한과 opt-in SSM 명령·취소·결과 조회 및 최소 S3 command pointer 권한을 병행해서 갖는다.
- Nginx가 TLS를 종단하고 외부의 80/443 요청을 직접 받는다. `/internal/**`은 계속 외부 `404`이며, legacy ALB target health용 `/_masiton/alb-health`는 ALB 정리 전까지 default server에만 보존한다.
- 앱 EC2와 Redis를 동거시키지 않는다. 앱은 `t4g.small`, Redis는 전용 인스턴스에서 실행한다.

## 이행 순서

1. PostgreSQL EC2의 private IP/SG와 백업·복구 절차를 확정하고 `/masiton/db/url`을 새 endpoint로 준비한다.
2. 기존 앱 EC2의 AMI, subnet, SG, IAM profile, root volume을 확인하고 `aws_instance.app`에 state import할 대상을 확정한다. 이 저장소는 import나 종료를 자동 실행하지 않는다.
3. 새 direct app SG의 PostgreSQL·Redis ingress가 각 대상 SG에 허용되는지 확인한다. Redis 레이어의 `app_security_group_ids`와 `ssm_endpoint_client_security_group_ids`에는 병행 기간 동안 legacy app SG와 direct app SG를 모두 넣는다.
4. 기존 앱 EC2를 `aws_instance.app`으로 import한 뒤 EIP를 연결하고, 기본 CodeDeploy 경로를 유지한 채 `deployment_target=ssm` 직접 배포로 앱·Nginx·health metrics를 검증한다.
5. `direct_traffic_enabled=true`를 별도 plan으로 적용해 Route53 A record가 EIP를 가리키는지 확인하고 외부 HTTPS/API/smoke를 검증한다.
6. PostgreSQL·앱·Redis가 안정화된 뒤에만 ALB·ASG·CodeDeploy·RDS의 실제 AWS 리소스를 삭제하는 별도 승인 plan을 만든다. 이 저장소에서는 `terraform apply`를 실행하지 않는다.

기존 CodeDeploy revision bucket은 기본 CodeDeploy revision과 SSM command pointer를 함께 보관하므로 `revision-bucket.tf`에 이행 자원으로 남긴다. bucket 삭제는 두 배포 경로와 보존 데이터 확인 후 별도 수행한다.

## 첫 plan 파괴 방지 게이트

준비 단계의 첫 plan은 `direct_traffic_enabled=false`로 만든다. `terraform show -json`으로
기존 ALB·ASG·launch template·CodeDeploy·Route53 record의 action에 `delete` 또는
`replace`가 없는지 확인한다. 아래 검사는 준비 plan에서 legacy 주소가 새로 삭제되거나
교체되는 경우 실패한다.

```powershell
terraform show -json preparation.tfplan | jq -e '[
  .resource_changes[]
  | select(.address | test("^(aws_lb|aws_lb_listener|aws_lb_target_group|aws_autoscaling_group|aws_launch_template|aws_codedeploy|aws_route53_record\\.app)"))
  | .change.actions
] | all(. == ["no-op"] or . == ["update"])'
```

이 게이트를 통과하고 직접 앱 EC2의 SSM 배포·health·외부 smoke를 확인한 뒤에만 DNS
전환 plan을 만든다. ALB·ASG·CodeDeploy·RDS 삭제는 DNS 전환 이후의 별도 정리 plan에서
만 검토한다.

## 비용 기준

서울 리전 730시간, 환율 1달러=1,470원 기준의 저장소 추정치는 [단일 EC2·PostgreSQL EC2 전환 계산](../../docs/08-planning/postgres-ec2-single-instance-transition.md)에 기록한다. ALB와 RDS를 제거하고 Redis를 전용 인스턴스로 유지하며 Redis SSM endpoint까지 제거한 목표 구성은 약 `$47.72/월`이며, PostgreSQL 데이터용 20 GiB EBS를 별도로 붙이면 약 `$49.54/월`이다. 데이터 전송·요청량·백업·공인 IPv4 외 추가 사용량은 별도다.

## 확인 명령

```powershell
Copy-Item .env.example .env
docker compose up -d postgres redis wiremock
.\gradlew.bat test --tests com.masiton.deployment.RuntimeDeploymentContractTest --tests com.masiton.deployment.AppRunScriptContractTest
```

운영 리소스에 대한 `terraform apply`, ALB/ASG/RDS/Redis 삭제, Redis 데이터 volume 조작은 이 저장소의 작업 범위에서 실행하지 않는다.

# 운영 단일 EC2 인프라

`terraform/`은 기존 VPC와 public subnet을 입력으로 받아 직접 서비스할 앱 EC2 한 대, EIP, 앱 security group, Route53 A record, CloudWatch health alarm을 관리한다. 운영 전환은 완료되었고, ALB·CodeDeploy 실행 경로와 관련 권한·알람은 Terraform에서 제거했다. 기존 blue seed ASG와 target group은 정리 런북의 보존 대상이라 state에 남겨 두며, 자동 배포에는 사용하지 않는다.

PostgreSQL은 별도 private EC2에서 실행하며, 이 레이어는 PostgreSQL EC2를 생성하지 않는다. PostgreSQL EC2에는 기존 RDS SG와 분리된 전용 DB SG를 사용하고, 앱 SG의 `database_security_group_id`에는 그 전용 SG를 넣는다. `/masiton/db/url`은 PostgreSQL EC2 endpoint를 가리킨다. `rds_security_group_id`와 기존 DB ingress rule은 호환·롤백 경계로 state에 남아 있다.

전용 Redis는 `terraform-redis/`의 private EC2를 사용한다. 앱은 `/masiton/redis/host`·`/masiton/redis/port`를 통해 Redis endpoint를 읽고 `REQUIRE_SHARED_REDIS=true`로 endpoint 누락을 fail-closed 처리한다. Redis 비밀번호는 버전 관리된 S3 SSE-KMS 객체를 기존 S3 Gateway Endpoint로 받아 재기동 때마다 `/run` tmpfs에 렌더링한다. 앱이 사용하는 기존 SSM Parameter Store 값은 유지하되, Redis는 SSM interface endpoint에 의존하지 않는다([ADR-SEC-002](../../docs/07-adr/security/sec-002-redis-bootstrap-secret-transport.md)).

## 배포 경계

- GitHub Actions는 `environment: production` 승인 뒤 단일 앱 EC2에 SSM Run Command로 배포한다. `push`와 `workflow_dispatch` 모두 이 경로를 사용하며, 저장소 변수 `PRODUCTION_INSTANCE_ID`와 다른 instance ID는 거부한다.
- 배포 명령은 커밋 SHA와 함께 스크립트·Nginx·systemd 산출물을 전달하고 `app-deploy.sh`의 health check와 rollback 결과를 그대로 반환한다.
- 앱 EC2의 IAM role은 런타임 SSM/ECR/ACM/CloudWatch 권한을 갖고, GitHub Actions role은 SSM 명령·취소·결과 조회 및 최소 S3 command pointer 권한만 갖는다.
- Nginx가 TLS를 종단하고 외부의 80/443 요청을 직접 받는다. `/internal/**`은 계속 외부 `404`이며, Route53 A record는 앱 EIP를 가리킨다.
- 앱 EC2와 Redis를 동거시키지 않는다. 앱은 `t4g.small`, Redis는 전용 인스턴스에서 실행한다.

## 전환 및 정리 결과

1. PostgreSQL과 Redis를 각각 전용 private EC2로 전환하고, SSM Parameter Store·S3 secret 객체의 앱·Redis 주입 경계를 확인했다.
2. 앱 EC2에 EIP를 연결하고 `direct_traffic_enabled=true`를 적용했다.
3. Route53·외부 HTTPS·API smoke 및 `/internal/**` 비노출을 확인했다.
4. ALB·리스너·ALB security group/rule·CodeDeploy 앱/그룹/IAM·레거시 알람을 삭제했다.
5. CodeDeploy transient ASG 5개와 레거시 앱 EC2 3대를 종료하고, 연결된 EBS volume도 삭제되었음을 확인했다.
6. RDS 인스턴스가 없는 상태에서 전환용 수동 스냅샷 3개를 삭제했다. PostgreSQL EC2의 데이터 volume과 Redis volume은 삭제하지 않았다.

기존 revision bucket은 SSM command pointer와 Redis secret 객체를 보관하므로 `revision-bucket.tf`에 남긴다. Redis secret 객체는 Terraform resource로 관리하지 않아 실제 비밀번호가 state에 들어가지 않게 한다. 객체 교체 시 기존 SSM Parameter Store 값도 함께 갱신하고 Redis 재시작·앱 health를 확인한다.

## 보존 리소스와 재검증

다음 리소스는 비용·운영 목적을 확인한 뒤 별도 변경으로 다룬다.

- `masiton-prod-blue-asg`와 `masiton-prod-blue` target group은 seed/이력 보존 대상이다.
- S3 revision bucket은 SSM command pointer를 보관하므로 유지한다.
- SSM·SSMMessages interface endpoint는 Redis secret 주입에 사용하지 않으며, 앱 SSM public 경로 검증 후 삭제 완료했다. S3 Gateway Endpoint는 Redis secret·배포 자산 경로로 유지한다.
- `masiton.click` hosted zone과 앱 EIP·앱/DB/Redis EC2·volume은 운영 대상이다.
- `roviq.click` hosted zone은 다른 프로젝트 소유이므로 이 저장소 정리 범위가 아니다.

Terraform 변경 뒤에는 `terraform plan`에서 위 보존 리소스에 `delete` 또는 `replace`가
없는지 확인한다. AWS 자원 삭제는 대상 ID를 먼저 확인하고 실행한다.

## 비용 기준

서울 리전 730시간 기준으로 앱·PostgreSQL·Redis EC2 3대, EBS, 앱 EIP, hosted zone을 유지하고 SSM·SSMMessages interface endpoint를 제거한 현재 구성은 기본 비용이 대략 `$37/월`부터로 추정된다. S3 Gateway Endpoint는 추가 endpoint 사용료가 없고, 데이터 전송·요청량·백업·공인 IPv4·KMS 요청 비용은 별도다. 실제 청구액은 Cost Explorer에서 확인한다.

## 확인 명령

```powershell
Copy-Item .env.example .env
docker compose up -d postgres redis wiremock
.\gradlew.bat test --tests com.masiton.deployment.RuntimeDeploymentContractTest --tests com.masiton.deployment.AppRunScriptContractTest
```

운영 중인 앱·PostgreSQL·Redis EC2와 volume은 삭제하지 않는다. seed ASG·target group과 S3 bucket은 유지한다. SSM·SSMMessages interface endpoint는 Redis 재부팅 검증과 앱 SSM public smoke를 통과해 삭제 완료했다.

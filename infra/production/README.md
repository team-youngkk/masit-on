# 운영 단일 EC2 인프라

`terraform/`은 기존 VPC와 public subnet을 입력으로 받아 직접 서비스할 앱 EC2 한 대, EIP, 앱 security group, Route53 A record, CloudWatch health alarm을 관리한다. 운영 전환은 완료되었고, ALB·CodeDeploy 실행 경로와 관련 권한·알람은 Terraform에서 제거했다. 기존 blue seed ASG와 target group은 정리 런북의 보존 대상이라 state에 남겨 두며, 자동 배포에는 사용하지 않는다.

PostgreSQL은 별도 private EC2에서 실행하며, 이 레이어는 PostgreSQL EC2를 생성하지 않는다. PostgreSQL EC2에는 기존 RDS SG와 분리된 전용 DB SG를 사용하고, 앱 SG의 `database_security_group_id`에는 그 전용 SG를 넣는다. `/masiton/db/url`은 PostgreSQL EC2 endpoint를 가리킨다. `rds_security_group_id`와 기존 DB ingress rule은 호환·롤백 경계로 state에 남아 있다.

전용 Redis는 `terraform-redis/`의 private EC2를 사용한다. 앱은 `/masiton/redis/host`·`/masiton/redis/port`를 통해 Redis endpoint를 읽고 `REQUIRE_SHARED_REDIS=true`로 endpoint 누락을 fail-closed 처리한다. Redis 비밀번호는 버전 관리된 S3 SSE-KMS 객체를 기존 S3 Gateway Endpoint로 받아 재기동 때마다 `/run` tmpfs에 렌더링한다. 앱이 사용하는 기존 SSM Parameter Store 값은 유지하되, Redis는 SSM interface endpoint에 의존하지 않는다([ADR-SEC-002](../../docs/07-adr/security/sec-002-redis-bootstrap-secret-transport.md)).

## 배포 경계

- GitHub Actions는 `main`의 CI 성공 뒤 `environment: production` 승인 후 Docker Hub에 backend/frontend 이미지를 커밋 SHA 태그로 게시하고, 게시 job이 반환한 두 개의 canonical digest 참조와 배포 산출물을 SSH로 앱 EC2에 전달한다. `workflow_dispatch`에서는 게시 job output을 사용하지 않고 지정한 태그를 Docker Hub에서 직접 pull해 digest로 확인한다. 두 경로 모두 `main`의 조상 커밋만 지정할 수 있어 수동 롤백·재배포에 사용한다.
- 배포에 `PRODUCTION_INSTANCE_ID`, AWS OIDC, ECR, SSM Run Command는 사용하지 않는다. 대상 계정의 public IPv4, SSH 사용자, private key, 검증된 known_hosts만 필요하다.
- GitHub 저장소 변수는 `DOCKERHUB_NAMESPACE`, `PRODUCTION_HOST`, `PRODUCTION_SSH_USER`를 사용하고, `production` environment secret은 `DOCKERHUB_USERNAME`, `DOCKERHUB_PUSH_TOKEN`, `DOCKERHUB_PULL_TOKEN`, `PRODUCTION_SSH_PRIVATE_KEY`, `PRODUCTION_SSH_KNOWN_HOSTS`를 사용한다. 이미지 게시에는 커밋 SHA 태그를 사용하고 결과는 digest로 고정한다. `images` job도 `production` environment에 속하며, Docker Hub backend/frontend 저장소의 tag immutability를 필수로 켠다. workflow도 이미 존재하는 SHA tag를 덮어쓰지 않고 실패시킨다.
- SSH 대상 서버는 Docker, `bash`, `tar`, `base64`, `curl`, AWS CLI, Python 3, `systemctl`, `sudo -n`을 제공해야 하며 workflow와 원격 wrapper가 Docker daemon·x86_64/amd64 플랫폼·필수 산출물·명령을 배포 전에 점검한다. 점검이 끝나기 전에는 Docker Hub 로그인이나 활성 파일 교체를 시작하지 않는다. 앱 runtime은 기존처럼 EC2 IAM role로 SSM Parameter Store·ACM·CloudWatch·Redis secret S3를 읽으므로, GitHub 배포 인증과 runtime 인증을 혼동하지 않는다.
- 운영 job은 x86_64 GitHub-hosted `ubuntu-24.04` runner에서 실행한다. 대상 security group의 22번 포트는 GitHub Actions runner가 접근할 수 있는 네트워크 경계에서만 허용하고, known_hosts는 신뢰할 수 있는 환경에서 fingerprint를 확인해 등록하며 CI에서 `ssh-keyscan`을 실행하지 않는다.
- 배포 bundle은 root 소유의 `/run/masiton/deploy` 아래 무작위 stage 최상위에 필요한 파일을 평탄화해 담고, 로컬 manifest·원격 SHA-256·압축 해제 결과를 순서대로 확인한다. 일반 SSH 사용자가 쓰는 `/tmp` archive는 사용하지 않는다. 배포 명령은 커밋 SHA와 함께 스크립트·Nginx·systemd 산출물을 전달하고 `app-deploy.sh`의 이미지 pull, health check, CloudWatch 지표 설치, rollback 결과를 그대로 반환한다. 이미지 pull 이후 활성 파일을 교체하는 단계에서 실패하거나 배포가 중단되면 이전 산출물과 관측성 상태로 복구하며, 임시 bundle·Docker 인증 설정·runner SSH 비밀 파일은 성공·실패와 관계없이 정리한다.

### 외부 AWS 계정 EC2 1회 사전 준비

대상 EC2가 이 저장소의 AWS 계정 소유가 아니면 이 저장소의 Terraform은 해당 인스턴스의
`key_name`, SSH 22번 ingress, `authorized_keys`, `sudoers`를 대신 만들 수 없다. 대상 계정
운영자가 다음을 먼저 준비해야 한다.

1. `PRODUCTION_SSH_USER`의 `authorized_keys`에 배포 public key를 등록한다.
2. GitHub Actions runner가 접근할 수 있는 제한된 egress 범위에서만 security group의 TCP 22를 연다.
3. 배포 사용자가 `sudo -n true`, `sudo -n docker info`를 통과하고 Docker·AWS CLI·Python 3·`systemctl`을 사용할 수 있게 한다.
4. 같은 host key fingerprint를 확인한 known_hosts 한 줄을 `PRODUCTION_SSH_KNOWN_HOSTS`에 등록한다.

workflow의 SSH preflight가 위 조건을 다시 검증하며, 조건이 맞지 않으면 Docker Hub
로그인이나 운영 파일 교체를 시작하지 않는다.
- Nginx가 TLS를 종단하고 외부의 80/443 요청을 직접 받는다. `/internal/**`은 계속 외부 `404`이며, Route53 A record는 앱 EIP를 가리킨다.
- 앱 EC2와 Redis를 동거시키지 않는다. 현재 x86_64 운영 프로파일은 앱 `t2.micro`,
  PostgreSQL 전용 EC2 `t2.nano`, Redis 전용 EC2 `t2.nano`다.
- 이 저장소의 `terraform/`과 `terraform-redis/` 레이어는 각각 앱과 Redis를 관리하지만
  PostgreSQL EC2는 관리하지 않는다.
  PostgreSQL 인스턴스 타입 변경은 AWS에서 별도 stop → modify → start 작업으로
  적용하고, 연결 수·FreeableMemory·디스크 여유·OOM 로그를 확인한다.
- 앱 `t2.micro`는 1GiB 호스트이므로 backend/frontend 컨테이너 메모리 상한은
  각각 `512m`/`256m`으로 둔다. 이 값은 무부하 기동만으로 안전성이 증명되지 않으므로
  적용 후 OOMKilled·호스트 OOM·재시작·health·지연을 함께 확인한다.

## 전환 및 정리 결과

1. PostgreSQL과 Redis를 각각 전용 private EC2로 전환하고, SSM Parameter Store·S3 secret 객체의 앱·Redis 주입 경계를 확인했다.
2. 앱 EC2에 EIP를 연결하고 `direct_traffic_enabled=true`를 적용했다.
3. Route53·외부 HTTPS·API smoke 및 `/internal/**` 비노출을 확인했다.
4. ALB·리스너·ALB security group/rule·CodeDeploy 앱/그룹/IAM·레거시 알람을 삭제했다.
5. CodeDeploy transient ASG 5개와 레거시 앱 EC2 3대를 종료하고, 연결된 EBS volume도 삭제되었음을 확인했다.
6. RDS 인스턴스가 없는 상태에서 전환용 수동 스냅샷 3개를 삭제했다. PostgreSQL EC2의 데이터 volume과 Redis volume은 삭제하지 않았다.

기존 revision bucket은 Redis secret 객체를 보관하므로 `revision-bucket.tf`에 남긴다. Redis secret 객체는 Terraform resource로 관리하지 않아 실제 비밀번호가 state에 들어가지 않게 한다. 객체 교체 시 기존 SSM Parameter Store 값도 함께 갱신하고 Redis 재시작·앱 health를 확인한다.

## 보존 리소스와 재검증

다음 리소스는 비용·운영 목적을 확인한 뒤 별도 변경으로 다룬다.

- `masiton-prod-blue-asg`와 `masiton-prod-blue` target group은 seed/이력 보존 대상이다.
- S3 revision bucket은 Redis secret 객체를 보관하므로 유지한다.
- SSM·SSMMessages interface endpoint는 Redis secret 주입에 사용하지 않으며, 앱 SSM public 경로 검증 후 삭제 완료했다. S3 Gateway Endpoint는 Redis secret·배포 자산 경로로 유지한다.
- `masiton.click` hosted zone과 앱 EIP·앱/DB/Redis EC2·volume은 운영 대상이다.
- `roviq.click` hosted zone은 다른 프로젝트 소유이므로 이 저장소 정리 범위가 아니다.

Terraform 변경 뒤에는 `terraform plan`에서 위 보존 리소스에 `delete` 또는 `replace`가
없는지 확인한다. AWS 자원 삭제는 대상 ID를 먼저 확인하고 실행한다.

## 비용 기준

서울 리전 730시간 기준의 기존 `$28.55/월` 산정은 ARM `t4g` 구성에 대한 값이므로,
현재 x86 `t2` 구성의 비용 근거로 사용하지 않는다. 새 인스턴스 타입과 실제 사용 중인
앱 EIP·EBS·Route 53·CloudWatch 비용은 적용 전에 Cost Explorer와 AWS 요금표로
재산정한다. Docker Hub 요금·pull 제한·데이터 전송·요청량·백업·KMS 요청 비용은
별도다.

## 확인 명령

```powershell
Copy-Item .env.example .env
docker compose up -d postgres redis wiremock
.\gradlew.bat test --tests com.masiton.deployment.RuntimeDeploymentContractTest --tests com.masiton.deployment.AppRunScriptContractTest
```

운영 중인 앱·PostgreSQL·Redis EC2와 volume은 삭제하지 않는다. seed ASG·target group과 S3 bucket은 유지한다. SSM·SSMMessages interface endpoint는 Redis 재부팅 검증과 앱 SSM public smoke를 통과해 삭제 완료했다. GitHub Actions용 `PRODUCTION_INSTANCE_ID`는 등록하지 않는다.

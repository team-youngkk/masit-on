# Issue #207 격리 성능 환경

이 디렉터리는 운영 환경에 부하를 보내지 않고 `NFR-PERFORMANCE-006`과 #207 성능 검증을 반복하기 위한 Terraform 구성을 담는다.

Terraform은 기존 VPC와 서브넷을 **읽기만** 한다. 운영 EC2, 운영 RDS, 운영 보안 그룹은 리소스로 선언하지 않는다. 생성되는 모든 리소스에는 `Environment=isolated-performance`, `Purpose=issue-207`, 실행별 `RunId` 태그가 붙고 이름도 `masiton-perf-207-` 접두사를 사용한다.

구성 범위는 다음과 같다.

- 기존 VPC 안의 성능 검증 전용 EC2 3대 — 측정 대상 앱, WireMock·Redis 의존, k6 부하 생성기
- 성능 검증 전용 RDS PostgreSQL
- 앱·의존·부하 생성기·RDS 사이의 최소 보안 그룹 규칙
- EC2 Instance Profile, SSM Parameter Store SecureString, ECR 읽기 권한
- 의존 인스턴스의 WireMock·Redis 컨테이너와 부하 생성기의 고정 버전 k6 부트스트랩

### 왜 3대인가

운영 앱 인스턴스는 backend·frontend·Nginx만 실행하고 Redis는 사설 서브넷의 전용 인스턴스에 있다. 성능 환경이 WireMock·Redis를 앱 호스트에 동거시키면 **호스트 메모리 여유와 Redis 왕복이 운영과 달라져 측정값이 운영을 대변하지 못한다.** [전환 후 런타임 실측 기준선](../../docs/08-planning/post-cutover-runtime-baseline.md) 5절이 이 조건을 정리했다.

`app_instance_type` 기본값은 운영 ASG와 같은 `t4g.small`이다. **컨테이너 메모리 제한이 JVM heap 상한과 GC 선택을 결정하므로**(같은 문서 2.1·2.2절) 백엔드를 기동할 때 운영과 같은 `--memory 1024m`, 프론트엔드는 `--memory 512m`를 사용해야 한다. 이 값이 다르면 heap과 GC가 달라져 비교가 성립하지 않는다.

의존 인스턴스의 Redis는 운영과 같은 `maxmemory 256mb`·`noeviction`·AOF·컨테이너 `--memory 384m`·`protected-mode yes`·`requirepass`로 기동한다. 운영과 다른 점은 비밀값의 출처뿐이며, 성능 전용 `requirepass`는 이 구성이 만드는 별도 SecureString에서 읽는다.

### Redis requirepass는 연결 조건이다

**앱이 다른 인스턴스에 있으므로 `requirepass` 없이는 Redis에 아예 붙지 못한다.** Redis 8.8은 `bind`를 명시했더라도 기본 사용자에 비밀번호가 없으면 protected mode에서 loopback 외 연결에 `DENIED Redis is running in protected mode`를 반환한다. `--bind 0.0.0.0`이 이 판정을 우회한다는 통념은 실제 동작과 다르다. 보안 그룹은 네트워크 경계일 뿐 이 판정에 관여하지 않는다.

비밀값은 명령행에 두지 않는다. 같은 인스턴스의 `ps`에서 읽히기 때문이다. 운영과 같이 기동 시점에 Parameter Store에서 읽어 tmpfs(`/run/masiton-perf/redis.conf`, `0400`, uid 999)에 렌더링하고 읽기 전용으로 마운트한다. 절차의 원문은 [deploy/scripts/redis-render-conf.sh](../../deploy/scripts/redis-render-conf.sh)이며, 성능 환경에서는 deps user-data가 설치하는 `/opt/masiton-perf/render-redis-conf.sh`가 같은 일을 한다.

`/run`은 tmpfs라 **의존 인스턴스를 재기동하면 설정이 사라진다.** 재기동했다면 Redis 컨테이너를 올리기 전에 `render-redis-conf.sh`를 다시 실행한다. 측정 도중 재기동이 일어났다면 그 구간의 결과는 쓰지 않는다.

### WireMock 관리 API 노출은 수용한 범위다

WireMock 8081은 인증이 없고 `0.0.0.0`에 바인딩되므로 **`/__admin`에 대해서는 app 보안 그룹 규칙 하나가 유일한 방어선이다.** 이전 구성은 `127.0.0.1`에 바인딩해 네트워크로 아예 도달할 수 없었으므로, 앱과 의존을 서로 다른 인스턴스로 분리한 지금은 노출 범위가 그때보다 넓다. app SG 안의 호스트에서 스텁을 런타임에 재정의·삭제할 수 있다.

측정이 끝나면 `terraform destroy`하는 임시 자원이고 app SG에서 실제로 도달 가능한 호스트가 앱 인스턴스 하나뿐이라는 전제에서 이 노출을 수용한다. WireMock 앞단에 인증 프록시를 두지 않는다. 대신 다음을 지킨다.

- deps 보안 그룹의 8081·6379 인바운드를 app SG 이외로 넓히지 않는다.
- **측정 중에는 `/__admin`을 호출하지 않는다.** 매핑이 바뀌면 이후 수집한 지연·에러율이 기준선과 다른 fixture를 대변한다. 매핑 확인은 백엔드 기동 전 사전 검증에서 한 번만 한다.
- 위 전제가 깨지면 — 측정 기간이 길어지거나 app SG에 도달 가능한 호스트가 늘어나면 — 관리 API 보호를 다시 판단한다.

백엔드 이미지 실행, `perf/seed` 적재, 시나리오 실행과 결과 기록은 Terraform의 책임이 아니다. 인프라가 준비된 뒤 SSM으로 실행하며, 실제 실행 결과는 [issue-207-isolated-performance-result.md](../../docs/08-planning/issue-207-isolated-performance-result.md)에 기록한다.

## 사전 조건

- Terraform 1.6.6 (`terraform/.terraform-version` 기준)
- AWS provider 5.100.0
- AWS CLI와 `masiton` SSO 프로파일
- 대상과 같은 리전(`ap-northeast-2`)의 AWS 자격 증명
- Terraform 상태를 저장할 암호화된 비공개 S3 backend와 DynamoDB locking table
- 성능 전용 RDS 비밀번호를 `TF_VAR_db_password` 환경 변수로 주입
- 성능 전용 Redis `requirepass`를 `TF_VAR_redis_password` 환경 변수로 주입. 16~128자이고 공백을 넣지 않는다
- 기존 VPC의 ID, public subnet 1개, private subnet 2개
- 실행할 ECR 이미지의 **digest 고정 URI**
- 운영 SSM 인터페이스 endpoint가 VPC 안에서 443을 허용하는 상태([infra/production/terraform-redis](../production/terraform-redis)의 `vpce_from_vpc` 규칙)

### SSM 경로는 운영 endpoint에 의존한다

운영 VPC에는 `private_dns_enabled = true`인 SSM 인터페이스 endpoint가 있어 `ssm.<region>.amazonaws.com`이 **VPC 전역에서** endpoint 사설 IP로 해석된다. 성능 인스턴스가 public subnet에 공인 IP를 갖고 있고 egress로 HTTPS를 전부 열어도, DNS가 인터넷 게이트웨이 경로를 쓰지 못하게 만든다. 따라서 **endpoint security group이 성능 인스턴스를 허용하지 않으면 SSM Agent 등록 자체가 실패하고, `send-command`가 `InvalidInstanceId`로 거부된다.**

운영 모듈의 `vpce_from_vpc` 규칙이 VPC CIDR에 443을 허용해 이 조건을 만족시킨다. 성능 SG를 실행마다 열거하지 않는 이유는 측정 1회마다 운영 모듈을 두 번 apply해야 하고, 그 규칙 변경이 2026-08-18 SSM 이탈 사고의 원인이었기 때문이다. 경위는 [ops-2026-08-20-perf-env-bootstrap-failure.md](../../docs/troubleshooting/ops-2026-08-20-perf-env-bootstrap-failure.md)에 있다.

`ssmmessages`·`ec2messages` endpoint는 두지 않는다. 성능 인스턴스는 운영 앱 인스턴스와 같이 public subnet에 공인 IP가 있어 그 도메인은 인터넷 게이트웨이로 나간다.

Terraform을 실행하기 전에 현재 계정과 리전을 확인한다.

```powershell
aws sso login --profile masiton
aws sts get-caller-identity --profile masiton
aws configure get region --profile masiton
```

## 실행

`terraform.tfvars.example`을 복사해 실제 값으로 채운다. 이 파일에는 비밀번호를 넣지 않는다.

```powershell
Set-Location infra/performance/terraform
Copy-Item terraform.tfvars.example terraform.tfvars
Copy-Item backend.hcl.example backend.hcl
$env:AWS_PROFILE = 'masiton'
terraform init -backend-config=backend.hcl
terraform fmt -check
terraform validate
```

backend는 S3 bucket `masiton-terraform-state-711457211155`와 DynamoDB table `masiton-terraform-state-lock`을 사용한다. 두 리소스는 이 Terraform state와 분리된 1회성 bootstrap 대상이며, 다음 조건을 먼저 확인한다.

- S3 bucket은 `ap-northeast-2`에 만들고 versioning, SSE 암호화, public access block을 켠다.
- DynamoDB table은 `LockID` 문자열 hash key와 `PAY_PER_REQUEST` billing mode로 만든다.
- bucket과 table이 생성·보호된 뒤에만 `terraform init -backend-config=backend.hcl`을 실행한다.

`backend.hcl.example`의 이름과 실제 bootstrap 결과가 다르면 로컬 `backend.hcl`만 실제 값으로 바꾼다. `backend.hcl`에는 자격 증명을 넣지 않는다.

`TF_VAR_db_password`와 `TF_VAR_redis_password`는 비밀 관리 도구에서 읽어 현재 프로세스에만 주입한다. `terraform.tfvars`나 명령행 인자에 비밀번호를 넣지 않는다.

계획에서 다음을 확인한 뒤에만 apply한다.

- 변경 대상이 `masiton-perf-207-` 리소스뿐인지
- 운영 인스턴스 ID `<production-app-instance-id>`, 운영 RDS `masiton-db`, 운영 보안 그룹이 변경 대상에 없는지
- 앱 8080 포트는 load generator 보안 그룹에서만 허용되는지
- 의존 인스턴스의 8081·6379가 app 보안 그룹에서만 허용되는지
- SecureString parameter가 DB·Redis 두 건뿐이고, deps 역할이 Redis parameter만 읽는지
- app egress가 HTTPS·VPC DNS·RDS 5432·의존 8081·6379만, loadgen egress가 HTTPS·VPC DNS·app 8080만, deps egress가 HTTPS·VPC DNS만 허용되는지
- 명시적 route table 연결이 없는 subnet은 VPC main route table로 fallback한 뒤, public subnet route table에 `0.0.0.0/0 -> internet gateway` 경로가 있고 private subnet route table에는 해당 IGW 기본 경로가 없는지
- RDS 보안 그룹에 egress 규칙이 없는지
- RDS가 `publicly_accessible=false`인지

## 인프라 준비 후

**apply가 끝난 것은 부트스트랩이 끝난 것이 아니다.** user-data는 `plan`으로 검증되지 않고 실패해도 apply는 성공으로 끝난다. SSM 에이전트 등록에 1~2분, user-data 완주에 몇 분이 더 걸리므로 다음을 먼저 확인한다.

```bash
aws ssm describe-instance-information --query 'InstanceInformationList[].{Id:InstanceId,Ping:PingStatus}' --output table
```

3대가 `Online`으로 보이지 않거나 `send-command`가 `InvalidInstanceId`로 거부되면 콘솔 출력을 읽어 user-data 실패를 확인한다. 2026-08-20에 이 경로로 `dnf` 패키지 충돌과 SSM endpoint 도달 실패를 진단했다([기록](../../docs/troubleshooting/ops-2026-08-20-perf-env-bootstrap-failure.md)).

```bash
aws ec2 get-console-output --instance-id <deps_instance_id> --output text --query Output
```

Terraform 출력의 `app_instance_id`로 SSM 명령을 실행해 다음 순서로 진행한다.

1. **백엔드를 기동하기 전에** 앱 인스턴스에서 `/opt/masiton-perf/check-dependencies.sh`를 SSM으로 실행한다. 이 스크립트는 Redis `requirepass`를 Parameter Store에서 읽어 앱 호스트에서 deps Redis로 RESP inline `AUTH`·`PING`을 보내 `Redis AUTH+PING: OK`를 확인하고, user-data가 커밋 고정 fixture archive의 SHA-256을 검증해 배포한 매핑이 WireMock `/__admin/mappings`에 로드됐는지 확인해 `WireMock mappings: OK`를 출력한다. 인증 실패는 `Redis AUTH rejected: -WRONGPASS ...`로 도달 불가와 구분된다. 확인에 실패하면 백엔드와 부하 테스트를 시작하지 않는다.
2. 앱 인스턴스의 `/opt/masiton-perf/`에 백엔드 이미지와 성능 전용 설정을 기동한다. `runtime.env`의 `WIREMOCK_BASE_URL`·`REDIS_HOST`는 의존 인스턴스를 가리킨다. 컨테이너 메모리 제한은 운영과 같은 값(`--memory 1024m`, 프론트엔드 `--memory 512m`)을 사용한다.

   **GC 로그는 측정 전용 추가 설정이다.** `runtime.env`의 `JAVA_TOOL_OPTIONS`가 컨테이너 안 `/var/log/masiton-gc/gc.log`에 기록하도록 지정하므로, 기동할 때 `-v /opt/masiton-perf/gc:/var/log/masiton-gc`를 함께 마운트해야 호스트에서 회수할 수 있다. 운영에는 이 옵션이 없다 — SerialGC full GC 정지를 지연 스파이크와 구분하기 위한 측정 목적이며, 로깅 오버헤드만큼 운영과 조건이 다르다는 점을 결과 문서에 적는다.
3. `perf/seed/`를 RDS에 적재하고 `ANALYZE`를 실행한다.
4. 같은 VPC의 `loadgen_instance_id`에서 k6 시나리오를 실행한다.
5. RDS·EC2·Redis·WireMock 증적과 k6 결과를 기록한다. 앱 인스턴스의 GC 로그(`/opt/masiton-perf/gc/`)와 `CPUCreditBalance`, Redis의 `used_memory`·`evicted_keys`·`rejected_connections`를 함께 남긴다. 갓 기동한 t4g 인스턴스는 CPU 크레딧이 0에서 시작하므로 warmup과 회복 대기를 거친 뒤 측정한다.

   **의존 컨테이너의 OOM·재시작 여부도 증적에 포함한다.** Redis는 `--memory 384m` 안에서 `maxmemory 256mb`와 AOF rewrite·RDB 스냅샷을 함께 돌리므로, 쓰기 부하 구간의 fork copy-on-write가 한도를 넘기면 컨테이너가 조용히 OOM-kill되고 `--restart unless-stopped`가 다시 살린다. 이 경우 그 구간의 지연·에러율은 측정 대상 조건을 대변하지 않으므로 결과를 무효로 판정한다.

   ```bash
   docker inspect --format '{{.State.OOMKilled}} {{.RestartCount}}' masiton-perf-redis masiton-perf-wiremock
   ```
6. 증적을 보존한 뒤 `terraform destroy`한다.

Terraform은 백엔드 기동과 시드 적재를 자동 실행하지 않는다. 이 단계를 분리해 두어 계획·적용 중 실수로 부하가 시작되지 않도록 했다. 실제 외부 Kakao·YouTube API를 호출하지 말고 WireMock만 사용한다.

WireMock fixture는 최신 PR HEAD를 따라가는 대신 Terraform 변수 `wiremock_fixture_commit`으로 검토된 Issue #207 기준 커밋을 고정하고, `wiremock_fixture_sha256`으로 GitHub archive 무결성을 검증한다. fixture를 변경할 때만 두 값을 함께 갱신하며, 검토되지 않은 fixture를 사용하지 않는다.

## 정리

이 구성은 성능 전용 임시 환경이다. 운영 상태를 확인하고 결과를 보존한 뒤 같은 작업 디렉터리에서 삭제한다.

```powershell
terraform plan -destroy -out issue-207-destroy.tfplan
terraform apply issue-207-destroy.tfplan
```

`terraform destroy`를 운영 상태를 확인하기 위한 일반 명령으로 사용하지 않는다. RDS는 `deletion_protection=false`, `skip_final_snapshot=true`로 설정되어 있어 이 구성이 관리하는 성능 DB는 삭제 시 복구되지 않는다. 운영 RDS에는 이 Terraform 구성을 적용하지 않는다.

## 상태와 비밀정보

Terraform state에는 RDS 비밀번호와 Redis `requirepass`, SecureString 값이 포함될 수 있다. S3 versioning과 암호화를 유지하고, bucket·DynamoDB table 접근은 성능 검증 담당자의 AWS role로 제한한다. `backend.hcl`, `terraform.tfvars`, `*.tfstate`, `.terraform/`은 저장소에 올리지 않는다.

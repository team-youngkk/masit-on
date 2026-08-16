# Issue #207 격리 성능 환경

이 디렉터리는 운영 환경에 부하를 보내지 않고 `NFR-PERFORMANCE-006`과 #207 성능 검증을 반복하기 위한 Terraform 구성을 담는다.

Terraform은 기존 VPC와 서브넷을 **읽기만** 한다. 운영 EC2, 운영 RDS, 운영 보안 그룹은 리소스로 선언하지 않는다. 생성되는 모든 리소스에는 `Environment=isolated-performance`, `Purpose=issue-207`, 실행별 `RunId` 태그가 붙고 이름도 `masiton-perf-207-` 접두사를 사용한다.

구성 범위는 다음과 같다.

- 기존 VPC 안의 성능 검증 전용 앱 EC2와 k6 EC2
- 성능 검증 전용 RDS PostgreSQL
- 앱·부하 생성기·RDS 사이의 최소 보안 그룹 규칙
- EC2 Instance Profile, SSM Parameter Store SecureString, ECR 읽기 권한
- 앱 인스턴스의 WireMock·Redis 컨테이너와 부하 생성기의 고정 버전 k6 부트스트랩

백엔드 이미지 실행, `perf/seed` 적재, 시나리오 실행과 결과 기록은 Terraform의 책임이 아니다. 인프라가 준비된 뒤 SSM으로 실행하며, 실제 실행 결과는 [issue-207-isolated-performance-result.md](../../docs/08-planning/issue-207-isolated-performance-result.md)에 기록한다.

## 사전 조건

- Terraform 1.6 이상
- AWS CLI와 `masiton` SSO 프로파일
- 대상과 같은 리전(`ap-northeast-2`)의 AWS 자격 증명
- Terraform 상태를 저장할 암호화된 비공개 S3 backend와 DynamoDB locking table
- 성능 전용 RDS 비밀번호를 `TF_VAR_db_password` 환경 변수로 주입
- 기존 VPC의 ID, public subnet 1개, private subnet 2개
- 실행할 ECR 이미지의 **digest 고정 URI**

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

`TF_VAR_db_password`는 비밀 관리 도구에서 읽어 현재 프로세스에만 주입한다. `terraform.tfvars`나 명령행 인자에 비밀번호를 넣지 않는다.

계획에서 다음을 확인한 뒤에만 apply한다.

- 변경 대상이 `masiton-perf-207-` 리소스뿐인지
- 운영 인스턴스 ID `<production-app-instance-id>`, 운영 RDS `masiton-db`, 운영 보안 그룹이 변경 대상에 없는지
- 앱 8080 포트는 load generator 보안 그룹에서만 허용되는지
- RDS가 `publicly_accessible=false`인지

## 인프라 준비 후

Terraform 출력의 `app_instance_id`로 SSM 명령을 실행해 다음 순서로 진행한다.

1. 앱 인스턴스의 `/opt/masiton-perf/`에 백엔드 이미지와 성능 전용 설정을 기동한다.
2. WireMock stub과 Redis가 정상인지 확인한다.
3. `perf/seed/`를 RDS에 적재하고 `ANALYZE`를 실행한다.
4. 같은 VPC의 `loadgen_instance_id`에서 k6 시나리오를 실행한다.
5. RDS·EC2·Redis·WireMock 증적과 k6 결과를 기록한다.
6. 증적을 보존한 뒤 `terraform destroy`한다.

Terraform은 백엔드 기동과 시드 적재를 자동 실행하지 않는다. 이 단계를 분리해 두어 계획·적용 중 실수로 부하가 시작되지 않도록 했다. 실제 외부 Kakao·YouTube API를 호출하지 말고 WireMock만 사용한다.

## 정리

이 구성은 성능 전용 임시 환경이다. 운영 상태를 확인하고 결과를 보존한 뒤 같은 작업 디렉터리에서 삭제한다.

```powershell
terraform plan -destroy -out issue-207-destroy.tfplan
terraform apply issue-207-destroy.tfplan
```

`terraform destroy`를 운영 상태를 확인하기 위한 일반 명령으로 사용하지 않는다. RDS는 `deletion_protection=false`, `skip_final_snapshot=true`로 설정되어 있어 이 구성이 관리하는 성능 DB는 삭제 시 복구되지 않는다. 운영 RDS에는 이 Terraform 구성을 적용하지 않는다.

## 상태와 비밀정보

Terraform state에는 RDS 비밀번호와 SecureString 값이 포함될 수 있다. S3 versioning과 암호화를 유지하고, bucket·DynamoDB table 접근은 성능 검증 담당자의 AWS role로 제한한다. `backend.hcl`, `terraform.tfvars`, `*.tfstate`, `.terraform/`은 저장소에 올리지 않는다.

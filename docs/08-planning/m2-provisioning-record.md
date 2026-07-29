---
status: In progress
started_date: 2026-07-29
owners:
  - 이우람
related_documents:
  - m2-deployment-plan.md
  - m2-cost-and-sizing.md
  - ../01-requirements/non-functional-requirements.md
  - ../07-adr/security/sec-001-secrets-workload-identity.md
  - ../07-adr/platform/deploy-002-validation-deployment-before-expansion.md
---

# M2 자원 생성 기록

## 1. 문서 목적

M2 초기 운영 배포에서 생성한 AWS 자원의 식별자와 완료 조건 검증 결과를 Task별로 남긴다. `M2-13` 복구 리허설이 "문서화된 절차만으로 복구"를 요구하므로 복구 대상 자원의 식별자와 구성이 한곳에 있어야 한다.

계획과 완료 조건은 [M2 초기 운영 배포 계획](m2-deployment-plan.md), 사양 근거는 [사양과 월 비용 산정](m2-cost-and-sizing.md)에 있다.

## 2. 계정과 리전

| 항목 | 값 |
|---|---|
| 계정 | `711457211155` |
| 리전 | `ap-northeast-2` |
| 접근 방식 | IAM Identity Center 조직 인스턴스 `ssoins-7230c72b8df2ccaf`, 권한 세트 `AdministratorAccess`(세션 8시간) |
| CLI 프로파일 | `masiton` (SSO. 장기 액세스 키 없음) |

**이 계정은 맛잇온 전용이 아니다.** 2026-06-05에 만든 다른 프로젝트의 ECR 리포지토리 `commerce-payment`(이미지 8개)가 있고 삭제된 RDS의 로그 그룹 `RDSOSMetrics`가 남아 있다. 예산 범위 영향은 4절에 적었다.

## 3. M2-03 네트워크와 EC2 (#42)

생성 일시 2026-07-29.

### 3.1. 네트워크

| 자원 | 식별자 | 구성 |
|---|---|---|
| VPC | `vpc-05441ae76eaa1131c` | `10.0.0.0/16`, DNS 확인·호스트명 활성화 |
| 인터넷 게이트웨이 | `igw-044b5aa740dc977e6` | VPC에 연결 |
| 퍼블릭 서브넷 | `subnet-049d1cb5252d5b796` | `10.0.0.0/24`, `ap-northeast-2a` |
| 사설 서브넷 | `subnet-0042348af8936bf92` | `10.0.10.0/24`, `ap-northeast-2a` |
| 사설 서브넷 | `subnet-015c5a6eee6878c8a` | `10.0.11.0/24`, `ap-northeast-2c` |
| 퍼블릭 라우트 테이블 | `rtb-0cd5a7a8ffff5a5a3` | `0.0.0.0/0` → IGW. 퍼블릭 서브넷에 연결 |

사설 서브넷은 기본 라우트 테이블(local 전용)을 쓴다. **`0.0.0.0/0` 라우트가 없어 인터넷에서 도달하지 않고 인터넷으로 나가지도 않는다.** NAT Gateway와 인터페이스 VPC 엔드포인트는 비용 때문에 만들지 않았다([사양과 월 비용 산정 6.1절](m2-cost-and-sizing.md)).

사설 서브넷을 2개 만든 이유는 RDS가 Single-AZ여도 서브넷 그룹에 AZ 2개 이상을 요구하기 때문이다(`M2-04`).

### 3.2. 보안 그룹

| 자원 | 식별자 | 인바운드 |
|---|---|---|
| 앱 | `sg-01b22e8a546dc40e0` | `80` ← `0.0.0.0/0`, `443` ← `0.0.0.0/0`, `22` ← `39.123.84.157/32` |
| RDS | `sg-0a85c62e8e98cf169` | `5432` ← `sg-01b22e8a546dc40e0` (보안 그룹 참조) |

`22`의 출처는 작업자 공인 IP 단일 주소다. **작업자 IP가 바뀌면 이 규칙을 갱신해야 SSH가 된다.** RDS는 CIDR이 아니라 앱 보안 그룹을 출처로 참조하므로 앱 인스턴스가 교체돼도 규칙을 고치지 않는다.

### 3.3. IAM

| 자원 | 식별자 |
|---|---|
| 역할 | `masiton-app-role` (`arn:aws:iam::711457211155:role/masiton-app-role`) |
| 인스턴스 프로파일 | `masiton-app-profile` |

| 정책 | 범위 |
|---|---|
| `AmazonSSMManagedInstanceCore` (관리형) | SSM 등록과 Session Manager·RunCommand |
| `CloudWatchAgentServerPolicy` (관리형) | `M2-10` CloudWatch Agent |
| `AmazonEC2ContainerRegistryReadOnly` (관리형) | `M2-09` 이미지 pull |
| `masiton-parameter-store-read` (인라인) | `arn:aws:ssm:ap-northeast-2:711457211155:parameter/masiton/*` 읽기. `kms:Decrypt`는 `kms:ViaService`가 `ssm.ap-northeast-2.amazonaws.com`일 때만 허용 |

인라인 정책이 KMS를 `*` 리소스로 허용하지만 `kms:ViaService` 조건으로 Parameter Store 경유만 남겼다. 다른 서비스나 직접 호출로는 복호화할 수 없다.

### 3.4. EC2

| 항목 | 값 |
|---|---|
| 인스턴스 | `i-0b451f18bca827cc9` |
| 타입 | `t4g.medium` (arm64, 2 vCPU / 3,835 MB) |
| AMI | `ami-0a1231e819ae021a0` (Amazon Linux 2023, arm64) |
| AZ | `ap-northeast-2a` |
| 루트 볼륨 | gp3 30 GiB, 암호화, 종료 시 삭제 |
| 사설 IP | `10.0.0.231` |
| Elastic IP | `3.37.228.52` (`eipalloc-0b50b23651d166133`, 연결 `eipassoc-0751939fdce9f1568`) |
| IMDS | IMDSv2 강제 (`HttpTokens=required`) |
| CPU 크레딧 | `standard` (unlimited 미사용. 초과 과금을 만들지 않는다) |
| 키 페어 | `masiton-app` (ed25519) |

**Elastic IP `3.37.228.52`가 `M2-02`(#41) A 레코드의 대상이다.**

CPU 크레딧을 `unlimited`가 아니라 `standard`로 둔 이유는 버스트가 예산 밖 과금을 만들지 않게 하는 것이다. 크레딧이 고갈되면 성능이 떨어지지만 요금이 늘지 않는다. `M2-12`에서 `CPUCreditBalance`를 확인한다.

### 3.5. 완료 조건 검증

`M2-03` 완료 조건은 세 가지다. 인스턴스에서 SSM RunCommand로 실행해 확인했다.

| 완료 조건 | 결과 |
|---|---|
| 22 포트가 전체 공개되지 않는다 | 통과. 출처가 `39.123.84.157/32` 단일 주소다 |
| EC2가 IAM Role로 Parameter Store·ECR·CloudWatch에 접근한다 | 통과 |
| 사설 서브넷이 인터넷에서 직접 도달되지 않는다 | 통과. 라우트 테이블에 `0.0.0.0/0`이 없다 |

IAM Role 검증에 사용한 호출과 결과다.

```text
aws sts get-caller-identity
  -> arn:aws:sts::711457211155:assumed-role/masiton-app-role/i-0b451f18bca827cc9
aws ssm get-parameters-by-path --path /masiton/    -> 인가됨 (결과 0건, 아직 등록 전)
aws ecr describe-repositories                      -> 인가됨
aws logs describe-log-groups                       -> 인가됨
```

인스턴스가 역할을 수임했고 세 서비스 호출이 모두 인가됐다. Parameter Store 결과가 0건인 것은 `M2-07`에서 등록하기 때문이며 권한 문제가 아니다.

### 3.6. 실측한 기준값

| 항목 | 실측 | [산정](m2-cost-and-sizing.md) 3절 |
|---|---|---|
| 전체 메모리 | 3,835 MB | 4 GiB 기준 |
| 기동 직후 사용 메모리 (OS + SSM Agent) | **173 MB** | OS 250 MB + Agent 150 MB = 400 MB |
| 루트 디스크 사용 | 1.9 GB / 30 GB (7%) | — |
| 아키텍처 | `aarch64` | arm64 |

**기준 메모리 사용량이 산정치보다 227 MB 낮다.** CloudWatch Agent를 더해도 산정치를 넘지 않을 것으로 보이므로 3절 합계 2,100 MB에는 여유가 있다. 애플리케이션 기동 후 실측은 `M2-09`에서 한다.

**Docker가 설치돼 있지 않다.** `M2-05` Redis 컨테이너와 `M2-09` 애플리케이션 실행 전에 설치해야 한다.

## 4. 예산 범위에 관한 확인 사항

`My Monthly Cost Budget`(`$100`/월)은 **계정 전체 비용**을 대상으로 한다. 2절에 적었듯 이 계정에는 다른 프로젝트 자원이 있어 그 비용도 이 예산에 합산된다.

현재 다른 프로젝트의 비용은 `commerce-payment` ECR 이미지 8개의 스토리지 요금뿐이고 월 1달러 미만으로 추정되므로 **예산 판정에 영향이 없다.** 맛잇온 비용만 분리해야 할 필요가 생기면 다음을 한다.

1. 생성한 모든 자원에 `Project=masit-on` 태그를 이미 붙여 두었다.
2. Billing 콘솔에서 `Project` 비용 할당 태그를 활성화한다.
3. `Project=masit-on` 필터를 건 예산을 따로 만든다.

비용 할당 태그는 활성화 후 최대 24시간이 지나야 새 데이터에 적용되므로 M2 일정 안에서는 즉시 쓸 수 없다.

**7월 순 비용은 크레딧으로 상쇄돼 거의 0이지만 이 크레딧은 만료 예정이다**(2026-07-29 확인). 따라서 M2 운영 기간의 실제 청구액은 크레딧이 없는 정가 기준으로 봐야 한다. [사양과 월 비용 산정](m2-cost-and-sizing.md)의 예상 비용이 이미 정가 기준이므로 산정치를 조정하지 않는다.

크레딧이 소진되면 예산 알림이 실제 청구액을 기준으로 동작하기 시작한다. 그 전까지는 크레딧이 지출을 가려 알림이 늦게 올 수 있으므로, `M2-12` 시점에 실제 청구액과 산정치를 대조한다(계획 10절 마지막 완료 항목).

## 5. 검증하지 못한 항목

- **예산 초과 알림 실제 도달.** 시험 예산 `masiton-alert-test`(한도 `$0.5`, 실제 1% 초과)를 만들어 확인 중이다. AWS Budgets가 하루 약 3회만 평가해 즉시 도달하지 않는다. 도달 확인 후 시험 예산을 삭제한다.
- **SSH 접속.** 키 페어 `masiton-app`을 만들었으나 실제 SSH 접속은 시도하지 않았다. 인스턴스 접근은 SSM RunCommand로 검증했다.
- **애플리케이션 기동 후 메모리.** 3.6절은 기동 직후 기준값이며 Nginx·Next.js·Spring Boot 실행 후 실측은 `M2-09`에서 한다.

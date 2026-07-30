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
  - ../07-adr/platform/runtime-001-docker.md
  - ../07-adr/data/data-005-redis-refresh-token.md
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

**이 계정은 맛잇온 전용이 아니다.** 2026-06-05에 만든 다른 프로젝트의 ECR 리포지토리 `commerce-payment`(이미지 8개)가 있고 삭제된 RDS의 로그 그룹 `RDSOSMetrics`가 남아 있다. 예산 범위 영향은 16절에 적었다.

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

**Docker가 설치돼 있지 않았다.** `M2-05` Redis 컨테이너와 `M2-09` 애플리케이션 실행 전에 설치해야 했고, `M2-05`에서 설치했다(7.1절).

## 4. M2-02 도메인과 DNS (#41)

| 항목 | 값 |
|---|---|
| 도메인 | **`masiton.click`** (Amazon Registrar, 2026-07-29 등록) |
| 등록 비용 | `$3` 1회 청구, 1년. 만료 2027-07-29 |
| 자동 갱신 | **끔.** 단기 프로젝트라 갱신 과금을 만들지 않기로 했다(2026-07-29 결정) |
| 호스팅 영역 | `Z01447273NZ8O8LL4IA5` (`masiton.click.`, 퍼블릭). 등록 시 자동 생성 |
| A 레코드 | `masiton.click.` → `3.37.228.52`, TTL 300, 상태 `INSYNC` |

TTL을 300초로 둔 이유는 Elastic IP가 바뀌거나 `M2-13` 복구에서 인스턴스를 교체할 때 전파를 빨리 끝내기 위한 것이다.

**자동 갱신을 끈 결과 2027-07-29에 도메인이 만료된다.** [ADR-DEPLOY-002](../07-adr/platform/deploy-002-validation-deployment-before-expansion.md) 4절은 M2 환경을 1~3차 확장까지 계속 운영한다고 정했으므로, 확장이 그 시점을 넘기면 갱신 여부를 다시 판단해야 한다.

기존에 다른 프로젝트의 `roviq.click`(호스팅 영역 `Z06023901J95I2V1Q2QH`)이 같은 계정에 있으나 맛잇온과 무관하며 사용하지 않는다.

### 4.1. 완료 조건 검증

| 완료 조건 | 결과 |
|---|---|
| 도메인이 Elastic IP로 해석된다 | 통과 |

```text
aws route53domains list-operations   -> REGISTER_DOMAIN SUCCESSFUL
Resolve-DnsName masiton.click -Type A -Server 8.8.8.8
  -> 3.37.228.52
```

외부 공개 리졸버(Google DNS `8.8.8.8`)로 확인했으므로 로컬 캐시가 아닌 실제 전파 결과다. **`M2-08`의 도메인 검증 방식 인증서 발급을 막던 전파 대기가 해소됐다.**

## 5. M2-04 RDS PostgreSQL (#43)

| 항목 | 값 |
|---|---|
| 인스턴스 | `masiton-db` |
| 엔진 | PostgreSQL **17.10** ([기술 정책 3절](../06-architecture/technology-policy.md) 고정 버전) |
| 클래스 | `db.t4g.micro` (2 vCPU / 1 GiB) |
| 스토리지 | gp3 20 GiB, 암호화 (`aws/rds` 관리형 KMS 키) |
| 초기 데이터베이스 | `masiton` |
| 마스터 사용자 | `masiton` |
| 배치 | `masiton-db-subnet-group` (사설 서브넷 2a·2c), AZ `ap-northeast-2c` |
| 보안 그룹 | `sg-0a85c62e8e98cf169` (5432, 출처 앱 SG만) |
| 퍼블릭 액세스 | 없음 |
| Multi-AZ | 사용하지 않음 |
| 자동 백업 | 보관 7일, 창 `18:00-18:30` UTC (KST 03:00-03:30) |
| 유지 관리 창 | `sun:19:00-sun:19:30` UTC (KST 월 04:00) |
| 자동 마이너 버전 업그레이드 | **끔** |
| 삭제 방지 | 켬 |

`--no-auto-minor-version-upgrade`가 중요하다. [기술 정책 3절](../06-architecture/technology-policy.md)이 "고정 버전을 다른 패치·메이저 버전으로 바꾸지 않는다"고 정했으므로 AWS가 패치 버전을 임의로 올리면 고정 정책이 깨진다.

Multi-AZ, Performance Insights, 확장 모니터링은 켜지 않았다. 계획 범위 밖이고 비용이 늘어난다.

### 5.1. 마스터 암호 취급

[ADR-SEC-001](../07-adr/security/sec-001-secrets-workload-identity.md)과 계획 `M2-07`이 지정한 대로 **Parameter Store SecureString**에 저장했다. RDS 관리형 마스터 암호(Secrets Manager)는 저장소가 달라 ADR 개정이 필요하므로 채택하지 않았다(2026-07-29 결정).

| 파라미터 | 유형 | KMS 키 |
|---|---|---|
| `/masiton/db/password` | `SecureString` | `alias/aws/ssm` (관리형. 고객 관리 키의 월 `$1`이 발생하지 않는다) |

암호는 담당자가 직접 생성해 입력했고 생성 명령과 파라미터 등록을 한 번의 입력으로 처리해 셸 히스토리에 남기지 않았다. **평문은 담당자 외 누구에게도 전달하지 않았다.** 연결 검증도 EC2가 IAM Role로 파라미터를 읽어 수행하며 평문을 출력하지 않는다.

접속 정보 중 비밀이 아닌 값도 같은 경로에 등록해 `M2-09`가 한 곳에서 읽게 했다.

| 파라미터 | 유형 | 값 |
|---|---|---|
| `/masiton/db/username` | `String` | `masiton` |
| `/masiton/db/url` | `String` | `jdbc:postgresql://masiton-db.cvg4846kmjle.ap-northeast-2.rds.amazonaws.com:5432/masiton` |

### 5.2. 완료 조건 검증

| 완료 조건 | 결과 |
|---|---|
| RDS가 인터넷에서 직접 접근되지 않는다 | 통과 |
| EC2에서만 연결된다 | 통과 |
| 스냅샷 일정이 활성화된다 | 통과 |

엔드포인트는 사설 IP로 해석되고 작업자 PC에서 TCP 연결이 되지 않는다.

```text
Resolve-DnsName masiton-db.cvg4846kmjle.ap-northeast-2.rds.amazonaws.com
  -> 10.0.11.60                          (사설 서브넷 2c)
TcpClient 5432 (작업자 PC, 5초 대기)     -> 연결 실패
```

EC2에서는 SSM RunCommand로 실제 인증까지 확인했다. 암호는 인스턴스가 Parameter Store에서 직접 읽었고 값을 출력하지 않았다.

```text
psql (PostgreSQL) 16.14                       # 클라이언트 설치
password: fetched OK                          # 값 미출력
select version()
  -> PostgreSQL 17.10 on aarch64-unknown-linux-gnu ...
select current_database(), current_user
  -> masiton, masiton
select count(*) from information_schema.tables where table_schema='public'
  -> 0
```

`version()`이 고정 버전 **17.10**을 그대로 보고한다. `public` 스키마 테이블이 **0개**이므로 `M2-09`가 요구하는 "빈 RDS에 초기 스키마 Flyway 마이그레이션 적용" 전제도 만족한다.

자동 스냅샷은 생성 직후 첫 스냅샷이 만들어져 일정이 동작함을 확인했다.

```text
aws rds describe-db-snapshots --snapshot-type automated
  -> rds:masiton-db-2026-07-29-06-06  available
```

첫 스냅샷은 인스턴스 생성 시점에 만들어진 것이고, 이후로는 `18:00-18:30` UTC 창에서 일 1회 생성돼 7일간 보관된다. `M2-13`에서 이 스냅샷으로 복구 시험을 한다.

**psql 클라이언트를 EC2에 설치했다**(`postgresql16`). 검증용이며 애플리케이션 실행에는 필요하지 않다.

## 6. M2-06 ECR과 이미지 검증 (#45)

### 6.1. ECR 리포지토리

| 리포지토리 | URI |
|---|---|
| 백엔드 | `711457211155.dkr.ecr.ap-northeast-2.amazonaws.com/masiton-backend` |
| 프론트엔드 | `711457211155.dkr.ecr.ap-northeast-2.amazonaws.com/masiton-frontend` |

| 설정 | 값 | 이유 |
|---|---|---|
| 태그 변경 가능성 | `IMMUTABLE` | 같은 태그가 다른 이미지를 가리키는 것을 막는다. digest 식별 요구와 일관된다 |
| push 시 스캔 | 활성 | 완료 조건의 취약점 검사. 기본 스캔이라 추가 비용이 없다 |
| 암호화 | `AES256` | ECR 관리형. 고객 관리 KMS 키 비용이 발생하지 않는다 |
| 수명 주기 | 최근 10개 초과분 만료 | 스토리지 비용 상한 |

`latest` 태그는 쓰지 않는다([기술 정책 3절](../06-architecture/technology-policy.md)). 이미지는 커밋 SHA 태그와 digest로 식별한다.

### 6.2. GitHub Actions OIDC

| 자원 | 값 |
|---|---|
| OIDC 공급자 | `arn:aws:iam::711457211155:oidc-provider/token.actions.githubusercontent.com` |
| 역할 | `masiton-github-actions-role` |
| 최대 세션 | 3600초 |

신뢰 정책이 허용하는 주체를 두 브랜치로 제한했다.

```text
repo:team-youngkk/masit-on:ref:refs/heads/deploy/m2
repo:team-youngkk/masit-on:ref:refs/heads/main
```

PR 브랜치는 AWS 자격 증명을 받지 못한다. **이미지 빌드와 검증은 자격 증명 없이 수행할 수 있고 push만 AWS 권한이 필요하므로**, PR에서는 빌드·검증까지만 돌리고 push는 위 두 브랜치에서만 일어나게 하는 구성이다.

인라인 정책 `masiton-ecr-push`는 `masiton-backend`·`masiton-frontend` 두 리포지토리로 범위를 좁혔다. `ecr:GetAuthorizationToken`만 리소스 범위를 지정할 수 없어 `*`로 두었다. 장기 액세스 키는 만들지 않았다([ADR-SEC-001](../07-adr/security/sec-001-secrets-workload-identity.md)).

### 6.3. 이미지 검증 결과

두 이미지를 **운영과 같은 `linux/arm64`로 빌드**해 [ADR-RUNTIME-001 13절](../07-adr/platform/runtime-001-docker.md)의 검사 항목을 확인했다.

| 검사 | 백엔드 | 프론트엔드 |
|---|---|---|
| 클린 컨텍스트 빌드 | 성공 | 성공 |
| 이미지 크기 | 132 MB | 74 MB |
| 실행 사용자 | `uid=1001(masiton)` | `uid=1001(masiton)` |
| `.env` 파일 | 0건 | 0건 |
| 평문 비밀 패턴 | 0건 | 0건 |
| 플랫폼 | `linux/arm64` | `linux/arm64` |
| 컨테이너 기동 | 미확인 (17절) | Next.js 16.2.11 기동, `/` → `200` |

평문 비밀 검사는 `BEGIN PRIVATE KEY`, `BEGIN RSA PRIVATE KEY`, `JWT_PRIVATE_KEY_PEM`, `POSTGRES_PASSWORD`, `masiton_local` 다섯 패턴을 이미지 파일 시스템에서 찾는 방식이다. 백엔드 `/app`에는 `application.jar` 하나만 있고 빌드 컨텍스트 잔여물이 없다.

### 6.4. 베이스 이미지 digest 고정

완료 조건이 "베이스 이미지가 명시 태그(운영은 digest)를 사용하고 대조된다"이므로 태그와 digest를 함께 고정했다.

| 이미지 | digest |
|---|---|
| `node:24.18.0-alpine` | `sha256:a0b9bf06e4e6193cf7a0f58816cc935ff8c2a908f81e6f1a95432d679c54fbfd` |
| `eclipse-temurin:21.0.11_10-jdk-alpine` | `sha256:1ff763083f2993d57d0bf374ab10bb3e2cb873af6c13a04458ebbd3e0337dc76` |
| `eclipse-temurin:21.0.11_10-jre-alpine` | `sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c` |

세 이미지 모두 `linux/arm64/v8` 매니페스트를 포함한 다중 아키텍처 이미지임을 확인했다. digest는 매니페스트 목록 digest이므로 아키텍처와 무관하게 같은 값을 쓴다.

**베이스 이미지를 갱신할 때는 태그와 digest를 함께 바꿔야 한다.** 한쪽만 바꾸면 빌드가 실패하거나 의도와 다른 이미지를 쓴다.

### 6.5. 프론트엔드 이미지가 새로 필요했던 이유

저장소에 백엔드 `Dockerfile` 하나만 있고 **프론트엔드 이미지를 만들 수단이 없었다.** `M2-06`이 프론트엔드 ECR 리포지토리와 이미지 push를 요구하므로 [frontend/Dockerfile](../../frontend/Dockerfile)을 추가했다.

`next.config.ts`에 `output: 'standalone'`을 넣어 런타임 스테이지가 `node_modules` 전체 대신 추려낸 의존성만 담게 했다. 그 결과 이미지가 74 MB다. standalone 출력에는 정적 파일이 포함되지 않아 `.next/static`을 따로 복사한다.

`frontend/.dockerignore`를 추가해 `node_modules`와 `.env`가 빌드 컨텍스트에 들어가지 않게 했다. 루트 `.dockerignore`에는 `frontend`를 넣어 백엔드 빌드 컨텍스트에서 프론트엔드 `node_modules` 전송을 없앴다.

### 6.6. 워크플로 — 해소됨

이 절은 작성 시점에 **GitHub Actions 워크플로가 저장소에 없다**는 사실을 기록했다. ADR-CI-001(Accepted), ADR-DEPLOY-002 4절, [M2 계획](m2-deployment-plan.md) 8절, `M2-06` 작업 항목이 모두 워크플로를 전제하는데 구현된 적이 없었다.

**[`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) 하나로 해소됐다.** 이 워크플로가 품질 게이트(빌드·테스트)와 이미지 빌드·검증·ECR push를 모두 갖는다. 이미지 job은 `deploy/m2`·`main`의 `push`에서만 실행돼 ADR-CI-001 9절의 "이미지 생성·push는 M2부터" 범위를 지킨다.

**이미지 job을 별도 워크플로로 분리하지 않는다.** 한때 `images.yml`을 만들어 `workflow_run`으로 CI 성공 뒤에 실행하려 했으나 세 가지 이유로 되돌렸다.

- OIDC `sub`가 실제 push된 ref가 아니라 기본 브랜치를 가리켜 `AssumeRoleWithWebIdentity`가 거절된다. IAM 신뢰 정책은 `refs/heads/deploy/m2`와 `refs/heads/main`만 허용한다.
- `workflow_run`은 권한 있는 컨텍스트라 fork PR의 head를 체크아웃한 상태로 OIDC를 주면 외부 코드가 ECR에 push할 수 있다.
- `workflow_run` 이벤트는 **기본 브랜치에 있는 워크플로 파일만** 발동시킨다. 이 저장소의 기본 브랜치는 `main`이고 `main`에는 워크플로 파일이 없어 애초에 실행되지 않았다. 트리거를 `push`로 바꾸면 `ci.yml`의 이미지 job과 경합해 IMMUTABLE 태그에서 실패한다(2026-07-30 실제로 겪었다).

`push` 이벤트는 저장소 내부 브랜치에서만 발생하므로 위 두 위험이 없다. 그래서 이미지 job은 `ci.yml`에 두고 조건으로 범위를 좁힌다.

### 6.7. ECR push와 취약점 검사 결과

`M2-06` 완료 조건 중 "GitHub Actions가 장기 키 없이 OIDC로 push하고 이미지가 digest로 식별된다"와 취약점 검사 결과를 확인했다. `ci.yml`의 `이미지 빌드·검증·push` job이 `deploy/m2` push에서 실행돼 두 커밋을 게시했다.

| 커밋 | CI 실행 | 리포지토리 | digest | push 시각 |
|---|---|---|---|---|
| `69eb607` | `30506706010` | `masiton-backend` | `sha256:e325ff30058007a103ca5761067f86e8b36d6e149600f71f71f94e296cf7960b` | 2026-07-30 10:53 KST |
| `69eb607` | `30506706010` | `masiton-frontend` | `sha256:e46ca3569e2ee66e291a2bc47b8df3b58444a048bcf8d81d4b9c50110b72c9dc` | 2026-07-30 10:54 KST |
| `1b98b71` | `30507994704` | `masiton-backend` | `sha256:a35f172ba7f57b3c59447179d2785155ca9063569eb30079bfc2725d678f72db` | 2026-07-30 11:20 KST |
| `1b98b71` | `30507994704` | `masiton-frontend` | `sha256:738298c9d36bab3293f9d45b7390b25219b45af00b596dda1f7238a7d83d5432` | 2026-07-30 11:21 KST |

두 실행 모두 세 job(`백엔드 빌드·테스트`, `프론트엔드 빌드·타입 검사`, `이미지 빌드·검증·push`)이 성공했다. **장기 액세스 키 없이 OIDC로 push됐고 태그는 커밋 SHA, 실행 참조는 digest다.**

그 이전 `0b8daf4`·`630259b` 쌍도 남아 있다(2026-07-29 21:50·21:58 KST).

취약점 검사는 push 시 기본 스캔으로 `COMPLETE`, 심각도별 발견 0건이다.

`aws ecr describe-images`의 `imageScanStatus`는 비어 보이지만 `describe-image-scan-findings`가 `COMPLETE`와 심각도별 0건을 보고한다. 조회 API에 따라 표시가 달라 스캔이 안 된 것처럼 보일 수 있으므로 확인은 후자로 한다.

## 7. M2-05 Redis (#44)

생성 일시 2026-07-30. 배치는 [ADR-DATA-005](../07-adr/data/data-005-redis-refresh-token.md) 6절 개정(2026-07-30 김인안·이우람 합의)에 따라 앱 EC2 동거다.

### 7.1. 구성

| 항목 | 값 |
|---|---|
| 엔진 | Redis Open Source **8.8.1** (`redis:8.8-alpine`) |
| 이미지 digest | `sha256:8096655e437712b07503796fb64d81359256cfcff0ab29d95a7da72863786efb` (`arm64/linux`) |
| 컨테이너 | `masiton-redis` (앱 EC2 `i-0b451f18bca827cc9`) |
| 호스트 노출 | `127.0.0.1:6379`만 publish |
| 데이터 | `/opt/masiton/redis/data` (호스트 볼륨, `0700`, uid 999) |
| 컨테이너 메모리 상한 | 384 MB (`maxmemory` 256 MB + fork·버퍼 여유) |
| 수명 주기 | systemd unit `masiton-redis.service` (`enabled`) |
| 자격 증명 | `requirepass`. 값은 Parameter Store `/masiton/redis/password` (`SecureString`, `alias/aws/ssm`) |

Docker는 이 Task에서 처음 설치했다(`docker 25.0.14`, `overlay2`, cgroup v2, `docker.service` `enabled`). 이전 기록 3.6절이 남긴 "Docker가 설치돼 있지 않다"는 제약이 해소됐다.

저장소 산출물은 [`deploy/`](../../deploy) 아래에 있다.

| 파일 | 역할 |
|---|---|
| [`deploy/redis/redis.conf`](../../deploy/redis/redis.conf) | 기준 설정. 비밀값을 담지 않는다 |
| [`deploy/redis/masiton-redis.service`](../../deploy/redis/masiton-redis.service) | systemd unit. 이미지를 digest로 고정한다 |
| [`deploy/scripts/redis-render-conf.sh`](../../deploy/scripts/redis-render-conf.sh) | 기동 직전 `requirepass`를 붙여 tmpfs에 렌더링 |
| [`deploy/scripts/redis-install.sh`](../../deploy/scripts/redis-install.sh) | 인스턴스 설치·기동. 재실행해도 결과가 같다 |

### 7.2. 자격 증명을 디스크에 두지 않는 방법

`requirepass`는 저장소의 설정 파일에 없다. 기동할 때마다 `redis-render-conf.sh`가 Parameter Store에서 값을 읽어 **tmpfs인 `/run/masiton/redis.conf`** 에 기준 설정 + `requirepass` 한 줄로 렌더링하고, 그 파일만 컨테이너에 읽기 전용으로 마운트한다.

- tmpfs이므로 재기동하면 사라진다. 자격 증명이 루트 볼륨과 볼륨 스냅샷에 남지 않는다(NFR-SECURITY-003).
- 그래서 컨테이너의 `--restart` 정책을 쓰지 않고 systemd가 수명 주기를 소유한다. `--restart`로 되살아나는 컨테이너는 렌더링을 기다리지 않아 마운트 대상이 없는 상태로 기동한다.
- 값을 명령행 인자로 넘기지 않는다. `redis-server --requirepass`나 `redis-cli -a`는 같은 인스턴스의 `ps`에서 읽힌다. 검증에도 `REDISCLI_AUTH` 환경 변수를 썼다.
- 렌더링한 파일은 `uid 999`(컨테이너의 `redis` 사용자) 소유 `0400`이다. 읽기 전용 마운트라 이미지 entrypoint의 `chown`이 실패하므로 호스트에서 미리 맞춘다.

### 7.3. 완료 조건 검증

| 완료 조건 | 결과 |
|---|---|
| 인터넷·VPC 어디에서도 직접 접근되지 않고 같은 인스턴스에서만 연결된다 | 통과 |
| 재기동 후에도 저장된 인증 상태가 유지된다 | 통과 (컨테이너 재기동·인스턴스 재기동 모두) |
| `auth:refresh:*`·`auth:login-failure:*`가 eviction 대상이 되지 않는다 | 통과 |

접근 경로를 인스턴스에서 확인했다.

```text
ss -ltn '( sport = :6379 )'   -> LISTEN 127.0.0.1:6379
/dev/tcp/10.0.0.231/6379      -> 연결 실패 (사설 IP로 도달하지 않는다)
docker exec ... redis-cli ping (무인증) -> NOAUTH Authentication required
```

사양이 [산정 5.4절](m2-cost-and-sizing.md)과 일치하는지 실행 중인 서버에서 대조했다.

```text
appendonly       yes
appendfsync      everysec
maxmemory        268435456        # 256 MB
maxmemory-policy noeviction
dir              /data
save             900 1 300 10 60 10000
```

재기동 유지는 실제 키로 확인했다. `auth:refresh:*`와 `auth:login-failure:*`에 TTL을 준 검증용 키를 넣고 두 층위의 재기동을 거쳤다.

| 시점 | `auth:refresh` TTL | `auth:login-failure` TTL |
|---|---:|---:|
| 기록 직후 | 1,209,600 | 900 |
| `systemctl restart` 후 | 1,209,597 | 897 |
| 인스턴스 재기동 후 | 1,209,444 | 744 |

TTL이 초기화되지 않고 이어서 감소하므로 AOF에서 복원된 것이다. 인스턴스 재기동은 부팅 `01:09:57 UTC`, 컨테이너 기동 `01:10:07 UTC`로 자동 기동됐고 `/run/masiton/redis.conf`가 같은 시각에 다시 렌더링됐다. `aof_enabled:1`, `aof_last_write_status:ok`, `rdb_last_bgsave_status:ok`다. **검증용 키 2개는 확인 후 삭제했고 `auth:*` 잔존 0건이다.**

### 7.4. 진행 중 발견한 것

- **Parameter Store 값에 섞인 `\r`로 인증이 어긋났다.** Windows 셸에서 `openssl rand -base64 36 | tr -d '\n'`으로 만든 값이 캐리지 리턴을 물고 등록됐다. Redis 설정 파서는 줄 끝의 `\r`을 떼어내는 반면 클라이언트는 그대로 보내 `WRONGPASS`가 됐다. 값을 다시 만들고 `redis-render-conf.sh`가 CR·LF를 걷어내며 공백이 있으면 기동을 실패시키게 했다.
- **`aws ssm get-parameter --output text`는 Windows에서 출력에 `\r\n`을 쓴다.** 길이를 재서 값을 검증하면 실제보다 1자 길게 보인다. 저장된 값의 이상 여부는 인스턴스에서 판단해야 한다.

## 8. M2-08 Nginx와 HTTPS (#47)

구성 일시 2026-07-30. 인증서 방식은 [계획 4.1절](m2-deployment-plan.md) 결정(ACM exportable + EC2 Nginx 종료)을 따랐다.

### 8.1. 인증서

| 항목 | 값 |
|---|---|
| ARN | `arn:aws:acm:ap-northeast-2:711457211155:certificate/645e947f-f018-449d-9fc2-822563cf0c74` |
| 도메인 | `masiton.click` (apex 단독. 와일드카드 미발급) |
| 발급자 | `C=US, O=Amazon, CN=Amazon RSA 2048 M01` |
| 키 알고리즘 | RSA-2048 |
| Export | `ENABLED` (**요청 시점에만 정할 수 있고 나중에 바꿀 수 없다**) |
| 유효기간 | 2026-07-30 ~ **2027-02-12** (198일) |
| serial | `0E4C9FD3A2CFD7D945344298BE487B0C` |
| 검증 | Route 53 DNS 검증. CNAME `_f56d012ccc6bcec12ec01777862e98c6.masiton.click` |

검증 CNAME은 호스팅 영역 `Z01447273NZ8O8LL4IA5`에 UPSERT했고 요청부터 `ISSUED`까지 1분 이내였다. **A 레코드 전파가 이미 끝나 있어(4절) 대기가 없었다.**

내보내기에 필요한 값은 Parameter Store에 있다.

| 파라미터 | 유형 | 용도 |
|---|---|---|
| `/masiton/tls/certificate-arn` | `String` | 내보낼 인증서 ARN |
| `/masiton/tls/export-passphrase` | `SecureString` | 개인키 내보내기 암호 |

인스턴스 역할 `masiton-app-role`에 인라인 정책 `masiton-acm-export`를 추가했다. `acm:ExportCertificate`·`acm:DescribeCertificate`를 **이 인증서 ARN 하나로만** 제한했다.

### 8.2. 인증서 배포와 갱신 재배포

[`deploy/scripts/tls-deploy-cert.sh`](../../deploy/scripts/tls-deploy-cert.sh)가 내보내기부터 Nginx 반영까지 처리한다.

1. Parameter Store에서 ARN과 내보내기 암호를 읽는다. 암호는 tmpfs 파일로 써서 `fileb://`로 넘긴다. 명령행 인자로 주면 같은 인스턴스의 `ps`에서 읽힌다.
2. `aws acm export-certificate` 결과에서 인증서·체인·암호화된 개인키를 분리하고, 개인키를 풀어 평문 PEM으로 만든다.
3. **인증서와 개인키가 짝인지 공개키 해시로 대조한다.** 어긋난 쌍을 배포하면 Nginx가 기동하지 못한다.
4. 설치본과 같으면 아무 것도 하지 않는다. 다르면 `fullchain`(0644)과 개인키(0600)를 교체하고 `nginx -t` 후 reload한다.

중간 산출물은 전부 tmpfs(`/run/masiton`)에 만들고 종료 시 삭제한다. 개인키 평문과 내보내기 암호가 루트 볼륨과 볼륨 스냅샷에 남지 않는다.

ACM은 만료 45일 전에 자동 갱신하지만 **갱신본을 EC2로 다시 내보내는 것은 자동이 아니다.** 그 공백을 [`masiton-tls-renew.timer`](../../deploy/nginx/masiton-tls-renew.timer)가 메운다. 일 1회 실행이고 갱신되지 않았으면 파일이 같아 즉시 끝난다. `Persistent=true`라 인스턴스가 꺼져 지나간 실행은 부팅 후 따라 실행한다.

**갱신 재배포는 인증서 만료 감시로 이중화해야 한다.** 타이머가 조용히 실패하면 만료 전까지 아무도 모른다. `M2-10`에서 만료 임박 알람을 구성한다.

### 8.3. Nginx 구성

| 항목 | 값 |
|---|---|
| 버전 | nginx 1.30.3 (Amazon Linux 2023 `dnf`) |
| 최상위 설정 | [`deploy/nginx/nginx.conf`](../../deploy/nginx/nginx.conf). 배포판 원본은 `/etc/nginx/nginx.conf.masiton-orig`에 보관 |
| 서버 블록 | [`deploy/nginx/masiton.click.conf`](../../deploy/nginx/masiton.click.conf) |
| TLS | `TLSv1.2`·`TLSv1.3`만. 세션 티켓 끔, HSTS 1년 |

경로 소유권은 [ADR-WEB-003](../07-adr/platform/web-003-routing-boundary.md) 6.1절 그대로다.

| 경로 | 처리 |
|---|---|
| `/api/**` | `127.0.0.1:8080` Spring Boot |
| `/internal/**`, `/internal` | `404`. 인터넷 진입점에서 전달하지 않는다 |
| 그 외 | `127.0.0.1:3000` Next.js |
| 알려지지 않은 Host·Elastic IP 직접 접근 | `444` (응답 없이 연결 종료) |

`/internal/`은 `location ^~`로 선언해 뒤에 어떤 규칙이 추가돼도 우회되지 않게 했다. 배포판 기본 설정을 저장소 산출물로 교체한 이유는 원본에 `/usr/share/nginx/html`을 서비스하는 server 블록이 있어 도메인 밖 접근에 기본 페이지가 응답하기 때문이다.

### 8.4. 완료 조건 검증

| 완료 조건 | 결과 |
|---|---|
| 인터넷에서 `/internal/health/live`가 차단된다 | 통과 |
| HTTPS로 프론트엔드와 `/api/**`가 모두 응답한다 | 이 시점 **미완**(애플리케이션 미배포로 `502`). `M2-09` 배포 후 충족 — 9.4절 |
| 인증서 갱신·재배포 절차가 문서화되고 최소 1회 시연된다 | 통과 (8.2절 문서화, 아래 시연) |

인터넷(작업자 PC)에서 확인한 결과다.

```text
http://masiton.click/                     -> 301, Location: https://masiton.click/
https://masiton.click/                    -> 502, ssl_verify_result=0 (공개 신뢰 저장소로 검증됨)
https://masiton.click/internal/health/live        -> 404
https://masiton.click/internal/health/ready       -> 404
https://masiton.click/internal/health/dependencies-> 404
https://masiton.click/internal                    -> 404
https://masiton.click/api/restaurants     -> 502
http://3.37.228.52/                       -> 응답 없이 종료 (444)
Host: example.invalid                     -> 응답 없이 종료 (444)
```

**`502`는 예상된 상태다.** `/api/**`와 화면 경로의 upstream이 아직 없다. 라우팅이 목적지까지 도달했다는 뜻이며, `M2-09`에서 `200`으로 바뀌는 것을 확인한다.

TLS 버전은 인스턴스에서 서버 응답으로 확인했다. `tls1_1` 거부, `tls1_2`·`tls1_3` 성립이다.

갱신 재배포는 두 경우를 시연했다.

| 시연 | 결과 |
|---|---|
| 갱신본이 없을 때 | 파일 해시 동일. 교체·reload 없이 종료 |
| 갱신본이 온 상황(설치본을 치워 재현) | 다시 내보내 같은 내용으로 복구, `nginx -t` 후 reload. master pid 3349 유지, `nginx` active |

reload가 master 프로세스를 바꾸지 않으므로 **인증서 교체에 서비스 중단이 없다.**

## 9. M2-09 애플리케이션 배포 (#48)

배포 일시 2026-07-30. 배포 대상 커밋 `a824c4e`.

### 9.1. 배포된 것

| 항목 | 값 |
|---|---|
| 커밋 태그 | `a824c4e77c3ad2f2f0c1d3f7e950057c0864ebc9` |
| 백엔드 digest | `sha256:61c4043214c20d102d2bab89bf5b85fd5dfc31f972e063285869bb8d2b2eae83` |
| 프론트엔드 digest | `sha256:0643d6712f52eac23945a4d9a8f2decab7ec8f8d88199a3d1e5c9cd373162e7c` |
| 실행 프로파일 | `prod` ([application-prod.yml](../../src/main/resources/application-prod.yml)) |
| 수명 주기 | systemd unit `masiton-backend.service`, `masiton-frontend.service` (둘 다 `enabled`) |
| 적용된 마이그레이션 | `V1 create initial schema` (`installed_rank` 1, 성공). `public` 스키마 테이블 9개 |

실행 참조는 태그가 아니라 **digest**다. `app-deploy.sh`가 태그로 조회한 digest를 `/opt/masiton/etc/{backend,frontend}.image`에 기록하고 unit이 그 파일을 읽는다. 배포마다 unit을 고치지 않고 파일 한 줄만 바뀐다. **롤백은 이전 커밋 SHA로 같은 스크립트를 다시 실행하는 것이다**(NFR-DEPLOYMENT-003). CD를 통한 롤백 경로는 15.4절에 있다.

`app-deploy.sh`가 활성 경로에 반영하는 것은 다음 6개다. **두 이미지 pull이 모두 성공한 뒤에 함께 반영한다.**

| 활성 경로 | 출처 |
|---|---|
| `/opt/masiton/bin/app-run.sh` | 스테이징 |
| `/opt/masiton/bin/app-secrets-render.sh` | 스테이징 |
| `/etc/systemd/system/masiton-backend.service` | 스테이징 |
| `/etc/systemd/system/masiton-frontend.service` | 스테이징 |
| `/opt/masiton/etc/backend.image` | ECR digest 조회 |
| `/opt/masiton/etc/frontend.image` | ECR digest 조회 |

실행 스크립트와 unit을 이미지 준비 전에 덮어쓰면, 이후 단계가 실패했을 때 이미지 참조와 실행 중 컨테이너는 이전 버전인데 **다음 재기동부터만 새 `app-run.sh`·unit이 적용된다.** 설정 형식이나 사전 실행 조건이 함께 바뀐 배포에서는 실패한 배포가 재부팅 후 장애를 만든다. PR 리뷰에서 지적받아 순서를 바꿨다.

`app-secrets-render.sh`도 배포 산출물이다. backend unit의 `ExecStartPre`가 이 경로를 실행하므로 설치하지 않으면 새 인스턴스는 파일 없음으로 기동에 실패하고, 기존 인스턴스는 렌더러 변경이 배포에 반영되지 않는다. 이것도 리뷰 지적으로 필수 파일 목록과 설치 대상에 넣었다.

### 9.2. 설정과 비밀값 주입 방식

컨테이너는 `--network host`로 실행한다. [ADR-RUNTIME-001](../07-adr/platform/runtime-001-docker.md) 11절이 운영 설정의 Docker 서비스명을 금지하므로 앱이 저장소에 `127.0.0.1`로 붙어야 하고, Nginx도 `127.0.0.1`의 8080·3000으로 전달한다. 브리지 네트워크로는 두 방향이 함께 성립하지 않는다.

비밀값은 **tmpfs 파일로 주입한다.** 컨테이너 환경 변수로 넘기지 않는다.

처음에는 `docker run -e VAR` 통과 형식을 썼다. 명령행 노출은 막았지만 **값이 컨테이너 스펙에 들어가고 Docker가 그것을 `/var/lib/docker/containers/<id>/config.v2.json`에 평문으로 적는다.** `docker inspect`로 JWT 개인키 전문이 읽혔고 루트 볼륨 스냅샷에도 함께 들어간다. [ADR-SEC-001](../07-adr/security/sec-001-secrets-workload-identity.md) 11절의 평문 저장 금지에 걸리며, PR 리뷰에서 지적받아 바꿨다.

당시 기록에 "root만 조회할 수 있어 새 권한 경계가 생기는 것은 아니다"라고 적었는데, ADR 문구는 조건 없이 평문 저장을 금지하므로 그 판단이 틀렸다.

| 항목 | 값 |
|---|---|
| 렌더링 | [`app-secrets-render.sh`](../../deploy/scripts/app-secrets-render.sh)가 기동 직전 실행 |
| 위치 | `/run/masiton/secrets` (**tmpfs**), 디렉터리 `0500` uid 1001, 파일 `0400` uid 1001 |
| 읽는 쪽 | 컨테이너에 같은 경로로 읽기 전용 마운트, Spring `configtree:` |
| 파일 이름 | 곧 속성 이름이다. `spring.datasource.password`, `masiton.security.jwt.private-key-pem` 등 |
| 환경 변수로 남긴 것 | `DB_URL`, `DB_USERNAME`, `REDIS_HOST`, `REDIS_PORT`, `SECRETS_DIR`, `SPRING_PROFILES_ACTIVE` (비밀 아님) |

파일 하나가 속성 하나이므로 JWT PEM처럼 여러 줄인 값도 이스케이프가 필요 없다. Redis `requirepass`(7.2절)와 Basic Auth `htpasswd`(10절)에 이미 쓰는 방식과 같다.

`ExecStartPre`에 `-` 접두사를 붙이지 않아 렌더링이 실패하면 컨테이너를 띄우지 않는다. 매 렌더링에서 이전 잔여물을 지운다. 파라미터를 삭제한 뒤에도 옛 파일이 남으면 지웠다고 믿은 비밀값이 계속 주입된다.

**노출 경로를 다시 측정했다.**

| 노출 경로 | 환경 변수 방식 | tmpfs 파일 방식 |
|---|---|---|
| `ps auxww` | 0건 | 0건 |
| `docker inspect` Env | **개인키 평문 노출** | **0건** (5개 패턴 모두) |
| 컨테이너 스펙 파일 | **평문 포함** | **0건** (실제 값 문자열 대조) |
| 디스크의 환경 파일 | 없음 | 없음 |

**파일 이름을 최종 속성 이름으로 두는 이유**는 프로파일 문서에 속성을 선언하지 않기 위해서다. `private-key-pem: ${jwt.private-key-pem}`처럼 매핑하면 값이 아니라 플레이스홀더라도 `ConfigurationLayeringTest`의 "프로파일 계층은 JWT 키 재료를 담지 않는다" 규칙에 걸린다. 규칙을 느슨하게 하지 않고 선언을 없애는 방향으로 맞췄다.

`configtree` 값이 공통 설정의 빈 기본값(`${JWT_KEY_ID:}` 등)을 이기는지는 추측하지 않고 테스트로 확정했다. 이긴다. `ProdSecretsConfigTreeTest` 5건이 매핑, 여러 줄 PEM 원문 보존, 선택 값 부재, 운영 불변값 상속을 고정한다.

**전환 과정에서 결함을 하나 만들었다.** 프로파일을 최종 속성 이름 방식으로 바꿀 때 렌더링 스크립트의 이름을 함께 바꾸지 않아 DB 비밀번호가 주입되지 않았고 Flyway가 커넥션을 열지 못해 재기동 루프에 들어갔다. 테스트는 최종 이름으로 파일을 만들어 통과했으므로 **테스트가 검증한 것과 스크립트가 만드는 것이 어긋난** 셈이다. 12.1절 Kakao 스텁 건과 같은 유형이다. 약 6분간 `/api`가 응답하지 않았다.

`prod` 프로파일은 저장소 접속값과 JWT 키에 기본값을 두지 않아 값이 없으면 기동이 실패한다. **Kakao·YouTube Key만 빈 기본값을 허용한다.** Key 없이도 공개 탐색·상세와 상태 확인이 동작하므로 배포와 Key 발급을 분리했다.

### 9.3. 관리자 계정

| 파라미터 | 유형 | 값 |
|---|---|---|
| `/masiton/admin/login-id` | `String` | `masiton-admin` |
| `/masiton/admin/password` | `SecureString` | 미출력 |

BCrypt 해시는 `htpasswd -nbBC 10 -i`로 만들었다. 강도 10은 애플리케이션 `BCryptPasswordEncoder` 기본값과 같아야 로그인이 성립한다. 애플리케이션 이미지에는 JRE만 있어 로컬 절차([scripts/New-LocalAdmin.ps1](../../scripts/New-LocalAdmin.ps1))처럼 클래스를 새로 컴파일할 수 없고 `openssl`은 bcrypt를 지원하지 않으므로 `httpd-tools`를 인스턴스에 설치했다. 검증용 도구이며 애플리케이션 실행에는 필요하지 않다.

암호는 명령행 인자로 넘기지 않았다(`-i`는 표준 입력에서 읽는다). 평문은 Parameter Store에만 있고 어디에도 출력하지 않았다.

### 9.4. 완료 조건 검증

| 완료 조건 | 결과 |
|---|---|
| `/internal/health/ready`와 `/internal/health/dependencies`가 EC2 내부에서 정상 | 통과 |
| PostgreSQL·Redis 상태가 각각 구분된다 | 통과 |
| 적용된 마이그레이션 버전과 이미지 digest가 기록된다 | 통과 (9.1절) |

```text
/internal/health/live         -> 200 {"components":{"ping":{"status":"UP"}},"status":"UP"}
/internal/health/ready        -> 200 {"components":{"db":{"status":"UP"}},"status":"UP"}
/internal/health/dependencies -> 200 {"components":{"db":{"status":"UP"},"redis":{"status":"UP"}},"status":"UP"}
```

`M2-08`에서 미완으로 남긴 "HTTPS로 프론트엔드와 `/api/**`가 모두 응답한다"도 이 시점에 충족됐다. 인터넷에서 확인한 결과다(제한 공개 적용 전).

```text
https://masiton.click/restaurants      -> 200 (화면)
https://masiton.click/api/restaurants  -> 200 {"items":[],"page":{...}}
https://masiton.click/api/creators     -> 200 {"items":[]}
https://masiton.click/internal/health/live -> 404
무인증 https://masiton.click/api/admin/restaurants -> 401 (traceId 포함)
```

### 9.5. 관리자 인증 흐름

`prod` 프로파일이 Parameter Store의 JWT 키와 Redis를 실제로 쓰는지 인스턴스에서 확인했다.

| 검증 | 결과 |
|---|---|
| 로그인 `POST /api/admin/auth/tokens` | `200`, `tokenType=Bearer`, `expiresInSeconds=1800` |
| JWT 헤더 | `{"kid":"prod-1","alg":"RS256"}` |
| Refresh 쿠키 | `HttpOnly`, `Secure` |
| Refresh Token 회전 | `200`이고 쿠키 값이 바뀐다 |
| 이전 Refresh Token 재사용 | `401`. 재사용 탐지 |
| 재사용 탐지 후 회전본 | `401`. Token 계열이 함께 폐기된다(ADR-DATA-005 6절) |
| 토큰 없음·잘못된 토큰으로 관리자 API | `401 AUTHENTICATION_REQUIRED` |
| 유효한 토큰으로 관리자 API | `400`으로 본문 검증 단계 진입. 인증·인가 통과 |
| 공개 GET 3종 무인증 | `200`·`200`·없는 자원 `404` |

관리자 경로는 모두 POST여서 `GET`은 `405`다. 인증 통과는 유효한 토큰으로 `401`이 아닌 `400`이 나오는 것으로 확인했다.

**로그인 실패 제한은 이 시점에 시험하지 않았다.** 실패 카운터의 `source`가 연결 원격 주소 기준인데 Nginx를 경유한 모든 요청이 `127.0.0.1`로 보이므로, 5회 실패를 만들면 15분 동안 모든 출처의 로그인이 막힌다. `M2-12`에서 시점을 정해 확인한다.

### 9.6. 애플리케이션 기동 후 실측

| 항목 | 실측 | [산정](m2-cost-and-sizing.md) 3절 |
|---|---|---|
| 전체 메모리 | 3,835 MB | 4 GiB 기준 |
| 사용 중 | **675 MB** | 합계 2,100 MB |
| 백엔드 컨테이너 | 320 MiB / 1 GiB 상한 | Spring Boot 1,000 MB |
| 프론트엔드 컨테이너 | 34 MiB / 512 MiB 상한 | Next.js 400 MB |
| Redis 컨테이너 | 8.3 MiB / 384 MiB 상한 | 256 MB |

**실측이 산정치의 3분의 1 수준이다.** 3.6절의 기동 직후 173 MB에서 502 MB만 늘었다. `t4g.medium`에 여유가 충분하고 CloudWatch Agent(`M2-10`)를 더해도 상한에 닿지 않는다.

## 10. M2-11 검증 참여자 제한 공개 (#50)

구성 일시 2026-07-30. 방식은 [계획 4절](m2-deployment-plan.md) 결정에 따라 Nginx Basic Auth다.

| 항목 | 값 |
|---|---|
| 자격 증명 | `/masiton/access/basic-auth-username`(`String`, `masiton-verify`), `/masiton/access/basic-auth-password`(`SecureString`) |
| htpasswd | `/run/masiton/htpasswd` (**tmpfs**), 소유자 `nginx`, 권한 `0400`, apr1 해시 |
| 렌더링 시점 | `nginx.service` drop-in `10-masiton-basic-auth.conf`의 `ExecStartPre` |
| realm | `masiton verification` |

htpasswd를 tmpfs에 두는 이유는 자격 증명이 루트 볼륨과 볼륨 스냅샷에 남지 않게 하려는 것이다. 그래서 기동마다 다시 만들어야 하고 drop-in이 그것을 보장한다. `reload`는 `ExecStartPre`를 실행하지 않으므로 설치 스크립트는 `restart`를 쓴다.

해시는 `openssl passwd -apr1`로 만든다. `httpd-tools`를 전제하지 않으려는 선택이며 Nginx가 apr1을 지원한다.

### 10.1. 완료 조건 검증

| 완료 조건 | 결과 |
|---|---|
| 검증 참여자만 접근하고 그 외 접근이 차단된다 | 통과 |
| 자격 증명이 저장소·이미지에 남지 않는다 | 통과. 저장소에는 렌더링 스크립트만 있고 값은 Parameter Store에만 있다 |

```text
인터넷 무인증  https://masiton.click/            -> 401  WWW-Authenticate: Basic realm="masiton verification"
인터넷 무인증  https://masiton.click/restaurants -> 401
인터넷 무인증  https://masiton.click/api/restaurants -> 401
인터넷 무인증  https://masiton.click/internal/health/live -> 404
자격 증명 사용 /restaurants                      -> 200
자격 증명 사용 /api/restaurants                  -> 200
```

**`/internal/**`은 자격 증명과 무관하게 `404`다.** `auth_basic off`로 인증보다 앞서 차단했다. 인증을 상속시키면 자격 증명이 없을 때 `401`이 되어 "인증만 통과하면 열리는 경로"로 보이고 `M2-08`에서 검증한 `404`와도 달라진다.

Basic Auth는 제한 공개 수단이며 관리자 인증을 대체하지 않는다. `/api/admin/**`의 JWT·`ADMIN` 검증은 9.5절 그대로다.

**검증 참여자에게 전달할 값은 Parameter Store에서 직접 읽는다.** 이 문서와 저장소에 평문을 적지 않는다.

```bash
aws ssm get-parameter --profile masiton --name /masiton/access/basic-auth-password --with-decryption --query Parameter.Value --output text
```

## 11. M2-10 CloudWatch 로그·지표·알람 (#49)

구성 일시 2026-07-30. **부분 완료다.** Slack 도달 시험만 남았고 이유는 11.4절에 있다.

### 11.1. 로그 수집

| 로그 그룹 | 원본 | 보관 |
|---|---|---|
| `/masiton/nginx/access` | `/var/log/nginx/access.log` (JSON) | 14일 |
| `/masiton/nginx/error` | `/var/log/nginx/error.log` | 14일 |
| `/masiton/containers` | `/var/lib/docker/containers/*/*-json.log` | 14일 |

수집은 CloudWatch Agent `1.300067.1`(arm64)이 한다. 설정은 [`deploy/cloudwatch/amazon-cloudwatch-agent.json`](../../deploy/cloudwatch/amazon-cloudwatch-agent.json)이고 호스트 지표로 `MemoryUsedPercent`·`DiskUsedPercent`를 함께 올린다.

**Nginx 로그 포맷을 JSON으로 바꿨다.** 배포판 기본 `main` 포맷에는 응답 시간이 없어 p95를 계산할 수 없었다. `status`와 `request_time`을 필드로 남겨 지표 필터가 읽는다. 요청·응답 본문은 남기지 않는다(ADR-OBS-001 11장).

컨테이너 로그는 `json-file` 드라이버를 그대로 두고 Agent가 파일을 tail한다. `awslogs` 드라이버로 바꾸면 컨테이너별 로그 그룹을 나눌 수 있지만 `docker logs`와 systemd journal에서 로그가 사라진다. **대가로 세 컨테이너 로그가 한 스트림에 섞인다.** 구분이 필요해지면 그때 드라이버 전환을 판단한다.

`log_stream_name`에 `{filename}`을 쓰면 리터럴로 들어간다. Agent가 지원하는 것은 `{instance_id}`·`{hostname}`·`{local_hostname}`·`{ip_address}`·`{date}`다. 초기 스트림 `i-0b451f18bca827cc9-{filename}`이 그 흔적이며 14일 후 만료된다.

### 11.2. 지표 필터

`/masiton/nginx/access`에 세 개를 걸었다.

| 필터 | 패턴 | 지표 |
|---|---|---|
| `masiton-request-count` | `{ $.status >= 100 }` | `masiton/nginx RequestCount` |
| `masiton-server-error-count` | `{ $.status >= 500 }` | `masiton/nginx ServerErrorCount` |
| `masiton-request-time` | `{ $.request_time >= 0 }` | `masiton/nginx RequestTimeSeconds` (값은 `$.request_time`) |

상태 확인은 `/internal/**`이 인터넷에서 차단돼 외부 감시로 볼 수 없으므로 인스턴스 안에서 1분 주기로 호출해 지표로 올린다([`health-metrics.sh`](../../deploy/scripts/health-metrics.sh), `masiton-health-metrics.timer`). 정상 1 / 실패 0이고 `masiton/health` 네임스페이스에 `HealthLive`·`HealthReady`·`DependencyPostgres`·`DependencyRedis`로 남는다.

### 11.3. 알람

| 알람 | 조건 | 상태 |
|---|---|---|
| `masiton-server-error-rate` | 5분 구간 `IF(total>0, 100*5xx/total, 0)` ≥ 5 | `OK` |
| `masiton-latency-p95` | 5분 구간 `RequestTimeSeconds` p95 > 2초 | `OK` |
| `masiton-health-ready-failure` | `HealthReady` < 1이 연속 3회(1분 주기) | `OK` |
| `masiton-dependency-postgres-failure` | `DependencyPostgres` < 1이 연속 3회 | `OK` |
| `masiton-dependency-redis-failure` | `DependencyRedis` < 1이 연속 3회 | `OK` |

**저장소 알람을 둘로 나눈 이유**는 완료 조건이 "PostgreSQL·Redis 연결 실패가 각각 저장소 장애 알림을 발생시킨다"를 요구하기 때문이다. 하나로 묶으면 어느 저장소가 죽었는지 알림만으로 알 수 없다.

오류율 알람은 metric math를 쓴다. 전체 요청이 0이면 `IF`가 0을 반환해 트래픽 없는 구간의 오탐을 막는다. 상태 확인 알람은 `treat-missing-data breaching`이다. 지표가 끊긴 것 자체가 인스턴스나 timer 장애이므로 정상으로 보면 안 된다.

### 11.4. 알림 경로

```text
CloudWatch 알람 → SNS masiton-alerts → Lambda masiton-slack-notifier → Slack Incoming Webhook
```

| 자원 | 값 |
|---|---|
| SNS 토픽 | `arn:aws:sns:ap-northeast-2:711457211155:masiton-alerts` |
| Lambda | `masiton-slack-notifier` (python3.13, arm64, 128 MB, 15초) |
| 실행 역할 | `masiton-slack-notifier-role` (`AWSLambdaBasicExecutionRole` + `/masiton/alerts/*` 읽기) |
| 코드 | [`deploy/lambda/slack_notifier.py`](../../deploy/lambda/slack_notifier.py). 표준 라이브러리와 boto3만 사용 |

**Webhook URL은 코드와 환경 변수에 넣지 않고 Parameter Store SecureString `/masiton/alerts/slack-webhook-url`에서 읽는다.** URL 자체가 그 채널에 글을 쓸 수 있는 자격 증명이다.

이 파라미터는 2026-07-30에 등록했다. Lambda가 호출 시점마다 읽으므로 URL을 교체할 때 재배포가 필요 없다.

```bash
aws ssm put-parameter --profile masiton --name /masiton/alerts/slack-webhook-url --type SecureString --key-id alias/aws/ssm --tags Key=Project,Value=masit-on --value "발급받은_Webhook_URL"
```

AWS Chatbot을 쓰지 않은 이유는 ADR-OBS-001이 알림 채널을 **Slack Webhook**으로 명시했고 Chatbot은 Webhook이 아니라 Slack 앱 인증 방식이라 ADR 개정이 필요하기 때문이다.

### 11.5. 완료 조건 검증

| 완료 조건 | 결과 |
|---|---|
| 시험 알람이 Slack에 실제로 도달한다 | 통과 (11.6절) |
| PostgreSQL·Redis 연결 실패가 각각 저장소 장애 알림을 발생시킨다 | 알람 2종을 구분해 구성. 실제 장애 주입은 `M2-13` fail-closed 시험과 함께 한다 |
| 로그에 비밀번호·JWT·API 키 원문이 없다 | 통과 |

로그 검사는 패턴과 실제 값 두 방식으로 했다.

```text
패턴(BEGIN PRIVATE KEY|JWT_PRIVATE_KEY_PEM|requirepass|Bearer eyJ|eyJraWQ)
  nginx access.log      -> 0건
  nginx error.log       -> 0건
  컨테이너 로그          -> 0건
실제 값 대조(admin·redis·tls·db 네 비밀값 문자열)
  전체                  -> 0건
```

패턴만으로는 형식이 다른 값을 놓칠 수 있으므로 Parameter Store의 실제 값을 읽어 문자열 일치도 함께 확인했다.

### 11.6. Slack 도달 시험

SNS 토픽에 알람 형식 메시지를 직접 publish해 경로 전체를 확인했다.

| 확인 | 결과 |
|---|---|
| Lambda 호출 | 성공. 818 ms·781 ms, 예외 없음 |
| Slack 응답 | `ok`. 코드가 `ok`가 아니면 예외를 던지므로 무예외가 곧 수신 증거다 |
| 채널 도달 | `#masiton-alerts`에 메시지 표시 확인 |
| 표시 형식 | 상태 표시(`OK`/`ALARM`), 알람 이름, 설명, 상태 변경 이유, 리전·시각 |

**시각 표기를 KST로 바꿨다.** CloudWatch `StateChangeTime`은 항상 UTC로 오는데 그대로 보내면 운영자가 매번 9시간을 환산해 읽어야 한다. `+0000`과 `Z` 두 표기를 받아 `2026-07-30 15:10:00 +09:00` 형태로 만들고, 해석하지 못하는 값은 원문을 그대로 둔다. 알림이 시간 파싱 실패로 사라지는 것보다 원문을 보여주는 편이 낫다. 고정 오프셋을 쓰는 이유는 KST에 일광 절약 시간이 없고 Lambda 런타임에 tzdata가 없을 수 있기 때문이다.

### 11.7. 인증서 만료 임박 알람

PR 리뷰 지적으로 추가했다. 배포 계획 4.1절이 M2-10 알람에 포함한다고 정한 항목인데 빠져 있었다.

| 알람 | 지표 | 임계 | 잡는 것 |
|---|---|---|---|
| `masiton-acm-certificate-expiry` | `AWS/CertificateManager DaysToExpiry` | 30일 | ACM 자동 갱신 실패 |
| `masiton-installed-certificate-expiry` | `masiton/health InstalledCertificateDaysToExpiry` | 21일 | 갱신본 재배포 실패 |

**ACM 지표만으로는 두 번째를 잡을 수 없다.** ACM이 갱신하면 그쪽 남은 일수는 늘어나고 설치본만 만료로 간다. 그래서 [`health-metrics.sh`](../../deploy/scripts/health-metrics.sh)가 Nginx에 설치된 인증서의 남은 일수를 지표로 올린다. 임계를 다르게 둬 두 알람의 원인이 구분된다. ACM 알람 없이 설치본 알람만 뜨면 재배포 경로 문제다.

인증서를 읽지 못하면 지표를 올리지 않는다. 0을 올리면 만료 임박으로 오탐하고 임의값을 올리면 실제 만료를 가린다. 지표가 끊기는 것은 `treat-missing-data=breaching`이 잡는다.

측정값은 197일이고 인증서 만료일(2027-02-12)과 일치한다. 알람은 7종이 됐다.

## 12. M2-12 배포 후 기능 검증 (#51)

검증 일시 2026-07-30. **진행 중이다.** 남은 항목은 12.5절에 있다.

### 12.1. 실제 외부 API 연동에서 드러난 결함 2건

**로컬 통합 검증에서는 드러나지 않았고 운영에서 실제 Kakao API를 호출하자 나타났다.** 두 건 모두 원인이 같다. WireMock 스텁이 제공자의 실제 응답과 달랐다.

| 필드 | 스텁 | 실제 Kakao | 결과 |
|---|---|---|---|
| `place_url` | `https://place.map.kakao.com/...` | **`http://`** | scheme이 https일 때만 후보로 채택해 모든 후보가 탈락 |
| `road_address_name` | `서울특별시 마포구 ...` | **`서울 강남구 ...`** | 자치구 추출 패턴 `^서울특별시\s+([^\s]+구)\s+.+$`가 매칭되지 않아 검증 예외 |

**두 결함이 겹쳐 맛집 등록이 실제 API로는 전혀 성립하지 않았다**(FR-ADMIN-002). 등록은 관리자 화면의 4종 중 하나이고 방문관계·상세·검색이 모두 맛집에 의존하므로 M2-12 전체가 막히는 결함이었다.

수정은 제공자 표기를 도메인 표기로 바꾸는 Adapter 책임으로 처리했다. `place_url`은 http·https를 모두 받아 host·path로 동일성을 판정하고 저장 값을 https로 정규화한다. 도로명주소는 `서울 `로 시작하면 `서울특별시 `로 바꾸고, **서울 밖 주소는 바꾸지 않는다.** 등록 서비스가 자치구 추출에서 거부해야 하며 여기서 서울로 보이게 만들면 그 판정이 무력해진다.

스텁도 실제와 같은 표기로 바꿨다. 같은 유형이 다음에는 로컬에서 잡힌다. `restaurant` 도메인과 외부 검증 테스트 59건이 통과한다.

**교훈은 스텁이 계약이 아니라는 것이다.** 스텁은 제공자 응답을 모사한 것이므로, 제공자의 실제 표기와 다르면 통합 테스트가 통과해도 운영에서 실패한다. 외부 연동 스텁을 만들 때 실제 응답 표본을 근거로 삼아야 한다.

### 12.2. 관리자 등록 4종

`prod` 프로파일로 실제 Kakao·YouTube API를 호출해 확인했다. 미리보기가 `decision: READY`와 `confirmationToken`을 주고 생성이 `201`을 반환하는 2단계 흐름이 계약대로다.

| 자원 | 값 | 식별자 |
|---|---|---|
| 맛집 | `서울집` (강남구, 한식, 서울특별시 강남구 언주로93길 22-3) | `42854b7b-705f-45c7-80b7-471f4270089a` |
| 유튜버 | `성시경 SUNG SI KYUNG` | `353be37a-447a-44a8-b002-4f658614c2c0` |
| 영상 | `[sub] 성시경의 먹을텐데 l 역삼역 서울집` | `3daf1b02-168f-47c7-ae9f-e4fb54552b51` |
| 방문관계 | 위 셋의 연결 | `3db9fed4-0e43-4692-b049-185c392d34b9` |

**실제 방문 사실과 일치하는 조합이다.** 성시경 채널의 해당 영상이 그 맛집을 다룬다. 흐름만 확인하려고 임의로 엮은 관계가 아니다.

Kakao place ID와 YouTube channel/video ID는 관리자 API 응답에 노출되지 않는다(계약 4절). 응답에는 정규화된 이름·주소·URL·제목·채널명·썸네일만 있다.

### 12.3. 공개 탐색·상세

| 검증 | 결과 |
|---|---|
| 전체 목록 | `200`. `visitedBy`와 `remainingVisitedByCount` 포함 |
| 이름 검색 `query=서울집` | `200` |
| 지역 필터 `district=강남구` | `200` |
| 음식 종류 `category=한식` | `200` |
| 유튜버 필터 `creatorId` | `200` |
| 복합 AND (4개 동시) | `200` |
| 일치 없는 필터·검색어 | `200` + 빈 `items` |
| 페이지 1-base, 크기 10 | `200` |
| 유튜버 목록 | `200`. 페이지 없는 `{ "items": [...] }` |
| 맛집 상세 | `200`. `contentStatus: AVAILABLE`, `visitedBy` 1건, `videos` 1건 |
| 없는 맛집 | `404 RESTAURANT_NOT_FOUND` |
| 서울 밖 지역 | `400 INVALID_FIELD_VALUE` (`district`) |
| 허용되지 않은 크기 `size=30` | `400`. 허용값 안내 포함 |

빈 결과가 `200`에 빈 `items`이고 없는 단일 자원만 `404`라는 계약이 운영에서도 유지된다.

**한글 쿼리 파라미터를 퍼센트 인코딩하지 않고 raw 바이트로 보내면 Tomcat이 `400`(HTML)로 거부한다.** 처음 검증에서 이 형태로 호출해 애플리케이션 결함으로 오인했다. 브라우저와 `URLSearchParams`는 항상 인코딩하므로 화면 경로에는 영향이 없다. 검증 스크립트를 쓸 때 주의할 지점이다.

### 12.4. 인증 경계

9.5절에서 확인한 항목이 등록 데이터가 있는 상태에서도 같다. 무인증·잘못된 토큰은 `401`, 유효한 토큰은 통과, 공개 GET 3종은 무인증으로 동작한다. 제한 공개(Basic Auth)는 10절 그대로다.

### 12.5. 제한 공개 Basic Auth가 관리자 API를 전부 차단하던 결함

화면 수준 인수 확인에서 드러났다. **12.2절 검증은 서버 안에서 `127.0.0.1:8080`으로 직접 호출해 Nginx를 건너뛰었기 때문에, 그 절이 통과했어도 브라우저에서는 관리자 등록이 하나도 되지 않는 상태였다.**

원인은 헤더 충돌이다. `M2-11` 제한 공개의 Basic Auth와 관리자 JWT가 같은 `Authorization` 헤더를 쓴다. 브라우저는 앱이 `Authorization: Bearer`를 지정하면 Basic 자격 증명을 붙이지 않으므로, Bearer를 실은 요청은 Nginx의 `auth_basic`에서 자격 증명 없는 요청으로 판정돼 `401`이 되고 백엔드에 도달하지 않는다.

접근 로그가 그대로 보여준다. 같은 브라우저에서 18초 차이다.

```text
11:19:43 POST /api/admin/auth/tokens                       status=200 upstream=200
11:20:01 POST /api/admin/restaurant-registration-previews  status=401 upstream=-
```

`upstream`이 비어 있으면 프록시를 하지 않았다는 뜻이다. 로그인과 재발급만 Bearer를 싣지 않아 통과했고 등록 4종과 로그아웃은 전부 막혀 있었다.

**화면에 오류가 뜨지 않고 `미리보기 확인 중…`에서 멈춘 것도 같은 원인이다.** [`auth.ts`](../../frontend/lib/admin/auth.ts)의 `authenticatedFetch`는 `401`을 받으면 곧바로 재발급을 호출하는데, 미리보기 POST 3건에 재발급 POST가 0건이었다. `401`이 앱 코드까지 전달되지 않아 `fetch`가 끝나지 않았고 mutation이 pending에 머물렀다. Nginx가 돌려준 `WWW-Authenticate: Basic` 챌린지를 브라우저가 자체 처리로 붙잡기 때문이다.

수정은 `location /api/`의 `auth_basic`을 변수로 받아 요청별로 판정한다([`01-masiton-api-auth-map.conf`](../../deploy/nginx/01-masiton-api-auth-map.conf)). `/api/admin/**`에 Bearer를 실은 요청만 Basic을 면제하고, 그 요청은 백엔드가 JWT와 `ADMIN`을 검증한다.

**로그인과 재발급은 JWT를 요구하지 않는 무인증 경로여서 Bearer 유무와 무관하게 Basic을 계속 요구한다.** 면제하면 Bearer를 임의로 붙여 인터넷에서 자격 증명 시도를 반복할 수 있고, 로그인 실패 5회 차단이 전원을 잠그는 수단이 된다(13.7절).

반영 후 Nginx를 경유해 경계를 다시 확인했다.

| 요청 | 기대 | 결과 |
|---|---|---|
| 관리자 API + Bearer | 백엔드 도달 | `upstream=401`. 도달 후 JWT 필터가 거부 |
| 관리자 API, 헤더 없음 | Basic 유지 | Nginx 차단 |
| 로그인 POST, Bearer 유무 무관 | Basic 유지 | Nginx 차단 |
| 재발급 POST + Bearer | Basic 유지 | Nginx 차단 |
| 로그아웃 DELETE + Bearer | 백엔드 도달 | `upstream=401`. Refresh Token 폐기 경로가 열림 |
| 공개 GET, Bearer 유무 무관 | Basic 유지 | Nginx 차단 |
| 화면 경로 + Bearer | Basic 유지 | Nginx 차단 |
| `/internal/health/live` | `404` | Bearer 유무 무관 `404` |

제한 공개 범위는 그대로다. 공개 GET과 화면 경로는 Bearer를 붙여도 Basic을 요구하고, `/internal/**`은 자격 증명과 무관하게 없는 것으로 응답한다.

**브라우저에서도 확인했다.** 관리자 화면에서 이미 등록된 `서울집`의 카카오 장소 URL로 미리보기를 요청해 `이미 등록된 맛집입니다`와 기존 자원 정보를 3초 안에 받았다. 50초 넘게 끝나지 않던 동작이다. 중복 판정이라 자원은 만들어지지 않았고, 브라우저부터 Kakao 호출까지 전체 경로가 동작하는 것을 확인했다.

**교훈은 인터넷 진입점을 경유하지 않은 검증이 화면 흐름을 보장하지 않는다는 것이다.** 12.2~12.4절이 모두 통과했는데도 Nginx가 앞에 있는 실제 경로에서는 관리자 흐름이 성립하지 않았다. 12.1절의 "스텁이 계약이 아니다"와 같은 유형이다. 검증 경로가 사용자 경로와 다르면 통과가 통과가 아니다.

### 12.6. 남은 항목

- ~~팀원 관리자 계정 4개~~ → 2026-07-30에 생성하고 네 계정 모두 로그인 `200`을 확인했다. [`admin-account-create.sh`](../../deploy/scripts/admin-account-create.sh)로 만들었고 **생성 후 password 파라미터 4개를 삭제해 평문이 남지 않는다.**
- **`masiton-admin` 폐기.** 배포·리허설 검증용 계정이다. M2 종료 시 판단한다.
- ~~로그인 실패 5회 차단~~ → 13.7절에서 확인했다.
- **WS 담당자별 인수 확인.** 계획 `M2-12`는 4개 WS 담당자가 각자 흐름을 확인하고 기록하도록 정했다. 12.2~12.4절은 이우람이 API 수준에서 수행한 것이고 화면 수준 확인은 담당자별로 진행 중이다. 2026-07-30 기준으로 목록·검색·필터(양성훈)와 상세·관련 영상(박진영)이 통과했다. 상세 화면은 잘못된 식별자를 일시적 조회 실패로 표시하고 `traceId`를 노출하지 않는 문제가 남았다. **같은 오류를 목록 화면은 종류에 맞는 문구와 `traceId`로 노출하므로 상세 화면의 오류 매핑 문제다.** 관리자 인증·등록(김인안)은 12.5절 결함으로 막혀 있었고 수정 후 미리보기 동작을 확인했으므로 담당자 재확인이 필요하다. 유튜버 탐색(이우람)은 중복 제거 1건을 뺀 나머지가 통과했다.

**중복 제거는 운영 화면에서 검증할 수 없다.** 같은 맛집을 같은 유튜버가 여러 영상으로 방문한 상황이 필요한데, `성시경 SUNG SI KYUNG` 채널에 `서울집`을 다룬 영상은 한 건뿐이다. 만들려면 실제로 없는 방문 근거를 등록해야 하고, 그것은 12.2절이 지킨 "실제 방문 사실과 일치하는 조합"을 깨뜨린다. 이 규칙은 `VisitQueryIntegrationTest`의 `같은Restaurant에같은Creator가다른Video로두번방문_후보는한번만반환한다`가 PostgreSQL에서 검증한다. 실제 중복 사례가 등록되면 화면에서 다시 확인한다.

## 13. M2-13 복구 리허설 (#52)

리허설 일시 2026-07-30. 계획이 정한 네 항목을 모두 수행했다.

### 13.1. RDS 스냅샷 복구

| 항목 | 값 |
|---|---|
| 사용한 스냅샷 | `masiton-db-m2-13-rehearsal` (수동, 2026-07-30 07:20 UTC) |
| 복구 인스턴스 | `masiton-db-restore-test` (`db.t4g.micro`, 사설 서브넷, 퍼블릭 액세스 없음) |
| 스냅샷 생성 소요 | 2분 20초 |
| 복구 완료 소요 | 약 7분 (`creating` → `available`) |

**먼저 최신 자동 스냅샷(`rds:masiton-db-2026-07-29-18-05`)으로 복구했더니 테이블이 하나도 없었다.** 그 스냅샷은 2026-07-29 18:05 UTC 것이고 Flyway 마이그레이션은 2026-07-30 05:12 UTC(`M2-09` 배포)에 적용됐기 때문이다. 결함이 아니라 자동 백업이 일 1회(18:00-18:30 UTC)라는 구성의 결과다.

**그래서 현재 데이터가 담긴 수동 스냅샷을 만들어 다시 복구했다.** 자동 스냅샷만으로는 "데이터가 복원되는지"를 검증할 수 없었다.

복구 인스턴스와 운영을 대조한 결과다.

| 테이블 | 운영 | 복구 |
|---|---:|---:|
| `admin_account` | 5 | 5 |
| `confirmation_token` | 3 | 3 |
| `creator` | 1 | 1 |
| `food_category` | 10 | 10 |
| `region` | 25 | 25 |
| `restaurant` | 1 | 1 |
| `video` | 1 | 1 |
| `visit` | 1 | 1 |

내용도 일치한다. 맛집 `서울집 / 서울특별시 강남구 언주로93길 22-3`, 유튜버 `성시경 SUNG SI KYUNG`, 관리자 계정 5개 목록이 같다. 마이그레이션 이력도 `V1 create initial schema success=true` 하나로 동일하다.

**RPO 판정.** 복구 지점은 스냅샷 시각이며 그 이후 변경은 유실된다. 자동 스냅샷이 일 1회이므로 최악의 경우 24시간 직전 상태로 돌아간다. 계약이 정한 **RPO 최대 24시간을 충족한다**(RV-NFR-010). 리허설 시점의 실제 간격은 11분이었다.

복구 인스턴스와 수동 스냅샷은 대조 후 삭제했다. 자동 스냅샷 2건은 그대로 남아 있다.

### 13.2. Redis 재기동과 장애 대응

| 검증 | 결과 |
|---|---|
| 재기동 후 인증 상태 유지 | 통과. `auth:refresh:*` 22개가 그대로이고 재발급이 `200` |
| Redis 장애 시 재발급 | `401 AUTHENTICATION_REQUIRED`. fail-closed 성립 |
| Redis 장애 시 공개 조회 | `200`. 영향 없음 |
| Redis 장애 시 상태 확인 | `503`, `db: UP` / `redis: DOWN`. 저장소 구분 성립 |
| 복구 후 | 로그인 `200`, `dependencies` `200` |

AOF로 인증 상태가 복원되므로 재기동이 세션 전량 소실로 이어지지 않는다(계획 9절 위험 항목 해소).

**Redis 장애 중 로그인은 `500 INTERNAL_SERVER_ERROR`다.** [ADR-DATA-005](../07-adr/data/data-005-redis-refresh-token.md) 12절이 fail-closed 후 "재로그인을 요구한다"고 정했지만, 로그인 자체가 실패 카운터와 Refresh Token 저장에 Redis를 쓰므로 Redis가 살아나기 전에는 성립하지 않는다. 그 자체는 불가피하다. 문제는 표현이다. [오류 계약](../05-specs/api/common/error-contract.md)에 저장소 장애용 응답 코드가 없어 "예상하지 못한 내부 실패"로 정의된 `500`이 쓰인다. **공통 계약 변경은 소유자 합의가 필요하므로 임의로 바꾸지 않았고 팀 결정 사항으로 남긴다.** 운영 감지 경로인 `/internal/health/dependencies`는 이미 `503`으로 정확히 구분한다.

### 13.3. 직전 이미지 롤백

| 단계 | 소요 | 확인 |
|---|---:|---|
| `8b77449` → `e41c19e` 롤백 | 30초 | 목록·상세·유튜버 목록·`dependencies` 모두 `200` |
| `e41c19e` → `8b77449` 복귀 | 29초 | 목록·`dependencies` `200`, digest 복귀 확인 |

롤백은 이전 커밋 SHA로 `app-deploy.sh`를 다시 실행하는 것이며, 실행 참조가 digest 파일 한 줄이라 unit 수정이 없다(9.1절).

### 13.4. 인스턴스 재기동

재기동 요청 07:32:18 UTC, 부팅 07:32:29 UTC.

| 확인 | 결과 |
|---|---|
| 인터넷 응답 회복 | 부팅 후 **약 30초** |
| 서비스 자동 기동 | `docker`·`masiton-redis`·`masiton-backend`·`masiton-frontend`·`nginx`·`amazon-cloudwatch-agent`와 타이머 2종 모두 `enabled`·`active` |
| tmpfs 재렌더링 | `/run/masiton/redis.conf`와 `/run/masiton/htpasswd`가 07:32:37에 다시 생성 |
| 핵심 조회 | 상태 확인 3종과 공개 API 3종 모두 `200` |
| 인증 상태 | `auth:refresh:*` 24개 유지 |
| 인증서 | `notAfter=Feb 12 2027` 유지 |

**수동 개입 없이 복구됐다.** 문서화된 절차만으로 복구가 성공한다는 완료 조건을 만족한다(NFR-AVAILABILITY-002).

### 13.5. 리허설이 잡아낸 결함

**`/run/masiton` 디렉터리 권한이 스크립트마다 달라 재기동 후 Basic Auth가 깨졌다.**

`basic-auth-render.sh`는 `0755`, `redis-render-conf.sh`와 `tls-deploy-cert.sh`는 `0700`으로 같은 디렉터리를 만들었다. 마지막에 만든 쪽이 이기므로 Redis가 먼저 뜨는 부팅 경로에서는 `0700`이 남고, Nginx worker가 `htpasswd`에 도달하지 못한다.

증상이 까다로웠다. **무인증 요청은 `401`로 정상이고 자격 증명을 넣은 사람만 `500`을 받는다.** 겉으로는 제한 공개가 동작하는 것처럼 보인다. `error_log`의 `open() "/run/masiton/htpasswd" failed (13: Permission denied)`로 확인했다.

실제로 Redis fail-closed 리허설에서 `systemctl restart masiton-redis`를 실행한 07:26부터 약 20분간 검증 참여자 접속이 막혔다. 로그에 `222.109.6.149`, `180.64.121.111`의 같은 오류가 남아 있다.

`0711`로 통일해 고쳤다. 탐색만 허용하고 목록 열거는 막으며 파일 내용은 각 파일의 `0400`이 지킨다. `nginx` 사용자가 `htpasswd`는 읽고 `redis.conf`는 읽지 못하는 것을 확인했고, Redis를 다시 재기동해도 권한이 회귀하지 않는 것까지 확인했다.

**재기동 경로에서만 재현되는 결함이었다.** 리허설이 없었다면 다음 재기동이나 인스턴스 교체 때 조용히 접속이 막혔을 것이다.

### 13.6. 배포가 알람을 발생시킨다

롤백 리허설로 백엔드를 두 번 재기동하는 동안 Nginx가 upstream에 붙지 못해 `502`가 발생했고, 5분 구간 5xx 비율이 **9.17%**가 되어 `masiton-server-error-rate`가 `ALARM`으로 전환됐다. Slack에도 도달했다.

알람 경로가 실제 지표에 반응한다는 증거이므로 `M2-10` 완료 조건에는 긍정적이다. 다만 **배포마다 알람이 울리는 운영 소음**이 된다. [NFR-DEPLOYMENT-004](../01-requirements/non-functional-requirements.md#nfr-deployment-004-단계별-실행-및-초기-운영-배포-복잡도-제한)가 무중단 배포를 요구하지 않으므로 동작 자체는 정상이다.

**임계값을 임의로 완화하지 않는다.** [ADR-OBS-001](../07-adr/quality/obs-001-logging-observability.md) 11절이 "운영 실측 없는 임계값 완화"를 금지한다. 대응 방향은 팀 결정 사항이며 선택지는 배포 창을 알람 억제와 함께 운영하는 것, Nginx가 upstream 재시도로 재기동을 흡수하게 하는 것, 실측 누적 후 임계를 재산정하는 것이다.

### 13.7. 로그인 실패 제한

| 검증 | 결과 |
|---|---|
| 잘못된 비밀번호 5회 | 매회 `401 AUTHENTICATION_REQUIRED`. 원인을 구분하지 않는다 |
| 카운터 | `auth:login-failure:source:{해시}`와 `auth:login-failure:login-id:{해시}` 모두 `5`, TTL `899`초 |
| 차단 중 올바른 비밀번호 | `401`. 차단 성립 |
| 차단 중 다른 `loginId` | `401`. **`source` 기준 차단이 확인됐다** |
| 공개 조회 | `200`. 영향 없음 |

카운터 키가 원문이 아니라 SHA-256 해시라는 계약도 그대로다.

**TTL은 추가 실패로 연장되지 않는다.** 차단 중 시도가 카운터를 계속 올려 TTL이 갱신되는 것으로 의심했으나 코드를 확인해 사실이 아님을 확정했다. `RedisLoginFailureStore`의 Lua 스크립트가 `attempts == 1`일 때만 `EXPIRE`를 설정하므로 계약의 "첫 실패부터 15분"이 지켜진다. 관측한 잔여 TTL은 시험 이후 경과 시간과 일치했다.

**차단 해제는 두 경로가 있다.** TTL 만료를 기다리거나, 운영자가 카운터를 삭제한다. 리허설에서는 검증 참여자 접속을 빨리 되돌리기 위해 후자를 썼고 삭제 직후 로그인이 `200`으로 복구됐다. 이 절차는 운영 대응 수단으로 기록해 둔다.

```text
docker exec -e REDISCLI_AUTH=... masiton-redis redis-cli --scan --pattern 'auth:login-failure:*'
docker exec -e REDISCLI_AUTH=... masiton-redis redis-cli del <키>
```

시험이 만든 차단이 실제로 검증 참여자 로그인을 막았다. 카운터에 시험과 무관한 `loginId` 항목이 함께 잡혀 있었고, 그 사람은 자신의 비밀번호가 맞는데도 `401`을 받았다. **`source` 차단의 영향 범위를 실측으로 확인한 셈이다.**

**`source` 차단의 운영 영향을 기록해 둔다.** 실패 카운터의 `source`가 연결 원격 주소이고 Nginx를 경유한 모든 요청이 `127.0.0.1`로 보이므로, **한 사람이 5회 틀리면 15분 동안 전원의 로그인이 막힌다.** [인증 API 계약](../05-specs/api/admin/authentication-api.md) 8절이 "신뢰 프록시 설정이 없는 현재 MVP에서 연결 원격 주소를 사용한다"고 명시했으므로 계약대로의 동작이다. 검증 참여자에게 이 사실을 함께 안내했다.

## 14. PR 리뷰 반영 (#76)

`deploy/m2` → `main` PR에서 리뷰를 두 차례 받았다. 1차 5건은 커밋 `261d596`, `0aa41ce`, `cda12e8`, `4bbb80a`로, CD 추가 후 받은 2차 3건은 그 뒤 커밋으로 반영했다.

**1차 — 이미지·비밀값·외부 연동**

| 우선순위 | 지적 | 반영 |
|---|---|---|
| P1 | 이미지 비밀값 검사가 JAR 내부를 보지 못한다 | `/app`을 러너로 꺼내 JAR을 풀어 검사. `BOOT-INF/lib` 제외 |
| P1 | 컨테이너 환경 변수에 비밀값 평문이 남는다 | tmpfs 파일 주입으로 전환 (9.2절) |
| P2 | Kakao `place_url`의 비 HTTP scheme이 통과한다 | 정규화 전 scheme 검증, 회귀 테스트 6건 |
| P2 | 배포가 혼합 버전을 남길 수 있다 | 두 digest를 준비한 뒤 활성 참조를 함께 교체 |
| P2 | 인증서 만료 임박 감시가 빠졌다 | 알람 2종 추가 (11.7절) |

**2차 — CD와 배포 스크립트**

| 우선순위 | 지적 | 반영 |
|---|---|---|
| P1 | `app-secrets-render.sh`가 배포 산출물로 검증·설치되지 않는다 | 필수 파일 목록과 설치 대상에 추가 (9.1절) |
| P1 | 승인 게이트에서 롤백 대상 SHA를 고를 수 없다 | `workflow_dispatch` 입력 추가 (15.4절) |
| P2 | 실행 스크립트·unit이 이미지 준비 전에 활성 경로에 반영된다 | 두 pull 성공 후 이미지 참조와 함께 반영 (9.1절) |

### 14.1. JAR 내부 비밀값 검사

`grep -rIl`의 `-I`가 바이너리를 제외하므로 `/app`에 `application.jar` 하나만 있는 백엔드 이미지는 **사실상 무검사**였다.

`BOOT-INF/lib`은 검사에서 뺀다. 서드파티 라이브러리가 시험용 키를 담고 있어 오탐이 되고, 막아야 하는 것은 우리 소스·리소스가 비밀값을 싣고 나가는 경우다.

**패턴도 좁혔다.** JAR 내부까지 보게 되자 기존 패턴이 항상 걸렸다. `application.yml`의 `${JWT_PRIVATE_KEY_PEM:}`는 환경 변수 **이름**이고, `application-local.yml`의 `masiton_local`은 저장소에 공개된 로컬 기본값이다. 둘 다 이미지에 정상적으로 담긴다. 그래서 그 자체로 유출을 뜻하는 세 패턴만 남겼다.

- 개인키 PEM 블록
- AWS 장기 액세스 키(`AKIA…`). ADR-SEC-001이 발급을 금지한다
- Slack Webhook URL

실제 부트 JAR로 다섯 경우를 재현했다. 정상 JAR 0건, 심은 개인키·AWS 키·Webhook URL 각각 검출, `BOOT-INF/lib`에만 있는 값은 무시다.

**후속 수정이 하나 있었다.** 프론트엔드 이미지에는 JAR이 없어 해제 디렉터리가 만들어지지 않고, 존재하지 않는 경로를 받은 `grep`이 exit 2를 반환해 **"검사 실행 실패를 미검출로 처리하지 않는다" 가드가 CI를 실패시켰다.** 가드는 의도대로 동작했고 준비가 빠졌던 것이다.

### 14.2. 검증 결과

리뷰 반영 후 운영 환경에서 다시 확인했다.

```text
docker inspect Env 비밀값 패턴 5종   -> 모두 0건
컨테이너 스펙 파일 실제 값 대조 3종  -> 모두 0건
마운트                               -> /run/masiton/secrets 읽기 전용
상태 확인 3종                        -> 200, db·redis 개별 UP
공개 조회                            -> 200
관리자 로그인                        -> 200, kid=prod-1, RS256
맛집 등록 미리보기                   -> 200 DUPLICATE (실제 Kakao 호출 성립)
인터넷 화면·API·관리자 화면          -> 200
```

자동 테스트는 `KakaoPlaceVerificationAdapterTest` 6건, `ProdSecretsConfigTreeTest` 5건, `ConfigurationLayeringTest` 6건이 통과한다. CI 세 job 모두 통과했다.

### 14.3. 남은 팀 결정 — CD 범위

리뷰 과정에서 문서 간 간극을 하나 더 찾았다. [ADR-CI-001](../07-adr/platform/ci-001-github-actions-quality-gate.md)은 네 곳에서 EC2 배포를 M2 범위로 적는다("EC2 배포는 M2부터 활성화한다", "운영 배포 수동 승인 … 은 M2에서 검증한다"). 반면 계획의 `M2-09`는 승인 게이트 자동화를 요구하지 않는다.

M2에서 실제로 한 것은 파이프라인 없는 수동 실행이다. 담당자가 배포 시점을 판단해 SSM RunCommand로 `app-deploy.sh`를 실행했다.

**2026-07-30에 CD를 M2 범위로 포함하기로 정했다.** 이슈 [#78](https://github.com/team-youngkk/masit-on/issues/78)에 현재 상태, 제안 방식(GitHub Actions `environment` 승인 게이트 + SSM 실행), 완료 조건, 검토가 필요한 지점을 정리했다.

## 15. CD — 승인 게이트 배포 (#78)

구성 일시 2026-07-30. **CD를 M2 범위로 포함하기로 정한 결정**(14.3절)에 따라 이 마일스톤에서 구현했다.

### 15.1. 구성

경로가 두 개다. 둘 다 같은 승인 게이트를 거친다.

```text
push(main·deploy/m2)  → 빌드·테스트 → 이미지 빌드·검증·ECR push
                      → [environment: production 승인 대기]
                      → SSM으로 app-deploy.sh 실행 → 상태 확인

workflow_dispatch     → (빌드·테스트·이미지 job 건너뜀)
  image_tag=<커밋 SHA> → [environment: production 승인 대기]
                      → SSM으로 app-deploy.sh 실행 → 상태 확인
```

| 항목 | 값 |
|---|---|
| 위치 | [`ci.yml`](../../.github/workflows/ci.yml)의 `운영 배포` job |
| 트리거 | `push`(`main`·`deploy/m2`), `workflow_dispatch` |
| 배포 대상 | `push`는 그 실행의 커밋, `workflow_dispatch`는 `image_tag` 입력(비우면 브랜치 현재 커밋) |
| 순서 보장 | `needs: [images]`. `workflow_dispatch`에서는 이미지 job이 `skipped`이고 job 조건이 그 경우만 통과시킨다 |
| 승인 게이트 | GitHub `environment: production` (두 경로 공통) |
| 필수 리뷰어 | 팀 4인(`w00lam`·`tjdgns0618`·`inan0226`·`jinyp01`) |
| 배포 허용 브랜치 | `main`, `deploy/m2` |
| 실행 경로 | OIDC → `ssm:SendCommand`(`AWS-RunShellScript`) |
| 추가 권한 | `masiton-ssm-deploy` 인라인 정책. `SendCommand`를 대상 인스턴스와 문서로 제한 |

**별도 워크플로로 나누지 않았다.** 이미지 job과 순서를 보장하려면 `needs`가 필요하고, 워크플로를 나누면 `workflow_run`에 의존해야 한다. 그 트리거는 기본 브랜치에 파일이 있어야 발동하고 OIDC `sub`가 실제 push된 ref를 가리키지 않는다. 6.6절에서 이미 겪은 문제다.

**배포 스크립트를 저장소에서 실어 보낸다.** 인스턴스에 미리 설치해 두면 스크립트를 바꿀 때 수동 단계가 생겨 [ADR-RUNTIME-001](../07-adr/platform/runtime-001-docker.md) 12절이 경계한 "재현 절차가 문서 밖 암묵 지식이 되는 것"에 가까워진다. base64로 실어 인용과 개행 처리를 없앤다.

### 15.2. environment를 쓰면 OIDC subject가 바뀐다

첫 실행에서 `AssumeRoleWithWebIdentity`가 거절됐다. **job에 `environment`를 지정하면 OIDC `sub` claim이 `ref:refs/heads/...`가 아니라 `environment:production`이 된다.** 신뢰 정책이 ref 두 개만 허용하고 있어 맞지 않았다.

신뢰 정책에 다음 subject를 추가해 해결했다.

```text
repo:team-youngkk@307880221/masit-on@1308471593:environment:production
```

6.6절의 "`workflow_run`은 `sub`가 기본 브랜치를 가리켜 거절된다"와 같은 계열이다. **OIDC subject는 트리거와 job 구성에 따라 달라지므로 신뢰 정책을 고칠 때 실제 발급되는 값을 확인해야 한다.**

### 15.3. 완료 조건 검증

| 완료 조건 | 결과 |
|---|---|
| 승인 없이는 배포 job이 실행되지 않는다 | 통과. 이미지 job 후 `waiting`으로 정지 |
| 승인 후 배포가 실행되고 digest가 기록된다 | 통과. job summary와 실행 출력에 digest |
| 배포 후 상태 확인 실패 시 job이 실패한다 | 스크립트가 폴링 후 non-zero로 끝나고 명령 상태가 `Success`가 아니면 job도 실패한다 |
| 롤백도 같은 경로로 가능하다 | `workflow_dispatch`의 `image_tag`로 대상 커밋 SHA를 입력한다. 15.4절 |
| 권한이 인스턴스와 문서 단위로 제한된다 | 통과. `SendCommand`가 `i-0b451f18bca827cc9`와 `AWS-RunShellScript`로 제한 |
| 장기 AWS 액세스 키를 쓰지 않는다 | 통과. OIDC 단기 자격 증명 |

배포 결과를 인스턴스에서 대조했다.

```text
CD가 기록한 참조   backend  @sha256:183d652d71f445c49d9e2dac6a4cad0e0966b941771bd8d8140c731cf2b91399
ECR의 같은 태그    masiton-backend  sha256:183d652d... (일치)
서비스 4종         masiton-redis·masiton-backend·masiton-frontend·nginx 모두 active
상태 확인 3종      200, db·redis 개별 UP
공개 조회          200
docker inspect     비밀값 패턴 0건
인터넷             화면·API·관리자 화면 모두 200
```

**의도적 실패 차단은 아직 시험하지 않았다.** 이슈 완료 조건의 마지막 항목이며 16절에 남겼다.

### 15.4. 롤백 입력 경로

처음에는 롤백을 "이전 커밋 SHA의 실행을 재실행한다"로 적었다. **그 방식으로는 대상을 고를 수 없다.** 재실행은 그 실행의 `github.sha`를 그대로 다시 배포하므로, 배포 job이 없던 시점의 커밋에는 재실행할 실행 자체가 없다. 결국 장애 시 직전 digest를 되돌리려면 승인 게이트 밖에서 SSM을 수동 실행해야 했고, 이것이 "롤백도 같은 경로로" 요구를 충족하지 못한다. PR 리뷰에서 지적받아 고쳤다.

`workflow_dispatch` 입력을 넣었다.

| 항목 | 값 |
|---|---|
| 입력 | `image_tag` — 배포할 커밋 SHA 40자. 비우면 선택한 브랜치의 현재 커밋 |
| 승인 | `push` 경로와 같은 `environment: production` |
| 브랜치 제한 | `main`, `deploy/m2` (job 조건이 먼저 막아 승인 요청이 뜨지 않는다) |
| 빌드·테스트·이미지 job | 건너뛴다 |
| 대상 이미지가 없으면 | `app-deploy.sh`가 ECR 조회에서 실패해 배포하지 않는다 |

**빌드·테스트를 건너뛰어도 품질 게이트는 유지된다.** 이 경로는 ECR에 이미 있는 이미지만 배포하고, 그 이미지는 push 때 빌드·테스트 job을 통과해야 만들어졌다. 반대로 다시 도는 것은 배포 대상이 아닌 브랜치 현재 커밋을 검사하는 일이다. 롤백은 현재 커밋이 문제라서 하는 것이므로 그 검사를 기다리게 하면 복구만 늦어진다.

**입력값은 커밋 SHA 형식만 받는다.** 이 값은 SSM 명령 문자열에 들어가 인스턴스에서 root로 실행되므로 자유 문자열을 그대로 실으면 명령 주입이 된다. 승인 게이트는 사람의 판단을 요구하지만 입력 내용을 검사하지 않는다. 소문자 16진수 40자가 아니면 job이 자격 증명 획득 전에 실패하고, 스크립트 인수도 인용한다.

**동시성 그룹에 이벤트 종류를 넣었다.** `ref`만으로 묶으면 `cancel-in-progress`가 롤백 배포 중에 들어온 같은 브랜치 push 때문에 그 배포를 취소한다. 장애 대응 중 복구가 조용히 멈추는 방향의 실패다.

**이 롤백이 되돌리는 것은 이미지뿐이다.** 실어 보내는 `app-deploy.sh`·`app-run.sh`·`app-secrets-render.sh`·unit은 선택한 브랜치의 현재 내용이다(15.1절의 "배포 스크립트를 저장소에서 실어 보낸다"). 스크립트나 unit 변경 자체가 장애 원인이면 그 커밋을 되돌리는 변경을 브랜치에 반영해야 한다. 이미지 태그만 바꿔서는 해소되지 않는다.

**이 경로는 아직 운영에서 실행하지 않았다.** 워크플로 구성과 입력 검증까지 확인했고, 실제 `workflow_dispatch` 롤백 배포는 16절에 미검증으로 남긴다.

## 16. 예산 범위에 관한 확인 사항

`My Monthly Cost Budget`(`$100`/월)은 **계정 전체 비용**을 대상으로 한다. 2절에 적었듯 이 계정에는 다른 프로젝트 자원이 있어 그 비용도 이 예산에 합산된다.

현재 다른 프로젝트의 비용은 `commerce-payment` ECR 이미지 8개의 스토리지 요금뿐이고 월 1달러 미만으로 추정되므로 **예산 판정에 영향이 없다.** 맛잇온 비용만 분리해야 할 필요가 생기면 다음을 한다.

1. 생성한 모든 자원에 `Project=masit-on` 태그를 이미 붙여 두었다.
2. Billing 콘솔에서 `Project` 비용 할당 태그를 활성화한다.
3. `Project=masit-on` 필터를 건 예산을 따로 만든다.

비용 할당 태그는 활성화 후 최대 24시간이 지나야 새 데이터에 적용되므로 M2 일정 안에서는 즉시 쓸 수 없다.

### 16.1. 크레딧 만료

**계정 크레딧 4건이 2026-07-29에 전량 만료됐다.** 따라서 M2 운영 비용은 전액 실제 청구다.

| 크레딧 | 발행액 | 사용액 | 만료 후 소멸 |
|---|---:|---:|---:|
| AWS Free Tier | `$100` | `$32.42` | `$67.58` |
| Explore AWS: Launch an instance using EC2 | `$20` | `$0` | `$20` |
| Explore AWS: Create an Aurora or RDS database | `$20` | `$0` | `$20` |
| Explore AWS: Set up a cost budget using AWS Budgets | `$20` | `$0` | `$20` |

5·6·7월 사용액은 전부 크레딧으로 상쇄돼 순 청구가 사실상 0이었다(6월 `$29.81` 포함). **이후 사용분에는 상쇄가 없다.** [사양과 월 비용 산정](m2-cost-and-sizing.md)의 예상 비용이 이미 정가 기준이므로 산정치는 조정하지 않는다.

결제 수단 유효성은 담당자가 확인했다(2026-07-29). 크레딧 만료로 인한 계정 정지 위험은 없다.

`M2-12` 시점에 실제 청구액과 산정치를 대조한다(계획 10절 마지막 완료 항목).

## 17. 검증하지 못한 항목

2026-07-30 기준이다. Kakao·YouTube 운영 API Key와 Slack Webhook 발급은 이 목록에서 해소됐다(12절, 11.4절).

- **예산 초과 알림 실제 도달.** 시험 예산 `masiton-alert-test`(한도 `$0.5`, 실제 1% 초과)를 만들어 확인 중이다. AWS Budgets가 하루 약 3회만 평가해 즉시 도달하지 않는다. 도달 확인 후 시험 예산을 삭제한다.
- **SSH 접속.** 키 페어 `masiton-app`을 만들었으나 실제 SSH 접속은 시도하지 않았다. 인스턴스 접근은 SSM RunCommand로 검증했다.
- **`masiton-admin` 폐기.** 배포·리허설 검증용 계정이며 M2 종료 시 폐기 여부를 판단한다.
- **WS 담당자별 화면 인수.** 12.5절.
- **CD의 의도적 실패 차단.** 15절에서 승인 게이트와 정상 배포는 검증했으나, 실패하는 배포를 일부러 만들어 job이 차단되는지는 시험하지 않았다.
- **`workflow_dispatch` 롤백 배포.** 15.4절. 워크플로 구성과 입력 검증까지 확인했고 실제 실행은 하지 않았다. 스크립트 수준의 롤백은 13.3절에서 검증했다.
- **2차 리뷰 반영본의 운영 배포.** 14절 2차 3건은 배포 스크립트와 워크플로 변경이며, 컨테이너 안에서 스텁으로 활성화 순서를 검증했다. 인스턴스에서 실제 배포로 확인하지 않았다.

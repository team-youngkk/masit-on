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

**이 계정은 맛잇온 전용이 아니다.** 2026-06-05에 만든 다른 프로젝트의 ECR 리포지토리 `commerce-payment`(이미지 8개)가 있고 삭제된 RDS의 로그 그룹 `RDSOSMetrics`가 남아 있다. 예산 범위 영향은 8절에 적었다.

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
| 컨테이너 기동 | 미확인 (9절) | Next.js 16.2.11 기동, `/` → `200` |

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

**두 워크플로가 생겨 해소됐다.**

| 워크플로 | 소유 | 역할 |
|---|---|---|
| [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) | `develop` | 모든 브랜치 공통 품질 게이트. 빌드·테스트·이미지 검증·취약점 검사 |
| [`.github/workflows/images.yml`](../../.github/workflows/images.yml) | `deploy/m2` | `workflow_run`으로 CI 성공 뒤에만 실행. ECR push와 digest 기록 |

파일을 나눈 이유는 `ci.yml`이 `develop` 소유 공통 게이트여서 M2 전용 ECR·OIDC 단계를 섞으면 두 브랜치가 계속 어긋나기 때문이다. CI가 실패하면 `images.yml`에 도달하지 않으므로 push와 배포가 차단된다.

### 6.7. ECR push와 취약점 검사 결과

`M2-06` 완료 조건 중 워크플로가 없어 미검증이던 두 항목을 확인했다.

| 리포지토리 | 태그(커밋) | digest | push 시각 | 기본 스캔 |
|---|---|---|---|---|
| `masiton-backend` | `0b8daf4` | `sha256:ddcca41c2b02b0e0549056f908f60a0a022a1595c8165a0395e246a85e713afd` | 2026-07-29 21:58 KST | `COMPLETE`, 발견 0건 |
| `masiton-frontend` | `0b8daf4` | `sha256:3f32cde364e941ca5f559ea406c70a708ecc8b90eb70b375070f12a414830e11` | 2026-07-29 21:58 KST | `COMPLETE`, 발견 0건 |

같은 커밋 쌍이 그 이전 `630259b`에도 있다. **장기 액세스 키 없이 OIDC로 push됐고 digest로 식별된다.**

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

## 8. 예산 범위에 관한 확인 사항

`My Monthly Cost Budget`(`$100`/월)은 **계정 전체 비용**을 대상으로 한다. 2절에 적었듯 이 계정에는 다른 프로젝트 자원이 있어 그 비용도 이 예산에 합산된다.

현재 다른 프로젝트의 비용은 `commerce-payment` ECR 이미지 8개의 스토리지 요금뿐이고 월 1달러 미만으로 추정되므로 **예산 판정에 영향이 없다.** 맛잇온 비용만 분리해야 할 필요가 생기면 다음을 한다.

1. 생성한 모든 자원에 `Project=masit-on` 태그를 이미 붙여 두었다.
2. Billing 콘솔에서 `Project` 비용 할당 태그를 활성화한다.
3. `Project=masit-on` 필터를 건 예산을 따로 만든다.

비용 할당 태그는 활성화 후 최대 24시간이 지나야 새 데이터에 적용되므로 M2 일정 안에서는 즉시 쓸 수 없다.

### 8.1. 크레딧 만료

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

## 9. 검증하지 못한 항목

- **예산 초과 알림 실제 도달.** 시험 예산 `masiton-alert-test`(한도 `$0.5`, 실제 1% 초과)를 만들어 확인 중이다. AWS Budgets가 하루 약 3회만 평가해 즉시 도달하지 않는다. 도달 확인 후 시험 예산을 삭제한다.
- **SSH 접속.** 키 페어 `masiton-app`을 만들었으나 실제 SSH 접속은 시도하지 않았다. 인스턴스 접근은 SSM RunCommand로 검증했다.
- **HTTPS와 도메인 응답.** A 레코드 전파는 확인했으나 `masiton.click`으로 실제 HTTP·HTTPS 응답은 받지 못한다. Nginx가 아직 없다(`M2-08`).
- **백엔드 이미지 컨테이너 기동.** 이미지는 arm64로 빌드해 정적 검사를 통과했으나 컨테이너를 띄워 `/internal/health/live`를 확인하지는 않았다. 기동에 PostgreSQL·Redis 접속값이 필요하고 사설 서브넷 RDS는 작업자 PC에서 도달하지 않는다. `M2-09`에서 EC2 위에서 확인한다.
- **애플리케이션과 Redis 연결.** Redis 자체는 7절에서 검증했으나 Spring Boot가 `127.0.0.1:6379`로 붙어 Refresh Token 회전과 `/internal/health/dependencies`가 동작하는 것은 `M2-09`에서 확인한다. 앱 컨테이너가 호스트 loopback에 도달하는 방식(ADR-RUNTIME-001 11절이 운영 설정의 Docker 서비스명을 금지한다)도 그때 확정한다.
- **Redis 장애 시 fail-closed.** ADR-DATA-005 12절의 재발급 차단과 재로그인 복구는 애플리케이션이 붙은 뒤 `M2-13`에서 확인한다.
- **애플리케이션 기동 후 메모리.** 3.6절은 기동 직후 기준값이며 Nginx·Next.js·Spring Boot 실행 후 실측은 `M2-09`에서 한다. Redis 컨테이너까지 올라간 현재 값도 그때 함께 기록한다.

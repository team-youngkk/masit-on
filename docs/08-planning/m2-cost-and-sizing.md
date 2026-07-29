---
status: Draft
estimate_date: 2026-07-29
owners:
  - 이우람
related_documents:
  - m2-deployment-plan.md
  - ../01-requirements/non-functional-requirements.md
  - ../06-architecture/technology-policy.md
  - ../07-adr/platform/deploy-002-validation-deployment-before-expansion.md
  - ../07-adr/data/data-005-redis-refresh-token.md
  - ../07-adr/security/sec-001-secrets-workload-identity.md
  - ../07-adr/quality/obs-001-logging-observability.md
---

# M2 인스턴스 사양과 월 비용 산정

## 1. 문서 목적

[M2 초기 운영 배포 계획](m2-deployment-plan.md) 4절의 확인 필요 항목 중 **EC2 인스턴스 타입, RDS 인스턴스 클래스, Redis 인스턴스 사양**을 산정하고 월 예상 비용을 예산 목표 150,000원과 대조한다. `M2-01`의 산출물이며 `M2-03`·`M2-04`·`M2-05`가 이 값을 사용한다.

이 문서는 사양과 비용만 다룬다. 자원 생성 절차는 계획 문서 5절의 각 Task를 따른다.

## 2. 가격 자료의 신뢰 범위

**모든 금액은 콘솔에서 재확인해야 하는 산정치다.** 자료 출처를 등급으로 구분해 표기한다.

| 등급 | 뜻 |
|---|---|
| 확인 | `ap-northeast-2` 가격을 자료에서 직접 확인했다 |
| 환산 | `us-east-1` 가격을 확인하고 서울 배수를 적용해 계산했다 |
| 가정 | 사용량을 가정해 계산했다. 실제 사용량에 따라 달라진다 |

EC2 t4g 계열은 서울 가격을 세 개 인스턴스에서 직접 확인했고 `us-east-1` 대비 배수가 **1.238**로 일치한다(t4g.small `$0.0168`→`$0.0208`, t4g.medium `$0.0336`→`$0.0416`, t4g.large `$0.0672`→`$0.0832`). RDS·ElastiCache·EBS의 서울 가격은 공개 자료가 지역별 값을 가려 두어 직접 확인하지 못했고, 같은 1.238 배수를 적용해 환산했다. **RDS와 ElastiCache의 실제 서울 배수는 EC2와 다를 수 있으므로 `M2-01` 콘솔 작업에서 AWS Pricing Calculator로 대조한다.**

환율은 2026-07-28 종가 **1 USD = 1,470원**을 사용한다. 2026년 최고치 1,559원을 적용하면 예산 목표 150,000원은 `$96.2`에 해당하므로 환율 변동을 여유 판단에 포함한다.

- 월 시간은 730시간으로 계산한다.
- 데이터 전송(Out)은 월 100 GB 무료 한도 안이라고 보고 0으로 계산한다. 검증 참여자 제한 공개 트래픽은 이 한도에 크게 못 미친다.

## 3. 단일 EC2 메모리 산정

단일 인스턴스에 Nginx·Next.js·Spring Boot가 함께 올라간다([기술 정책 13절](../06-architecture/technology-policy.md)). 상주 메모리를 구성요소별로 합산한다.

| 구성요소 | 예상 RSS | 근거 |
|---|---:|---|
| Amazon Linux 2023 커널·기본 데몬 | 250 MB | arm64 최소 설치 기준 |
| SSM Agent + CloudWatch Agent | 150 MB | `M2-10`에서 설치 |
| Nginx | 50 MB | worker 2, 정적 캐시 없음 |
| Next.js 16 `next start` | 250 MB | App Router SSR, `frontend/package.json` 의존성 기준 |
| Spring Boot 4.1 JVM | 1,400 MB | heap 1 GiB + Metaspace·스레드·direct 약 400 MB |
| **합계** | **2,100 MB** | |

JVM heap 1 GiB는 **가정이 아니라 현재 설정의 결과다.** `Dockerfile`의 `ENTRYPOINT`에 heap 옵션이 없어 JVM이 기본 `MaxRAMPercentage` 25%를 적용하고, 4 GiB 호스트에서 최대 heap이 1 GiB가 된다.

- **t4g.small(2 GiB)은 불가하다.** 합계 2,100 MB가 물리 메모리를 이미 넘는다.
- **t4g.medium(4 GiB)을 사용한다.** 여유 약 1.9 GiB로 배포 중 구·신 컨테이너 동시 상주와 Flyway 마이그레이션 피크를 흡수한다.
- t4g.large(8 GiB)는 현재 사용량 대비 과도하고 월 `$30` 추가된다.

`M2-09`에서 실제 RSS를 측정해 이 표와 대조한다. heap 상한을 명시 옵션으로 고정할지 여부도 `M2-09`에서 판단한다. 이 문서는 사양만 정하고 실행 옵션은 바꾸지 않는다.

## 4. RDS 인스턴스 클래스

MVP 데이터는 초기 스키마 baseline의 기준 데이터(Region 25건, FoodCategory 10건)와 검증 중 등록하는 소량의 맛집·영상뿐이다. 동시 접속은 검증 참여자 수준이고 Hikari 기본 풀(10)을 넘지 않는다.

| 클래스 | vCPU / 메모리 | 판단 |
|---|---|---|
| db.t4g.micro | 2 / 1 GiB | 현재 데이터량과 접속 수에는 충분하다. `shared_buffers`가 256 MB로 잡힌다 |
| db.t4g.small | 2 / 2 GiB | 여유가 있으나 월 약 `$14` 더 든다 |

**db.t4g.micro로 시작하고 `M2-12` 기능 검증에서 `FreeableMemory`와 `CPUCreditBalance`를 확인한 뒤 필요하면 db.t4g.small로 올린다.** RDS 인스턴스 클래스 변경은 재기동만 필요하고 스키마·데이터에 영향이 없어 되돌리기 쉽다. 6절에서 두 경우의 총액을 모두 제시한다.

스토리지는 gp3 20 GiB(RDS 최소)로 시작한다. 자동 스냅샷은 프로비저닝 용량의 100%까지 무료이므로 7일 보관 스냅샷은 추가 비용이 발생하지 않는다.

## 5. Redis 사양

**계획 문서와 ADR이 요구하는 Redis 구성을 AWS 관리형 서비스로 만들 수 없다.** 제약을 확인한 뒤 2026-07-29 이우람이 **ElastiCache를 사용하지 않고 초기에는 단일 인스턴스로 배포한다**고 결정했다. 5.4절이 결정 내용이고 5.1~5.3절은 그 근거다.

### 5.1. 확인한 제약 두 가지

**첫째, ElastiCache는 Redis OSS 8.x를 제공하지 않는다.** AWS 문서는 "ElastiCache Serverless caches and node-based clusters support all Redis OSS versions 7.1 and before"라고 명시하고, 7.2 이상은 Valkey 계열만 있다. [기술 정책 3절](../06-architecture/technology-policy.md)이 고정한 `Redis Open Source 8.8`과 [ADR-DATA-005](../07-adr/data/data-005-redis-refresh-token.md) 10절의 `8.8 계열` 강제 규칙을 관리형으로는 만족할 수 없다.

**둘째, ElastiCache는 AOF를 지원하지 않는다.** 같은 문서가 "Redis OSS configuration variables `appendonly` and `appendfsync` are not supported on Redis OSS version 2.8.22 and later"라고 적었다. 계획 문서 `M2-05`가 요구하는 `AOF everysec`을 관리형에서는 설정할 수 없다.

두 제약은 독립적이다. 버전 문제를 Valkey로 우회해도 AOF 문제는 남는다.

### 5.2. 필요한 Redis 용량

저장 대상은 두 종류뿐이다.

- `auth:refresh:{adminId}` — 관리자 계정당 1건, JSON 약 200 B, TTL 14일
- `auth:login-failure:{loginIdHash}` — 실패 카운터, TTL 15분

MVP는 `ADMIN` 단일 역할에 소수 계정이므로 **전체 keyspace가 1 MB에 못 미친다.** 관리형 최소 노드(cache.t4g.micro, 0.5 GiB)조차 용량 관점에서는 과도하다. 즉 이 결정은 용량이 아니라 **버전 준수·영속화·비용·장애 격리**의 문제다.

### 5.3. 선택지

| 선택지 | 8.8 준수 | AOF everysec | 인터넷 미노출 | 월 추가 비용 | 계약 문서 변경 |
|---|:---:|:---:|:---:|---:|---|
| **R1** 앱 EC2에 Redis 8.8 컨테이너 동거 | O | O | O (loopback 바인딩) | `$0` | ADR-DATA-005 6절 `사설 서브넷 전용 인스턴스` 표현과 어긋난다 |
| **R2** 사설 서브넷 전용 EC2에 Redis 8.8 | O | O | O | `$43`~`$59` | 없다 |
| **R3** ElastiCache Valkey 8.x | X | X | O | `$14.5` | technology-policy 3절, ADR-DATA-005 10·11절 개정 필요 |
| **R4** ElastiCache Redis OSS 7.1 | X | X | O | `$14.5` | 같음. 게다가 EOL이 R3보다 이르다 |

**R2의 비용이 큰 이유**는 인스턴스 요금이 아니라 사설 서브넷 EC2의 외부 접근 경로다. 패키지 설치와 SSM 접속에 NAT Gateway(서울 약 `$0.059/hr`, 월 `$43` + 데이터 처리) 또는 인터페이스 VPC 엔드포인트 3종(`ssm`·`ssmmessages`·`ec2messages`, 월 약 `$28`)이 필요하다. 6절에서 보듯 R2는 예산 목표를 초과하거나 턱걸이한다.

**R1은 두 강제 규칙을 모두 지키면서 비용이 0이지만 단일 장애점이 커진다.** 앱 인스턴스가 죽으면 Redis도 함께 죽는다. 다만 [ADR-DATA-005](../07-adr/data/data-005-redis-refresh-token.md) 12절이 Redis 장애를 fail-closed로 처리하고 재로그인을 요구하도록 이미 정했고, [NFR-AVAILABILITY-002](../01-requirements/non-functional-requirements.md#nfr-availability-002-초기-운영-배포-가용성과-수동-복구)가 단일 인스턴스·수동 복구를 허용하므로 이 위험은 M2 요구 수준 안에 있다.

### 5.4. 결정 — R1

**ElastiCache를 사용하지 않고 Redis Open Source 8.8을 앱 EC2에 함께 올린다**(2026-07-29, 이우람). 고정 버전과 AOF `everysec`을 모두 지킬 수 있고, 추가 비용이 없으며, `M2`가 단일 인스턴스 구성을 전제하므로 전용 인스턴스를 따로 두는 이점이 크지 않다는 판단이다. 채택 구성은 6.2절의 **A**다.

`M2-05`가 만들 사양은 다음과 같다.

| 항목 | 값 | 근거 |
|---|---|---|
| 엔진 | Redis Open Source 8.8 (`redis:8.8-alpine`) | [기술 정책 3절](../06-architecture/technology-policy.md), 로컬 `docker-compose.yml`과 같은 태그 |
| 배치 | 앱 EC2의 컨테이너 | 5.4절 결정 |
| 바인딩 | `127.0.0.1:6379` | 인터넷·VPC 어디에서도 도달하지 않게 한다. [ADR-DATA-005](../07-adr/data/data-005-redis-refresh-token.md) 11절 퍼블릭 IP 금지 |
| 영속화 | AOF `appendfsync everysec` + RDB 스냅샷, 호스트 볼륨에 저장 | 계획 `M2-05`, 기술 정책 7절 |
| eviction | `maxmemory-policy noeviction` | `auth:refresh:*`·`auth:login-failure:*`가 축출되지 않아야 한다(계획 `M2-05` 완료 조건) |
| `maxmemory` | 256 MB | 5.2절 실제 keyspace가 1 MB 미만이라 상한에 닿지 않는다. 3절 메모리 여유 1.9 GiB 안이다 |
| 백업 | EC2 볼륨 스냅샷에 포함 | 관리형 자동 백업이 없으므로 `M2-13`에서 재기동 후 인증 상태 유지를 확인한다 |

**남은 절차 하나.** 이 결정은 [ADR-DATA-005](../07-adr/data/data-005-redis-refresh-token.md) 6절의 "운영은 사설 서브넷 전용 Redis 8.8 인스턴스를 사용한다"는 표현과 어긋난다. 강제 규칙(8.8 계열, 인터넷 미노출, 네임스페이스, TTL, 회전 원자성)은 모두 유지되지만 배치 표현은 갱신해야 한다. ADR 개정은 이 문서가 하지 않는다. 공동 owner(김인안·이우람) 합의로 ADR-DATA-005를 개정하거나 배치만 다루는 후속 ADR을 추가한다. **`M2-05` 착수 전에 처리한다.**

## 6. 월 비용 산정

### 6.1. 항목별 단가

| 항목 | 단가 | 등급 |
|---|---|---|
| EC2 t4g.medium | `$0.0416/hr` | 확인 |
| EC2 t4g.small | `$0.0208/hr` | 확인 |
| 퍼블릭 IPv4 (Elastic IP) | `$0.005/hr` | 확인 |
| EBS gp3 | `$0.0912/GB-월` | 환산 |
| RDS db.t4g.micro (PostgreSQL, Single-AZ) | `$0.0198/hr` | 환산 (`us-east-1` `$0.016` 확인) |
| RDS db.t4g.small (PostgreSQL, Single-AZ) | `$0.0396/hr` | 환산 (`us-east-1` `$0.032` 확인) |
| RDS gp3 스토리지 | `$0.131/GB-월` | 환산 |
| RDS 자동 스냅샷 | `$0` | 프로비저닝 용량 100%까지 무료 |
| ElastiCache cache.t4g.micro | `$0.0198/hr` | 환산 (`us-east-1` `$0.016` 확인) |
| NAT Gateway | `$0.059/hr` | 환산 |
| Route 53 호스팅 영역 | `$0.50/월` | 확인 |
| ECR 스토리지 | `$0.10/GB-월` | 확인 |
| CloudWatch 로그·알람·지표 | 약 `$3/월` | 가정 (로그 1.5 GB/월, 알람 4종, 사용자 지정 지표 5개) |
| KMS | `$0` | `aws/ssm` 관리형 키 사용 시. 고객 관리 키는 `$1/월` |
| Parameter Store 표준 | `$0` | 표준 파라미터·표준 처리량 무료 |

### 6.2. 구성별 월 총액

공통 기반은 EC2 t4g.medium + EBS 30 GiB + Elastic IP + RDS gp3 20 GiB + Route 53 + ECR + CloudWatch = `$43.07`이다. E·F의 Redis 전용 EC2는 t4g.small + EBS 8 GiB이고, 인터페이스 VPC 엔드포인트는 서울 `$0.0126/hr` 3종으로 계산했다.

| 구성 | Redis | RDS | 월 USD | 월 KRW | 예산 대비 |
|---|---|---|---:|---:|---:|
| **A** | R1 동거 | db.t4g.micro | `$57.53` | 84,600원 | 56% |
| **B** | R1 동거 | db.t4g.small | `$71.98` | 105,800원 | 71% |
| **C** | R3 ElastiCache | db.t4g.micro | `$71.98` | 105,800원 | 71% |
| **D** | R3 ElastiCache | db.t4g.small | `$86.44` | 127,100원 | 85% |
| **E** | R2 전용 EC2 + VPC 엔드포인트 | db.t4g.micro | `$101.04` | 148,500원 | 99% |
| **F** | R2 전용 EC2 + NAT Gateway | db.t4g.micro | `$116.51` | 171,300원 | **114% 초과** |

도메인 요금은 `M2-02`(#41)에서 도메인이 정해지지 않아 제외했다. `.com` 기준 연 `$14`로 월 환산 약 1,700원이며 어느 구성에서도 판정을 바꾸지 않는다.

### 6.3. 판정

- **A·B·C·D는 예산 목표 이내다.** `M2-01` 완료 조건 "산정한 월 예상 비용이 예산 목표 이내다"를 만족한다.
- **F는 예산을 초과한다.** NAT Gateway를 쓰는 구성은 채택할 수 없다.
- **E는 산정치가 예산의 99%다.** RDS·ElastiCache 서울 배수가 EC2보다 크면 초과하고, 환율이 2026년 최고치로 가면 초과한다. 여유가 없어 권하지 않는다.
- **NAT Gateway와 인터페이스 VPC 엔드포인트를 M2 구성에 넣지 않는다.** 두 항목만으로 예산의 28~43%를 쓴다. 앱 EC2는 계획대로 퍼블릭 서브넷에 두고 Elastic IP로 Parameter Store·ECR·CloudWatch에 접근하며, 인바운드는 보안 그룹으로 80·443과 작업자 IP의 22만 허용한다(`M2-03`).
- **채택 구성은 A다**(5.4절 R1 결정). 월 예상 비용 `$57.53` = 84,600원으로 예산 목표의 56%다. `M2-12` 이후 RDS를 db.t4g.small로 올려도 구성 B의 105,800원(71%)으로 여유가 남는다.

## 7. 후속 Task에 넘기는 확정값

| 항목 | 값 | 사용 Task |
|---|---|---|
| 리전 | `ap-northeast-2` | 전체 |
| EC2 인스턴스 타입 | `t4g.medium` (arm64, 2 vCPU / 4 GiB) | `M2-03` (#42) |
| EC2 루트 볼륨 | gp3 30 GiB | `M2-03` (#42) |
| AMI 아키텍처 | arm64 | `M2-03` (#42). `Dockerfile`이 `eclipse-temurin:21.0.11_10-jre-alpine` 멀티아치 이미지를 쓰므로 arm64에서 빌드·실행된다. `M2-06`에서 ECR 이미지를 arm64로 빌드한다 |
| RDS 인스턴스 클래스 | `db.t4g.micro` (2 vCPU / 1 GiB) | `M2-04` (#43) |
| RDS 스토리지 | gp3 20 GiB | `M2-04` (#43) |
| RDS 배치 | Single-AZ, 사설 서브넷, 퍼블릭 액세스 없음 | `M2-04` (#43) |
| Redis | `redis:8.8-alpine` 컨테이너를 앱 EC2에 동거, `127.0.0.1:6379` 바인딩, AOF `everysec`, `noeviction`, `maxmemory` 256 MB | `M2-05` (#44). 상세는 5.4절 |
| ElastiCache | 사용하지 않는다 | `M2-05` (#44) |
| NAT Gateway·인터페이스 VPC 엔드포인트 | 사용하지 않는다 | `M2-03` (#42) |
| KMS 키 | `aws/ssm` 관리형 키 | `M2-07` (#46) |

## 8. M2-01에서 콘솔로 수행할 나머지 항목

사양·비용 산정 외 `M2-01` 작업은 AWS 콘솔·CLI 실행이며 계정 소유자가 직접 수행한다.

1. 리전 `ap-northeast-2` 고정
2. 루트 계정 MFA 활성화
3. 작업용 IAM 사용자·역할 생성. 장기 액세스 키를 만들지 않고 IAM Identity Center 또는 역할 수임을 사용한다([ADR-SEC-001](../07-adr/security/sec-001-secrets-workload-identity.md))
4. AWS Budgets에 월 150,000원 예산과 초과 알림 설정

4번은 완료 조건이 "예산 초과 알림이 **실제로** 발송되며"이므로 임계값을 낮춘 시험 예산으로 알림 도달을 한 번 확인한 뒤 본 임계값으로 되돌린다. 예산 알림은 자원 생성보다 먼저 설정한다(계획 9절 첫 번째 위험).

실행 결과와 생성한 자원 식별자는 이슈 [#40](https://github.com/team-youngkk/masit-on/issues/40)에 기록한다.

## 9. 검증하지 못한 항목

- **서울 리전 RDS·ElastiCache·EBS 단가를 직접 확인하지 못했다.** 2절의 환산 방법을 사용했다. `M2-01` 콘솔 작업에서 AWS Pricing Calculator로 대조하고 차이가 있으면 6절 표를 갱신한다.
- **CloudWatch 비용은 로그량 가정에 의존한다.** `M2-10` 이후 실측한다.
- **3절 메모리 산정은 실측이 아니다.** 로컬 Docker 실행에서 컨테이너별 RSS를 측정한 기록이 없어 구성요소별 일반값으로 계산했다. `M2-09`에서 실측해 대조한다.
- **월 인프라 비용 실측치는 M2 완료 정의 항목이다**(계획 10절). 이 문서는 사전 산정이며 실측 기록은 별도로 남긴다.

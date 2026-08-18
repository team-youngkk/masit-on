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

**단가는 2026-07-29 AWS Price List Query API로 `ap-northeast-2` 값을 직접 조회했다.** 조회 명령은 `aws pricing get-products --service-code <서비스> --filters Type=TERM_MATCH,Field=regionCode,Value=ap-northeast-2 ...`이며 리전 필터를 `regionCode`로 걸어 서울 값만 받았다. 등급은 둘뿐이다.

| 등급 | 뜻 |
|---|---|
| 확인 | Price List API로 `ap-northeast-2` 단가를 조회했다 |
| 가정 | 사용량을 가정해 계산했다. 실제 사용량에 따라 달라진다 |

**초기 산정에서 RDS 단가를 낮게 잡았다.** 공개 자료가 지역별 값을 가려 `us-east-1` 가격에 EC2의 서울 배수 1.238을 적용했는데, 실측 결과 **RDS의 서울 배수는 1.5625로 EC2와 다르다**(db.t4g.micro `$0.016`→`$0.025`, db.t4g.small `$0.032`→`$0.051`). EC2·EBS·퍼블릭 IPv4·NAT Gateway는 환산값이 실측값과 일치했다. 서비스마다 리전 배수가 다르므로 배수 환산을 다른 서비스에 재사용하지 않는다.

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
| **R1** 앱 EC2에 Redis 8.8 컨테이너 동거 | O | O | O (loopback 바인딩) | `$0` | ADR-DATA-005 6절 배치 표현 개정 필요 (2026-07-30 개정 완료) |
| **R2** 사설 서브넷 전용 EC2에 Redis 8.8 | O | O | O | `$43`~`$59` | 없다 |
| **R3** ElastiCache Valkey 8.x | X | X | O | 약 `$15` (미확인) | technology-policy 3절, ADR-DATA-005 10·11절 개정 필요 |
| **R4** ElastiCache Redis OSS 7.1 | X | X | O | 약 `$15` (미확인) | 같음. 게다가 EOL이 R3보다 이르다 |

R3·R4의 비용은 버전·AOF 제약만으로 이미 탈락하므로 서울 단가를 조회하지 않았다.

**R2의 비용이 큰 이유**는 인스턴스 요금이 아니라 사설 서브넷 EC2의 외부 접근 경로다. 패키지 설치와 SSM 접속에 NAT Gateway(`$0.059/hr`, 월 `$43.07` + 데이터 처리) 또는 인터페이스 VPC 엔드포인트 3종(`ssm`·`ssmmessages`·`ec2messages`, `$0.013/hr`씩 월 `$28.47`)이 필요하다. R2 총액은 `$105.71`~`$120.31`(155,400~176,900원)로 **어느 쪽이든 예산 목표를 초과한다.**

**R1은 두 강제 규칙을 모두 지키면서 비용이 0이지만 단일 장애점이 커진다.** 앱 인스턴스가 죽으면 Redis도 함께 죽는다. 다만 [ADR-DATA-005](../07-adr/data/data-005-redis-refresh-token.md) 12절이 Redis 장애를 fail-closed로 처리하고 재로그인을 요구하도록 이미 정했고, [NFR-AVAILABILITY-002](../01-requirements/non-functional-requirements.md#nfr-availability-002-초기-운영-배포-가용성과-수동-복구)가 단일 인스턴스·수동 복구를 허용하므로 이 위험은 M2 요구 수준 안에 있다.

### 5.4. 결정 — R1

**M2에서는 ElastiCache를 사용하지 않고 Redis Open Source 8.8을 앱 EC2에 함께 올린다**(2026-07-29, 이우람). 고정 버전과 AOF `everysec`을 모두 지킬 수 있고, 추가 비용이 없으며, `M2`가 단일 인스턴스 구성을 전제하므로 전용 인스턴스를 따로 두는 이점이 크지 않다는 판단이다. 채택 구성은 6.2절의 **A**다. 배포 고도화 운영의 비용·운영 기준은 2026-08-18 Accepted [ADR-DEPLOY-005](../07-adr/platform/deploy-005-asg-blue-green-rollout.md)에 따라 별도 private Redis 인스턴스를 사용하며, 해당 비용은 5절의 고도화 산정으로 관리한다.

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

**M2 선행 절차는 처리됐다.** 당시 결정은 [ADR-DATA-005](../07-adr/data/data-005-redis-refresh-token.md) 6절의 사설 전용 Redis 표현과 달랐으므로, **2026-07-30 공동 owner(김인안·이우람) 합의로 M2의 배치 표현만 동거·loopback으로 개정했다.** 2026-08-18 재합의로 배포 고도화 운영에는 다시 사설 subnet 전용 Redis를 적용하도록 개정했으며, 강제 규칙(8.8 계열, 인터넷 미노출, 네임스페이스, TTL, 회전 원자성)은 모두 유지한다.

## 6. 월 비용 산정

### 6.1. 항목별 단가

| 항목 | 단가 | 등급 |
|---|---|---|
| EC2 t4g.medium | `$0.0416/hr` | 확인 |
| 퍼블릭 IPv4 (Elastic IP) | `$0.005/hr` (in-use·idle 동일) | 확인 |
| EBS gp3 | `$0.0912/GB-월` | 확인 |
| RDS db.t4g.micro (PostgreSQL, Single-AZ) | `$0.025/hr` | 확인 |
| RDS db.t4g.small (PostgreSQL, Single-AZ) | `$0.051/hr` | 확인 |
| RDS gp3 스토리지 (Single-AZ) | `$0.131/GB-월` | 확인 |
| RDS 자동 스냅샷 | `$0` | 프로비저닝 용량 100%까지 무료 |
| Route 53 호스팅 영역 | `$0.50/월` | 확인 |
| ECR 스토리지 | `$0.10/GB-월` | 확인 |
| CloudWatch 로그·알람·지표 | 약 `$3/월` | 가정 (로그 1.5 GB/월, 알람 4종, 사용자 지정 지표 5개) |
| KMS | `$0` | `aws/ssm` 관리형 키 사용 시. 고객 관리 키는 `$1/월` |
| Parameter Store 표준 | `$0` | 표준 파라미터·표준 처리량 무료 |

M2 구성에서 **제외한** 항목의 단가도 함께 조회했다. 제외 근거가 금액이므로 남긴다.

예산 목표 150,000원은 1,470원 환율에서 `$102.04`다. 이 값을 분모로 쓴다.

| 제외 항목 | 단가 | 월 환산 | 예산 대비 |
|---|---|---:|---:|
| NAT Gateway | `$0.059/hr` + `$0.059/GB` | `$43.07` + 데이터 처리 | 42% |
| 인터페이스 VPC 엔드포인트 | `$0.013/hr` (엔드포인트·AZ당) | 3종 `$28.47` | 28% |

### 6.2. 구성별 월 총액

M2에서는 Redis를 앱 EC2에 동거시키므로(5.4절) 남는 변수는 RDS 클래스뿐이다. 공통 기반은 EC2 t4g.medium `$30.37` + EBS 30 GiB `$2.74` + Elastic IP `$3.65` + RDS gp3 20 GiB `$2.62` + Route 53 `$0.50` + ECR `$0.20` + CloudWatch `$3.00` = **`$43.07`**이다. 배포 고도화 private Redis 비용은 별도 영향 검토의 S1/S2 산정에 포함한다.

| 구성 | RDS | RDS 요금 | 월 USD | 월 KRW | 예산 대비 |
|---|---|---:|---:|---:|---:|
| **A** (채택) | db.t4g.micro | `$18.25` | **`$61.32`** | **90,100원** | **60%** |
| **B** (필요 시 승급) | db.t4g.small | `$37.23` | `$80.30` | 118,000원 | 79% |

M2 Redis는 앱 EC2 안에서 돌아 인스턴스·스토리지 요금이 추가되지 않는다. 배포 고도화에서는 private Redis 전용 인스턴스·EBS 비용을 별도로 반영한다. 도메인 요금은 `M2-02`(#41)에서 도메인이 정해지지 않아 제외했다. `.com` 기준 연 `$14`로 월 환산 약 1,700원이며 판정을 바꾸지 않는다.

### 6.3. 판정

- **채택 구성 A의 월 예상 비용은 `$61.32` = 90,100원으로 예산 목표의 60%다.** `M2-01` 완료 조건 "산정한 월 예상 비용이 예산 목표 이내다"를 만족한다.
- **RDS를 db.t4g.small로 올려도 118,000원(79%)으로 여유가 남는다.** `M2-12`에서 메모리·CPU 크레딧이 부족하면 승급할 수 있다.
- **환율이 2026년 최고치 1,559원으로 가도 구성 A는 95,600원(64%)이다.** 환율 변동이 판정을 뒤집지 않는다.
- **NAT Gateway와 인터페이스 VPC 엔드포인트를 M2 구성에 넣지 않는다.** 6.1절 제외 표에서 보듯 두 항목만으로 예산의 42%·28%를 쓴다. 앱 EC2는 퍼블릭 서브넷에 두고 Elastic IP로 Parameter Store·ECR·CloudWatch에 접근하며, 인바운드는 보안 그룹으로 80·443과 작업자 IP의 22만 허용한다(`M2-03`).
- **초기 산정보다 5,500원 올랐다.** RDS 실측 단가가 환산값보다 높았기 때문이며(2절) 예산 판정에는 영향이 없다.

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

## 8. M2-01 계정 준비 진행 상태

| 항목 | 상태 | 확인 값 |
|---|---|---|
| 계정 | 완료 | `711457211155` |
| 루트 계정 MFA | 완료 (기존) | IAM 대시보드 보안 권장 사항 0건, 루트 활성 액세스 키 없음 |
| 작업용 자격 증명 | 완료 | IAM Identity Center 조직 인스턴스 `ssoins-7230c72b8df2ccaf`, 조직 `o-3std4xzihg`, 기본 리전 `ap-northeast-2` |
| 권한 세트 | 완료 | `AdministratorAccess`, 세션 기간 8시간 |
| 리전 고정 | 완료 | CLI 프로파일 `masiton`의 region이 `ap-northeast-2` |
| 장기 액세스 키 미사용 | 완료 | SSO 수임 역할로만 접근. `arn:aws:sts::711457211155:assumed-role/AWSReservedSSO_AdministratorAccess_*/woolam` |
| 월 예산 | 완료 | `My Monthly Cost Budget`, `$100`/월 COST 예산. 통화는 계정 청구 통화인 USD |
| 예산 알림 | 완료 | 실제 80%, 실제 100%, 예측 100% 3종. 수신 `wlam250216@gmail.com` |
| 예산 알림 실제 도달 | **미완** | 시험 예산 `masiton-alert-test`로 확인 중 |

예산 한도는 `$100`으로 두었다. 예산 목표 150,000원은 1,470원 환율에서 `$102.04`이므로 `$100`은 목표보다 약 3,000원 낮은 지점에서 먼저 경고한다. 환율이 움직여도 경고가 목표보다 늦게 오지 않는 방향이라 이 값을 유지한다.

알림 3종 중 **예측 100%가 실질적으로 중요하다.** 실제 사용량 알림은 이미 지출이 발생한 뒤에 오지만, 예측 알림은 현재 추세로 월말에 초과할 것을 미리 알린다. EC2·RDS를 켜는 순간부터 월 `$61`가 흐르므로 월중 대응 여지를 만드는 것은 예측 알림이다.

도달 확인은 한도 `$0.5`, 실제 사용량 1% 초과 임계값의 시험 예산 `masiton-alert-test`로 한다. 7월 사용액이 `$0.595`이므로 임계값 `$0.005`를 넘어 알림이 발송된다. **AWS Budgets는 하루 약 3회만 평가하므로 즉시 도달하지 않는다.** 도달을 확인한 뒤 이 시험 예산만 삭제하고 본 예산은 남긴다. 본 예산의 임계값을 낮췄다 되돌리는 방식은 되돌리기를 잊으면 알림이 계속 발송되므로 쓰지 않았다.

**예산에 크레딧 제외 설정이 없으면 알림이 동작하지 않는다.** 시험 예산을 처음 만들 때 `CostTypes`를 지정하지 않아 크레딧이 포함됐고, 크레딧(`-$0.595`)이 사용액(`+$0.595`)을 상쇄해 실적이 `$0.00`으로 잡혔다. 임계값에 영원히 걸리지 않는 상태였다. `IncludeCredit=false`, `IncludeRefund=false`로 수정한 뒤 실적이 `$0.595`로 잡혔다.

본 예산 `My Monthly Cost Budget`은 처음부터 `RECORD_TYPE NOT IN (Credit, Refund)` 필터가 걸려 있어 정가 사용액을 측정한다. **따라서 크레딧이 청구액을 0원으로 만들고 있어도 본 예산 알림은 정가 기준으로 정상 동작한다.** 크레딧은 2026-07-29에 전량 만료됐으므로 이후 사용분에는 상쇄가 없다.

세션 기간을 기본 1시간에서 8시간으로 둔 이유는 CLI 작업이 많아 재로그인이 잦으면 작업이 끊기기 때문이다. 짧은 수명 자격 증명이라는 성질은 유지되므로 [ADR-SEC-001](../07-adr/security/sec-001-secrets-workload-identity.md)의 장기 키 금지 조건에 영향이 없다.

예산과 알림은 EC2·RDS 생성보다 먼저 설정했다(계획 9절 첫 번째 위험). 보호 장치가 이미 동작하므로 도달 확인을 기다리는 동안 `M2-03` 이후를 진행할 수 있다. 다만 도달을 확인하기 전까지 `M2-01`은 완료로 보지 않는다.

**IAM 역할의 결제 정보 액세스 토글은 켜지 않아도 됐다.** Budgets API는 SSO 수임 역할의 IAM 권한으로 동작한다. 이 토글은 SSO 사용자가 **콘솔에서** Billing 화면을 볼 때만 필요하므로, 콘솔로 예산을 보려 할 때 루트로 켠다.

실행 결과와 생성한 자원 식별자는 이슈 [#40](https://github.com/team-youngkk/masit-on/issues/40)에 기록한다.

## 9. 검증하지 못한 항목

- **CloudWatch 비용은 로그량 가정에 의존한다.** 로그 1.5 GB/월, 알람 4종, 사용자 지정 지표 5개를 가정한 약 `$3`이며 단가를 조회하지 않았다. `M2-10` 이후 실측한다.
- **예산 초과 알림이 실제로 도달하는지 확인하지 못했다.** 예산 생성 후 시험 알림 도달을 확인해야 `M2-01` 완료 조건을 만족한다.
- **3절 메모리 산정은 실측이 아니다.** 로컬 Docker 실행에서 컨테이너별 RSS를 측정한 기록이 없어 구성요소별 일반값으로 계산했다. `M2-09`에서 실측해 대조한다.
- **월 인프라 비용 실측치는 M2 완료 정의 항목이다**(계획 10절). 이 문서는 사전 산정이며 실측 기록은 별도로 남긴다.

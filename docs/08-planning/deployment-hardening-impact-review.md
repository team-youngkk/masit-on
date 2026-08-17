---
status: PROPOSED
review_date: 2026-08-17
owners:
  - 이우람
decision_pending:
  - 배포 고도화 착수 여부와 단계 순서
  - Redis 전용 인스턴스의 사설 서브넷 외부 접근 경로
  - Blue-Green 도입에 따른 마이그레이션 하위 호환 규칙
  - 앱 인스턴스 t4g.medium → t4g.small 하향
related_documents:
  - ../07-adr/platform/deploy-002-validation-deployment-before-expansion.md
  - ../07-adr/platform/web-003-routing-boundary.md
  - ../07-adr/data/data-005-redis-refresh-token.md
  - ../07-adr/data/data-009-pre-release-migration-consolidation.md
  - ../07-adr/adr-backlog.md
  - ../00-overview/scope.md
  - ../01-requirements/non-functional-requirements.md
  - ../05-specs/data/migration-plan.md
  - ../06-architecture/technology-policy.md
  - m2-cost-and-sizing.md
  - m2-provisioning-record.md
  - m2-deployment-plan.md
  - third-expansion-final-gate-result.md
  - issue-190-operational-performance-result.md
  - issue-200-application-port-binding.md
---

# 배포 고도화 비용·일정 영향 검토

## 1. 이 문서의 근거와 판정 요약

[ADR-DEPLOY-002](../07-adr/platform/deploy-002-validation-deployment-before-expansion.md) 3.1절은 2026-07-28 팀 4인 전원이 배포 고도화 착수 시점을 "3차 확장 이후"로 합의했다고 기록하면서, [범위 문서 7절](../00-overview/scope.md#7-범위-변경-절차) 3항(개발 비용·데이터·외부 연동·운영 영향)과 4항(일정 영향)을 수행하지 않았고 **"실제 착수는 위 두 검토를 통과한 뒤에 시작한다"**고 못박았다. 이 문서가 그 두 검토다.

검토 대상 구성은 **Blue-Green 무중단 배포 + Redis 전용 인스턴스 분리 + 앱 인스턴스 t4g.small 하향**이다. ALB·ASG·Blue-Green을 medium 인스턴스에 그대로 얹는 초안은 예산을 넘겨 폐기했고(5.5절에 기록), 인스턴스 하향을 전제로 다시 산정했다.

| 검토 항목 | 결과 |
|---|---|
| 3항 비용 | **Redis 전용 인스턴스의 외부 접근 경로가 예산 안팎을 가른다.** 같은 구성이 경로에 따라 72%에서 114%까지 움직인다(5절) |
| 3항 운영 | Nginx는 ALB로 대체되지 않고 병존해야 한다(6.1절). **Blue-Green은 모든 Flyway 마이그레이션에 하위 호환을 강제한다**(6.2절). 무중단의 약한 고리가 Redis로 옮겨간다(6.3절) |
| 4항 일정 | **산정할 수 없다.** 4차 확장 기능 계획 문서가 아직 없어 비교 대상이 존재하지 않는다(7절) |

**앱 인스턴스를 t4g.small로 내리는 것이 이 구성의 재원이다.** 하향만으로 월 22,300원이 빠지고, 그 돈이 ALB 요금의 대부분을 덮는다. 하향이 불가능하면 8절 권고는 성립하지 않는다.

## 2. 검토 대상 범위

[ADR-DEPLOY-002](../07-adr/platform/deploy-002-validation-deployment-before-expansion.md) 2.1절이 배포 고도화 단계의 인프라를 "ALB, Blue-Green, 다중 인스턴스"로 정의했다. 여기에 이번 검토에서 전제로 추가한 두 항목을 합쳐 다섯 요소를 분리한다. 한 덩어리로 두면 비용과 위험이 어디서 오는지 판별할 수 없다.

| 요소 | 얻는 것 | 성격 |
|---|---|---|
| ALB | TLS 종단 이관, 대상 그룹 전환, 상태 검사 기반 자동 제외 | 나머지 전부의 선행 조건 |
| Blue-Green 배포 | 배포 중 무중단, 즉시 롤백 | 배포 시점에만 두 번째 인스턴스가 필요 |
| ASG 다중 인스턴스 | 인스턴스 장애 시 무중단 | 상시 2대 이상 필요 |
| **Redis 전용 인스턴스** | 인스턴스가 바뀌어도 관리자 세션 유지 | **Blue-Green 무중단의 전제** |
| **앱 인스턴스 t4g.small 하향** | 고도화 비용의 재원 | Blue-Green이 가능하게 만든다(4.2절) |

**"무중단 배포"와 "고가용성"은 같은 것이 아니다.** Blue-Green은 배포 중 중단만 없애고, 인스턴스가 죽었을 때의 중단은 ASG 상시 다중화로만 없앤다.

**Redis 분리는 선택이 아니라 전제다.** 현재 Redis는 앱 인스턴스에 `127.0.0.1:6379`로 붙어 있다([masiton-redis.service](../../deploy/redis/masiton-redis.service), `--publish 127.0.0.1:6379:6379`). 이 상태로 Blue-Green만 도입하면 전환할 때마다 새 인스턴스의 Redis가 비어 있어 `auth:refresh:{adminId}`가 전부 사라진다. 관리자가 매 배포마다 재로그인해야 하므로 "무중단"이라고 부를 수 없다.

## 3. 단가

### 3.1. 이번 검토에서 실측한 값

AWS 요금 공개 페이지에 서울 리전 표가 없어 **AWS Price List API의 리전별 offer 파일에서 직접 읽었다.** 조회 시각은 2026-08-17이다.

| 항목 | 단가 | 월 환산 | 출처 |
|---|---|---:|---|
| Application Load Balancer 시간 요금 | `$0.0225/hr` | `$16.43` | `AWSELB/current/ap-northeast-2` |
| ALB LCU | `$0.008/LCU-hr` | `$5.84` (1 LCU) | 같음 |
| EC2 t4g.nano (0.5 GiB) | `$0.0052/hr` | `$3.80` | `AmazonEC2/current/ap-northeast-2` |
| EC2 t4g.micro (1 GiB) | `$0.0104/hr` | `$7.59` | 같음 |
| EC2 t4g.small (2 GiB) | `$0.0208/hr` | `$15.18` | 같음 |
| EC2 t4g.medium (4 GiB) | `$0.0416/hr` | `$30.37` | 같음. **M2-01 산정값과 일치해 기준선이 재확인됐다** |
| S3 게이트웨이 VPC 엔드포인트 | `$0` | `$0` | AWS 문서 "There is no additional charge for using gateway endpoints" |
| ACM 공인 인증서 (ALB 연결) | `$0` | `$0` | ALB·CloudFront 연결 시 무료 |
| Route 53 alias 레코드 (ALB 대상) | `$0` | `$0` | AWS 자원 대상 alias 쿼리는 과금하지 않는다 |

t4g 계열은 vCPU·메모리가 2배 오를 때 단가도 정확히 2배다. **비례 가정이 아니라 네 타입 모두 실측한 결과다.**

### 3.2. M2-01에서 인용한 값

다음은 [M2 인스턴스 사양과 월 비용 산정 6.1절](m2-cost-and-sizing.md)의 값을 그대로 인용했고 **이번 검토에서 재조회하지 않았다.**

| 항목 | 단가 |
|---|---|
| 퍼블릭 IPv4 (Elastic IP·자동 할당 동일) | `$0.005/hr` |
| EBS gp3 | `$0.0912/GB-월` |
| 인터페이스 VPC 엔드포인트 | `$0.013/hr` (엔드포인트·AZ당, 월 `$9.49`) |
| NAT Gateway | `$0.059/hr` (월 `$43.07`) + 데이터 처리 |
| RDS db.t4g.micro + gp3 20 GiB | `$20.87` |
| Route 53 · ECR · CloudWatch | `$3.70` |

### 3.3. 계산 전제

M2 산정과 맞춘다. **월 730시간, 1 USD = 1,470원, 예산 목표 150,000원 = `$102.04`.** 전제를 바꾸면 M2 산정과 비교할 수 없다.

LCU는 사용량 과금이라 상수가 아니다. 제한 공개 트래픽은 1 LCU(초당 신규 연결 25, 분당 활성 연결 3,000, 시간당 처리 1 GB, 초당 규칙 평가 1,000)에 크게 못 미치므로 **모든 표에 보수적 상한 1 LCU를 적용했다.** 실제 청구액은 이보다 낮다.

Blue-Green 전환용 임시 인스턴스는 **월 10회 배포 × 회당 1시간**으로 잡아 `$0.30`을 계상한다.

## 4. 인스턴스 하향의 근거

### 4.1. micro는 불가능하고 small이 경계다

[M2 비용 산정 3절](m2-cost-and-sizing.md)이 단일 인스턴스 상주 메모리를 구성요소별로 합산해 **2,100 MB**로 산정했고, 그 근거로 "t4g.small(2 GiB)은 불가하다. 합계 2,100 MB가 물리 메모리를 이미 넘는다"고 판정했다. 1 GiB인 micro는 검토 대상도 되지 않는다.

그러나 2,100 MB 중 1,400 MB가 JVM이고, 같은 절이 **"heap 1 GiB는 가정이 아니라 현재 설정의 결과"**라고 밝혔다. `Dockerfile`에 heap 옵션이 없어 JVM이 기본 `MaxRAMPercentage` 25%를 적용하고, 4 GiB 호스트라서 상한이 1 GiB가 된 것이다. **호스트를 2 GiB로 내리면 같은 설정에서 heap이 512 MB가 되고 JVM 전체가 약 900 MB로 내려간다.**

Redis를 분리하면 컨테이너 `--memory 384m` 제한분도 이 인스턴스에서 빠진다.

| 구성요소 | medium(현행) | small(하향 + Redis 분리) |
|---|---:|---:|
| AL2023 커널·기본 데몬 | 250 MB | 250 MB |
| SSM + CloudWatch Agent | 150 MB | 150 MB |
| Nginx | 50 MB | 50 MB |
| Next.js `next start` | 250 MB | 250 MB |
| Spring Boot JVM | 1,400 MB | 약 900 MB |
| **합계** | **2,100 MB** | **약 1,600 MB** |

t4g.small의 실사용 가능 메모리는 약 1,900 MB이므로 여유가 약 300 MB다. **medium의 1.9 GiB 여유에 비하면 빠듯하고, 이 수치는 추정이다.**

### 4.2. Blue-Green이 medium을 정당화한 이유를 없앤다

M2가 small을 버리고 medium을 고른 이유는 두 가지였다. 하나는 4.1절의 메모리 합계이고, 다른 하나는 **"여유 약 1.9 GiB로 배포 중 구·신 컨테이너 동시 상주와 Flyway 마이그레이션 피크를 흡수한다"**였다.

**Blue-Green은 구·신 동시 상주를 다른 인스턴스로 옮긴다.** 한 인스턴스 안에서 두 버전이 겹칠 이유가 사라지므로 그 헤드룸 요구도 사라진다. 즉 하향은 고도화와 무관한 절감이 아니라 **고도화가 가능하게 만드는 절감**이다.

Flyway 마이그레이션 피크는 남는다. 이 부분은 4.3절의 실측 대상이다.

### 4.3. 하향 전에 확인해야 하는 것

- **실제 RSS 측정.** `M2-09`가 미룬 항목이다. 4.1절 표는 전부 추정이며 실측으로 대체해야 한다.
- **JVM heap 명시 고정.** 지금은 `MaxRAMPercentage` 기본값에 의존한다. 호스트 크기가 바뀌면 heap이 따라 움직이므로, 하향할 때 heap을 명시 옵션으로 고정할지 결정해야 한다. `Dockerfile` 변경이므로 소유자 합의가 필요하다.
- **CPU 크레딧.** t4g는 버스터블이다. small과 medium의 baseline 사용률·크레딧 적립률이 같은지 확인하지 않았다. AI Worker가 도는 인스턴스라 크레딧 소진은 실제 위험이다.

## 5. 구성별 월 비용

### 5.1. Redis 전용 인스턴스의 외부 접근 경로

**이 항목 하나가 전체 판정을 가른다.**

[ADR-DATA-005](../07-adr/data/data-005-redis-refresh-token.md) 10절이 "사설 네트워크", 11절이 "퍼블릭 IP 금지"를 강제하므로 전용 Redis는 사설 서브넷에 두어야 한다. [M2 자원 생성 기록 3절](m2-provisioning-record.md)에 따르면 사설 서브넷은 기본 라우트 테이블(local 전용)을 쓰고 `0.0.0.0/0` 라우트가 없다. **인터넷으로 나가는 경로가 아예 없다.**

인스턴스 자체는 싸다. keyspace가 1 MB 미만이라([M2 비용 산정 5.2절](m2-cost-and-sizing.md)) t4g.nano로 충분하고, EBS 8 GiB를 더해 월 `$4.53`이다. **비싼 것은 인스턴스가 아니라 접근 경로다.**

경로가 필요한 이유는 세 가지이고, 서로 다른 해법을 요구한다.

**첫째, 컨테이너 이미지를 가져와야 한다.** [masiton-redis.service](../../deploy/redis/masiton-redis.service)의 `IMAGE`는 `redis@sha256:8096655e…`로 레지스트리 접두사가 없는 Docker Hub digest 고정이다. [ADR-DATA-005](../07-adr/data/data-005-redis-refresh-token.md) 10절이 8.8 계열을 강제하므로 AL2023 패키지로 대체할 수 없고, 컨테이너 이미지가 반드시 필요하다.

**둘째, 관리 접속이 필요하다.** 현재 운영은 SSM RunCommand를 쓰지만, 전용 Redis의 E1·E2 기준선은 EC2 Instance Connect Endpoint를 관리 접속 경로로 둔다.

**셋째, Redis 기동 때마다 Parameter Store에서 비밀값을 읽어야 한다.** [`redis-render-conf.sh`](../../deploy/scripts/redis-render-conf.sh:38)가 `aws ssm get-parameter`를 실행하므로, 이미지와 관리 접속 경로가 사설 네트워크 안에 있어도 `ssm` 인터페이스 엔드포인트가 추가로 필요하다.

| 경로 | 이미지 | 관리 접속 | Parameter Store 접근 | 월 USD | 등급 |
|---|---|---|---|---:|---|
| **E1** | 이미지 tar를 S3에 두고 `docker load` (S3 게이트웨이 엔드포인트) | EC2 Instance Connect Endpoint | `ssm` 인터페이스 엔드포인트 (`$9.49`) | **`$9.49`** | **EC2 Instance Connect Endpoint 요금 미확인** |
| **E2** | ECR 미러 + `ecr.api`·`ecr.dkr` 인터페이스 엔드포인트 + S3 게이트웨이 엔드포인트 | EC2 Instance Connect Endpoint | `ssm` 인터페이스 엔드포인트 (`$9.49`) | `$28.47` | 인터페이스 단가는 M2 인용 |
| **E3** | NAT Gateway로 Docker Hub 직접 | SSM (NAT 경유) | NAT 경로에 포함 | `$43.07` | M2 산정이 쓴 전제 |

이번 기준선은 전용 Redis 인스턴스에 SSM Agent·CloudWatch Agent를 설치하지 않고, 관리 접속은 EC2 Instance Connect Endpoint로 수행한다고 가정한다. 따라서 E1·E2에는 `redis-render-conf.sh`에 필요한 `ssm` 엔드포인트만 반영했다. SSM Agent·CloudWatch Agent를 유지하기로 하면 `ssmmessages`·`ec2messages`·`monitoring` 엔드포인트를 추가하고 비용을 다시 계산해야 한다.

**E1의 `$9.49`는 아직 근거가 완결되지 않았다.** S3 게이트웨이 엔드포인트가 무료이고 `ssm` 인터페이스 엔드포인트가 월 `$9.49`인 것은 확인했지만, **EC2 Instance Connect Endpoint의 요금을 확인하지 못했다.** AWS 사용 설명서의 생성·접속 페이지 어디에도 요금 문구가 없다. 이 값이 유료로 밝혀지면 E1은 다시 산정해야 한다.

E1은 배포 절차도 비표준이다. 이미지 tar를 S3에 올려두고 `docker load`로 적재하는 방식이라 `redis-install.sh`와 systemd unit을 고쳐야 하고, 이미지 갱신 절차를 새로 문서화해야 한다. **금액만 보고 고를 항목이 아니다.**

### 5.2. S1 — Blue-Green 무중단 (앱 상시 1대)

| 항목 | 월 USD |
|---|---:|
| 앱 EC2 t4g.small 1대 | `$15.18` |
| EBS gp3 30 GiB | `$2.74` |
| 퍼블릭 IPv4 1개 | `$3.65` |
| 배포 전환용 임시 인스턴스 (월 10회 × 1시간) | `$0.30` |
| Redis 전용 t4g.nano + EBS 8 GiB | `$4.53` |
| ALB + LCU(1) | `$22.27` |
| RDS + Route 53 · ECR · CloudWatch | `$24.57` |
| **소계 (Redis 접근 경로 제외)** | **`$73.24`** |

| 접근 경로 | 월 USD | 월 KRW | 예산 대비 |
|---|---:|---:|---:|
| E1 | `$82.73` | 121,600원 | **81%** |
| E2 | `$101.71` | 149,500원 | **100%** |
| E3 | `$116.31` | 171,000원 | **114%** ❌ |

### 5.3. S2 — ASG 상시 2대 (S1 + 인스턴스 장애 무중단)

| 항목 | 월 USD |
|---|---:|
| 앱 EC2 t4g.small 2대 | `$30.36` |
| EBS gp3 30 GiB × 2 | `$5.48` |
| 퍼블릭 IPv4 2개 | `$7.30` |
| 배포 전환용 임시 인스턴스 | `$0.30` |
| Redis 전용 t4g.nano + EBS 8 GiB | `$4.53` |
| ALB + LCU(1) | `$22.27` |
| RDS + Route 53 · ECR · CloudWatch | `$24.57` |
| **소계 (Redis 접근 경로 제외)** | **`$94.81`** |

| 접근 경로 | 월 USD | 월 KRW | 예산 대비 |
|---|---:|---:|---:|
| E1 | `$104.30` | 153,300원 | **102%** ❌ |
| E2 | `$123.28` | 181,200원 | **121%** ❌ |
| E3 | `$137.88` | 202,700원 | **135%** ❌ |

### 5.4. 비교

| 구성 | 월 KRW | 예산 대비 |
|---|---:|---:|
| 현행 기준선 (medium 1대, Redis 동거) | 90,100원 | 60% |
| 하향만 (small 1대, 고도화 없음) | 67,800원 | 45% |
| S1 + E1 | 121,600원 | 81% |
| S1 + E2 | 149,500원 | 100% |
| S2 + E1 | 153,300원 | 102% |
| S1 + E3 | 171,000원 | 114% ❌ |

**하향만으로 월 22,300원이 빠진다.** 이 절감이 ALB 요금 `$16.43`(24,100원)의 대부분을 덮는다. 고도화 비용이 예산 안에 들어오는 이유가 이것이다.

**S2와 S1의 차이는 월 31,700원이다(E1 기준).** 다만 `ssm` 엔드포인트를 반영하면 S2 + E1도 153,300원으로 예산을 넘는다. E2에서는 S1이 149,500원으로 예산 안이지만 여유가 500원뿐이다.

### 5.5. 환율 민감도

M2 산정과 같이 2026년 최고치 1,559원을 적용한다.

| 구성 | 1,470원 | 1,559원 | 판정 변화 |
|---|---:|---:|---|
| S1 + E1 | 121,600원 | 129,000원 | 없음 |
| S1 + E2 | 149,500원 | 158,600원 | 1,470원에서는 통과하지만 최고 환율에서 초과 |
| S2 + E1 | 153,300원 | 162,600원 | 없음 (초과 유지) |
| S1 + E3 | 171,000원 | 181,300원 | 없음 (초과 유지) |

**S1 + E2는 1,470원 기준으로 예산의 100%에 가까우며, 환율 최고치에서는 106%로 초과한다.** S2 + E1은 현재 환율에서도 102%로 이미 초과하므로, S2를 선택하려면 Redis 접근 경로 또는 전제 비용을 별도로 낮춰야 한다.

### 5.6. 폐기한 초안

이 문서의 최초 산정은 앱 인스턴스를 t4g.medium으로 두고 ALB·ASG를 얹는 구성이었다. 결과는 월 176,900원(118%)이었고, LCU를 하한으로 낮춰도 169,200원(113%)이라 초과가 트래픽 가정에 의존하지 않았다. **초과의 원인은 고도화 자체가 아니라 medium을 2대로 늘린다는 전제였다.** 인스턴스 하향이 결정되면서 이 초안은 폐기했다. 기록으로만 남긴다.

## 6. 비용 외 영향

### 6.1. ALB는 Nginx를 대체하지 못한다 — 병존이다

[ADR-DEPLOY-002](../07-adr/platform/deploy-002-validation-deployment-before-expansion.md) 3.1절이 "Nginx의 경로 라우팅 책임을 ALB가 대체할지 여부"를 미확정으로 남겼다. **[masiton.click.conf](../../deploy/nginx/masiton.click.conf)를 확인한 결과 대체할 수 없다.**

| Nginx가 하는 일 | ALB로 가능한가 |
|---|---|
| `auth_request`로 검증 참여자 세션 게이트 — 모든 요청을 `/internal/verification/session`에 되물어 통과시킴 | **불가.** ALB 인증은 OIDC·Cognito뿐이고 백엔드에 되묻는 방식이 없다 |
| 401 → 로그인 리다이렉트, 500·503 → 계약 JSON 어댑터 변환 | **불가.** `error_page` 재진입에 해당하는 기능이 없다 |
| Webhook 경로 `limit_req` 429 | **불가.** WAF가 필요하고 추가 비용이 붙는다 |
| `X-Forwarded-For`를 `$remote_addr`로 덮어쓰기 | **불가.** ALB는 XFF에 append한다 |
| HSTS·`X-Content-Type-Options`·`Referrer-Policy` 응답 헤더 | 불가. 애플리케이션으로 옮겨야 한다 |
| `client_max_body_size` 2m, Webhook 128k | 불가 |
| 알 수 없는 Host·Elastic IP 직접 접근 `return 444` | 403 고정 응답으로 근사만 가능 |
| TLS 종단, `/internal/**` 차단, 경로 분배 | 가능 |

**Nginx를 걷어낼 수 없는 더 직접적인 이유가 하나 더 있다.** Spring Boot와 Next.js는 지금 `127.0.0.1:8080`·`127.0.0.1:3000` loopback에 묶여 있다([운영 애플리케이션 포트 바인딩 계획](issue-200-application-port-binding.md)). ALB가 이 프로세스를 직접 대상으로 잡으려면 VPC 주소로 열어야 하고, **그 보안 결정이 되돌아간다.** `AppRunScriptContractTest`가 이 계약의 회귀를 막고 있다.

따라서 구성은 **ALB → 각 인스턴스의 Nginx → loopback 애플리케이션**이다. ALB는 TLS 종단·상태 검사·대상 그룹 전환만 가져간다.

여기서 파생되는 결정 항목이 둘이다.

- **ALB↔Nginx 구간 프로토콜.** HTTP로 두면 인스턴스 포트 80을 ALB 보안 그룹 출처로만 열어야 하고, Nginx의 `X-Forwarded-Proto $scheme`가 `http`가 되어 애플리케이션이 HTTPS를 인식하지 못한다. ALB가 붙인 값을 신뢰하도록 바꿔야 한다.
- **XFF 신뢰 경계 재설계.** 진짜 클라이언트 IP가 ALB가 붙인 XFF 안에만 존재하므로, Nginx는 그 값을 신뢰하되 **ALB가 붙인 값만** 신뢰해야 한다. 관리자 로그인 rate-limit의 출처가 여기에 걸려 있고, 이미 한 번 고친 자리다(PR #205).

**딸려오는 이득도 있다.** TLS를 ALB의 ACM으로 옮기면 `masiton-tls-renew.timer`의 Let's Encrypt 갱신 운영이 사라진다.

### 6.2. Blue-Green은 모든 마이그레이션에 하위 호환을 강제한다

**이 항목이 이 검토에서 가장 큰 제약이다.** ADR-DEPLOY-002 3.1절이 열거한 미확정 4항목에 들어 있지 않다.

Blue-Green 전환 중에는 구버전(blue)과 신버전(green)이 같은 RDS를 동시에 바라본다. green이 새 마이그레이션을 적용하면 blue는 **자기가 모르는 스키마 위에서 돌게 된다.** 컬럼 삭제나 이름 변경이면 blue의 쿼리가 즉시 깨지고, `ddl-auto=validate`라 재기동 시 기동 자체가 실패할 수도 있다.

따라서 도입 이후 모든 스키마 변경은 **확장 후 축소(expand-contract)** 로 나뉜다. 컬럼 추가와 양쪽 배포를 먼저 끝내고, 구 컬럼 제거는 별도 배포로 미룬다. 이는 배포 방식 결정이 아니라 **마이그레이션 작성 규칙 변경**이며 [Flyway 마이그레이션 계획](../05-specs/data/migration-plan.md)과 [ADR-DATA-009](../07-adr/data/data-009-pre-release-migration-consolidation.md)에 영향이 간다.

착수 ADR에 이 규칙을 명시하지 않으면, 규칙을 모르는 상태로 작성된 마이그레이션 한 건이 전환 중 장애를 만든다.

### 6.3. 무중단의 약한 고리가 Redis로 옮겨간다

Redis를 분리하면 앱은 무중단이 되지만, 전용 인스턴스는 복제 없는 단일 장애점이다. ElastiCache는 8.8·AOF 제약으로 쓸 수 없으므로([M2 비용 산정 5.1절](m2-cost-and-sizing.md)) 관리형 failover도 없다. [ADR-DATA-005 12절](../07-adr/data/data-005-redis-refresh-token.md)이 Redis 장애를 fail-closed로 정했으므로 **Redis가 죽으면 앱이 살아 있어도 관리자·회원 인증은 전부 중단된다.**

가용성이 나빠지는 것은 아니다. 지금도 앱과 Redis가 같이 죽는다. 다만 **"무중단"의 범위를 "앱 배포와 인스턴스 장애에 대한 무중단, Redis 장애는 여전히 전면 인증 중단"으로 문서에 명시해야 한다.**

분리에 따라오는 문서·설정 변경도 함께 처리한다.

- [ADR-DATA-005](../07-adr/data/data-005-redis-refresh-token.md) 6절은 2026-07-30에 공동 owner(김인안·이우람) 합의로 "앱 인스턴스 동거"로 개정한 조항이다. **분리는 이 개정을 되돌리는 것이므로 같은 owner의 합의가 다시 필요하다.** 10·11절의 강제 규칙(8.8 계열, 사설 네트워크, 퍼블릭 IP 금지)은 사설 서브넷 배치로 그대로 지켜진다.
- [masiton-redis.service](../../deploy/redis/masiton-redis.service)의 `--publish 127.0.0.1:6379:6379`를 사설 IP 바인딩으로 바꾸고, 보안 그룹으로 앱 보안 그룹 출처의 6379만 허용한다. **loopback이 사라지므로 보안 그룹이 유일한 경계가 된다.**
- 배포 스크립트도 함께 바뀐다. 6.8절 표의 `docker exec masiton-redis` 항목을 참조한다.
- AOF `everysec`·`noeviction`·`maxmemory` 설정은 전용 인스턴스의 EBS 볼륨으로 그대로 옮긴다. 재기동 후 인증 상태 유지 검증(`M2-13`에 해당)을 새 구성에서 다시 한다.

### 6.4. 다중 인스턴스 안전성은 이미 확보돼 있다

다중 인스턴스에서 먼저 깨지는 것은 보통 스케줄러 중복 실행인데, 큐 성격의 작업은 이미 행 단위로 선점한다.

| 위치 | 방식 |
|---|---|
| [JdbcAiExtractionWorkerStore.java:55](../../src/main/java/com/masiton/ai/infrastructure/persistence/JdbcAiExtractionWorkerStore.java) | `FOR UPDATE OF job SKIP LOCKED` |
| [JdbcMemberActionMailOutboxStore.java:67](../../src/main/java/com/masiton/member/infrastructure/persistence/JdbcMemberActionMailOutboxStore.java) | `FOR UPDATE OF outbox SKIP LOCKED` + `locked_until` |
| [JdbcIdempotencyRecordStore.java:80](../../src/main/java/com/masiton/common/idempotency/infrastructure/persistence/JdbcIdempotencyRecordStore.java) | `FOR UPDATE SKIP LOCKED` |

메일 아웃박스가 특히 중요하다. 선점이 없었다면 전환 중 회원에게 인증 메일이 두 번 발송됐을 것이다.

**다만 스케줄러 8개 전부를 확인하지는 않았다.** 정리 성격의 스케줄러 5개는 중복 실행이 쿼리 낭비에 그칠 것으로 보이지만 확인 대상이고, Gemini·Mobility quota 소비 경로가 인스턴스별로 이중 계상되지 않는지도 별도로 확인해야 한다.

### 6.5. 퍼블릭 서브넷이 한 AZ에만 있다

[M2 자원 생성 기록 3절](m2-provisioning-record.md) 기준으로 현재 퍼블릭 서브넷은 `ap-northeast-2a` 하나뿐이고 사설 서브넷만 2a·2c 두 개다(RDS 서브넷 그룹 요구). **ALB는 최소 두 AZ의 서브넷을 요구하므로 퍼블릭 서브넷을 하나 더 만들어야 한다.** 서브넷 자체는 무료라 5절 금액은 바뀌지 않지만 작업 항목이고, 만들면 M2 자원 생성 기록의 네트워크 표를 갱신해야 한다.

### 6.6. 앱 인스턴스를 사설 서브넷으로 옮길 수 없다

ALB를 앞에 두면 앱 인스턴스를 사설 서브넷에 넣는 구성이 자연스럽지만, 5.1절과 같은 이유로 NAT Gateway나 인터페이스 엔드포인트가 필요해진다. 앱 인스턴스는 ECR·Parameter Store·CloudWatch를 모두 쓰므로 필요한 엔드포인트 수가 Redis보다 많다.

따라서 **앱 인스턴스는 퍼블릭 서브넷에 남기고 보안 그룹 인바운드만 ALB 보안 그룹 출처로 좁히는 구성**을 전제로 5절을 계산했다. 퍼블릭 IPv4 요금이 인스턴스 수만큼 계속 붙는 이유가 이것이다.

### 6.7. NFR-AVAILABILITY-002 개정 범위

[NFR-AVAILABILITY-002](../01-requirements/non-functional-requirements.md#nfr-availability-002-초기-운영-배포-가용성과-수동-복구)의 적용 대상은 "M2 초기 운영 배포부터 **배포 고도화 전까지**"이고 목표 기준은 "단일 EC2 인스턴스와 수동 복구를 유지"다.

- **S1(상시 1대)** 은 인스턴스가 여전히 하나이므로 이 요구사항의 목표 기준을 유지한다. 배포 중 중단만 사라진다.
- **S2(상시 2대)** 는 목표 기준을 개정해야 한다. 같은 PR에서 처리한다.

### 6.8. 배포 스크립트와 CI에서 손대야 하는 자리

현재 배포는 **제자리 재기동**이다. [app-deploy.sh:76-77](../../deploy/scripts/app-deploy.sh)이 `systemctl restart masiton-backend` → `systemctl restart masiton-frontend`를 실행하고, 그 사이 컨테이너가 내려갔다 올라온다. 스크립트는 `/internal/health/ready`를 최대 300초까지 폴링하는데 **Flyway 마이그레이션과 커넥션 풀 초기화가 그 대기 안에서 돈다.** 마이그레이션이 무거울수록 중단이 길어지는 구조다.

전환 방식을 바꾸면 다음 여섯 지점이 함께 바뀐다. 착수 ADR의 작업 목록으로 그대로 쓸 수 있다.

| 지점 | 현재 | Blue-Green 전환 시 |
|---|---|---|
| [app-deploy.sh](../../deploy/scripts/app-deploy.sh) `systemctl restart` | 제자리 재기동. 이 구간이 중단 | green에서 기동 → 대상 그룹 등록 → 상태 검사 통과 → 리스너 전환 |
| 같은 스크립트의 검증 5단계 | **트래픽을 받는 인스턴스에서 사후 검증** | **전환 전 green에서 선행 검증.** 통과한 대상만 트래픽에 붙인다 |
| 롤백 절차 | 이전 커밋 SHA로 재배포(pull·restart·검증 전체 반복) | 리스너를 blue로 되돌린다 |
| [ci.yml](../../.github/workflows/ci.yml)의 `INSTANCE_ID` | 단일 인스턴스 ID 하드코딩 | 태그 기반 타겟팅으로 교체 |
| [app-deploy.sh](../../deploy/scripts/app-deploy.sh)의 `docker exec masiton-redis` **4곳** | Redis 동거 전제 | 원격 `redis-cli -h` 호출로 변경. 6.3절 분리와 같은 PR에서 처리 |
| Flyway 적용 시점 | 재기동 중 단독 실행 | blue가 살아 있는 채로 green이 적용. 6.2절 하위 호환 강제 |

**두 번째 줄이 구조적으로 가장 크다.** 지금은 "갈아끼운 뒤 문제가 있으면 실패로 보고"인데, Blue-Green은 "검증을 통과한 것만 트래픽에 붙인다"가 되어야 의미가 있다. 스크립트를 나누는 수준이 아니라 검증과 교체의 순서를 뒤집는 작업이다.

**다섯 번째 줄은 Redis 분리를 단독으로 수행할 때도 필요하다.** 배포 검증이 `docker exec masiton-redis`로 같은 호스트의 컨테이너를 직접 호출하므로, 분리하면 배포가 검증 단계에서 실패한다. 8.2절 1단계의 작업 범위에 포함된다.

승인 게이트(`environment: production`)와 SSM 전달 경로는 전환 방식과 무관하게 유지한다.

## 7. 일정 영향

[범위 문서 7절](../00-overview/scope.md#7-범위-변경-절차) 4항은 "기존 일정 또는 다른 기능에 미치는 영향"을 요구하고, ADR-DEPLOY-002 3.1절은 이를 "고도화 작업이 4차 이후 기능 일정에 미치는 영향"으로 구체화했다.

**이 항목은 지금 완결할 수 없다.** `docs/08-planning`에 1·2·3차 확장 계획과 Task 분해는 있지만 **4차 확장 계획 문서가 없다.** 영향을 미칠 대상 일정이 존재하지 않으므로 산정하면 근거 없는 수치가 된다. 4차 범위가 확정된 뒤 이 절만 갱신한다.

대신 지금 확인할 수 있는 일정 사실 둘을 기록한다.

**첫째, 착수 시점 조건인 "3차 확장 이후"에 아직 도달하지 않았다.** [3차 확장 최종 게이트](third-expansion-final-gate-result.md)는 `CONDITIONAL`이고 4절의 여섯 항목이 모두 미해소다. 같은 문서 1.1절이 승격·배포는 막지 않는다고 개정했지만 **단계 완료 선언은 4절이 해소된 시점**이라고 명시했다.

**둘째, 토폴로지를 먼저 바꾸면 남은 성능 증거의 비교 기준이 끊긴다.** 게이트 4절 3번은 측정 전용 환경에서 최대 `200/80`을 실행·판정하고, 정상 `50/20`은 2차 확장 성능 검증 결과의 `Verified` 증거를 재사용하도록 요구한다. [#190 운영 직접 관찰](issue-190-operational-performance-result.md)은 단일 medium 인스턴스에서 수집됐다. **인스턴스를 small로 내리는 것만으로도 이 운영 비교 기준은 무효가 된다.** 성능 게이트를 먼저 닫고 하향·고도화를 시작하는 순서가 재측정 비용을 줄인다.

## 8. 판정과 권고

### 8.1. 판정

| 구성 | 예산 대비 | 판정 |
|---|---:|---|
| S1(Blue-Green) + E1 | 81% | 통과 |
| S1 + E2 | 100% | 통과. 500원 여유, 환율 최고치에서 106%로 초과 |
| S2(ASG 2대) + E1 | 102% | 초과 |
| S2 + E2 | 121% | 초과 |
| S1 + E3 | 114% | 초과 |
| S2 + E3 | 135% | 초과 |

**앱 인스턴스를 t4g.small로 내린다는 전제에서, S1과 Redis 분리는 E1 또는 E2로만 현재 환율 예산 안에 들어온다.** E2는 여유가 500원뿐이고 환율 최고치에서 초과하며, S2는 `ssm` 엔드포인트를 반영하면 E1에서도 예산을 넘는다. Redis 접근 경로가 E3(NAT Gateway)로 결정되면 어떤 구성도 예산을 넘는다.

### 8.2. 권고 — 세 단계로 나눈다

한 번에 전환하지 않는다. 단계를 나누면 각각을 독립적으로 되돌릴 수 있고, 앞 단계의 실측이 뒤 단계의 판정 근거가 된다.

**1단계 — Redis 분리.** 현재 단일 인스턴스 구조에서 먼저 수행한다. ALB도 Blue-Green도 없는 상태에서 전용 인스턴스만 떼어낸다. **여기서 E1·E2·E3 중 어느 경로가 실제로 성립하는지 확정되고, 그 값이 2·3단계의 예산 판정을 결정한다.** ADR-DATA-005 6절 개정에 공동 owner 합의가 필요하고, 배포 검증의 `docker exec masiton-redis` 4곳을 원격 호출로 바꾸는 작업이 같은 범위에 포함된다(6.8절).

**2단계 — ALB 도입, Nginx 유지.** TLS를 ACM으로 옮기고 XFF·XFP 신뢰 경계를 재설계한다(6.1절). 퍼블릭 서브넷을 두 번째 AZ에 추가한다(6.5절). 이 시점까지 상시 1대이므로 NFR-AVAILABILITY-002는 손대지 않는다.

**3단계 — Blue-Green 활성화.** 마이그레이션 하위 호환 규칙을 확정해 문서화한다(6.2절). Redis 접근 경로와 예산을 다시 확인하고, 현재 `ssm` 엔드포인트 전제에서는 ASG 상시 2대를 함께 도입하지 않는다. 별도 비용 절감 근거가 확인될 때만 NFR-AVAILABILITY-002 개정과 함께 재검토한다.

인스턴스 하향은 2단계와 3단계 사이에서 수행한다. 4.3절의 실측과 heap 고정이 선행 조건이다.

### 8.3. 착수 전 남는 선행 조건

1. 3차 확장 최종 게이트 4절 여섯 항목을 해소한다(7절 둘째 사실).
2. 4차 확장 범위를 확정하고 이 문서 7절의 일정 영향을 채운다.
3. 착수 ADR을 작성한다. ADR-DEPLOY-002 3.1절이 열거한 미확정 4항목에 **마이그레이션 하위 호환 규칙(6.2절), Redis 접근 경로와 전용 인스턴스 에이전트 유지 여부(5.1절)를 추가한 여섯 항목**이 목차가 된다.
4. `M2-09`가 미룬 RSS 실측과 JVM heap 고정 여부를 결정한다(4.3절).

## 9. 이 검토가 확인하지 않은 것

- **EC2 Instance Connect Endpoint 요금.** E1의 `$0`이 여기에 달려 있다. AWS 사용 설명서의 생성·접속 페이지에 요금 문구가 없어 확인하지 못했다. **유료로 밝혀지면 E1이 사라지고 S2가 예산 밖으로 나간다.**
- **퍼블릭 IPv4·EBS·인터페이스 엔드포인트·NAT Gateway·RDS 단가.** 2026-07-28 M2-01 산정값을 인용했다. EC2와 ALB만 재조회했다. 착수 판정 직전에 나머지도 재조회한다.
- **t4g.small의 실제 메모리 사용량과 CPU 크레딧 거동.** 4.1절 표는 전부 추정이고, small과 medium의 baseline 사용률이 같은지 확인하지 않았다.
- **스케줄러 8개 중 5개.** 6.4절에서 선점 방식을 확인한 것은 3개다. 나머지와 Gemini·Mobility quota 이중 계상 여부는 미확인이다.
- **실제 LCU 소비량.** 모든 표에 보수적 상한 1 LCU를 적용했다. 실제 청구액은 더 낮다.
- **AWS 자원을 하나도 만들지 않았다.** 문서와 공개 단가만 사용했다.
- **일정 영향(범위 문서 7절 4항)을 완결하지 못했다.** 7절에 사유를 적었다. 이 항목이 비어 있는 동안 배포 고도화는 착수 조건을 완전히 충족하지 않는다.

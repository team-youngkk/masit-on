---
status: Not Measured
verification_date: null
owners:
  - 이우람
related_documents:
  - README.md
  - second-expansion-test-matrix.md
  - expansion-2-task-breakdown.md
  - m2-deployment-plan.md
  - m2-cost-and-sizing.md
  - ../01-requirements/non-functional-requirements.md
  - ../07-adr/quality/perf-001-k6-load-testing.md
  - ../07-adr/quality/test-001-automation-strategy.md
  - ../07-adr/data/data-011-popular-restaurant-request-time-aggregation.md
  - ../05-specs/api/discovery/popular-restaurant-api.md
  - ../05-specs/api/curation/curation-api.md
---

# 2차 확장 성능 검증 결과

`NFR-PERFORMANCE-006`의 정상 부하 조건을 측정하고 판정한 기록이다. `TST-E2-PERF-001`의 부하 증거로 연결한다.

**현재 상태: 미측정.** 측정 수단([ADR-PERF-001](../07-adr/quality/perf-001-k6-load-testing.md))은 2026-08-06에 확정됐고 시나리오·기준 데이터·실행 절차가 저장소에 있다. 같은 날 팀이 **실측을 3차 확장 이후로 연기**하기로 결정했다. 구현에 우선순위를 두기 위한 판단이며, 수단이 없어서가 아니다.

**측정 전까지 `NFR-PERFORMANCE-006`의 정상 부하 조건은 충족 여부를 알 수 없는 상태이고, 충족했다고 보고하지 않는다.**

연기로 남는 위험을 명시한다. [ADR-DATA-011](../07-adr/data/data-011-popular-restaurant-request-time-aggregation.md)이 인기 맛집에 캐시·Snapshot·Batch를 배제한 근거는 "필요하다는 측정 근거가 없다"이다. 요청마다 `favorite` 전량을 집계하는 구조가 p95 500ms를 지키는지는 실측 전까지 확인되지 않으며, 해당 코드는 이미 제한 공개 환경에 배포돼 있다. 3차 확장에서 데이터가 늘어난 뒤 처음 측정해 미달이 나오면 캐시 도입 비용이 지금보다 커진다.

또한 [ADR-DEPLOY-002](../07-adr/platform/deploy-002-validation-deployment-before-expansion.md) 3.1절의 배포 고도화(ALB·Blue-Green·다중 인스턴스)도 "3차 확장 이후" 검토 대상이다. 실측이 그 시점으로 밀리면 **2차 확장이 실제로 운영된 단일 EC2 구성을 한 번도 측정하지 못한 채 그 구성이 바뀔 수 있다.** 배포 고도화가 착수되면 `RV-NFR-011`의 "운영 동급 단일 EC2" 전제부터 다시 정해야 한다.

## 1. 판정 기준

[NFR-PERFORMANCE-006](../01-requirements/non-functional-requirements.md#nfr-performance-006-2차-확장-공개-조회와-인기-집계-성능) 원문이며 이 문서가 완화하지 않는다.

| 항목 | 기준 |
|---|---|
| 애플리케이션 서버 내부 처리 p95 | 500ms 이하 |
| 서버 오류율 | 1% 미만 |
| 정상 부하 | 동시 사용자 50명, 20 RPS |

## 2. 측정 대상

| 대상 | 경로 | 계약 |
|---|---|---|
| 인기 맛집 실시간 집계 | `GET /api/restaurants/popular` | [인기 맛집 API](../05-specs/api/discovery/popular-restaurant-api.md), [ADR-DATA-011](../07-adr/data/data-011-popular-restaurant-request-time-aggregation.md) |
| 공개 큐레이션 목록 | `GET /api/curations` | [큐레이션 API](../05-specs/api/curation/curation-api.md) |
| 공개 큐레이션 상세 | `GET /api/curations/{curationId}` | [큐레이션 API](../05-specs/api/curation/curation-api.md) |

셋 다 무인증 공개 GET이고 rate limit이 걸려 있지 않다. `GET /api/restaurants/popular`는 쿼리 파라미터를 하나라도 붙이면 `400 INVALID_REQUEST`이므로 캐시 버스팅 파라미터를 붙이지 않는다.

## 3. 측정 환경

[RV-NFR-011](../01-requirements/non-functional-requirements.md#rv-nfr-011-성능-테스트-환경)과 [ADR-PERF-001](../07-adr/quality/perf-001-k6-load-testing.md) 6.4절이 정한 구성이다.

| 항목 | 값 |
|---|---|
| 애플리케이션 인스턴스 | 측정 전용 임시 EC2 `t4g.medium` (arm64, 2 vCPU / 4 GiB) |
| 데이터베이스 | `db.t4g.micro` (2 vCPU / 1 GiB), gp3 20 GiB, Single-AZ |
| 외부 연동 | Kakao·YouTube는 WireMock Stub |
| 부하 생성기 | 대상과 같은 리전·같은 VPC |
| 부하 도구 | k6 v2.1.0 |

제한 공개 중인 운영 인스턴스를 부하 대상으로 삼지 않는다. 측정이 끝나면 임시 자원을 종료한다.

## 4. 기준 데이터

| 대상 | 건수 | 근거 |
|---|---|---|
| 맛집 | 1,000 | [RV-NFR-002](../01-requirements/non-functional-requirements.md#rv-nfr-002-초기-데이터-규모) |
| 유튜버 | 200 | `RV-NFR-002` |
| 영상 | 5,000 | `RV-NFR-002` |
| 방문 관계 | 10,000 | `RV-NFR-002` |
| 회원 | 1,000 | [ADR-PERF-001](../07-adr/quality/perf-001-k6-load-testing.md) 6.5절 |
| 찜 | 20,000 | `ADR-PERF-001` 6.5절 |
| 게시 큐레이션 | 5 | `V3` 스키마의 `uq_curation__status_main_position` 상한 |
| 큐레이션당 맛집 | 20 | `V3` 스키마의 `curation_restaurant.position` CHECK 상한 |

찜은 균등 분포가 아니라 상위권에 편차를 둔다. 균등 분포에서는 `favoriteCount DESC, restaurantId ASC` 정렬의 동점 타이브레이커만 검사되고 순위 집계 자체가 검증되지 않는다. 적재 후 `ANALYZE`를 실행한다.

## 5. 재현 절차

### 5.1. 기준 데이터 적재

측정 대상 인스턴스의 DB에 Flyway 마이그레이션이 `V3`까지 적용된 상태를 전제한다. `perf/seed/` 아래 SQL을 파일명 순서대로 실행한다. 실행 명령과 파일 목록은 `perf/seed/README.md`를 따른다.

이 시드는 마이그레이션이 아니다. `src/main/resources/db/migration/`에 추가하지 않는다.

### 5.2. 직접 실행 절차

**대상과 같은 VPC 안에서 k6를 직접 실행한다.** 이것이 판정에 쓰는 방식이다.

k6 설치 시 기술 정책 3절에 따라 `latest`를 사용하지 않고 버전 **v2.1.0**을 고정한다. 실행기 아키텍처(amd64/arm64)에 맞춰 체크섬(SHA-256)을 검증 후 설치한다.

```bash
K6_VERSION="2.1.0"
ARCH="amd64" # t4g 등 arm64 인스턴스에서는 "arm64"

# linux-amd64 : 295d961ebfca306f295f1133068dcd403a8171c87f387928f5f30b0fbcff858a
# linux-arm64 : 191fa8d89512a4e5083f3fabcb4c3828af9f5b9eee016de8443f6473c029ffb5
if [ "$ARCH" = "arm64" ]; then
  K6_SHA256="191fa8d89512a4e5083f3fabcb4c3828af9f5b9eee016de8443f6473c029ffb5"
else
  K6_SHA256="295d961ebfca306f295f1133068dcd403a8171c87f387928f5f30b0fbcff858a"
fi

asset="k6-v${K6_VERSION}-linux-${ARCH}"
curl -fsSL -o k6.tar.gz "https://github.com/grafana/k6/releases/download/v${K6_VERSION}/${asset}.tar.gz"
echo "${K6_SHA256}  k6.tar.gz" | sha256sum -c -
tar -xzf k6.tar.gz
sudo install -m 0755 "${asset}/k6" /usr/local/bin/k6
rm -rf k6.tar.gz "${asset}"
k6 version
```

시나리오 실행:

```bash
BASE_URL=http://<측정-대상>:8080 k6 run perf/k6/normal-load-public-read.js
```

GitHub Actions의 `성능` 워크플로(`workflow_dispatch`)로도 같은 시나리오를 돌릴 수 있지만, **운영 동급 판정에는 쓰지 않는다.** 이유는 둘이다.

- 이 저장소는 공개 저장소이고 `workflow_dispatch` 입력값은 실행 페이지에 마스킹 없이 표시된다. 검증 게이트 없이 8080을 노출한 측정 인스턴스의 주소가 공개된다.
- GitHub 호스티드 러너에는 고정 egress IP가 없어, 러너가 대상에 도달하려면 보안 그룹을 넓게 열어야 한다. 그리고 네트워크 왕복이 늘어 7절의 측정 오차가 커진다.

워크플로는 인터넷에 노출해도 되는 임시 환경에서 시나리오가 도는지 확인하는 보조 수단으로 남긴다.

k6는 `thresholds` 위반 시 0이 아닌 종료 코드로 끝난다. 통과·실패는 종료 코드로 판정하고 수치를 보고 사람이 정하지 않는다.

### 5.3. 결과 수집

워크플로 실행은 선택한 시나리오별 결과 요약을 artifact(`k6-<load_profile>-public-read-results`, `k6-<load_profile>-third-expansion-natural-language-results`, `k6-<load_profile>-third-expansion-course-<course_metric_mode>-results`)로 14일 보관하고 job summary에도 남긴다. 결과 파일은 `perf/k6/results/<load_profile>/public-read/`, `perf/k6/results/<load_profile>/third-expansion/natural-language/`, `perf/k6/results/<load_profile>/third-expansion/course/<course_metric_mode>/`에 각각 둔다. 직접 실행한 경우에도 같은 시나리오별 경로를 사용한다. 결과 파일 자체는 커밋하지 않고, 수치는 6절 표에 옮겨 적는다.

## 6. 측정 결과

**아직 측정하지 않았다.** 팀이 2026-08-06에 실측을 3차 확장 이후로 연기했다. 측정 수행 후 이 절을 채우고 문서 frontmatter의 `status`를 `Verified` 또는 `Failed`로, `verification_date`를 실제 측정일로 바꾼다.

| 대상 | p95 | 기준 | 판정 |
|---|---|---|---|
| `GET /api/restaurants/popular` | 미측정 | 500ms 이하 | 미판정 |
| `GET /api/curations` | 미측정 | 500ms 이하 | 미판정 |
| `GET /api/curations/{curationId}` | 미측정 | 500ms 이하 | 미판정 |

| 지표 | 값 | 기준 | 판정 |
|---|---|---|---|
| 서버 오류율(5xx) | 미측정 | 1% 미만 | 미판정 |
| `dropped_iterations` | 미측정 | 0건 | 미판정 |
| 실제 요청률 | 미측정 | 20 RPS | 미판정 |

측정 실행 정보도 함께 기록한다.

| 항목 | 값 |
|---|---|
| 측정일 | 미측정 |
| 측정자 | 미정 |
| 워크플로 실행 | 미실행 |
| 애플리케이션 커밋 SHA | 미정 |
| k6 버전 | v2.1.0 |
| 부하 생성기 아키텍처 | 미정 (linux-amd64 / linux-arm64) |
| 적재 확인 건수 | 미확인 |

## 7. 알려진 제약

- k6의 `http_req_duration`은 네트워크 왕복을 포함한다. `NFR-PERFORMANCE-006`이 말하는 "애플리케이션 서버 내부 처리"와 정확히 같지 않다. 부하 생성기를 대상과 같은 VPC에 두어 그 차이를 최소화하며, 측정 수치를 네트워크 지연 추정치로 사후 보정하지 않는다.
- 측정 전용 임시 인스턴스를 쓰므로 운영 인스턴스의 누적 데이터·동시 트래픽·캐시 워밍 상태는 반영되지 않는다.
- **애플리케이션 8080에 직결해 측정한다.** 운영 토폴로지는 같은 인스턴스에 Nginx·Next.js·Redis가 함께 올라가 있고 `/api`가 Nginx 뒤에 있지만, `NFR-PERFORMANCE-006`이 재라는 것은 "애플리케이션 서버 내부 처리"이므로 직결이 타당하다. 대신 이 수치에는 **Nginx 프록시 구간과 프론트엔드·Redis 동거 부하가 포함되지 않는다.**
- 이 문서는 정상 부하 50명·20 RPS만 다룬다. [RV-NFR-011](../01-requirements/non-functional-requirements.md#rv-nfr-011-성능-테스트-환경)이 확정한 최대 부하 200명·80 RPS는 **시나리오 자체가 아직 없어 미구현·미측정 상태**다. [이슈 #148](https://github.com/team-youngkk/masit-on/issues/148)의 완료 조건은 아니지만 요구사항이 이미 요구하는 항목이므로 범위 밖이 아니며, 별도 후속 작업으로 채운다.

## 8. 미충족 시 조치

기준을 충족하지 못하면 원인과 조치 항목을 **별도 이슈로 분리**한다. 이 문서에서 threshold를 낮추거나 판정 기준을 완화하지 않는다.

캐시·Snapshot·Batch 도입이 필요해 보이면 [ADR 백로그](../07-adr/adr-backlog.md)의 `ADR-CACHE-001` 등 해당 항목을 별도로 활성화한다. [ADR-DATA-011](../07-adr/data/data-011-popular-restaurant-request-time-aggregation.md)이 선제 캐시를 금지하므로, 측정된 병목 없이 도입하지 않는다.

## 9. 측정 전까지 유지되는 회귀 방어선

부하 측정이 실제로 수행되기 전까지 성능 회귀 탐지는 다음 셋이 담당한다. 셋 다 쿼리 수와 실행계획의 구조적 악화는 잡지만 부하 하의 p95 악화는 잡지 못한다.

- `PublicCurationQueryCountApiTest`, `PopularRestaurantQueryCountApiTest` — 공개 조회 쿼리 수 상수 가드
- `CurationPublicQueryPlanPostgreSqlIntegrationTest` — PostgreSQL 실행계획(`loops=1`) 검증
- `PublicCurationPerformanceIntegrationTest` — 순차 내부 처리 latency. CI 러너 편차로 인한 flaky를 피하려고 `@Disabled` 상태이며 수동 실행용이다

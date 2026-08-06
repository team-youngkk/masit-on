---
id: ADR-PERF-001
title: k6 부하 테스트 도구와 실행 체계
status: Accepted
decision_date: 2026-08-06
owners:
  - 이우람
reviewers:
  - 양성훈
  - 박진영
  - 김인안
related_requirements:
  - NFR-PERFORMANCE-006
  - NFR-TEST-005
  - RV-NFR-002
  - RV-NFR-011
related_documents:
  - ../../01-requirements/non-functional-requirements.md
  - ../../06-architecture/technology-policy.md
  - ../../08-planning/second-expansion-test-matrix.md
  - ../../08-planning/second-expansion-performance-verification.md
  - ../../08-planning/m2-deployment-plan.md
  - test-001-automation-strategy.md
  - ../data/data-011-popular-restaurant-request-time-aggregation.md
  - ../platform/ci-001-github-actions-quality-gate.md
  - ../platform/deploy-002-validation-deployment-before-expansion.md
  - ../adr-backlog.md
  - ../adr-index.md
  - ../adr-traceability.md
supersedes: []
superseded_by: null
---

# ADR-PERF-001 k6 부하 테스트 도구와 실행 체계

## 1. 상태

Accepted

## 2. 결정 요약

부하 생성 도구로 **k6 v2.1.0**을 고정한다. 시나리오는 저장소의 `perf/k6/`에 두고, 판정은 스크립트가 선언한 `thresholds`의 종료 코드로 한다. 실행은 **`workflow_dispatch` 전용 워크플로**(`.github/workflows/performance.yml`)로만 하며 PR·push·스케줄에 붙이지 않는다. 측정은 운영 동급 사양의 **측정 전용 임시 EC2**에서 수행하고, 제한 공개 중인 운영 인스턴스에는 부하를 걸지 않는다.

## 3. 배경

[ADR-TEST-001](test-001-automation-strategy.md) 13절과 [RV-NFR-011](../../01-requirements/non-functional-requirements.md#rv-nfr-011-성능-테스트-환경)은 성능 기준과 테스트 환경을 이미 확정했다. 운영 동급 단일 EC2, 초기 기준 데이터 100%, Kakao·YouTube WireMock Stub, **정상 부하 50명·20 RPS와 최대 부하 200명·80 RPS를 각각 실행**, 애플리케이션 서버 내부 처리 p95 500ms 이하와 서버 오류율 1% 미만이다. 확정되지 않은 것은 **그 부하 모델을 자동 반복 실행할 도구** 하나였고, 그래서 이 ADR이 백로그에 `Conditional`로 남아 있었다.

그 공백 때문에 `E2-T15`(#117) 최종 검증에서 `NFR-PERFORMANCE-006`의 정상 부하 측정을 수행하지 못했다. #117이 "미결정 기술을 완료 조건으로 추가하지 않는다"를 규정하므로, PR #147은 이 측정을 보류 항목으로 명시하고 [2차 확장 테스트 추적표](../../08-planning/second-expansion-test-matrix.md) 5절에 차단 사유와 해제 조건을 기록했다. 이 ADR이 그 해제 조건이다.

## 4. 결정 문제

정상 부하 50명·20 RPS를 반복 재현하고 p95·오류율을 사람 판단 없이 판정하려면 어떤 도구를 어느 버전으로, 어디서, 어떤 비용으로 실행해야 하는가.

## 5. 고려한 선택지

- k6 — 단일 정적 바이너리, JavaScript 시나리오, `thresholds` 기반 종료 코드 판정
- Gatling — JVM 기반, Gradle 통합 가능, HTML 리포트
- Apache JMeter — GUI·XML 시나리오, 성숙한 생태계
- 도구 없이 JUnit 동시 요청 하니스를 직접 작성

## 6. 결정

### 6.1. 도구와 버전

- **k6 v2.1.0**(2026-06-30 릴리즈)을 고정한다. major·minor·patch를 모두 명시하며, `latest`나 major만 지정하는 설치를 금지한다.
- k6는 애플리케이션 런타임 의존성이 아니다. `build.gradle`, `package.json` 어디에도 추가하지 않는다. 실행 시점에 GitHub 릴리즈 tarball을 버전 고정으로 내려받아 쓴다.

### 6.2. 시나리오와 판정

- 시나리오는 `perf/k6/`에 둔다. 부하 모델·임계값은 스크립트가 선언하고 저장소 리뷰 대상이다.
- `constant-arrival-rate` executor에 `rate: 20`, `preAllocatedVUs`·`maxVUs` 모두 50을 쓴다. VU 수를 부하에 따라 자동 증가시키지 않는다.
- 이 모델이 고정하는 것은 **요청률 20 RPS와 동시성 상한 50**이다. `RV-NFR-011`의 "50명"을 동시에 요청을 처리 중인 사용자 수로 재현하지는 않는다. 도착률 20/s에서 동시 활성 VU는 `20 × 평균 응답시간`이라, 활성 VU가 50이 되려면 평균 응답이 2.5초여야 하고 그건 이미 p95 500ms 기준을 위반한 상태다. 기준을 지키는 한 활성 VU는 50보다 훨씬 적다. **결과를 "동시 사용자 50명을 검증했다"로 보고하지 않는다.**
- 판정은 `thresholds`로 한다. 위반 시 k6가 0이 아닌 종료 코드로 끝나고 실행 job이 실패한다. 사람이 수치를 보고 통과 여부를 정하지 않는다.
- 필수 threshold는 다음 여섯이다. **p95와 오류율 값은 [NFR-PERFORMANCE-006](../../01-requirements/non-functional-requirements.md#nfr-performance-006-2차-확장-공개-조회와-인기-집계-성능)을 그대로 옮긴 것이며 이 ADR이 완화할 수 없다.**

  | 지표 | 임계값 | 근거 |
  |---|---|---|
  | `duration_restaurants_popular` p95 | 500ms 이하 | `NFR-PERFORMANCE-006` |
  | `duration_curations_list` p95 | 500ms 이하 | `NFR-PERFORMANCE-006` |
  | `duration_curations_detail` p95 | 500ms 이하 | `NFR-PERFORMANCE-006` |
  | `server_error_rate` | 0.01 미만 | `NFR-PERFORMANCE-006`의 **서버 오류율** 1% 미만. 5xx만 센다 |
  | `http_req_failed{phase:measured}` | 0.01 미만 | 계약값은 아니다. 4xx를 포함한 보조 가드이며 계약 지표보다 넓게 잡는다 |
  | `dropped_iterations` | 0건 | VU 50 고정이 요청률을 만들어내지 못했다면 측정 자체가 부하 모델을 재현하지 못한 것이다 |

  응답 시간은 k6 기본 `http_req_duration`이 아니라 엔드포인트별 커스텀 Trend로 잰다. 세 경로의 비용이 달라 하나로 합치면 느린 쪽이 빠른 쪽에 가려지고, 워밍업 구간을 판정에서 빼려면 별도 지표가 필요하기 때문이다.

  "서버 오류율"을 `http_req_failed`로 판정하지 않는다. 그 지표는 4xx도 실패로 세므로 계약이 말하는 서버 오류와 범위가 다르다.

- 워밍업 구간과 측정 구간을 태그로 분리하고 워밍업 구간의 응답 시간·오류율은 판정에서 제외한다. `dropped_iterations`만은 예외로 두 구간을 합쳐 본다. 워밍업에서 요청률을 못 만들었다면 그것도 부하 모델 재현 실패이기 때문이다.

### 6.3. 실행 위치와 비용

- **판정에 쓰는 실행은 대상과 같은 VPC 안에서 k6를 직접 돌리는 것이다.** GitHub Actions 워크플로는 보조 수단이다. 이 저장소는 공개 저장소여서 `workflow_dispatch` 입력값이 실행 페이지에 마스킹 없이 노출되고, 호스티드 러너에 고정 egress IP가 없어 대상 보안 그룹을 넓게 열어야 하며, 네트워크 왕복이 늘어 6.4절의 측정 오차가 커진다.
- 저장소 워크플로는 `.github/workflows/performance.yml` 하나이며 **`workflow_dispatch` 트리거만** 갖는다.
- `pull_request`, `push`, `schedule` 트리거를 추가하지 않는다. 따라서 정기 CI 러너 비용 증가는 0이다.
- [ADR-CI-001](../platform/ci-001-github-actions-quality-gate.md)의 필수 상태 검사(`백엔드 빌드·테스트`, `프론트엔드 빌드·타입 검사`)에 이 워크플로의 job을 추가하지 않는다. 저장소 ruleset의 필수 검사 목록도 바꾸지 않는다.
- 부하 테스트를 PR 병합 게이트로 쓰지 않는다. 러너 성능 편차가 p95를 좌우해 게이트가 flaky해지고, 이미 같은 이유로 `PublicCurationPerformanceIntegrationTest`가 `@Disabled` 상태다.

### 6.4. 측정 환경

- 측정 대상은 **측정 전용 임시 EC2**다. 사양은 운영과 동급인 `t4g.medium`(arm64, 2 vCPU / 4 GiB)이고 DB는 `db.t4g.micro`다([M2 배포 계획](../../08-planning/m2-deployment-plan.md) 4·5절).
- 제한 공개 중인 운영 인스턴스에 부하를 걸지 않는다. 검증 참여자 트래픽에 영향을 주고, 측정용 기준 데이터를 운영 DB에 적재해야 하기 때문이다.
- 측정이 끝나면 임시 인스턴스를 종료한다. 비용은 측정 시간분만 발생한다.
- 부하 생성기는 대상 서버와 **같은 리전·같은 VPC**에 둔다. k6가 재는 `http_req_duration`은 네트워크 왕복을 포함하므로, `NFR-PERFORMANCE-006`이 말하는 "애플리케이션 서버 내부 처리"에 가깝게 만들려면 네트워크 구간을 최소화해야 한다. 측정된 수치를 사후에 보정하지 않는다.
- Kakao·YouTube는 WireMock Stub으로 대체한다([RV-NFR-011](../../01-requirements/non-functional-requirements.md#rv-nfr-011-성능-테스트-환경)).

### 6.5. 기준 데이터

- 시드는 `perf/seed/`에 두고 마이그레이션이 아니다. [ADR-DATA-004](../data/data-004-flyway.md)의 Flyway 마이그레이션(`src/main/resources/db/migration/`)에 성능 시드를 추가하지 않는다.
- 규모는 [RV-NFR-002](../../01-requirements/non-functional-requirements.md#rv-nfr-002-초기-데이터-규모)의 맛집 1,000개·유튜버 200개·영상 5,000개·방문 관계 10,000개를 100% 채운다.
- `RV-NFR-002`가 정의하지 않은 회원·찜 규모는 **회원 1,000명·찜 20,000건**으로 이 ADR에서 확정한다. [ADR-DATA-011](../data/data-011-popular-restaurant-request-time-aggregation.md)의 인기 집계가 `favorite` 전량을 요청마다 GROUP BY 하므로, 이 수치가 곧 해당 API의 부하다. 값이 없으면 측정이 성립하지 않는다.
- 게시 큐레이션은 5건, 큐레이션당 맛집은 20건을 채운다. 둘 다 새로 정한 값이 아니라 `V3` 스키마 제약의 상한이다(`uq_curation__status_main_position`이 `main_position` 1~5를 유일하게 강제하고, `curation_restaurant.position`이 1~20으로 제한된다). 공개 큐레이션 조회의 최대 부하 조건이므로 상한을 그대로 쓴다.
- 찜은 균등 분포가 아니라 상위권에 편차가 있는 분포로 적재한다. 균등 분포에서는 `favoriteCount DESC, restaurantId ASC` 정렬의 동점 타이브레이커만 검사되고 순위 집계 자체가 검증되지 않는다.
- 시드 적재 후 `ANALYZE`를 실행한다. 통계가 없으면 실행계획이 운영과 달라져 측정이 무의미해진다.

### 6.6. 결과 보관

- 실행 결과 요약을 `actions/upload-artifact@v4`로 14일 보관한다([CI 워크플로](../../../.github/workflows/ci.yml)의 테스트 리포트 보관 기간과 같다).
- 판정에 쓴 실행 환경·기준 데이터·재현 명령·수치는 [2차 확장 성능 검증 결과](../../08-planning/second-expansion-performance-verification.md)에 기록하고 `TST-E2-PERF-001` 증거로 연결한다.
- 원시 결과 파일은 커밋하지 않는다. 저장소에 남는 것은 시나리오·워크플로·판정 결과 문서다.

## 7. 선택 근거

k6는 단일 정적 바이너리라 임시 EC2와 GitHub 러너 어디에도 런타임 의존성 없이 설치된다. `thresholds`가 통과·실패를 종료 코드로 만들어 "수치를 보고 사람이 판단한다"는 여지를 없애는데, 이는 [ADR-TEST-001](test-001-automation-strategy.md)이 커버리지 수치 대신 시나리오 통과를 완료 기준으로 삼는 방향과 같다. 시나리오가 일반 JavaScript 파일이라 코드 리뷰와 diff가 그대로 작동한다.

Gatling은 JVM 의존성과 Gradle 플러그인을 백엔드 빌드에 추가한다. 부하 도구는 애플리케이션 런타임과 무관한데 빌드 그래프를 오염시킬 이유가 없다. JMeter의 XML 시나리오는 리뷰와 버전 관리에 불리하다. 직접 작성하는 하니스는 요청률 제어·백분위 집계·워밍업 분리를 전부 새로 만들어야 하고, 그 코드가 맞는지 검증할 근거가 없다.

무엇보다 [ADR 백로그](../adr-backlog.md)가 이미 이 항목을 "k6 성능 테스트 체계"로 지목했다. 활성화 조건이 "정확한 k6 버전과 CI 비용 승인"이었으므로, 다른 도구를 고르면 조건 자체를 다시 쓰는 결정이 된다. 그럴 만한 근거가 없다.

버전은 최신 안정판 v2.1.0을 고정한다. 이 저장소는 Spring Boot 4.1.0, Next.js 16.2.11, Testcontainers 2.0.5처럼 최신 안정 major를 고정하는 [기술 정책](../../06-architecture/technology-policy.md)을 따르며, k6도 같은 규칙을 적용한다.

## 8. 트레이드오프

수동 트리거 전용이므로 성능 회귀가 자동으로 탐지되지 않는다. 누군가 워크플로를 돌리기 전까지 회귀는 드러나지 않는다. 그 사이의 회귀 탐지는 기존 세 방어선이 담당한다.

- `PublicCurationQueryCountApiTest`, `PopularRestaurantQueryCountApiTest` — 공개 조회 쿼리 수 상수 가드
- `CurationPublicQueryPlanPostgreSqlIntegrationTest` — PostgreSQL 실행계획 검증
- `PublicCurationPerformanceIntegrationTest` — 순차 내부 처리 latency. `@Disabled` 수동 실행용

이 셋은 쿼리 수와 실행계획의 구조적 악화는 잡지만 부하 하의 p95 악화는 잡지 못한다. 그 공백을 인정하고, 대신 정기 CI 비용과 flaky 게이트를 만들지 않는 쪽을 택한다.

측정 전용 임시 인스턴스를 쓰므로 운영 인스턴스의 실제 상태(누적 데이터, 동시 트래픽, 캐시 워밍 상태)는 반영되지 않는다. 재현성을 얻는 대신 운영 실측을 포기한다.

## 8.1. 이 ADR이 채우지 못한 공백

이 ADR은 측정 **수단**을 확정한다. 측정 **결과**는 확정하지 않는다. 아래 둘은 미측정 상태이며, 이 ADR이 그것을 범위 밖으로 낮추지 않는다.

| 공백 | 상태 | 계약 근거 |
|---|---|---|
| 정상 부하 50명·20 RPS 실측 | **미측정.** 시나리오·기준 데이터·실행 절차는 준비됐고, 팀이 2026-08-06에 실측을 3차 확장 이후로 연기하기로 결정했다 | [RV-NFR-011](../../01-requirements/non-functional-requirements.md#rv-nfr-011-성능-테스트-환경), `NFR-PERFORMANCE-006` |
| 최대 부하 200명·80 RPS 시나리오와 실측 | **미구현·미측정.** 시나리오 자체가 아직 없다 | `RV-NFR-011`이 "정상 부하와 최대 부하를 **각각 실행한다**"로 확정 |

`RV-NFR-011`은 두 부하를 모두 실행하도록 이미 확정한 상태다. 최대 부하가 아직 없는 것은 **결정되지 않은 확장 항목이라서가 아니라 아직 만들지 않았기 때문**이며, 별도 후속 작업으로 채운다.

이 공백 때문에 [ADR-DATA-011](../data/data-011-popular-restaurant-request-time-aggregation.md)이 캐시·Snapshot·Batch를 배제한 근거("필요하다는 측정 근거가 없다")도 **아직 측정으로 확인되지 않은 상태로 남는다.** 인기 맛집이 요청마다 `favorite` 전량을 집계하는 구조가 p95 500ms를 지키는지는 실측 전까지 알 수 없다.

## 9. 강제 규칙

- k6 버전은 `v2.1.0`으로 고정한다. 설치 단계에서 `latest`를 쓰지 않는다.
- `perf/k6/`의 threshold 값은 `NFR-PERFORMANCE-006`을 그대로 반영해야 한다. 측정이 실패했다는 이유로 threshold를 낮추지 않는다.
- `.github/workflows/performance.yml`에 `pull_request`·`push`·`schedule` 트리거를 추가하지 않는다.
- 이 워크플로의 job 이름을 저장소 ruleset의 필수 상태 검사에 추가하지 않는다.
- 성능 시드를 Flyway 마이그레이션으로 만들지 않는다.
- 제한 공개 중인 운영 인스턴스를 부하 대상으로 삼지 않는다.
- 측정하지 못한 항목을 측정한 것으로 보고하지 않는다. 판정 결과 문서에 미측정 사실을 남긴다.

## 10. 금지 사항

- 부하 결과를 근거로 캐시·Snapshot·Batch를 이 ADR에서 도입하지 않는다. 병목이 확인되면 [ADR 백로그](../adr-backlog.md)의 해당 항목(`ADR-CACHE-001` 등)을 별도로 활성화한다.
- 부하 테스트 중 실제 Kakao·YouTube API를 호출하지 않는다.
- 측정용 기준 데이터를 운영 DB에 적재하지 않는다.
- 성능 수치를 네트워크 지연 추정치로 사후 보정하지 않는다.

## 11. 구현·운영 영향

- 저장소에 `perf/` 디렉터리가 추가된다. 백엔드·프론트엔드 빌드 산출물과 무관하며 Gradle·npm 의존성이 늘지 않는다.
- `.github/workflows/performance.yml` 워크플로가 하나 늘어난다. 기존 `ci.yml`은 변경되지 않는다.
- 측정 시점마다 임시 EC2·RDS 기동 비용이 발생한다. 측정 시간분에 한정되며 상시 비용은 없다.
- `NFR-PERFORMANCE-006`의 정상 부하 판정 시점이 이 ADR 이후로 열린다. `TST-E2-PERF-001`의 보류 항목이 해제된다.

## 12. 검증 방법

- `perf/seed/`로 기준 데이터를 적재하고 각 테이블 건수가 6.5절 규모와 일치하는지 확인한다.
- `perf/k6/`의 시나리오를 측정 전용 임시 EC2 대상으로 실행하고 k6 종료 코드로 판정한다.
- threshold 위반 시 워크플로 job이 실패하는지 확인한다.
- 재현 명령·실행 환경·기준 데이터·결과 수치를 [2차 확장 성능 검증 결과](../../08-planning/second-expansion-performance-verification.md)에 기록한다.
- 기준 미충족 시 원인과 조치 항목을 별도 이슈로 분리한다.

## 13. 재검토 조건

k6 v2.1.0에 이 시나리오를 막는 결함이 발견되거나, 기준 데이터 규모([RV-NFR-002](../../01-requirements/non-functional-requirements.md#rv-nfr-002-초기-데이터-규모))나 목표 부하([RV-NFR-011](../../01-requirements/non-functional-requirements.md#rv-nfr-011-성능-테스트-환경))가 바뀌거나, 성능 회귀를 정기 자동 실행으로 탐지해야 할 필요가 생기면 재검토한다. 배포 토폴로지가 [ADR-DEPLOY-002](../platform/deploy-002-validation-deployment-before-expansion.md)의 단일 인스턴스에서 바뀌어도 측정 환경 전제가 달라지므로 재검토한다. `ADR-DEPLOY-002` 3.1절이 다루는 배포 고도화(ALB·Blue-Green·다중 인스턴스)가 실제로 착수되면 이 조건에 걸린다.

## 14. 관련 문서

- [비기능 요구사항](../../01-requirements/non-functional-requirements.md) — `NFR-PERFORMANCE-006`, `RV-NFR-002`, `RV-NFR-011`
- [ADR-TEST-001 계층별 자동화 테스트 전략](test-001-automation-strategy.md)
- [ADR-DATA-011 인기 맛집 요청 시점 실시간 집계](../data/data-011-popular-restaurant-request-time-aggregation.md)
- [ADR-CI-001 빌드·테스트 품질 게이트](../platform/ci-001-github-actions-quality-gate.md)
- [ADR-DEPLOY-002 초기 운영 배포 선행과 확장 단계별 인프라 반영](../platform/deploy-002-validation-deployment-before-expansion.md)
- [2차 확장 테스트 추적표](../../08-planning/second-expansion-test-matrix.md)
- [2차 확장 성능 검증 결과](../../08-planning/second-expansion-performance-verification.md)
- [M2 초기 운영 배포 계획](../../08-planning/m2-deployment-plan.md)
- [ADR 백로그](../adr-backlog.md)

---
status: Observed
evidence_completeness: partial
capacity_evidence: incomplete
run_date: 2026-08-14
issue: 190
follow_up_issue: 207
deployment_commit: d496889
run_id: 20260814
snapshot_identifier: masiton-db-perf-20260814
evidence_commands:
  - normal_public_read: de5b635a-6968-44b6-ae3d-01d088188b3d
  - max_public_read: c4a41bee-da0d-42ee-b7e4-51d12b9c6f5c
  - natural_language_normal: 734120ce-2c87-4282-adde-4a854c8d03be
requested_at_kst:
  - normal_public_read: 2026-08-14T15:41:39+09:00
  - max_public_read: 2026-08-14T15:50:05+09:00
  - natural_language_normal: 2026-08-14T16:21:38+09:00
evidence_sha256:
  - results-normal/summary.json: 56d1a39db58f5727cd1ca8a58eec493842aab09402a805bd200f5c75fc2e7915
  - results-max/summary.json: f8ab075647642b70dc874d9b53e2223f1f272c88264464b5eddc1f15f43936df
  - results-natural-normal-2/summary.json: f416966c4f317e5254a34eef8a98746b226e88fd2f5c5c8fb2dccad8d1e9dca0
related_documents:
  - second-expansion-performance-verification.md
  - third-expansion-final-gate-result.md
  - ../07-adr/quality/perf-001-k6-load-testing.md
  - ../07-adr/quality/perf-002-operational-participant-load-testing.md
---

# 이슈 #190 운영 검증 결과

## 1. 범위와 실행 환경

이 문서는 임시 EC2를 만들지 않고 운영 EC2에서 검증 참여자 전용 범위로 수행한 관찰 결과를 기록한다. 기본 성능 인증인 `ADR-PERF-001`을 대체하지 않으며, `RV-NFR-002` 전체 규모 달성으로 해석하지 않는다.

- GitHub issue: [#190](https://github.com/team-youngkk/masit-on/issues/190)
- 배포 기준 커밋: `d496889`
- EC2: `i-0b451f18bca827cc9`, `t4g.medium`, SSM 실행
- 부하 발생 위치: 운영 EC2 호스트, `http://127.0.0.1:8080`
- 데이터베이스: 운영 RDS `masiton-db`, PostgreSQL 17.10
- k6: `v2.1.0`, `linux/arm64`
- 사전 RDS 스냅샷: `masiton-db-perf-20260814`

운영 네트워크 경계 밖의 실제 사용자 트래픽과 동일하지 않고, EC2 호스트에서 백엔드 컨테이너까지의 애플리케이션 경로를 측정했다. 따라서 인터넷 진입점·ALB·외부 네트워크 지연은 이 결과에 포함하지 않는다.

## 2. 운영 fixture

적용 전 스냅샷을 생성하고 `perf/operational-fixture/`의 preflight → apply → verify를 실행했다. fixture는 다음 범위였다.

- public·active restaurant 25건, 그중 좌표 보유 5건
- `example.invalid` synthetic member 25건
- fixture member × fixture restaurant favorite 500건
- published synthetic curation 1건 및 restaurant relation 20건
- 기존 active admin 재사용, 기존 DRAFT curation은 변경하지 않음

검증 후 cleanup을 실행했고, fixture restaurant/member/curation은 0건으로 확인했다. cleanup 후 기준 데이터는 restaurant 22건, member 4건, published curation 0건으로 복원됐으며 readiness는 계속 `UP`이었다.

## 3. 부하 결과

### 3.1 기존 public-read 정상·최대 부하

`perf/k6/normal-load-public-read.js`를 운영 fixture가 적용된 상태에서 실행했다.

| 프로파일 | popular p95 | curation list p95 | curation detail p95 | measured samples | 5xx | dropped | HTTP failed |
|---|---:|---:|---:|---:|---:|---:|---:|
| normal (50 VU / 20 RPS) | 5.945ms | 8.960ms | 8.893ms | 6,001 | 0% | 0 | 0% |
| max (200 VU / 80 RPS) | 4.480ms | 7.606ms | 7.718ms | 24,001 | 0% | 0 | 0% |

normal·max 모두 실행 중 readiness `UP`을 유지했다. 측정 중 max 기준 backend는 약 14.73% CPU, 417.6MiB / 1GiB 메모리였고, 종료 후 약 0.15% CPU로 회복했다.

### 3.2 자연어 검색

`perf/k6/third-expansion-load.js`의 `SCENARIO=public-read`를 운영에서 실행한다. 이 시나리오는 자연어 검색 API만 호출하며 외부 유료 API를 호출하지 않는다.

| 프로파일 | natural-language p95 (200 응답 표본만) | measured samples | 5xx | dropped | HTTP failed |
|---|---:|---:|---:|---:|---:|
| normal (50 VU / 20 RPS) | 14.295ms | 6,001 | 0% | 0 | 95.00% |
| max (200 VU / 80 RPS) | 미실행 | - | - | - | - |

normal 측정의 전체 요청은 7,202건이었고 200 응답은 419건, 예상하지 않은 응답은 6,783건이었다. `server_error_rate`는 0%, `dropped_iterations`는 0건이었으며 p95는 성공한 200 응답 300건의 성능 표본에만 계산됐다. 따라서 p95 threshold 통과를 시나리오 통과로 해석하지 않으며, `http_req_failed{phase:measured}` 95.00%로 자연어 정상 부하는 `BLOCKED` 판정이다. k6 summary에는 HTTP 상태별 태그가 없어 6,783건 전체를 사후에 429로 확정할 수는 없었지만, 같은 운영 endpoint에 대한 별도 61건 제어 probe에서 200 응답 60건 뒤 429 응답 1건을 확인했다. 운영 구현의 자연어 검색 rate limit이 동일 client address에 분당 60건을 허용하고, loopback에서 발생한 20 RPS 요청이 같은 주소로 집계된 결과와 일치한다. 이는 애플리케이션 5xx 오류가 아니라 검증 부하 모델이 운영 rate-limit 정책과 충돌한 결과다.

단일 smoke 요청은 부하 전·후 모두 `200`과 빈 결과의 정상 계약을 확인했다. 별도 61건 제어 probe는 `200 × 60`, `429 × 1`이었으며 데이터 변경은 없었다. 동일 client address를 고정한 상태에서 max 프로파일을 추가 실행하면 같은 rate-limit 응답만 늘어나므로 실행하지 않았다. rate-limit을 우회하려고 production trusted proxy 설정이나 애플리케이션 설정을 변경하지 않는다. 정상 throughput 측정은 [후속 이슈 #207](https://github.com/team-youngkk/masit-on/issues/207)의 rate-limit 준수 저속 프로필 또는 승인된 다중 client 환경이 준비된 뒤 다룬다.

### 3.3 코스 추천

운영 코스 시나리오는 실행하지 않았다. 저장소 시나리오의 기본 측정 모드는 WireMock Stub을 전제로 하며, 운영에서는 Kakao Mobility production provider가 연결된다. 실제 provider 호출은 quota·비용 hard stop 및 “실제 유료 외부 호출 금지” 범위를 벗어나므로, 운영에서 임의로 외부 호출을 발생시키지 않았다.

## 4. 판단

이번 실행은 운영 애플리케이션의 검증 참여자 전용 범위에서 public-read와 fixture 기반 데이터 경로가 동작함을 확인한 관찰 결과다. 운영 직접 실행이라는 이유만으로 임시 환경 기반 `PERF-001` 인증이나 전체 최종 gate를 `GO`로 변경하지 않는다. 코스 추천은 WireMock 또는 별도 승인된 quota-safe provider 환경에서 추가 검증해야 한다.

이번 실행에서 RDS CloudWatch 시계열, DB pool, Redis command/error 지표의 별도 artifact는 수집하지 않았다. 따라서 컨테이너 자원 관찰은 기록했지만 운영 용량 승인이나 DB·Redis capacity 결론으로 확대하지 않는다.

SSM 명령에는 요청 시각만 보존됐고, 각 k6 summary에는 내부 실행 시간(`normal` 360057ms, `max` 360019ms, 자연어 `normal` 360005ms)만 남아 절대 종료 시각을 별도 artifact로 고정하지 못했다. 따라서 이 문서는 ADR-PERF-002의 **부분 운영 관찰 증거**이며, 정확한 시작·종료 시각과 RDS·DB pool·Redis 지표를 수집하는 후속 조건은 [#207](https://github.com/team-youngkk/masit-on/issues/207)에 남긴다.

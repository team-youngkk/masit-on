---
status: Implemented
verification_status: pending_operational_run
issue: 207
related_documents:
  - issue-190-operational-performance-result.md
  - ../01-requirements/non-functional-requirements.md
  - ../05-specs/api/discovery/natural-language-restaurant-discovery-api.md
  - ../07-adr/quality/perf-001-k6-load-testing.md
  - ../07-adr/quality/perf-002-operational-participant-load-testing.md
  - ../troubleshooting/pr-208-operational-performance-review.md
---

# 자연어 검색 부하 검증 모델

## 1. 문서 목적

자연어 검색 API의 요청 제한 정책과 부하 검증 모델이 충돌한 문제를 정리하고, 두 검증을 어떤 기준으로 나눠 실행하는지 기록한다. 이슈 [#207](https://github.com/team-youngkk/masit-on/issues/207)의 결과 문서이며, [이슈 #190 운영 검증 결과](issue-190-operational-performance-result.md) 3.2절이 남긴 후속 조건을 받는다.

이 문서는 요청 제한 정책을 바꾸지 않는다. production trusted proxy 설정, rate-limit 상한, 외부 provider quota는 측정 편의를 위해 완화하지 않는다.

이 문서는 [#190 결과](issue-190-operational-performance-result.md) 3.2절의 "rate-limit 준수 저속 프로필 또는 승인된 다중 client 환경이 준비된 뒤 다룬다"는 서술을 좁힌다. 저속 프로필은 정상 throughput 측정을 대신할 수 없다. 근거는 3절에 있다.

재측정 결과는 이 문서에 절을 추가해 기록한다. frontmatter의 `verification_status`를 그때 함께 갱신한다.

## 2. 해결한 문제

[#190](https://github.com/team-youngkk/masit-on/issues/190)의 자연어 검색 정상 부하 실행은 전체 요청 7,202건 중 200 응답이 419건이었고 `http_req_failed{phase:measured}`가 95.00%였다. 원인은 애플리케이션 결함이 아니라 검증 부하 모델과 요청 제한 정책의 충돌이다.

- 자연어 검색은 client address별로 요청을 제한한다([RedisNaturalLanguageRateLimitStore](../../src/main/java/com/masiton/restaurant/infrastructure/redis/RedisNaturalLanguageRateLimitStore.java)).
- 운영 EC2의 loopback 부하 생성기는 모든 요청이 같은 client address로 집계된다.
- 따라서 단일 client에서 20 RPS를 걸면 제한을 넘는 요청이 429로 응답한다. 이것은 정책이 설계대로 동작한 결과다.

여기에 계측 결함이 겹쳤다. 당시 시나리오의 자연어 arm에는 429 전용 counter가 없었다. 코스 arm에는 `course_service_rate_limit_responses`가 있었지만 자연어 arm에는 대응 지표가 없어, 예상하지 않은 응답 6,783건을 사후에 rate-limit으로 확정하지 못했다. 별도 61건 제어 probe로 `200 × 60`, `429 × 1`을 확인해 정황을 맞췄을 뿐이다.

## 3. 두 검증의 분리

`perf/k6/third-expansion-load.js`의 `SCENARIO=public-read`를 `PUBLIC_READ_MODE`로 나눈다.

| 항목 | `contract` (기본값) | `throughput` |
|---|---|---|
| 목적 | 요청 제한 아래에서 계약과 지연을 관찰 | 제한을 넘긴 포화 거동 관찰 |
| 요청률 | 기본 30건/분 (상한 59건/분) | 공용 LOAD 프로필의 20 / 80 RPS |
| 동시성 | 기본 5 VU | 50 / 200 VU |
| warmup 구간 | `WARMUP_DURATION` 기본 60초 | `WARMUP_DURATION` 기본 60초 |
| 측정 구간 | `NATURAL_LANGUAGE_CONTRACT_MEASURED_DURATION` 기본 10분 (기본값에서 300 표본). 공용 `MEASURED_DURATION`은 적용되지 않는다 | `MEASURED_DURATION` 기본 5분 |
| p95 threshold | `< 800ms` 적용 | **적용하지 않음** |
| 429 threshold | 측정 구간 `count == 0`. warmup 429는 직전 실행의 잔여 창일 수 있어 요약에만 남긴다 | 적용하지 않음 (429가 정상) |
| `http_req_failed` threshold | `< 1%` 적용 | **적용하지 않음** |
| `server_error_rate`·`dropped_iterations` | 적용 | 적용 |

**두 모드 모두 `NFR-PERFORMANCE-007`의 성능 인증이 아니다.** 해당 요구사항의 검증 방법은 정상 부하 50명·20 RPS와 최대 부하 200명·80 RPS 부하 테스트로 확정돼 있다([NFR-PERFORMANCE-007](../01-requirements/non-functional-requirements.md#nfr-performance-007-자연어-검색과-경로-응답-시간)). 단일 client source에서는 그 요청률을 요청 제한 안에서 만들 수 없다.

- `contract`는 요청률이 요구사항의 부하 조건에 미치지 못하므로 인증이 아니다. 계약 준수와 저부하 지연의 관찰 증거로만 쓴다.
- `throughput`은 요청률은 맞지만 표본 대부분이 429여서 지연 지표가 성능을 대표하지 못하므로 인증이 아니다. 서버 오류 확산과 부하 생성기 포화 여부만 본다.

시나리오는 이 경계를 요약 출력 첫머리에 직접 출력한다. artifact만 읽는 사람이 `[통과]` 표기를 인증으로 오해하지 않게 하려는 것이다.

자연어 검색의 정식 `NFR-PERFORMANCE-007` 성능 인증은 다중 client source가 확보된 환경에서만 성립한다. 그 환경은 이 이슈 범위 밖이며 별도 승인·비용 판단이 필요하다.

### 3.1 요청률 상한의 근거

`contract`의 요청률 상한은 구현 상수를 복제한 측정 전용 값이다. 구현은 client address별 60초 창에 60건을 허용한다. 상한과 같은 값으로 실행하면 고정 창 경계에서 429가 발생할 수 있으므로 k6 knob의 상한을 59건/분으로 두고 기본값을 30건/분로 잡았다.

이 수치는 응답 계약이 아니다. API 계약은 429 `NATURAL_LANGUAGE_RATE_LIMITED`만 정의하며 허용 건수를 노출하지 않는다([자연어 검색 API](../05-specs/api/discovery/natural-language-restaurant-discovery-api.md)). 구현 상수가 바뀌면 시나리오의 상수도 같은 PR에서 맞춘다. 코스 arm이 Mobility monthly quota를 다루는 방식과 같은 취급이다.

## 4. 실행 명령

측정 대상 환경은 [ADR-PERF-002](../07-adr/quality/perf-002-operational-participant-load-testing.md)의 운영 직접 실행 경계를 따른다. 실제 Kakao·YouTube·Gemini 유료 호출은 포함하지 않는다.

GitHub-hosted 보조 워크플로는 `BASE_URL`만 검증하며 대상 애플리케이션의 외부 provider가 WireMock을 향하는지 원격으로 증명하지 않는다. 따라서 `third-expansion` 또는 `all`을 실행하기 전 대상 환경의 provider base URL이 WireMock임을 운영자가 확인해야 한다. 이를 확인할 수 없으면 코스 arm을 실행하지 않고, ADR-PERF-002의 승인된 SSM 실행 또는 별도 격리 환경을 사용한다.

계약 검증을 실행한다.

```bash
k6 run -e SCENARIO=public-read -e PUBLIC_READ_MODE=contract -e BASE_URL=http://127.0.0.1:8080 -e RESULT_DIR=results-natural-contract perf/k6/third-expansion-load.js
```

포화 거동을 관찰한다.

```bash
k6 run -e SCENARIO=public-read -e PUBLIC_READ_MODE=throughput -e LOAD_PROFILE=normal -e BASE_URL=http://127.0.0.1:8080 -e RESULT_DIR=results-natural-throughput perf/k6/third-expansion-load.js
```

seed는 측정 환경에 따라 다르다. 일반 측정 환경은 `perf/seed/`, 운영 직접 검증은 `perf/operational-fixture/`를 사용한다.

저장소의 [성능 워크플로](../../.github/workflows/performance.yml)도 같은 두 모드를 쓴다. `public_read_mode` 입력으로 모드를 고르며, 결과 경로와 artifact 이름에 모드가 들어간다. 자연어 arm은 `load_profile`만으로 실행 조건이 결정되지 않는다. 러너 한 곳에서 보내는 요청은 같은 client address로 집계되므로, `load_profile=max`를 골라도 `contract` 모드는 저속으로 실행된다.

## 5. 결과에 남겨야 하는 항목

[#190](issue-190-operational-performance-result.md) 4절이 남긴 증적 부족을 반복하지 않기 위해, 재측정 결과에는 다음을 모두 보존한다. 하나라도 빠지면 결과는 부분 관찰로만 분류하고 용량 승인이나 `ADR-PERF-001` 성능 인증으로 승격하지 않는다([ADR-PERF-002](../07-adr/quality/perf-002-operational-participant-load-testing.md) 3절).

- SSM 명령 ID와 k6 실행의 시작·종료 시각(KST). #190은 요청 시각과 k6 내부 실행 시간만 남아 절대 종료 시각을 고정하지 못했다. 이 저장소는 공개돼 있으므로 SSM 명령 ID와 EC2 인스턴스 ID는 `<...>` 자리표시자로 마스킹하고 실제 값은 AWS 콘솔에서 확인한다([운영 프로비저닝 기록](m2-provisioning-record.md) 1절과 같은 규칙이다).
- 각 artifact의 SHA-256.
- RDS CloudWatch 시계열, DB connection pool, Redis command·error·메모리 지표.
- 두 모드의 summary를 각각 다른 `RESULT_DIR`에 분리 보존.
- 429 건수와 `rate-limit으로 설명되지 않는 200 아닌 응답` 건수. 후자가 0이 아니면 rate-limit 외의 원인이 있다는 뜻이므로 별도로 조사한다.

`contract` 실행 결과는 `NFR-PERFORMANCE-007` 인증과 분리해 기록하고, 표준 `PERF-001` 인증 상태는 이 실행으로 바뀌지 않는다.

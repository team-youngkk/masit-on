---
status: HOLD
decision: NO-GO
decision_date: 2026-08-13
issue: 166
task: E3-T13
baseline_commit: 47b90c6
evidence_fingerprint: f6cc13d6d89a7f1b21455c9ce45275a646506c5f88b3e85e347ae0dceb53b57b
evidence_manifest: third-expansion-evidence-manifest.txt
evidence_fingerprint_scope: "manifest에 고정한 E3-T13 구현·추적표 파일; manifest와 이 결과 문서 자체는 집계에서 제외"
evidence_captured_at: 2026-08-13T14:42:00+09:00
related_documents:
  - third-expansion-task-breakdown.md
  - third-expansion-test-matrix.md
  - third-expansion-ai-evaluation-result.md
  - third-expansion-browser-verification.md
  - third-expansion-evaluation-strategy.md
  - ../01-requirements/non-functional-requirements.md
  - ../04-product/traceability.md
  - ../05-specs/api-traceability.md
  - ../05-specs/data/data-traceability.md
  - ../07-adr/adr-traceability.md
  - ../07-adr/quality/perf-001-k6-load-testing.md
---

# 맛잇온 3차 확장 최종 게이트 판정

## 1. 판정 요약

이 문서는 [이슈 #166](https://github.com/team-youngkk/masit-on/issues/166)의 E3-T13 실행 결과를 기록한다. 기준선 비교 커밋은 `47b90c6`이며, 아래 결과는 해당 커밋에 현재 E3-T13 작업 트리를 적용한 검증 상태다. 코드·계약 테스트와 부하 시나리오 자산은 확인했지만, 운영·평가 증거가 모두 모이지 않아 3차 확장 기능 활성화는 **`NO-GO`**로 판정한다.

`NO-GO`는 구현을 되돌린다는 뜻이 아니라, 실제 증거가 없는 기능을 운영에서 활성화하지 않는다는 뜻이다. AI Worker와 Gemini/Mobility 호출은 기존 fail-closed 설정을 유지한다.

## 2. 판정 기준과 선행 Task

| 항목 | 기준 | 현재 판정 | 근거 |
|---|---|---|---|
| 보안·개인정보·로그 | `TST-E3-SEC-001`, Critical 0건 | `PASS` (자동화 범위; 운영 증거는 `HOLD`) | E3 보안·AI·운영 설정 회귀 테스트 |
| AI 품질 | Release holdout 24건 실제 실행·인간 판정·Critical 0건 | `HOLD` | [AI 평가 보류 기록](third-expansion-ai-evaluation-result.md) |
| 브라우저·접근성 | E3-T12 전체 여정과 지원 환경 증거 | `HOLD` | [브라우저 검증 기록](third-expansion-browser-verification.md) |
| 자연어·코스 성능 | `TST-E3-PERF-001`, 정상 50/20·최대 200/80 | `HOLD` | 시나리오 준비, 운영 동급 실측 미실행 |
| 좌표 보강률 | 운영 ACTIVE·공개 맛집 읽기 전용 집계·조치·재측정 | `HOLD` | 운영 DB 읽기 전용 측정 미실행 |
| Worker·Gemini 운영 | 단일 EC2 자원·backlog·재기동·quota | `HOLD` | 운영 계정·환경 측정 미실행 |
| Mobility 운영 | 계정·quota·호출·timeout·비용 hard stop | `HOLD` | 운영 계정·환경 측정 미실행 |
| 네 추적표 | 제품·API·데이터·ADR과 실제 증거 연결 | `HOLD` | 범위 연결은 있으나 실행 ID·운영 결과 미연결 |

선행 Task인 E3-T08·E3-T11·E3-T12는 구현·자동화 또는 부분 브라우저 증거가 있으나, 이 문서의 보류 항목을 해소하지 않는다. Task ID나 이슈 종료만으로 최종 완료를 선언하지 않는다.

## 3. 실행한 검증

### 3.1 자동화 회귀

다음 명령을 `47b90c6`에 현재 E3-T13 변경을 적용한 working tree에서 실행했고 통과했다.

```text
.\gradlew.bat test --tests "com.masiton.security.*" --tests "com.masiton.ai.*" --tests "com.masiton.restaurant.application.naturallanguage.*" --tests "com.masiton.restaurant.application.course.*" --tests "com.masiton.restaurant.infrastructure.redis.RedisCourseRouteQuotaIntegrationTest" --tests "com.masiton.deployment.AppRunScriptContractTest" --no-daemon --console=plain
```

검증 범위는 관리자·회원 보안 경계, 악성 입력과 원문/비밀정보 보존 경계, AI 후보·원자성·Worker lease/quota·YouTube 감시, 자연어·코스 애플리케이션 경계, Redis Mobility quota, 운영 SSM 전달 계약이다. 예상하지 못한 Worker 예외의 sentinel 원문·cause가 일반 로그에 남지 않는 회귀 테스트도 통과했다. 이 결과는 자동화 범위의 통과 증거이며 실제 Gemini/Mobility 계정 또는 운영 자원 검증을 대체하지 않는다.

### 3.2 부하 시나리오 자산

- `perf/k6/normal-load-public-read.js`에 `LOAD_PROFILE=normal|max`를 추가해 50/20과 200/80을 선택할 수 있게 했다.
- `perf/k6/third-expansion-load.js`에 자연어 검색과 코스 경로를 별도 시나리오로 분리하고 p95·오류율 threshold를 추가했다. 코스 내부 관측(`internal`, 무지연 WireMock)과 외부 호출 포함(`external`, 지연 WireMock)은 같은 실행에서 섞지 않고 별도 프로필로 실행한다. 코스 실행은 preflight·warmup·measured 합계가 Mobility monthly quota 1,000 미만인 측정 전용 예산으로 제한하며, production quota와 requests-per-second 설정을 변경하지 않는다. 외부 포함 요청은 단 한 건이라도 5초를 넘으면 `course_timeout_violations == 0` threshold로 실패하고, provider 차단·서비스 rate-limit 응답도 별도 counter로 실패시킨다.
- `.github/workflows/performance.yml`은 기존 `workflow_dispatch` 전용을 유지하며 공개 조회·자연어·코스 시나리오별 결과 artifact를 분리한다. 실행 전 `third-expansion-evidence-manifest.txt`의 파일별 SHA-256과 aggregate를 검증한다.
- 로컬 k6 inspect와 JavaScript 구문 검사, `git diff --check`는 통과했다.

실제 판정에는 ADR-PERF-001에 따라 측정 전용 EC2·RDS, 초기 기준 데이터, WireMock Stub, k6 v2.1.0이 필요하다. 현재 실행 결과 JSON·수치·artifact는 없으므로 성능 통과로 기록하지 않는다.

## 4. 남은 차단 조건

1. AI Release holdout 24건을 명시적 opt-in으로 실행하고 지정 인간 판정자·검증자의 합의를 기록한다. 실제 제공자 품질 수치나 합성 dry-run을 Release 통과로 대체하지 않는다.
2. 병합된 최신 HEAD에서 E3-T12의 Edge 여정·관리자 신규 화면·Webhook 여정과 지원 범위 내 브라우저 검증을 재실행한다. 실단말·배포 환경 검증은 실행하지 않았다면 미검증으로 남긴다.
3. 측정 전용 환경에서 자연어와 기존 공개 조회를 정상 `50/20`, 최대 `200/80`으로 실행한다. 코스는 Kakao Mobility production monthly quota `1,000`과 requests-per-second 기본값 `20`을 전제로 별도 quota-safe 실행을 한다. 실행 전에 KST 기준 현재 `YearMonth`의 Redis quota 키(`restaurant:course-route:quota:<YearMonth>`)를 읽기 전용으로 확인하고, 그 키를 권위 있는 사용량 기준으로 삼아 `남은 quota >= preflight·warmup·measured 실행 예산`일 때만 시작한다. `masiton.restaurant.course.route.monthly.quota.usage`·`masiton.restaurant.course.route.monthly.quota.remaining` 지표는 해당 Redis 키와 대조한 경우에만 보조 증거로 사용한다. 월별 키는 `YearMonth`로 누적되며 provider 호출 전에 permit을 소비하므로, 이후 timeout·429·5xx가 발생한 요청과 같은 달의 재시도·재실행·다른 트래픽도 잔여량을 차감한다. 잔여량이 부족하면 월별 quota 키를 임의로 초기화하지 말고, quota 키가 격리된 측정 전용 환경에서만 실행하거나 다음 달로 연기한다. 실행마다 시작·종료 사용량, 잔여량, 실제 요청 예산과 재실행 여부를 기록한다. preflight·warmup·measured 합계는 quota 미만이어야 하고, 코스 max 프로필도 provider 제한을 넘지 않는 별도 요청률을 사용하므로 `80 RPS` 전체 코스 부하의 증거로 해석하지 않는다. 코스에서 provider 차단·429 rate-limit 응답이 발생하면 성능 통과가 아니라 운영 선행조건 미충족으로 판정한다. threshold를 낮추거나 운영 인스턴스에 직접 부하를 걸지 않는다.
4. 운영 DB에서 ACTIVE·공개 맛집의 좌표 보유율을 읽기 전용으로 집계하고, 부족하면 좌표 소유 Workstream의 조치 후 동일 쿼리로 재측정한다.
5. 단일 EC2 Worker의 CPU·메모리·DB·backlog·처리시간·재기동과 Gemini/Mobility quota·비용 hard stop 증거를 비밀정보 없이 기록한다.
6. 위 결과의 실행 커밋·명령·환경·artifact 식별자를 제품·API·데이터·ADR 추적표와 연결한다. 현재는 k6 `inspect`·JavaScript 구문/차이 검토만 수행했으며 실제 setup/request 실행 결과는 없으므로 성능 통과로 기록하지 않는다.

## 5. 활성화 판정

현재 판정은 다음과 같다.

```text
자연어 검색: HOLD (운영 동급 성능·최종 운영 증거 미실행)
AI 영상 추출: HOLD (Release holdout·인간 판정 미완료)
맛집 코스 추천: HOLD (Mobility 운영 quota·좌표·성능 미검증)
3차 확장 전체: NO-GO
```

보류 조건이 모두 해소되고 재측정 결과가 추적표에 연결된 뒤에만 E3-T13을 `GO`로 갱신한다. 그 전에는 `develop → main` 승격이나 기능 활성화 판정을 이 문서의 근거로 진행하지 않는다.

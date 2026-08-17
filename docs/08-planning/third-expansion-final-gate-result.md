---
status: CONDITIONAL
decision: PROMOTE-AND-VERIFY
decision_date: 2026-08-13
decision_revised_date: 2026-08-13
issue: 166
task: E3-T13
baseline_commit: 47b90c6
evidence_fingerprint: 63be4f9e7cb8b8c63aaab0cd672982eb502e6b23dde8490f89927b9a4667a926
evidence_manifest: third-expansion-evidence-manifest.txt
evidence_fingerprint_scope: "manifest에 고정한 E3-T13 구현·추적표 파일; manifest와 이 결과 문서 자체는 집계에서 제외"
evidence_captured_at: 2026-08-13T14:42:00+09:00
evidence_recaptured_at: 2026-08-17T15:56:05+09:00
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
  - ../07-adr/quality/perf-002-operational-participant-load-testing.md
---

# 맛잇온 3차 확장 최종 게이트 판정

## 1. 판정 요약

이 문서는 [이슈 #166](https://github.com/team-youngkk/masit-on/issues/166)의 E3-T13 실행 결과를 기록한다. 기준선 비교 커밋은 `47b90c6`이며, 아래 결과는 해당 커밋에 현재 E3-T13 작업 트리를 적용한 검증 상태다. 코드·계약 테스트와 부하 시나리오 자산은 확인했지만 운영·평가 증거가 모두 모이지 않아, 최초 판정은 3차 확장 기능 활성화를 **`NO-GO`**로 기록했다.

최초 판정은 구현을 되돌린다는 뜻이 아니라, 실제 증거가 없는 기능을 운영에서 활성화하지 않는다는 뜻이었다. 그 판정은 2026-08-13에 아래 1.1절로 개정했다.

## 1.1. 판정 개정 — 승격·배포·활성화 분리

최초 판정은 세 가지를 하나로 묶어 `NO-GO`로 처리했다. 그 결과 순환이 생겼다.

| 행위 | 뜻 |
|---|---|
| 승격 | `develop`을 `main`에 병합해 릴리즈 기준선을 옮긴다 |
| 배포 | 그 코드를 운영 EC2에서 실행한다 |
| 활성화 | 기능 플래그를 켜 외부 제공자 호출과 화면 노출을 연다 |

4절의 남은 조건 여섯 항목 중 **1·4·5번은 운영 환경에서 실행해야만 만들 수 있는 증거**다. AI Release holdout(1번)은 운영 Gemini 계정, 좌표 보강률(4번)은 운영 DB, Worker 자원 측정과 Gemini·Mobility quota(5번)는 운영 EC2와 운영 제공자 계정을 요구한다. 6번(추적표 연결)은 그 결과에 종속된다. 2번(브라우저 재검증)과 3번(측정 전용 환경 부하)은 운영 환경이 아니어도 실행할 수 있다.

3차 확장 코드가 운영에 없으면 1·4·5번 증거를 만들 수단 자체가 없고, 증거가 없으면 배포할 수 없다는 규칙이 걸려 있었다.

증거를 요구하는 규칙이 증거 수집을 막고 있었으므로 규칙을 고친다. **판정 대상을 코드 승격이 아니라 기능 활성화 범위로 좁힌다.**

- `develop → main` 승격과 운영 배포는 이 문서의 미해소 항목을 이유로 막지 않는다.
- 제한 공개 범위에서 증거 수집을 위한 기능 활성화를 허용한다. 접근자는 검증 참여자로 한정되며 일반 사용자에게 노출되지 않는다.
- **일반 공개(`v1.0.0`) 전에는 4절의 항목을 모두 해소한다.** 제한 공개 해제는 이 문서가 `GO`로 갱신된 뒤에만 진행한다.
- 측정 중 Critical 결함이 확인되면 해당 기능 플래그를 즉시 `false`로 되돌린다. 플래그는 Parameter Store 값이므로 재배포 없이 컨테이너 재기동만으로 반영된다. **단 자연어 검색에는 대응 플래그가 없어 이 즉시 롤백 수단이 적용되지 않는다**(5절 참조).
- **활성화 이전 조건은 이 개정으로 옮기지 않는다.** [NFR-COST-001](../01-requirements/non-functional-requirements.md#nfr-cost-001-ai임베딩mobility-호출-비용-상한)의 Free Tier quota 확인과 결제 미연결 확인은 외부 연동 활성화 **전에** 끝나야 하며, 4절의 배포 후 수집 대상이 아니다(4절 서문 참조).
- **승격과 `v0.2.0` 태그는 릴리즈 기준선 이동이며 [NFR-TEST-006](../01-requirements/non-functional-requirements.md#nfr-test-006-3차-확장-품질과-완료-게이트)의 3차 확장 단계 완료 선언이 아니다.** 단계 완료는 4절 조건이 모두 해소되고 정식 판정이 보류된 2차 확장 최대 부하(200/80) 결과가 같은 완료 증거에 포함된 시점에 선언한다. [테스트 매트릭스](third-expansion-test-matrix.md) 5절과 [Task 분해](third-expansion-task-breakdown.md)의 미체크 항목이 남아 있는 동안 완료를 선언하지 않는다는 규칙은 그대로다.

이 개정은 판정 기준을 낮추는 것이 아니라 판정 시점을 옮기는 것이다. [3차 확장 테스트 매트릭스](third-expansion-test-matrix.md) 5절의 "팀이 측정을 연기한 항목은 보류 사유와 해제 조건을 기록하되 판정 기준을 낮추지 않는다"를 그대로 따른다. 4절의 수치와 통과 조건은 하나도 완화하지 않는다.

이번 정합성 수정으로 2차 부하 상태 문장과 3차 증거 표를 갱신했고, 변경 파일은 증적 manifest에 SHA-256으로 고정돼 있다. 그래서 manifest의 파일 해시와 aggregate, 이 문서의 `evidence_fingerprint`를 현재 HEAD 기준으로 다시 계산했다. 재계산하지 않으면 [성능 측정 워크플로](../../.github/workflows/performance.yml)의 증적 검증 단계가 영구히 실패한다. manifest 상단에 재계산 시각과 사유를 남겼고, 파일 목록과 검증 알고리즘은 바꾸지 않았다.

연기로 남는 위험을 명시한다. 제한 공개 기간 동안 검증 참여자는 품질이 확정되지 않은 AI 추출 결과와 코스 경로를 보게 된다.

**AI 추출에는 관리자 사전 검수 단계가 없다.** [범위 5.3.2](../00-overview/scope.md#532-ai-기반-영상-정보-추출)의 공개 경계는 "관리자 승인 없이도 자동 검증을 모두 통과한 결과는 정식 Entity·`VisitTag`로 생성·공개한다"이고, 구현도 같다 — `AiCandidateValidator`가 `AUTO_CONFIRMED`로 판정하면 `AiExtractionResultCommitService`가 관리자 개입 없이 정식 등록한다. 따라서 안전 경계는 관리자 검수가 아니라 **자동 검증**이다. 검증 실패·불확실·중복·근거 부족 결과만 자동 보류되고 공개되지 않는다.

이 경계에서 남는 위험은 두 가지다. 자동 검증을 통과했으나 실제로는 틀린 추출은 검증 참여자에게 그대로 공개되고, 그 오탐률은 4절 1번(Release holdout)을 측정하기 전까지 알 수 없다. 롤백은 사전 차단이 아니라 사후 조치다 — 관리자가 `ROLLBACK` 결정으로 자동 등록분을 되돌리거나(`AdminAiExtractionQueryService`), Critical 결함이면 `/masiton/ai/gemini/enabled`를 `false`로 내려 신규 추출을 멈춘다. 이미 공개된 결과는 플래그를 내려도 사라지지 않으므로 개별 롤백이 필요하다.

## 2. 판정 기준과 선행 Task

| 항목 | 기준 | 현재 판정 | 근거 |
|---|---|---|---|
| 보안·개인정보·로그 | `TST-E3-SEC-001`, Critical 0건 | `PASS` (자동화 범위; 운영 증거는 `HOLD`) | E3 보안·AI·운영 설정 회귀 테스트 |
| AI 품질 | Release holdout 24건 실제 실행·인간 판정·Critical 0건 | `HOLD` | [AI 평가 보류 기록](third-expansion-ai-evaluation-result.md) |
| 브라우저·접근성 | E3-T12 전체 여정과 지원 환경 증거 | `HOLD` | [브라우저 검증 기록](third-expansion-browser-verification.md) |
| 자연어·코스 성능 | `TST-E3-PERF-001`, 정상 50/20 Verified·최대 200/80 정식 판정 | `HOLD` | 정상 부하는 [2차 확장 성능 검증 결과](second-expansion-performance-verification.md)에 기록했으며, 최대 부하 정식 측정은 보류 |
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

기본 판정에는 ADR-PERF-001에 따른 측정 전용 EC2·RDS, 초기 기준 데이터, WireMock Stub, k6 v2.1.0이 필요하다. 이슈 #190에는 ADR-PERF-002에 따라 검증 참여자 전용 운영 fixture와 운영 인스턴스 직접 측정을 적용할 수 있지만, 이는 운영 회귀·용량 관찰 증거이며 기준 데이터 규모에 대한 독립 성능 인증으로 해석하지 않는다. #190 운영 직접 관찰의 실행 수치와 결과 JSON SHA-256은 [운영 검증 결과 문서](issue-190-operational-performance-result.md)에 기록했으며, 표준 PERF-001 성능 통과로 승격하지 않는다.

## 4. 남은 조건

1.1절 개정에 따라 이 항목들은 **승격·배포를 막는 조건이 아니라 제한 공개 해제를 막는 조건**이다. 수치와 통과 기준은 최초 판정 그대로이며 하나도 완화하지 않았다. 각 항목은 3차 확장이 운영에 배포된 뒤 수집한다.

**단 5번 항목 중 Gemini·Mobility의 Free Tier quota 확인과 결제 미연결 확인은 예외다.** [NFR-COST-001](../01-requirements/non-functional-requirements.md#nfr-cost-001-ai임베딩mobility-호출-비용-상한)(Critical·확정)은 "무료 quota·계약·결제 미연결 상태를 확인할 수 없으면 호출하지 않는다", "해당 설정을 검증하지 못하면 기능을 비활성화해야 한다"고 정하고, 비기능 요구사항 18절 측정 계획도 이 항목의 검증 시점을 "외부 연동 활성화 전"으로 고정한다. 코드도 같은 순서를 강제한다 — `GeminiProviderProperties`와 `KakaoMobilityProperties`는 `enabled=true`인데 `free-tier-verified=false`면 애플리케이션 기동을 실패시킨다. 따라서 이 확인은 **활성화 이전 조건**이며 배포 후 수집 대상이 아니다. 5번 항목에서 활성화 이후에 수집하는 것은 단일 EC2 Worker의 CPU·메모리·DB·backlog·처리시간·재기동 측정과 실사용 quota 소진·hard stop 동작 증거뿐이다.

1. AI Release holdout 24건을 명시적 opt-in으로 실행하고 지정 인간 판정자·검증자의 합의를 기록한다. 실제 제공자 품질 수치나 합성 dry-run을 Release 통과로 대체하지 않는다.
2. 병합된 최신 HEAD에서 E3-T12의 Edge 여정·관리자 신규 화면·Webhook 여정과 지원 범위 내 브라우저 검증을 재실행한다. 실단말·배포 환경 검증은 실행하지 않았다면 미검증으로 남긴다.
3. 측정 전용 환경에서 자연어와 기존 공개 조회의 최대 `200/80`을 실행·판정한다. 정상 `50/20`은 [2차 확장 성능 검증 결과](second-expansion-performance-verification.md)의 `Verified` 증거를 재사용한다. 코스는 Kakao Mobility production monthly quota `1,000`과 requests-per-second 기본값 `20`을 전제로 별도 quota-safe 실행을 한다. 실행 전에 KST 기준 현재 `YearMonth`의 Redis quota 키(`restaurant:course-route:quota:<YearMonth>`)를 읽기 전용으로 확인하고, 그 키를 권위 있는 사용량 기준으로 삼아 `남은 quota >= preflight·warmup·measured 실행 예산`일 때만 시작한다. `masiton.restaurant.course.route.monthly.quota.usage`·`masiton.restaurant.course.route.monthly.quota.remaining` 지표는 해당 Redis 키와 대조한 경우에만 보조 증거로 사용한다. 월별 키는 `YearMonth`로 누적되며 provider 호출 전에 permit을 소비하므로, 이후 timeout·429·5xx가 발생한 요청과 같은 달의 재시도·재실행·다른 트래픽도 잔여량을 차감한다. 잔여량이 부족하면 월별 quota 키를 임의로 초기화하지 말고, quota 키가 격리된 측정 전용 환경에서만 실행하거나 다음 달로 연기한다. 실행마다 시작·종료 사용량, 잔여량, 실제 요청 예산과 재실행 여부를 기록한다. preflight·warmup·measured 합계는 quota 미만이어야 하고, 코스 max 프로필도 provider 제한을 넘지 않는 별도 요청률을 사용하므로 `80 RPS` 전체 코스 부하의 증거로 해석하지 않는다. 코스에서 provider 차단·429 rate-limit 응답이 발생하면 성능 통과가 아니라 운영 선행조건 미충족으로 판정한다. threshold를 낮추거나 운영 인스턴스에 직접 부하를 걸지 않는다.
4. 운영 DB에서 ACTIVE·공개 맛집의 좌표 보유율을 읽기 전용으로 집계하고, 부족하면 좌표 소유 Workstream의 조치 후 동일 쿼리로 재측정한다.
5. 단일 EC2 Worker의 CPU·메모리·DB·backlog·처리시간·재기동과 Gemini/Mobility quota·비용 hard stop 증거를 비밀정보 없이 기록한다.
6. 위 결과의 실행 커밋·명령·환경·artifact 식별자를 제품·API·데이터·ADR 추적표와 연결한다. #190 운영 직접 관찰은 실행 커밋·SSM 명령·summary SHA-256과 함께 [결과 문서](issue-190-operational-performance-result.md)에 연결했지만, 표준 PERF-001 측정 전용 환경 결과가 아니므로 성능 통과로 기록하지 않는다.

## 5. 활성화 판정

현재 판정은 다음과 같다. 판정 축이 셋이므로 기능별로 나눠 기록한다.

```text
코드 승격·운영 배포: GO (세 기능 공통, v0.2.0 태그는 단계 완료 선언이 아님)

제한 공개 활성화
  자연어 검색:    GO (증거 수집 목적, 전용 플래그 없음 — 즉시 롤백 불가)
  AI 영상 추출:   GO (증거 수집 목적, 자동 검증 경유 — 관리자 사전 승인 없음)
  맛집 코스 추천: GO (증거 수집 목적, quota·결제 미연결 확인 선행)

일반 공개(v1.0.0)
  자연어 검색:    HOLD (표준 측정 전용 환경 미실행; 운영 직접 관찰은 rate-limit 충돌로 BLOCKED, 후속 [#207](https://github.com/team-youngkk/masit-on/issues/207))
  AI 영상 추출:   HOLD (Release holdout·인간 판정 미완료)
  맛집 코스 추천: HOLD (Mobility 운영 quota·좌표·성능 미검증)
  3차 확장 전체:  HOLD
```

승격과 배포는 이 문서의 미해소 항목을 이유로 막지 않는다. 4절 조건이 모두 해소되고 재측정 결과가 추적표에 연결된 뒤에 이 문서를 `GO`로 갱신하며, **제한 공개 해제는 그 갱신 뒤에만 진행한다.**

### 5.1 활성화 플래그

기능 활성화 상태는 Parameter Store 플래그가 권위 있는 기준이다. [`deploy/scripts/app-run.sh`](../../deploy/scripts/app-run.sh)가 읽는 값은 다음과 같고, 활성화 게이트는 기능당 하나가 아니다.

| 파라미터 | 뜻 | 제한 공개 기간 값 |
|---|---|---|
| `/masiton/ai/gemini/enabled` | Gemini 제공자 호출을 연다 | 증거 수집 시 `true` |
| `/masiton/ai/gemini/free-tier-verified` | NFR-COST-001의 quota·결제 미연결 확인이 끝났다는 선언. `enabled=true`인데 이 값이 `false`면 애플리케이션이 기동하지 않는다 | 확인 완료 후에만 `true` |
| `/masiton/ai/gemini/paid-billing-enabled` | 유료 결제 전환. `true`면 기동을 실패시킨다 | 기간 내내 `false` 유지 |
| `/masiton/ai/worker/enabled` | 비동기 추출 Worker를 켠다 | 증거 수집 시 `true` |
| `/masiton/integration/kakao-mobility/enabled` | Mobility 경로 호출을 연다 | 증거 수집 시 `true` |
| `/masiton/integration/kakao-mobility/free-tier-verified` | 위와 같은 선언. `enabled=true`인데 `false`면 기동하지 않는다 | 확인 완료 후에만 `true` |

`free-tier-verified`는 기동을 통과시키기 위한 형식 값이 아니라 quota·결제 미연결을 실제로 확인했다는 선언이다. 확인 없이 `true`로 올리면 NFR-COST-001 위반이다.

**자연어 검색에는 대응하는 Parameter Store 플래그가 없다.** 파서는 사전·규칙 기반(`NaturalLanguageRestaurantParser`)이라 외부 제공자 플래그를 쓰지 않고, `application.yml`의 `masiton` 블록에도 on/off가 없으며, `frontend/app/restaurants/page.tsx`가 `NaturalLanguageRestaurantSearch`를 조건 없이 렌더링한다. 이 기능만은 배포와 활성화가 분리되지 않아 배포 즉시 검증 참여자에게 노출되고, 유일한 보류 사유인 운영 동급 성능([NFR-PERFORMANCE-007](../01-requirements/non-functional-requirements.md#nfr-performance-007-자연어-검색과-경로-응답-시간))에서 문제가 확인돼도 재기동으로 끌 수 없다. **실제 롤백 수단은 해당 커밋 revert 후 재배포 또는 프런트 진입 제거이며, 둘 다 재배포를 요구한다.**

이 문서의 판정과 실제 플래그 값이 어긋나면 플래그를 먼저 맞추고 그 사실을 여기에 기록한다.

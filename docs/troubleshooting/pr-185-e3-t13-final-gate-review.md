---
related_documents:
  - README.md
  - ../08-planning/third-expansion-final-gate-result.md
  - ../08-planning/third-expansion-evidence-manifest.txt
  - ../08-planning/second-expansion-performance-verification.md
  - ../../.github/workflows/performance.yml
  - ../../perf/k6/third-expansion-load.js
  - ../07-adr/integration/route-001-kakao-mobility-course-routing.md
  - ../07-adr/quality/perf-001-k6-load-testing.md
  - pr-172-ai-worker-key-rotation-review.md
  - pr-177-ai-evaluation-review.md
  - pr-179-browser-capture-evidence-review.md
---

# PR #185 E3-T13 최종 게이트 리뷰 반영 기록

이 문서는 [PR #185](https://github.com/team-youngkk/masit-on/pull/185)의 리뷰를 재현하고 반영한 기록이다. 리뷰어가 지적한 문제를 구현 결함, 증적 무결성 결함, 운영 선행조건 결함으로 분류하고, 코드·워크플로·문서에 반영했다.

## 1. 리뷰 범위와 결론

- 대상: E3-T13 최종 게이트 증거, AI Worker 예외 로그, k6 성능 시나리오, performance workflow
- P1: 코스 부하가 Mobility monthly quota와 서비스 요청률을 초과할 수 있어 성능 결과가 quota/rate-limit 실패로 오염될 수 있음
- 공통 원인: 측정 시나리오가 실제 운영 제한과 분리되지 않았고, 실패 원인과 성능 표본의 경계가 충분히 명시되지 않음
- 결론: 운영 설정은 변경하지 않고 코스 시나리오를 quota-safe 별도 실행으로 분리했다. 실제 측정 증거가 없으므로 최종 게이트 판정은 계속 `HOLD`/`NO-GO`다.

## 2. 스레드별 조치

| 분류 | 대상 | 판단 | 조치 |
|---|---|---|---|
| 로그 진단성 | `AiExtractionWorkerService.java` · `PRRT_kwDOTf2xKc6Y1Dwp` | 수정 필요 | poll·execute·heartbeat의 infrastructure 예외에 예외 타입과 stack trace 위치만 기록하고 원문 message·cause는 기록하지 않도록 sanitized diagnostic을 추가했다. 테스트에서 raw/cause sentinel 비노출과 진단성 로그를 함께 확인한다. |
| 실행 조건 | `performance.yml` · `PRRT_kwDOTf2xKc6Y1Dwr` | 수정 필요 | `public-read`·`third-expansion`·`all` 실행 시나리오를 분리하고, 코스 ID는 3차 확장 실행일 때만 검증한다. 자연어·코스도 별도 k6 시나리오로 실행하며 결과 artifact를 경로별로 보관한다. |
| 증적 무결성 | `third-expansion-evidence-manifest.txt` · `PRRT_kwDOTf2xKc6Y1Dwt` | 수정 필요 | 파일별 SHA-256과 LF join aggregate를 다시 계산하고, `verify-third-expansion-evidence.ps1`를 workflow 시작 단계에서 실행한다. manifest와 final-gate 문서는 aggregate에서 계속 제외한다. |
| 중복 추상화 | `perf/k6/third-expansion-load.js` · `PRRT_kwDOTf2xKc6Y1Dww` | 수정 필요 | 부하 프로필, 시나리오 생성, summary 포맷, metric 조회 유틸을 `perf/k6/load-profile.js`로 추출하고 공개 조회·3차 확장 스크립트가 공유한다. |
| 테스트 접근 | `AiExtractionWorkerServiceTest.java` · `PRRT_kwDOTf2xKc6Y1Dwz` | 수정 필요 | `heartbeat`를 package-private로 바꾸고 동일 패키지 테스트에서 직접 호출해 reflection 의존을 제거했다. |
| API 단순화 | `third-expansion-load.js` · `PRRT_kwDOTf2xKc6Y1Dw1` | 수정 필요 | `evaluate`가 단일 Trend만 받도록 바꾸고 호출부도 단일 인자로 정리했다. |
| Mobility P1 | `third-expansion-load.js` · `PRRT_kwDOTf2xKc6Y1KN5` | 수정 필요 | 자연어와 코스를 `SCENARIO`로 분리했다. 코스는 preflight 1 + warmup 최대 200 + measured 최대 600 = 최대 801건으로 제한하고, normal 5 RPS/max 10 RPS와 5초 p95 허용치를 감당하는 기본 VU 26/51을 사용한다. 429·provider 차단 502를 별도 counter로 기록하고 성능 Trend 표본에서는 제외한다. 운영 quota/rate-limit 설정은 변경하지 않는다. |
| 최종 게이트 선행조건 | `third-expansion-final-gate-result.md` · `PRRT_kwDOTf2xKc6Y1KOD` | 수정 필요 | quota-safe 코스 측정, 20 RPS 운영 기본값, max 코스가 80 RPS 전체 코스 부하를 의미하지 않는다는 점, quota/rate-limit 발생 시 성능 통과가 아니라 blocker라는 점, 현재 inspect만 수행했다는 점을 명시했다. |
| 문서 표현 | `normal-load-public-read.js` · `PRRT_kwDOTf2xKc6Y1KOF` | 수정 필요 | 고정 수치 주석을 `LOAD.rate`·`LOAD.vus` 중심으로 일반화했다. |
| 낡은 artifact 경로 | `second-expansion-performance-verification.md` 리뷰 본문 | 수정 필요 | 현재 workflow의 profile·scenario별 artifact 이름과 결과 경로로 문서를 동기화했다. |
| 월별 quota 잔여량 | `third-expansion-final-gate-result.md` · `PRRT_kwDOTf2xKc6Y16m_` | 수정 필요 | Redis `YearMonth` quota 키 또는 usage/remaining 지표를 실행 전에 읽고, 잔여량이 전체 예산 이상일 때만 실행하도록 했다. permit은 provider 호출 전에 소비되므로 timeout·429·5xx와 재시도·재실행도 같은 달 잔여량을 차감하며, 부족하면 키를 초기화하지 않고 격리 환경 또는 다음 달을 사용한다. |
| 복호화 진단 로그 | `AiExtractionWorkerService.java` · `PRRT_kwDOTf2xKc6Y2OKq` | 수정 필요 | retryable `TemporaryInputDecryptionException` 경로에도 예외 타입과 sanitized stack trace만 기록하고 원문 message·cause는 남기지 않도록 보강했다. 기존 key sentinel 테스트에 진단 stack frame 검증을 추가했다. |
| auto-discovery 입력 | `performance.yml` · `PRRT_kwDOTf2xKc6Y2OKu` | 수정 필요 | course ID가 완전히 비어 있거나 공백이면 입력 검증을 건너뛰고 k6 setup의 공개 목록 auto-discovery를 사용한다. 값이 있으면 기존 2~5개·trim·빈 토큰 검증을 유지한다. |
| artifact 증거 분리 | `performance.yml` · `PRRT_kwDOTf2xKc6Y2OKx` | 수정 필요 | natural-language와 course 결과를 별도 upload-artifact step과 artifact 이름으로 분리해 한쪽 결과 누락이 다른 쪽 결과 존재로 가려지지 않게 했다. |
| quota 기준 신뢰성 | `third-expansion-final-gate-result.md` · 독립 재리뷰 P1 | 수정 필요 | 재기동 후 stale할 수 있는 Micrometer gauge가 아니라 KST `YearMonth` Redis quota 키를 권위 있는 사전 점검 기준으로 명시하고, gauge는 키 대조 후 보조 증거로 제한했다. |
| 복호화 원문 회귀 | `AiExtractionWorkerServiceTest.java` · 독립 재리뷰 P2 | 수정 필요 | retryable 복호화 로그가 실제 예외 message(`AI temporary input decryption key is unavailable.`)를 포함하지 않는다는 assertion을 추가했다. |
| auto-discovery 좌표 보장 | `third-expansion-load.js` · 독립 최종 재리뷰 P2 | 수정 필요 | 목록 응답에는 좌표가 없어 첫 ID 자동 선택이 코스 422를 만들 수 있었다. 자동 선택 경로를 좌표가 계약상 포함된 `/api/restaurants/map-points` 응답으로 바꾸고 좌표가 있는 ID만 선택하도록 했다. |

## 3. 검증

다음 검증을 수행했다.

- `.\gradlew.bat test --tests com.masiton.ai.application.AiExtractionWorkerServiceTest` — 12개 통과(복호화 retryable 로그 진단 검증 포함)
- `node --check perf/k6/load-profile.js`
- `node --check perf/k6/normal-load-public-read.js`
- `node --check perf/k6/third-expansion-load.js`
- `k6 inspect` — normal/max 및 public-read/course 프로필 확인
- `git diff --check`
- `powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-third-expansion-evidence.ps1 -RepositoryRoot (Get-Location).Path` — 파일 15개, manifest aggregate와 final-gate `evidence_fingerprint` 대조 통과

실제 측정 대상에 대한 k6 setup/request 실행은 하지 않았다. 최종 AI 워커 지정 테스트 12개는 통과했다. 전체 `test`는 3분 제한에서 완료되지 않아 완료로 보고하지 않는다. 따라서 이 기록은 리뷰 반영과 자동 검증의 증거이며, 운영 성능 통과나 최종 게이트 `GO`를 의미하지 않는다.

## 4. 후속 관리

리뷰 답글에는 해당 스레드의 원인·조치·검증을 간단히 남기고, 코드가 push된 뒤 반영된 스레드를 resolve한다. 이후 실제 측정 시에는 코스의 preflight·warmup·measured 요청 수, 429·502 counter, 대상 환경과 artifact 식별자를 최종 게이트 문서와 추적표에 연결한다.

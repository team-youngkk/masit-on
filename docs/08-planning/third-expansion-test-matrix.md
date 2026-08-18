---
status: In Progress
plan_date: 2026-08-10
implementation_gate: HOLD — E3-T13 운영·평가 증거 수집 중
related_documents:
  - third-expansion-baseline-review.md
  - third-expansion-scope-and-terminology.md
  - third-expansion-evaluation-strategy.md
  - third-expansion-ai-evaluation-result.md
  - third-expansion-implementation-plan.md
  - third-expansion-task-breakdown.md
  - third-expansion-browser-verification.md
  - third-expansion-final-gate-result.md
  - ../01-requirements/functional-requirements.md
  - ../01-requirements/business-rules.md
  - ../01-requirements/non-functional-requirements.md
  - ../04-product/traceability.md
  - ../05-specs/api-traceability.md
  - ../05-specs/data/data-traceability.md
  - ../07-adr/adr-traceability.md
  - ../07-adr/quality/test-001-automation-strategy.md
  - ../07-adr/quality/perf-001-k6-load-testing.md
  - ../07-adr/platform/web-004-supported-browser-matrix.md
---

# 맛잇온 3차 확장 테스트 추적표

## 1. 목적과 판정 규칙

이 문서는 3차 확장 요구사항·비즈니스 규칙·비기능 요구사항을 자동화 테스트, 평가 Dataset, 브라우저 인수와 운영 증거로 연결한다. `TST-E3-*`는 테스트 파일명이 아니라 완료 판정을 위한 검증 묶음 ID다. 실제 테스트 클래스·스크립트·평가 보고서가 해당 묶음과 연결되고 실행 결과가 보존될 때만 묶음을 완료한다.

확률적 품질은 [3차 확장 평가 주도 개발 전략](third-expansion-evaluation-strategy.md)의 `EVAL-*`로 판정하고, 결정론적 API·상태·트랜잭션·보안·외부 장애는 이 문서의 자동화 묶음으로 판정한다. 하나의 성공 사례만으로 완료하지 않으며 정상·예외·경계·동시성·부분 실패를 포함한다.

현재 문서는 실행 기준선이다. `implementation_gate: Pending evidence`는 계약이 미승인이라는 뜻이 아니라, 실제 테스트·평가·운영 증거가 아직 연결되지 않았다는 뜻이다.

## 2. 기능 요구사항 → 테스트 묶음 → Task

| 요구사항 | 핵심 검증 | 테스트 묶음 | 구현 Task |
|---|---|---|---|
| `FR-NLSEARCH-001` | 자연어 입력·맛집명/지역/카테고리/유튜버 조건 해석과 기존 목록 응답 | `TST-E3-NL-001` | `E3-T01`, `E3-T02` |
| `FR-NLSEARCH-002` | 직접 지정 필터 우선, 차원 간 AND, 동일 차원 충돌 요약 | `TST-E3-NL-001` | `E3-T01` |
| `FR-NLSEARCH-003` | 빈 결과·부분 미지원·해석 실패 구분, 전체 목록 대체 금지 | `TST-E3-NL-002` | `E3-T01`, `E3-T13` |
| `FR-NLSEARCH-004` | 활성 `TagDefinition`, 확정 `VisitTag`, 여러 태그 AND와 공개 상태 | `TST-E3-NL-001`, `TST-E3-DATA-001` | `E3-T01`, `E3-T06` |
| `FR-AIEXTRACT-001` | 관리자 작업 접수, URL·보완 텍스트 검증, `202`와 멱등성 | `TST-E3-AI-001` | `E3-T04` |
| `FR-AIEXTRACT-002` | 작업 목록·상세, 부분 결과·실패·페이지·입력 원문 비노출 | `TST-E3-AI-002` | `E3-T07` |
| `FR-AIEXTRACT-003` | 자동 확정·차단·폐기·사후 보정·롤백과 정식 Entity 원자성 | `TST-E3-AI-003`, `TST-E3-DATA-001` | `E3-T06`, `E3-T07` |
| `FR-AIEXTRACT-004` | YouTube 구독 확인·Atom 신규 영상·중복 Webhook·AI 호출 격리 | `TST-E3-AI-001`, `TST-E3-AI-004` | `E3-T04`, `E3-T05` |
| `FR-AIEXTRACT-005` | 관리자 신규 영상과 Webhook 작업 수렴, 입력 경계 | `TST-E3-AI-001` | `E3-T04` |
| `FR-AIEXTRACT-006` | 감시 채널 활성·해지·renewal 실패 상태 | `TST-E3-AI-004` | `E3-T04`, `E3-T05` |
| `FR-AIEXTRACT-007` | 통제 태그 후보·근거·사후 보정·확정 `VisitTag` 공개 경계 | `TST-E3-AI-003`, `TST-E3-DATA-001` | `E3-T06`, `E3-T07` |
| `FR-COURSE-001` | 공개·활성·좌표 보유 맛집 2~5개, 중복·출발점·30km 검증 | `TST-E3-COURSE-001` | `E3-T09` |
| `FR-COURSE-002` | 첫 장소 출발, 최근접 이웃 순서, 구간·전체 거리/시간, 만료 | `TST-E3-COURSE-002` | `E3-T09` |
| `FR-COURSE-003` | timeout·429·5xx·부분 실패·추정값 금지와 기능 격리 | `TST-E3-COURSE-003` | `E3-T10` |

## 3. BR·NFR 교차 검증

`TST-E3-AI-005`~`008`은 `합의 대기` 상태인 계약을 검증 대상으로 삼는다. 합의가 불발되면 네 묶음을 함께 제거한다. 절차와 되돌릴 범위는 [ADR-AI-001 1절](../07-adr/integration/ai-001-video-extraction-candidate-boundary.md)에 있다.

| 테스트 묶음 | 적용 계약 | 필수 계층·증거 | 완료 Task |
|---|---|---|---|
| `TST-E3-NL-001` | `BR-NLSEARCH-001`, `BR-NLSEARCH-002`, `BR-NLSEARCH-003`, `NFR-ACCURACY-001` | parser 단위, API 계약, 기존 목록 통합, 240건 Dataset exact match·재현율 | `E3-T01`, `E3-T02` |
| `TST-E3-NL-002` | `FR-NLSEARCH-003`, `NFR-SECURITY-007`, `NFR-PRIVACY-006` | 미지원·Prompt Injection 유사 입력, 오류 응답, 원문 로그·저장 금지 | `E3-T01`, `E3-T13` |
| `TST-E3-AI-001` | `BR-AIEXTRACT-001`, `BR-AIEXTRACT-005`, `BR-AIEXTRACT-006`, `BR-AIEXTRACT-007` | MockMvc, Webhook 계약, URL·Payload·멱등·보완 텍스트 암호화 | `E3-T04` |
| `TST-E3-AI-002` | `FR-AIEXTRACT-002`, `NFR-OBSERVABILITY-005` | 관리자 API, 페이지·상태·민감정보 마스킹, traceId | `E3-T07` |
| `TST-E3-AI-003` | `BR-AIEXTRACT-002`, `BR-AIEXTRACT-003`, `BR-AIEXTRACT-004`, `BR-AIEXTRACT-008`, `NFR-ACCURACY-002`, `NFR-INTEGRITY-006` | 실패 주입, 정식 Entity 0건, 자동 등록 원자성·멱등·태그 공개 경계 | `E3-T06`, `E3-T07` |
| `TST-E3-AI-008` | `BR-AIEXTRACT-001`, `BR-AIEXTRACT-004` | 후보 300건 응답 수용, 301건 이상 `SCHEMA` 기각, 절삭 표시 응답의 `candidateTruncated` 전파, 후보 수가 상한과 같으면 표시 없어도 `true` 판정, `P8`·`S2` 멱등성 키 분리와 P7 작업 보존 | `E3-T05`, `E3-T07` |
| `TST-E3-AI-005` | `BR-AIEXTRACT-001`, `BR-AIEXTRACT-009` | 다장소 영상 등록 단위 분해, Kakao 검색 WireMock, 상호명·시구 일치 1건 자동 확정, `PLACE_NOT_FOUND`·`PLACE_AMBIGUOUS` 차단, 일부 단위 차단 시 나머지 단위 등록 유지 | `E3-T06`, `E3-T07` |
| `TST-E3-AI-006` | `BR-AIEXTRACT-010` | `food_category_mapping` seed 고정 데이터 기준으로 Kakao 분류 1순위·메뉴 표현 2순위 매핑, 완전일치·부분일치 우선순위, 같은 순위 복수 일치 시 차단, 매핑 실패 시 `CATEGORY_UNRESOLVED` 차단, 기본값 대체 0건, 비활성 행 제외, 관리자 사후 카테고리 보정, 활성 행 부분 unique와 비활성 후 같은 키 재등록, 매핑 변경 뒤에도 `category_decision`으로 과거 판정 재현 | `E3-T06`, `E3-T07` |
| `TST-E3-AI-007` | `BR-AIEXTRACT-011`, `NFR-INTEGRITY-006` | 등록 실행 1회로 맛집·유튜버·영상·방문 관계 4종 등록, 기존 유튜버·영상 재사용, 재요청 멱등·동시 요청 충돌, 중간 실패 시 해당 단위 저장 0건, 외부 호출 중 트랜잭션 미개방, 예외 사유별 보조 입력 전환과 그 밖의 경우 관리자 입력 미요구, `CONFIRM` `supplements`의 사유별 필수·불허 필드와 재검증, 최상위 `reviewStatus` 요약 규칙 3개 분기(Snapshot 없음은 `null`, Snapshot 있고 등록 단위 0개는 Snapshot 판정값 `AUTO_BLOCKED`·`AUTO_REJECTED`, 등록 단위 있음은 혼합 5개 조합), `unitId` 경계값(0개·1개·2개 이상과 타 작업 단위 지정), 작업 상세 `registrationUnits`의 4종 식별자·`reusedResources`·`manualOverrideType` 노출과 상태별 null 규칙, 등록 유지·롤백 완료·폐기 완료 세 하위 상태의 API 판별, 동시 요청 `AIEXTRACT_CONCURRENT_REQUEST_CONFLICT`, 등록 단위 상태 6종별 허용·거절·멱등 결과(폐기 완료 포함), 네 `decision`에 같은 `unitId` 규칙 적용, `DISCARD` 후 `discarded_at` 저장과 이후 모든 요청 거절, `AIEXTRACT_UNIT_NOT_FOUND`와 `AIEXTRACT_JOB_NOT_FOUND` 구분, 보충 입력 불가 예외의 `CONFIRM` 거절과 복구 경로 안내, 업무 중복이 `AUTO_BLOCKED`로 귀결하고 `AUTO_REJECTED`로 전이하지 않는 상태 전이, `recoveryPaths` 11개 상황별 매핑과 `requiredSupplements`의 대응, `ADJUST_CATEGORY`가 공개 상태·등록 결과를 유지하는지, `AUTO_BLOCKED` 예외 화면에 롤백 동작 미노출, `AUTO_REJECTED`에 사후 보정·롤백 동작 미노출 | `E3-T06`, `E3-T07`, `E3-T12` |
| `TST-E3-AI-004` | `NFR-EXTERNAL-005`, `NFR-RELIABILITY-005`, `NFR-AVAILABILITY-003` | Gemini timeout·429·5xx·Schema 오류, retry·lease·heartbeat·재기동·동시 claim | `E3-T05`, `E3-T13` |
| `TST-E3-COURSE-001` | `BR-COURSE-001`, `BR-COURSE-002`, `NFR-PRIVACY-006` | 입력 경계값·좌표·공개 상태·비저장·현재 위치 미수집 | `E3-T09` |
| `TST-E3-COURSE-002` | `BR-COURSE-003`, `NFR-PERFORMANCE-007` | Kakao Mobility WireMock 계약, 순서·TTL 5분·코스당 1회 호출 | `E3-T09` |
| `TST-E3-COURSE-003` | `BR-COURSE-004`, `NFR-EXTERNAL-005`, `NFR-COST-001`, `NFR-AVAILABILITY-003` | timeout·429·5xx·부분 실패·quota hard stop·기존 탐색 격리 | `E3-T10`, `E3-T13` |
| `TST-E3-DATA-001` | `NFR-INTEGRITY-006`, `NFR-PRIVACY-006`, `NFR-TEST-006` | V1→V4·빈 DB migration, PK/UK/FK/CHECK, 보존·삭제·정식 저장 0건 | `E3-T03`, `E3-T11` |
| `TST-E3-SEC-001` | `NFR-SECURITY-007`, `NFR-OBSERVABILITY-005` | 관리자/공개 audience, 악성 입력, Secret·URL·자막·Provider 응답 로그 비노출 | `E3-T01`, `E3-T04`, `E3-T13` |
| `TST-E3-E2E-001` | 3차 확장 전체 FR·BR·NFR | 360px·390px·768px·1280px·1440px, 키보드, 정상·빈·오류·복구 브라우저 흐름 | `E3-T12` |
| `TST-E3-PERF-001` | `NFR-PERFORMANCE-007`, `NFR-TEST-006` | 자연어 p95, 코스 외부 포함 5초, 내부 처리 500ms, 2차 승계 부하 | `E3-T13` |

## 4. 평가 ID → 테스트·Task

| 평가 ID 범위 | 평가 대상 | 연결 테스트 | 연결 Task |
|---|---|---|---|
| `EVAL-NL-001~007` | 조건 정확성·미지원 안전성·직접 필터·공개·복구·태그 AND | `TST-E3-NL-001~002`, `TST-E3-E2E-001` | `E3-T02`, `E3-T12` |
| `EVAL-AI-001~010` | Schema·장소·방문 근거·불확실성·원자성·복구·영상 입력·태그 | `TST-E3-AI-001~004`, `TST-E3-DATA-001`, `TST-E3-SEC-001`; [manifest](../../src/test/resources/eval/aiextract-golden-v1.0.0/manifest.json)·[cases](../../src/test/resources/eval/aiextract-golden-v1.0.0/cases.json)·[평가기](../../src/test/java/com/masiton/ai/application/AiExtractionGoldenEvaluationTest.java)·[HOLD 판정](third-expansion-ai-evaluation-result.md#3-eval-ai-001010-증거-경로와-현재-상태) | `E3-T08`, `E3-T11`, `E3-T13` |
| `EVAL-COURSE-001~005` | 입력 경계·경로·실패 안전성·만료·호출·비용 | `TST-E3-COURSE-001~003`, `TST-E3-PERF-001` | `E3-T10`, `E3-T13` |

자연어 Dataset 240건, AI Dataset 120건, 코스 Fixture 60건의 분할·정답·판정 기준은 평가 전략을 따른다. AI 120건은 Development 72건·Calibration 24건·Release holdout 24건이며, 실제 Release holdout과 인간 판정 승인 전 상태는 [`HOLD`](third-expansion-ai-evaluation-result.md)다. 이 문서는 Dataset 내용을 복제하지 않고 실행 묶음과 구현 Task만 연결한다.

## 5. 브라우저·운영·성능 증거

| 증거 | 판정 내용 | 현재 상태 | 완료 조건 |
|---|---|---|---|
| 브라우저 인수 | 공개 자연어·코스, 관리자 AI 작업·예외 보정의 정상·빈·오류·복구 | 부분 실행. 내장 Chromium에서 화면 폭 5종·키보드·정상·빈·오류·복구를 확인했고, 공개 화면 초기 상태 캡처는 Chrome·Edge 실빌드로 보존했다. 여정·관리자 화면 캡처와 실빌드 여정 확인은 미검증이다([3차 확장 브라우저 검증 기록](third-expansion-browser-verification.md)) | `TST-E3-E2E-001` 화면 캡처·환경·접근성 결과 기록 |
| AI Worker 운영 | 단일 EC2 CPU·메모리·DB·backlog·처리시간·재기동·quota | 미실행 | [E3-T13 최종 게이트 판정](third-expansion-final-gate-result.md)과 장애 복구 결과 |
| Mobility 운영 | quota·호출 수·timeout·비용 hard stop | 미실행 | [E3-T13 최종 게이트 판정](third-expansion-final-gate-result.md), WireMock 및 운영 계정 검증 |
| 코스 좌표 적합성 | 운영 ACTIVE·공개 맛집 좌표 보강률 | 미측정 | [E3-T13 최종 게이트 판정](third-expansion-final-gate-result.md)의 읽기 전용 측정·조치·재측정 기록 |
| 2차 승계 부하 | 정상 50명/20 RPS, 최대 200명/80 RPS | 정상 부하는 `Verified` 결과 연결, 최대 부하는 관찰만 완료·정식 판정 보류 | [E3-T13 최종 게이트 판정](third-expansion-final-gate-result.md)와 [정본 결과](second-expansion-performance-verification.md) 대조 |

실행 증거가 없으면 해당 요구사항을 통과했다고 보고하지 않는다. 팀이 측정을 연기한 항목은 보류 사유와 해제 조건을 기록하되 판정 기준을 낮추지 않는다.

## 6. 범위 밖 검증

임베딩·RAG·챗봇, 현재 위치·도보·대중교통, 코스 저장·공유, 원본 영상·전체 자막 저장·재배포, 자동 주기 전체 재처리와 외부 Provider 자동 failover는 현재 테스트 대상이 아니다. 이 기능을 구현하거나 테스트에 추가하려면 먼저 범위·요구사항·ADR을 변경한다.

## 7. 완료 조건

- [ ] 14개 FR과 13개 `TST-E3-*` 묶음의 구현 Task 연결이 실제 코드·테스트 경로와 일치한다.
- [ ] 모든 3차 BR과 적용 NFR이 최소 한 개 이상의 자동화·평가·운영 증거에 연결된다.
- [ ] 자연어·AI·코스 계약 테스트와 실패·동시성·부분 저장 0건 증거가 통과한다.
- [ ] Release holdout과 Critical 오류 0건 판정이 평가 보고서에 보존된다.
- [ ] 브라우저 인수, AI Worker 운영 측정, Mobility quota 검증, 운영 ACTIVE·공개 맛집 좌표 보강률 측정·조치·재측정과 2차 승계 부하 결과가 기록된다.
- [ ] 제품·API·데이터·ADR 추적표와 E3 Task 분해가 동일한 ID를 사용한다.

이 문서는 테스트 기준과 추적을 완료하기 위한 계획이다. 현재 실행 결과와 보류 사유는 [E3-T13 최종 게이트 판정](third-expansion-final-gate-result.md)에 기록한다. 체크되지 않은 항목이 남아 있는 동안 3차 확장 최종 완료를 선언하지 않는다.

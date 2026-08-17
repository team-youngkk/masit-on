---
status: In Progress
plan_date: 2026-08-10
implementation_gate: HOLD — E3-T13 운영·평가 증거 수집 중
related_documents:
  - third-expansion-baseline-review.md
  - third-expansion-scope-and-terminology.md
  - third-expansion-evaluation-strategy.md
  - third-expansion-test-matrix.md
  - third-expansion-final-gate-result.md
  - third-expansion-implementation-plan.md
  - ../02-analysis/third-expansion-workstreams.md
  - ../02-analysis/third-expansion-domain-boundaries.md
  - ../03-team/ownership.md
  - ../04-product/traceability.md
  - ../05-specs/api-traceability.md
  - ../05-specs/data/data-traceability.md
  - ../07-adr/adr-traceability.md
---

# 맛잇온 3차 확장 최종 Task 분해

## 1. 사용 규칙과 게이트

이 문서의 `E3-T*`는 3차 확장 구현·PR·검증 상태를 기록하는 유일한 최종 Task ID다. `WS-14~16`은 기능 소유권, `P0~P6`은 [구현 계획](third-expansion-implementation-plan.md)의 세부 체크포인트이며 최종 Task ID가 아니다. `E3-WS*`, `E3-QUALITY-*`, `E3-OPS-*`, `E3-BASE-*` 표기는 더 이상 사용하지 않는다.

상위 Scope·FR·BR·NFR·PRD·API·데이터·ADR을 Task에서 임의로 바꾸지 않는다. 계약 변경이 필요하면 권위 문서와 추적표를 먼저 갱신하고 별도 승인을 받는다.

[선행 상태 검토](third-expansion-baseline-review.md)와 범위·요구사항·PRD·API·데이터·ADR·테스트·추적표·구현 계획의 정합화는 이 Task 분해 전에 수행하는 문서화 게이트다. 따라서 기준선 확인과 계약 정합화를 별도 `E3-T*`로 만들지 않는다. 다만 문서에 `미확인`·`미실행`·`미측정`으로 남은 운영 증거는 완료로 간주하지 않고 `E3-T13`에서 실행·판정한다.

`implementation_gate: HOLD — E3-T13 운영·평가 증거 수집 중`은 계약이 미승인이라는 뜻이 아니다. 실제 계약 테스트, Dataset 평가, 브라우저·운영·성능 결과와 owner/reviewer 판정이 연결되기 전까지 구현 완료·출시 가능으로 보지 않는다는 뜻이다.

## 2. 전체 Task 표

| ID | Task | 담당자 / 기본 리뷰어 | 선행 | 병렬 | 주 테스트·완료 조건 |
|---|---|---|---|---|---|
| `E3-T01` | 자연어 P1 parser·기존 목록 Query·오류·보안 API를 구현한다 | 양성훈 / 이우람 | 문서화 게이트 | 가능 | `TST-E3-NL-001~002`, `TST-E3-SEC-001`; 직접 필터 우선·공개 상태·원문 비저장·전체 목록 대체 금지 |
| `E3-T02` | 자연어 공개 화면·240건 평가·회귀 자동화를 완성한다 | 양성훈 / 박진영 | `E3-T01`, `E3-T06` | 불가 | `EVAL-NL-001~007`, 화면 폭 5종·키보드, exact match 90%+, 재현율 95%+, 미지원 오적용 0건 |
| `E3-T03` | AI Job·Snapshot·태그·시도·감시 물리 스키마와 V4 검증을 구현한다 | 박진영 / 김인안 | 문서화 게이트 | 가능 | `TST-E3-DATA-001`; V3→V4·빈 DB, PK/UK/FK/CHECK, 18개 태그 seed, 원문·평문 비저장 |
| `E3-T04` | AI 작업 접수·Webhook·Gemini Provider·버전 경계를 구현한다 | 김인안 / 이우람 | `E3-T03` | 가능 | `TST-E3-AI-001`; `202`, 멱등 Job, Webhook AI 호출 격리, 현재 Prompt P7·Schema S1·기존 P1·P2·P3·P4·P5·P6 이력 보존·Free Tier 차단 |
| `E3-T05` | AI Worker claim·retry·lease 복구·quota hard stop을 구현한다 | 이우람 / 박진영 | `E3-T04` | 가능 | `TST-E3-AI-004`; timeout·429·5xx·Schema 오류, heartbeat·재기동·동시 claim·기존 탐색 격리 |
| `E3-T06` | AI 후보 자동 검증·태그 통제·정식 등록 원자성을 구현한다 | 김인안 / 박진영 | `E3-T03`, `E3-T04`, `E3-T05` | 불가 | `TST-E3-AI-003`, `TST-E3-DATA-001`; 근거·장소·Visit·태그 검증, 실패 시 정식 Entity 0건 |
| `E3-T07` | AI 관리자 조회·사후 보정·롤백 API와 화면을 구현한다 | 김인안 / 양성훈 | `E3-T06` | 가능 | `TST-E3-AI-002`, `TST-E3-E2E-001`; 입력 원문·비밀정보 미노출, 정상 결과 사전 승인 금지 |
| `E3-T08` | AI 120건 평가·인간 사후 판정·출시 후보와 롤백 준비를 검증한다 | 박진영 / 이우람 | `E3-T06`, `E3-T07` | 불가 | `EVAL-AI-001~010`; Development 72·Calibration 24·Release holdout 24, Critical 오연결 0건, 운영 활성화는 `E3-T13` 전까지 보류 |
| `E3-T09` | 코스 후보·결정론적 순서·Mobility Route 조합을 구현한다 | 이우람 / 양성훈 | 문서화 게이트 | 가능 | `TST-E3-COURSE-001~002`; 2~5개·첫 장소·30km·동률 ID·코스당 외부 호출 1회·TTL 5분 |
| `E3-T10` | 코스 실패 경계·공개 화면·60건 Fixture 평가를 완성한다 | 이우람 / 박진영 | `E3-T09` | 불가 | `TST-E3-COURSE-003`, `EVAL-COURSE-001~005`; timeout·429·5xx·부분 실패·비저장·화면 폭 5종, 운영 quota 판정은 `E3-T13`에 위임 |
| `E3-T11` | 세 Workstream의 API·Repository·Worker·외부 Adapter 통합 회귀를 완료한다 | 박진영 / 영향 WS | `E3-T02`, `E3-T07`, `E3-T10` | 불가 | 정상·예외·경계·동시성·부분 저장 0건, V4 migration과 기존 공개 탐색 회귀 |
| `E3-T12` | 전체 사용자 여정의 브라우저·접근성·오류 복구 증거를 보존한다 | 전원 / 상호 교차 리뷰 | `E3-T11` | 불가 | `TST-E3-E2E-001`; 360·390·768·1280·1440px, 키보드, 정상·빈·오류·복구, 미검증 브라우저 기록 |
| `E3-T13` | 보안·성능·운영 적합성·추적표와 기능 활성화 최종 게이트를 판정한다 | 전원 / 상호 교차 리뷰 | `E3-T08`, `E3-T11`, `E3-T12` | 불가 | `TST-E3-SEC-001`, `TST-E3-PERF-001`; 자연어 p95, 운영 ACTIVE·공개 맛집 좌표 보강률 읽기 전용 측정·조치·재측정, Worker 자원·backlog·Gemini quota, Mobility 계정·호출·비용, 50/20·200/80 부하, 네 추적표 정합화와 활성화 go/no-go |

## 3. Task별 계약 경계

### E3-T01~E3-T02 자연어 맛집 탐색

- `FR-NLSEARCH-001~004`, `BR-NLSEARCH-001~003`과 [자연어 API](../05-specs/api/discovery/natural-language-restaurant-discovery-api.md)를 구현한다.
- 자연어는 P1 통제 사전으로만 해석한다. 임베딩·RAG·검색 이력·원문 저장은 추가하지 않는다.
- 확정 태그 조회는 `E3-T06`의 TagDefinition·VisitTag 공개 경계가 안정화된 뒤 연결한다.

### E3-T03~E3-T08 AI 영상 정보 추출

- 후보·시도·검수 이력은 정식 Restaurant·Creator·Video·Visit를 대체하지 않는다. 자동 검증 실패·외부 검증 실패 시 정식 Entity는 0건이다.
- Webhook은 작업 접수만 수행하고 AI 호출·정식 저장을 HTTP 요청 안에서 하지 않는다. Provider 호출 중 DB 트랜잭션을 열지 않는다.
- 사후 보정·폐기·롤백은 허용하지만 정상 결과의 관리자 사전 승인 단계를 만들지 않는다. LLM 심판도 사용하지 않는다.

### E3-T09~E3-T10 맛집 코스 추천

- 공개·활성·좌표 보유 맛집 2~5개만 입력으로 받고, 첫 장소 출발·최근접 이웃·동률 ID 순서를 적용한다.
- 현재 위치 수집, 경로 영속 캐시, 추정 거리·시간 반환, 유료 fallback은 금지한다.

### E3-T11~E3-T13 통합과 종료

- API 존재·코드 작성·이슈 종료만으로 완료 처리하지 않는다. 테스트 결과·Dataset manifest·실행 커밋·브라우저 캡처·운영 측정·owner/reviewer 판정이 모두 필요하다.
- Critical 오류, 자동 검증 우회, 잘못된 장소 연결, 근거 없는 태그 공개, 민감정보 노출은 0건이어야 한다.

## 4. 실행 순서와 병렬화

1. 문서화 게이트가 끝나면 `E3-T01`, `E3-T03`, `E3-T09`를 병렬로 시작할 수 있다.
2. AI는 `E3-T03 → E3-T04 → E3-T05 → E3-T06 → E3-T07 → E3-T08` 순서를 지킨다.
3. 자연어 `E3-T02`는 `E3-T06`의 확정 태그 공개 경계를, 코스 `E3-T10`은 `E3-T09`를 선행으로 둔다.
4. `E3-T11 → E3-T12 → E3-T13`에서 전체 통합·브라우저·운영 게이트를 판정한다.

공통 데이터·추적표·평가 파일은 박진영이 최종 조율한다. 자연어 공개 화면은 양성훈, AI 관리자 화면·등록은 김인안, Mobility·Worker·운영은 이우람이 최종 책임진다.

## 5. 완료 게이트

- [ ] Task 착수 전에 선행 상태·범위·요구사항·PRD·API·데이터·ADR·테스트·추적표·구현 계획의 문서화 게이트가 최신 상태다.
- [ ] `E3-T01~10`의 주 테스트·평가가 통과하고 각 API·데이터·ADR 계약이 연결된다.
- [ ] `E3-T11~12`의 통합·브라우저·접근성 증거가 보존된다.
- [ ] `E3-T13`의 보안·성능·좌표 보강률·Worker·Gemini/Mobility 계정과 quota·2차 승계 부하·네 추적표·활성화 판정 결과가 기록된다.
- [ ] 모든 완료 Task가 제품·API·데이터·ADR 추적표와 PR에 연결된다.

Task 완료 시 [테스트 추적표](third-expansion-test-matrix.md), [제품 추적표](../04-product/traceability.md), [API 추적표](../05-specs/api-traceability.md), [데이터 추적표](../05-specs/data/data-traceability.md), [ADR 추적표](../07-adr/adr-traceability.md)를 같은 변경 단위에서 갱신한다.

현재 E3-T13 실행 결과와 활성화 판정은 [최종 게이트 판정](third-expansion-final-gate-result.md)에 기록한다. 이 문서의 모든 체크가 완료되기 전까지 3차 확장 최종 완료나 출시 가능 상태를 선언하지 않는다.

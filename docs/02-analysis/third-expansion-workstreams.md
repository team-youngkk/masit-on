---
status: approved
decision_date: 2026-08-10
related_documents:
  - README.md
  - domain-boundaries.md
  - mvp-workstreams.md
  - first-expansion-workstreams.md
  - second-expansion-domain-boundaries.md
  - second-expansion-workstreams.md
  - third-expansion-domain-boundaries.md
  - ../00-overview/scope.md
  - ../01-requirements/functional-requirements.md
  - ../01-requirements/business-rules.md
  - ../01-requirements/non-functional-requirements.md
  - ../03-team/roles.md
  - ../03-team/ownership.md
  - ../04-product/prd/discovery/natural-language-restaurant-discovery.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../04-product/prd/discovery/restaurant-course-recommendation.md
  - ../04-product/traceability.md
  - ../08-planning/third-expansion-scope-and-terminology.md
  - ../08-planning/third-expansion-evaluation-strategy.md
  - ../08-planning/third-expansion-implementation-plan.md
  - ../07-adr/architecture/arch-005-natural-language-filter-interpretation.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../07-adr/integration/ext-003-ai-extraction-async-reliability.md
  - ../07-adr/integration/route-001-kakao-mobility-course-routing.md
---

# 맛잇온 3차 확장 Workstream

## 1. 목적과 결정

이 문서는 승인된 3차 확장 범위를 독립적인 사용자·관리자 가치 단위로 나누고 WS-14~WS-16의 최종 책임자, 기본 리뷰어, 의존성과 완료 경계를 확정한다. 확률적 품질을 공통으로 판정하는 EDD 업무는 제품 Workstream 번호를 소비하지 않는 `QUALITY-EVAL` 교차 품질 트랙으로 관리한다.

배정은 기존 소유권의 연속성, 3차 확장 외부 연동 위험과 4인 팀의 책임 균형을 기준으로 한다. 각 Workstream의 최종 책임자는 한 명이고 자신의 산출물을 단독 승인하지 않는다.

이 문서는 클래스·패키지·테이블·API 경로를 결정하지 않는다. AI 작업·후보와 평가 자산의 새 도메인·저장 책임은 승인된 3차 도메인 경계·데이터·ADR을 따른다. 물리 명세와 실행 증거는 해당 추적표·Task 문서에서 별도로 판정한다.

## 2. 구성과 확정 배정

| Workstream | 사용자·운영 가치 | 관련 요구사항 | 최종 책임자 | 기본 리뷰어 | 상태 |
|---|---|---|---|---|---|
| [WS-14](#5-ws-14-자연어-맛집-탐색) 자연어 맛집 탐색 | 문장을 기존 맛집 조건·확정 태그로 해석해 공개 목록 탐색 | `FR-NLSEARCH-001~004` | 양성훈 | 이우람 | 확정 |
| [WS-15](#6-ws-15-ai-영상-정보-추출) AI 영상 정보 추출 | AI 후보·태그 생성부터 자동 등록·예외 보정·기존 등록 연결 | `FR-AIEXTRACT-001~007` | 김인안 | 박진영 | 확정 |
| [WS-16](#7-ws-16-맛집-코스-추천) 맛집 코스 추천 | 선택 맛집의 자동차 방문 순서와 경로 제공 | `FR-COURSE-001~003` | 이우람 | 양성훈 | 확정 |
| [QUALITY-EVAL](#8-quality-eval-교차-품질-트랙) EDD 평가 | 골든 데이터·평가기·품질 게이트와 최종 평가 증거 | `NFR-ACCURACY-001~002`, `NFR-TEST-006` | 박진영 | 이우람 | 확정 — 교차 품질 트랙 |

`QUALITY-EVAL`은 기능 구현을 소유하지 않는다. 각 기능의 평가 코드와 오류 수정은 해당 WS가 책임지고, `QUALITY-EVAL`은 Dataset·Evaluator 계약, 독립 판정과 통합 평가 보고서를 소유한다.

## 3. 배정 근거와 책임 균형

| 배정 | 근거 |
|---|---|
| 양성훈 → WS-14 | WS-01 검색·필터·목록과 WS-10 인기 탐색의 연속성, 자연어 조건을 기존 목록 계약에 결합하는 최종 책임 |
| 김인안 → WS-15 | WS-04 관리자 등록, WS-11·WS-12 관리자 검수·조치와 AI 의사결정 역할의 연속성 |
| 이우람 → WS-16 | 외부 연동·인프라·비용·장애 격리 책임과 Kakao Mobility 계약·단일 EC2 운영 판단의 연속성 |
| 박진영 → QUALITY-EVAL | 통합 테스트·관계 정합성·데이터/Flyway 조율 책임을 활용한 독립 평가 증거와 품질 게이트 소유 |

3차 확장에서는 네 명이 각각 하나의 주 실행 단위를 가진다. WS-15는 High 복잡도이므로 김인안에게 다른 3차 WS를 추가 배정하지 않는다. 이우람은 WS-16과 인프라 책임을 함께 가지므로 AI Worker 공통 기반을 직접 독점 구현하지 않고 WS-15를 지원한다. 박진영은 제품 WS 대신 교차 평가·데이터 품질 트랙을 맡아 세 기능의 독립 검증을 수행한다.

## 4. 공통 선행 책임

| 선행 계약·작업 | 최종 조율 | 구현·작성 | 필수 리뷰 | 완료 판단 |
|---|---|---|---|---|
| 자연어·AI 골든 Dataset과 평가 보고서 | 박진영 | 박진영, 각 WS 정답 책임자 | 이우람, 영향 WS | 평가 전략 완료 조건과 Dataset 승인 |
| 자연어 해석 방식·태그 별칭·오류 계약 | 양성훈 | WS-14 | 이우람, 박진영 | 검색 ADR·API·평가 기준 승인 |
| AI 제공자·모델·Prompt·Schema | 김인안 | WS-15 | 이우람, 양성훈, 박진영 | `gemini-3.5-flash-lite`·P1·S1·무료 quota·결제 차단 계약 테스트 |
| AI 비동기 작업·복구·단일 EC2 한계 | 이우람 | WS-15, 공통 설정은 이우람 | 박진영, 김인안 | lease·retry·복구 계약과 최종 부하 증거 승인 |
| AI 후보·태그·검수·감사 데이터와 Flyway 순서 | 박진영 | WS-15 | 이우람 | 데이터 계약·마이그레이션 계획 승인 |
| Kakao Mobility `/v1/directions`·quota·TTL | 이우람 | WS-16 | 양성훈, 박진영 | 5분 TTL·캐시 없음·월 1,000건과 계정 연결 검증 |
| 운영 좌표 보강률·코스 성능 Fixture | 박진영 | 읽기 전용 조회와 Fixture 담당 티켓 | 양성훈, 이우람 | 운영 수치와 좌표 포함 성능 데이터 기록 |
| 2차 정상·최대 부하 승계 검증 | 박진영 | QUALITY-EVAL, 영향 WS | 이우람 | `NFR-TEST-006` 최종 증거 승인 |
| 공개 탐색 공통 화면·접근성 | 양성훈 | WS-14·WS-16 화면 티켓 | 김인안, 이우람 | 화면 폭·키보드·오류 복구 인수 통과 |
| 관리자 AI 자동 결과·예외 보정 화면 | 김인안 | WS-15 화면 티켓 | 박진영, 양성훈 | 정상·부분·오류·충돌·자동 등록·롤백 인수 통과 |

새 라이브러리·AI 제공자·외부 SDK는 이 배정만으로 도입하지 않는다. 팀 승인 ADR과 비용·보안 계약이 먼저다.

## 5. WS-14 자연어 맛집 탐색

### 책임

- 자연어 입력을 맛집명·자치구·카테고리·유튜버·확정 태그 조건으로 해석한다.
- 직접 지정 필터 우선, 서로 다른 조건의 AND 조합과 적용 조건 요약을 제공한다.
- 빈 결과·해석 실패·일부 미지원·서버 오류를 분리하고 기존 필터 탐색으로 복구한다.
- 기존 목록·페이지·정렬·공개 상태 계약을 재사용하고 임베딩·RAG·챗봇을 추가하지 않는다.
- 활성 `TagDefinition`·확정 `VisitTag`를 조회하고 여러 태그는 AND로 조합하며 태그 연결을 직접 변경하지 않는다.
- `EVAL-NL-*` 자동 평가와 브라우저 인수를 구현하고 품질 결과를 QUALITY-EVAL에 제공한다.

### 의존성과 협업

- WS-01의 Restaurant 목록·검색·페이지 계약과 WS-03의 유튜버 관계 판정을 사용한다.
- 이우람은 관계·공개 판정과 모델·장애 격리를 리뷰한다.
- 박진영은 Dataset 분할·정답 Schema·회귀 보고서를 검증한다.
- 김인안은 Prompt Injection·입력 개인정보와 공개 결과 경계를 검토한다.

### 소유 계약

- `FR-NLSEARCH-001~004`
- `BR-NLSEARCH-001~003`
- `NFR-ACCURACY-001` 구현, `NFR-SECURITY-007`·`NFR-PERFORMANCE-007` 자연어 적용
- [자연어 맛집 탐색 PRD](../04-product/prd/discovery/natural-language-restaurant-discovery.md)

### 완료 경계

고정 Dataset에서 확정 품질 목표와 Critical 실패 0건을 충족하고, 구조화 필터 회귀·공개 상태·입력 로그 금지·정상 부하·브라우저 복구 흐름을 검증하면 완료한다.

## 6. WS-15 AI 영상 정보 추출

### 책임

- 관리자 추출 요청, 중복 수렴과 비동기 실행 상태를 제공한다.
- 맛집명·주소·메뉴·방문·통제 태그 후보, 근거·신뢰도·모델·Prompt·Schema 버전을 후보로 관리한다.
- 정상·부분·실패 결과와 관리자 확정·폐기·보완·검증 충돌을 구분한다.
- Kakao·YouTube·Visit 기존 검증 뒤에만 정식 Entity를 원자적으로 저장한다.
- 허용 태그 정의만 후보로 제시하고 관리자 결정과 확정 Visit 등록 뒤에만 `VisitTag`를 저장한다.
- timeout·재시도·재기동·중복 claim·비용 차단과 기존 수동 등록 fallback을 구현한다.
- `EVAL-AI-*` 자동·인간 평가 자료와 관리자 검수 사유를 QUALITY-EVAL에 제공한다.

### 의존성과 협업

- WS-04 관리자 인증·등록, WS-03·WS-08 Creator·Video·Visit 판정과 기존 외부 검증 계약을 사용한다.
- 박진영은 기본 리뷰어로서 후보·감사 데이터, 정식 저장 0건, Flyway와 평가 증거를 검증한다.
- 이우람은 YouTube·Visit, 비동기·인프라·외부 장애와 단일 EC2 수용성을 리뷰한다.
- 양성훈은 AI 제공자·Prompt·Schema 기술 결정과 관리자 화면 공통 연동을 지원한다.

### 소유 계약

- `FR-AIEXTRACT-001~007`
- `BR-AIEXTRACT-001~011`
- `NFR-ACCURACY-002`, `NFR-INTEGRITY-006`, `NFR-RELIABILITY-005` 구현
- `NFR-SECURITY-007`, `NFR-PRIVACY-006`, `NFR-COST-001`, `NFR-EXTERNAL-005` AI 적용
- [AI 영상 정보 추출 PRD](../04-product/prd/admin/ai-video-information-extraction.md)

### 완료 경계

확정 Dataset·품질 게이트, 자동 검증·근거 없음·오연결 방지, 자동 검증 전 정식 저장 0건, 사후 보정·롤백, 복구·비용·보안·개인정보와 단일 EC2 운영 한계를 검증하고 기존 수동 등록 회귀가 통과하면 완료한다.

## 7. WS-16 맛집 코스 추천

### 책임

- 공개·활성·좌표 보유 맛집 2~5개와 첫 출발점을 검증한다.
- 자동차 이동 순서, 구간별 거리·시간, 전체 거리와 생성·만료 시각을 제공한다.
- 30km 상한, 좌표 누락, 외부 timeout·429·부분 실패와 비용 hard stop을 적용한다.
- 실패한 구간을 추정하거나 성공 구간만 합쳐 정상 코스로 표시하지 않는다.
- Kakao Mobility `/v1/directions`·첫 장소 출발·최근접 이웃·5분 TTL·캐시 없음·월 1,000건과 단일 EC2 외부 호출 한계를 계약화한다.
- `EVAL-COURSE-*` Fixture·장애·호출 수·브라우저 인수를 구현한다.

### 의존성과 협업

- WS-01 공개 Restaurant 결과와 WS-07 좌표·지도 경험을 사용한다.
- 양성훈은 기본 리뷰어로서 공개 탐색·좌표·코스 화면과 순서 UX를 검증한다.
- 박진영은 좌표 보강률, 캐시·비저장 데이터, Fixture와 성능 증거를 검토한다.
- 김인안은 외부 키·오류 정보와 개인정보 최소 전송을 검토한다.

### 소유 계약

- `FR-COURSE-001~003`
- `BR-COURSE-001~004`
- `NFR-EXTERNAL-005`, `NFR-PERFORMANCE-007` 코스 적용
- `NFR-PRIVACY-006`, `NFR-COST-001`, `NFR-AVAILABILITY-003` Mobility 적용
- [맛집 코스 추천 PRD](../04-product/prd/discovery/restaurant-course-recommendation.md)

### 완료 경계

입력·좌표·30km·TTL·호출 상한·외부 부분 실패 Fixture와 응답 시간·비용 차단·기존 탐색 장애 격리·브라우저 흐름이 통과하면 완료한다.

## 8. QUALITY-EVAL 교차 품질 트랙

### 성격과 책임

`QUALITY-EVAL`은 OPS 트랙과 같은 비제품 교차 트랙이다. 사용자 기능과 API를 소유하지 않고 다음을 책임진다.

- [3차 확장 평가 전략](../08-planning/third-expansion-evaluation-strategy.md), Dataset manifest와 평가 보고서 조율
- Development·Calibration·Release holdout 분리와 중복 그룹 누수 검사
- 프로그램·인간·조건부 LLM 심판의 사용 경계와 평가기 버전 관리
- 후보 버전과 활성 버전의 회귀 비교, Critical 실패와 잔여 위험 기록
- `G-EVAL-01~07` 증거 취합과 최종 품질 판정
- 2차 확장 정상 50명·20 RPS와 최대 200명·80 RPS 승계 측정 증거 취합
- 3차 테스트 매트릭스와 CI·브라우저·보안·복구·부하 결과 추적

### 정답·검증 역할

| 평가 자산 | 정답 책임자 | 검증 책임자 | 충돌 최종 판정 |
|---|---|---|---|
| 자연어 조건·미지원 표현 | 양성훈 | 이우람 | 박진영 |
| AI 장소·주소·방문 근거 | 김인안 | 이우람, 데이터·평가 검증은 박진영 | 박진영 |
| 관리자 검수 가능성·폐기 사유 | 김인안 | 박진영 | 박진영 |
| 코스·Mobility Fixture | 이우람 | 양성훈 | 박진영 |
| 공통 출시·롤백 평가 증거 | 각 WS 책임자 | 박진영 | 박진영, 범위·비용 변경은 팀 승인 |

박진영의 최종 판정은 평가 증거의 통과·보류 판단을 뜻하며 제품 범위·비용·외부 제공자 도입을 단독 승인하는 권한이 아니다. 자신의 평가 도구 변경은 이우람이 기본 리뷰하고 영향받는 WS가 정답·계약을 함께 검토한다.

### 완료·종료 조건

세 WS의 최종 평가 보고서, 품질·보안·비용·복구·성능 증거와 2차 성능 승계 결과가 승인되면 3차 릴리즈 게이트 책임을 완료한다. 운영 중 새 실패 유형의 Dataset 편입과 활성 버전 재평가는 기능 운영 기간 동안 계속한다.

## 9. 공통 파일과 최종 병합 책임

| 공통 범위 | 최종 조율·병합 책임 | 작성 책임 | 필수 리뷰 |
|---|---|---|---|
| 자연어 탐색 API·PRD·화면 | 양성훈 | WS-14 | 이우람 |
| AI 관리자 API·PRD·화면 | 김인안 | WS-15 | 박진영 |
| 코스 API·PRD·화면 | 이우람 | WS-16 | 양성훈 |
| 평가 전략·Dataset·테스트 매트릭스·결과 | 박진영 | QUALITY-EVAL, 각 WS | 이우람, 영향 WS |
| 데이터 명세·ERD·Flyway 순서 | 박진영 | 영향 WS | 이우람, 데이터 영향 WS |
| AI·Mobility 외부 연동·비동기·배포 설정 | 이우람 | WS-15·WS-16, 공통 설정 담당 티켓 | 박진영, 김인안 |
| 공개 프론트엔드 공통 Layout | 양성훈 | WS-14·WS-16 티켓 | 김인안 |
| 관리자 인증·검수 공통 UI | 김인안 | WS-15 티켓 | 양성훈, 박진영 |

공통 파일의 최종 병합 책임은 다른 WS의 계약이나 변경을 임의로 덮어쓸 권한이 아니다. 같은 파일을 병렬 수정해야 하면 담당 범위를 분리하거나 선행 PR을 먼저 병합한다.

## 10. 문서·구현 통합 순서

1. [3차 확장 도메인 경계](third-expansion-domain-boundaries.md)를 팀 승인 상태로 전환하고 소유권·추적표와 동기화한다.
2. QUALITY-EVAL이 골든 Dataset Schema·분할안을 만들고 각 WS 정답 책임자가 Development 사례를 작성한다.
3. WS-14 해석 방식, WS-15 AI 제공자·비동기·후보, WS-16 Mobility·TTL ADR 후보를 병렬 검토한다.
4. 박진영이 데이터·Flyway 순서를 조율하고 각 WS가 API·데이터 계약을 작성한다.
5. [3차 확장 구현 계획](../08-planning/third-expansion-implementation-plan.md)의 P0~P4 공통 기반과 WS-15 자동 등록 경계를 먼저 구현한다.
6. WS-14·WS-16 공개 흐름과 WS-15 예외 보정 흐름을 공통 기반 이후 병렬 구현한다.
7. 각 WS는 자기 `EVAL-*`·자동화·브라우저 증거를 만들고 QUALITY-EVAL은 독립 회귀와 보고서를 수행한다.
8. 외부 장애·비용·로그·복구·단일 EC2 부하와 2차 성능 승계 게이트를 통합 판정한다.

## 11. 변경 규칙

- Workstream마다 최종 책임자는 한 명이며 자신의 변경을 단독 승인하지 않는다.
- WS-14·WS-16을 하나의 탐색 WS로 합치거나 WS-15의 추출과 검수를 분리하려면 PRD·요구사항·소유권을 먼저 재검토한다.
- AI 후보·평가 Dataset·경로 캐시를 새 도메인이나 저장 Entity로 만들 때는 후속 도메인 경계와 데이터 ADR 승인을 받는다.
- 김인안의 WS-15가 2영업일 이상 AI 제공자·비동기 기반 때문에 차단되면 이우람이 외부 연동·운영 기반을 우선 지원한다.
- 이우람의 WS-16과 인프라 책임이 2영업일 이상 서로 차단되면 박진영이 운영 검증을, 양성훈이 공개 코스 화면을 우선 지원한다.
- 평가 증거와 제품 담당자 판단이 충돌하면 박진영이 품질 판정을 종결하되 범위·비용·외부 제공자 변경은 팀 승인으로 올린다.

---
id: PRD-ADMIN-002
title: AI 영상 정보 추출
status: approved
workstream: WS-15
owner: 김인안
reviewers:
  - 박진영
related_requirements:
  - FR-AIEXTRACT-001
  - FR-AIEXTRACT-002
  - FR-AIEXTRACT-003
  - FR-AIEXTRACT-004
  - FR-AIEXTRACT-005
  - FR-AIEXTRACT-006
  - FR-AIEXTRACT-007
related_business_rules:
  - BR-AIEXTRACT-001
  - BR-AIEXTRACT-002
  - BR-AIEXTRACT-003
  - BR-AIEXTRACT-004
  - BR-AIEXTRACT-005
  - BR-AIEXTRACT-006
  - BR-AIEXTRACT-007
  - BR-AIEXTRACT-008
  - BR-AIEXTRACT-009
  - BR-AIEXTRACT-010
  - BR-AIEXTRACT-011
related_nfr:
  - NFR-ACCURACY-002
  - NFR-INTEGRITY-006
  - NFR-SECURITY-007
  - NFR-PRIVACY-006
  - NFR-COST-001
  - NFR-EXTERNAL-005
  - NFR-RELIABILITY-005
  - NFR-AVAILABILITY-003
  - NFR-OBSERVABILITY-005
  - NFR-TEST-006
related_documents:
  - ../../../00-overview/scope.md
  - ../../../01-requirements/functional-requirements.md
  - ../../../01-requirements/business-rules.md
  - ../../../01-requirements/non-functional-requirements.md
  - ../../../08-planning/third-expansion-scope-and-terminology.md
  - ../../../08-planning/third-expansion-evaluation-strategy.md
  - ../../../02-analysis/third-expansion-workstreams.md
  - admin-data-management.md
  - ../../user-flows/third-expansion-user-flows.md
  - ../../wireframes/third-expansion-wireframes.md
---

# AI 영상 정보 추출 PRD

## 1. 목적과 관리자 문제

관리자가 영상마다 맛집명·주소·메뉴·방문 근거와 탐색용 태그를 직접 찾아 옮기는 반복 작업을 제거한다. AI 결과는 자동 정규화·외부 검증·중복·근거 검사를 통과하면 관리자 승인 없이 정식 데이터와 검색 태그로 공개하고, 불확실한 결과만 예외 작업으로 보류한다.

## 2. 대상 사용자와 선행 조건

- 대상: 인증된 관리자
- 선행 조건: 기존 관리자 인증, 관리자 활성화 채널의 Webhook 또는 관리자 신규 영상 추가, YouTube URL 확인, Kakao 장소 검증, Creator·Video·Visit 등록·중복·원자성 규칙
- 입력: Webhook이 전달한 공개 YouTube URL을 Google Gemini API의 `gemini-3.5-flash-lite` 영상 입력으로 사용하거나, 관리자 화면의 YouTube URL과 최대 20,000자의 선택적 보완 텍스트를 사용한다.
- 시스템은 원본 영상이나 전체 YouTube 자막·전사를 자동 수집·저장하지 않는다.
- Gemini 영상 입력 실패·접근 제한·부분 추출 시 관리자가 보완 텍스트를 추가해 재시도할 수 있다.
- 구현은 [WS-15](../../../02-analysis/third-expansion-workstreams.md#6-ws-15-ai-영상-정보-추출)가 담당하며 최종 책임자는 김인안, 기본 리뷰어는 박진영이다.

## 3. 목표와 성공 기준

- 관리자는 추출 작업을 요청한 뒤 웹 요청과 분리된 상태로 진행 상황을 확인한다.
- 관리자는 신규 영상 추가 화면에서 초기 데이터 적립·Webhook 누락 보완 작업을 직접 접수할 수 있고, 정상 결과는 별도 승인 없이 자동 등록된다. 예외·차단·롤백 작업만 관리자 화면에서 처리한다.
- 정상 경로에서 관리자는 후보를 고르지 않고 Kakao 장소 URL과 음식 카테고리도 입력하지 않는다. 한 영상에 여러 맛집이 등장해도 등록 단위별로 자동 등록된다.
- 정상 결과뿐 아니라 일부 필드만 추출된 결과, 근거 없음, 제공자 오류와 재시도 가능 여부를 구분한다.
- 자동 검증 실패·모호·중복 결과의 정식 Entity 생성·공개는 0건이다.
- 품질 목표는 평가 입력 120건(Development 72·Calibration 24·Release holdout 24), 맛집·주소 후보 정밀도 90% 이상, 방문 근거 재현율 80% 이상, 자동 등록 정밀도 90% 이상, 자동 태그 정밀도 90% 이상·재현율 80% 이상, Critical 오연결 0건이며 Release holdout에서 검증한다.

## 4. 범위

### 포함

- 관리자 추출 작업 요청과 중복 작업 안내
- 관리자 신규 영상 추가와 Webhook 신규 영상 감지
- Google Gemini API `gemini-3.5-flash-lite` 공개 YouTube URL 입력과 관리자 보완 텍스트 fallback
- 관리자 Webhook 감시 채널 활성화·중지와 구독 상태 확인
- 비동기 작업 상태 `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`
- 성공 결과의 완전·부분 추출 표시
- 맛집명·메뉴·주소/위치·방문 여부 후보, 신뢰도와 출처 구간
- 자막에서 생성한 `MENU`, `TASTE`, `OCCASION`, `ATMOSPHERE` 및 후속 승인된 태그 주제의 후보, 신뢰도와 출처 구간
- 모델·Prompt·결과 Schema 버전과 처리 시각
- 자동 판정 상태 `AUTO_CONFIRMED`, `AUTO_BLOCKED`, `AUTO_REJECTED`와 사후 보정·롤백 이력
- 장소 단위 등록 단위 분해와 등록 단위별 독립 판정
- 상호명·주소 기반 Kakao 장소 동일성 자동 판정과 대표 음식 카테고리 자동 선정
- 자동 확정 후보의 기존 Kakao·YouTube·Visit 검증·등록·공개 흐름 연결
- 제한 재시도, 실패 보관과 예외 수동 등록 대체

### 제외

- 자동 검증 없이 공개하는 AI 단독 정식 Entity 생성
- 원본 영상·전체 자막 저장·재배포와 자동 자막 수집
- 영업시간·폐업·예약·가격 확정과 공개 영상 요약
- 전체 채널 무차별 감시·무제한 재처리·모델 변경 시 전체 자동 재계산
- 일반 사용자용 AI 추출 결과 화면

## 5. 핵심 관리자 흐름

1. Webhook이 활성화 채널의 신규 영상을 알리거나 관리자가 신규 영상 추가 화면에서 URL을 제출한다.
2. 시스템은 영상·입력·버전 조합의 중복을 확인하고 새 작업 또는 기존 작업을 안내한다.
3. 작업은 비동기로 실행되며 관리자는 목록·상세에서 상태를 확인한다.
4. 성공하면 필드별 후보·신뢰도·근거와 완전·부분 추출 상태를 표시한다.
5. 시스템은 후보를 장소 단위 등록 단위로 묶고, 단위마다 상호명·주소로 Kakao 장소를 검색해 동일성을 판정하고 확정한 장소의 분류로 대표 음식 카테고리를 결정한다.
6. 검증을 통과한 등록 단위는 관리자 승인 없이 정식 저장·공개한다. 통과하지 못한 단위만 차단 사유와 함께 관리자 화면에 남는다.
7. AI·검증·정식 저장 실패는 해당 등록 단위의 정식 데이터 0건으로 끝나고 재시도·예외 보정 또는 기존 수동 등록으로 복구한다. 같은 작업에서 이미 통과한 등록 단위는 되돌리지 않는다.

아직 등록되지 않은 등록 단위는 관리자가 상세 화면에서 직접 실행할 수도 있다. 이때도 같은 판정 규칙을 사용한다.

1. 관리자가 등록 단위의 등록 실행을 누른다.
2. 시스템이 장소 동일성·카테고리·영상 메타데이터·방문 근거를 판정하고, 맛집·유튜버·영상·방문 관계 4종을 한 번에 등록한다. 이미 등록된 유튜버·영상은 기존 식별자를 재사용한다.
3. 성공하면 등록 결과와 판정 근거만 표시한다. 관리자는 장소 후보·주소 힌트·Kakao 장소 URL·음식 카테고리를 입력하지 않는다.
4. `BR-AIEXTRACT-011`이 정의한 예외 사유에 해당하면 자동 등록을 멈추고 정식 저장 0건으로 끝낸다. 장소 동일성과 카테고리 예외만 보조 입력 화면으로 전환한다. 나머지 예외는 사유별 복구 경로를 따른다. 필수 필드 부족·방문 근거 부족은 재추출 또는 기존 수동 등록으로, 외부 서비스 오류는 등록 재실행 또는 기존 수동 등록으로 안내한다. 업무 중복은 기존 등록 결과 확인(`EXISTING_RESOURCE`)이 유일한 복구 경로이며 재추출·재실행·수동 등록 경로는 없다. 다만 공통 종결 동작인 `DISCARD`(폐기)는 다른 예외와 같이 허용한다.

상태 전이는 [3차 확장 사용자 흐름](../../user-flows/third-expansion-user-flows.md#3-ai-영상-정보-추출과-자동-등록예외-보정)을 따른다.

## 6. 제품 요구사항

| 제품 요구사항 | 제품 동작 | 근거 |
|---|---|---|
| PR-AIEXTRACT-001 | Webhook 신규 영상과 관리자 URL 추가는 동일한 비동기 추출 작업 흐름을 사용한다. | FR-AIEXTRACT-001, FR-AIEXTRACT-004, FR-AIEXTRACT-005 |
| PR-AIEXTRACT-008 | 관리자는 검증된 Creator 채널의 Webhook 감시를 활성화·중지하고 구독 상태를 확인한다. | FR-AIEXTRACT-006, BR-AIEXTRACT-006 |
| PR-AIEXTRACT-009 | Webhook 작업은 Google Gemini API 영상 입력을 우선 사용하고, 입력 실패·부분 추출 시 관리자 보완 텍스트로 재시도한다. | FR-AIEXTRACT-001, BR-AIEXTRACT-007, NFR-PRIVACY-006 |
| PR-AIEXTRACT-002 | 같은 영상·입력 해시·모델·Prompt·Schema 조합은 기존 작업으로 수렴한다. | BR-AIEXTRACT-003 |
| PR-AIEXTRACT-003 | 관리자는 작업 상태·처리 시각·오류 범주를 조회한다. | FR-AIEXTRACT-002 |
| PR-AIEXTRACT-004 | 성공 결과는 필드별 후보·신뢰도·출처·버전과 완전·부분 추출 상태를 표시한다. | FR-AIEXTRACT-002, BR-AIEXTRACT-001 |
| PR-AIEXTRACT-005 | 자동 판정과 사후 보정·롤백 결과는 모두 감사 가능해야 한다. | FR-AIEXTRACT-003, BR-AIEXTRACT-004 |
| PR-AIEXTRACT-006 | 자동 확정 후보는 기존 외부 검증과 중복 판정을 통과한 뒤 관리자 승인 없이 정식 저장·공개한다. | BR-AIEXTRACT-002, NFR-INTEGRITY-006 |
| PR-AIEXTRACT-007 | 추출 실패·비용 상한 초과·모델 장애 시 정식 데이터를 변경하지 않고 수동 등록을 제공한다. 후보 수 상한은 여기 해당하지 않는다. | NFR-COST-001, NFR-AVAILABILITY-003 |
| PR-AIEXTRACT-016 | 후보 수 상한까지의 응답은 정상 수용해 등록을 진행하고, 상한 때문에 일부 장소가 생략됐으면 그 사실을 표시한다. | FR-AIEXTRACT-002, BR-AIEXTRACT-001 |
| PR-AIEXTRACT-017 | 후보 수가 상한을 넘는 응답은 Schema 위반이므로 기각하고 등록 단위를 만들지 않는다. | FR-AIEXTRACT-002, BR-AIEXTRACT-001 |
| PR-AIEXTRACT-010 | 자막 기반 태그 후보는 정규화·중복·근거 검증 후 관리자 승인 없이 `Visit`과 검색에 연결한다. | FR-AIEXTRACT-007, BR-AIEXTRACT-008 |
| PR-AIEXTRACT-011 | 한 영상의 맛집 후보는 장소 단위 등록 단위로 나뉘어 각각 독립적으로 판정·등록된다. 한 단위의 차단이 다른 단위의 등록을 막지 않는다. | FR-AIEXTRACT-003, BR-AIEXTRACT-001 |
| PR-AIEXTRACT-012 | 장소 동일성은 시스템이 상호명·주소로 Kakao 장소를 검색해 판정한다. 관리자에게 Kakao 장소 URL 입력이나 후보 선택을 요구하지 않는다. | FR-AIEXTRACT-003, BR-AIEXTRACT-009 |
| PR-AIEXTRACT-013 | 대표 음식 카테고리는 확정한 Kakao 장소 분류를 1순위, AI 메뉴 표현을 2순위 근거로 자동 선정하고, 둘 다 실패하면 자동 확정을 차단한다. | FR-AIEXTRACT-003, BR-AIEXTRACT-010 |
| PR-AIEXTRACT-014 | 관리자가 등록 단위의 등록을 실행하면 맛집·유튜버·영상·방문 관계 4종을 한 번에 등록하고 결과만 표시한다. 단계별 진행 화면과 장소·카테고리 입력을 요구하지 않는다. | FR-AIEXTRACT-003, BR-AIEXTRACT-011 |
| PR-AIEXTRACT-015 | 보조 입력은 장소 동일성과 카테고리 예외에서만 요구한다. 후보 값이 부족한 예외는 관리자 입력 대신 재추출·수동 등록으로 안내한다. | FR-AIEXTRACT-003, BR-AIEXTRACT-011 |

## 7. 관리자 화면과 상태

| 화면 상태 | 필수 표시 | 허용 동작 |
|---|---|---|
| 요청 전 | URL, 제공 텍스트, 데이터 전송·저장 안내 | 추출 요청 |
| `QUEUED` | 접수 시각, 버전, 대기 상태 | 새 중복 요청 금지 |
| `RUNNING` | 시작 시각, 진행 중, 취소 미지원 안내 | 상태 새로고침 |
| `SUCCEEDED`·완전 | 모든 후보 필드·태그, 근거, 신뢰도, 버전 | 자동 검증·등록·공개 |
| `SUCCEEDED`·부분 | 누락·`UNKNOWN` 필드·태그, 사용 가능한 근거, 불완전 경고 | 자동 보류·보완 재시도·예외 등록 |
| `FAILED` | 오류 범주, 시도 횟수, 마지막 실패 시각 | 허용 시 수동 재시도·수동 등록 |
| 검증 충돌(`AUTO_BLOCKED`) | 차단 사유, 근거, 정식 저장 0건 | 사유별 `recoveryPaths`만 노출. `review`는 `CONFIRM`(장소·카테고리 보충 입력)과 `DISCARD`만 허용하며 후보 값 직접 수정이나 일반 재검수는 없다 |
| `AUTO_CONFIRMED` | 등록 단위별 자동 검증·등록·공개 결과, 채택한 Kakao 장소와 카테고리 근거, 버전·시각 | 결과 조회·사후 롤백·카테고리 보정(`ADJUST_CATEGORY`) |
| `AUTO_BLOCKED` | 차단 사유·근거·시각. 사유는 `PLACE_NOT_FOUND`·`PLACE_AMBIGUOUS`·`CATEGORY_UNRESOLVED`·`MISSING_REQUIRED_FIELD`·`VISIT_EVIDENCE_REQUIRED`·`DUPLICATE_CONFLICT`·`EXTERNAL_SERVICE_ERROR` 7종으로 구분한다 | 사유별 복구 경로만 노출. 보조 입력·재추출·수동 등록·기존 자원 확인·재실행 |
| `AUTO_REJECTED` | 거부 사유·근거·시각. 입력·정책 검증 실패로 끝난 종결 상태이며 복구 경로가 없다 | 새 작업 |
| 등록 단위 · 미등록 | 등록 단위 요약과 등록 실행 동작 하나 | 4종 일괄 등록 실행 |
| 등록 단위 · 등록 완료 | 맛집·유튜버·영상·방문 관계 식별자, 채택한 장소와 카테고리 근거 | 결과 조회·사후 롤백·카테고리 보정(`ADJUST_CATEGORY`) |
| 등록 단위 · 롤백 완료 | 롤백 시각과 사유. 등록 결과 식별자는 표시하지 않는다 | 결과 조회만. 재등록 동작 없음 |
| 등록 단위 · 폐기 완료 | 폐기 시각과 사유 | 결과 조회만. 재등록 동작 없음 |
| 등록 단위 · 예외 전환 | 예외 사유 코드와 필요한 보충 입력만 | 보조 입력 제출·수동 등록·폐기 |
| 후보 절삭 발생 | 후보 수 상한 때문에 일부 장소가 누락됐다는 경고와 상한 값 | 결과 확인·누락분 수동 등록 |

화면 구조는 [3차 확장 와이어프레임](../../wireframes/third-expansion-wireframes.md#4-ai-영상-추출-자동-등록예외-보정)을 따른다.

## 8. 부분 추출 정책

- 작업 실행과 출력 Schema 검증에 성공하면 일부 필드가 `UNKNOWN`이어도 작업 상태는 `SUCCEEDED`이고 결과 완전성은 `PARTIAL`로 표시한다.
- 맛집 동일성·주소·방문 근거 등 정식 등록에 필요한 값이 부족하면 해당 등록 단위의 자동 검증을 차단하고 부족한 항목을 표시한다. 같은 작업의 다른 등록 단위는 계속 진행한다.
- 완전성은 작업 단위가 아니라 등록 단위 기준으로 판단한다. 일부 등록 단위가 차단돼도 통과한 단위의 정식 등록은 유지하며, 결과 완전성은 `PARTIAL`로 표시한다.
- 관리자 보완 경로는 두 가지이며 성격이 다르다. 두 경로를 한 화면에 섞지 않는다.
  - **보완 텍스트 재추출**: 관리자가 `supplementText`를 제출해 새 작업을 만든다. 맛집명·주소·메뉴·Kakao 장소 URL은 보완 텍스트의 해시·문자 범위가 일치하는 `TEXT_RANGE` 근거를 가질 때만 후보가 된다. 값을 직접 등록하는 것이 아니라 AI가 근거와 함께 다시 추출하는 것이다.
  - **`review`의 보충 입력**: 이미 만들어진 등록 단위의 차단을 푸는 판정 선택이며 Kakao 장소 URL과 음식 카테고리만 받는다. 후보 값을 새로 만들지 않는다.
- 어느 경로에서도 관리자가 맛집명·주소·방문 근거 값을 화면에서 직접 입력해 등록하지 않는다. 보완값도 기존 외부 검증을 우회하지 않는다.
- 보완 텍스트 자체의 방문 주장은 자동 확정 근거가 아니며 실제 영상 `TIMESTAMP`가 있어야 한다.
- 허용 태그 후보의 값·근거 위치 보정은 `review`의 `tagDecisions`로 수행한다.
- 부분 추출을 완전한 성공률로 집계하지 않고 필드별 누락률과 자동 등록 정밀도를 별도로 측정한다.
- 태그 후보도 필드별 후보와 같은 기준으로 근거·자동 판단 여부를 기록하며, 자동 검증 전에는 검색 결과에 반영하지 않는다.

## 9. 오류·재시도·복구

- Prompt Injection·Schema 이탈·과대 입력은 안전한 실패로 종료한다.
- 한 영상의 후보 수에는 상한이 있다. 상한보다 장소가 많으면 근거가 강한 후보만 남기고 나머지를 생략하되, 생략 사실을 결과에 표시하고 관리자 화면에 일부 맛집이 누락됐음을 경고한다. 표시 없는 조용한 누락을 허용하지 않는다.
- 상한을 지키지 않은 응답은 `SCHEMA` 실패로 기각하고 등록 단위를 만들지 않는다. 이 경우 관리자는 보완 텍스트 재시도 또는 기존 수동 등록으로 복구한다.
- timeout·429·5xx는 제공자 ADR의 제한 횟수와 전체 시간 예산 안에서만 재시도한다.
- Worker·단일 EC2 재시작 뒤 미종결 작업은 중복 정식 등록 없이 재개 또는 실패 종결한다.
- 비용·quota 상한을 넘으면 새 호출을 차단하고 관리자 수동 등록을 안내한다.
- 모델·Prompt·Schema 변경 재처리는 새 작업·후보를 만들고 이전 결과를 덮어쓰지 않는다.

## 10. 개인정보·저작권·비용

- 원본 영상과 전체 자막·전사 원문을 저장·재배포하지 않는다.
- 관리자가 직접 입력한 보완 텍스트는 비동기 Worker 재시작 복구를 위해 암호화된 임시 입력으로만 저장하고, 작업 종료 후 24시간 이내 삭제한다. 관리자 재시도는 새 텍스트를 다시 제출한다.
- 검수에 필요한 최소 근거 구간과 입력 해시만 관리자 전용으로 보존한다.
- Google Gemini API Free Tier의 global endpoint를 사용하며, Free Tier에서 입력·응답이 제품 개선에 사용될 수 있다는 정책과 원문 전송 범위를 활성화 전에 확인한다. `gemini-3.5-flash-lite`가 Free Tier에서 제공되지 않거나 billing account·결제수단 연결을 요구하면 호출하지 않는다. File API·context caching·자동 저장은 사용하지 않는다.
- 유료 호출·자동 유료 tier 승격·무료 quota 초과 후 과금 fallback은 금지한다. 모델·계정별 Free Tier quota의 80%에서 경보하고 100%에서 새 호출을 차단하며, 관리자 수동 등록 fallback을 제공한다.
- 입력 원문·Prompt 전문·AI 응답 본문·외부 인증정보를 일반 로그에 남기지 않는다.

## 11. 지표와 운영

- 대기·실행·성공·부분·실패·재시도·폐기 건수와 처리 시간, `WEBHOOK`·`ADMIN` 유입 경로별 backlog
- 필드별 정밀도·재현율·`UNKNOWN` 비율과 자동 등록 정밀도
- 태그 후보 정밀도·재현율, 자동 통합·차단·롤백율과 태그별 오탐률
- 중복 요청 수렴률, 검증 충돌률과 정식 등록 실패율
- 영상당 등록 단위 수, 등록 단위별 자동 확정률과 `PLACE_NOT_FOUND`·`PLACE_AMBIGUOUS`·`CATEGORY_UNRESOLVED` 차단 비율
- 카테고리 근거 순위별 사용 비율(Kakao 분류·메뉴 표현)과 관리자 사후 카테고리 보정률
- 모델·Prompt·Schema 버전별 Token·호출 수와 비용
- 자동 차단·롤백·사후 보정 처리 시간과 수동 등록 전환율

## 12. 완료 조건

- [ ] FR-AIEXTRACT-001~007과 BR-AIEXTRACT-001~011의 작업·중복·버전·태그 후보·자동 등록·예외 보정·감시·Gemini fallback 상태가 검증된다.
- [ ] 정상·부분·오류·중복·재시도·복구·자동 확정·자동 보류·롤백 화면 상태가 인수 테스트를 통과한다.
- [ ] 자동 검증 전 정식 저장·공개 0건과 검증 실패 시 부분 저장 0건을 검증한다.
- [ ] 다장소 영상에서 등록 단위별 독립 판정과 일부 단위 차단 시 나머지 단위의 정상 등록을 검증한다.
- [ ] 장소 자동 확정 조건(정확 1건 일치)과 `PLACE_NOT_FOUND`·`PLACE_AMBIGUOUS` 차단, 카테고리 1·2순위 근거와 `CATEGORY_UNRESOLVED` 차단을 검증한다.
- [ ] 관리자 등록 실행 한 번으로 맛집·유튜버·영상·방문 관계 4종이 등록되고, 기존 유튜버·영상이 재사용되며, 중간 실패 시 해당 등록 단위의 저장이 0건인 것을 검증한다.
- [ ] 정의된 예외 사유에서만 보조 입력 화면으로 전환하고 그 밖의 경우 관리자 입력을 요구하지 않는 것을 검증한다.
- [ ] Prompt Injection, 원문·비밀정보 로그 차단과 원본 영상·전체 자막 미보존을 검증한다.
- [ ] 비용 hard stop, 제공자 장애 격리와 기존 수동 등록 fallback을 검증한다.
- [ ] API·데이터·ADR·Workstream·담당자와 운영 절차가 승인된다.

## 13. 운영 리스크와 변경 게이트

- 모델 종료·quota·장애 시 자동 모델 교체 없이 실패·수동 등록으로 전환한다.
- 현재 운영 계약인 `gemini-3.5-flash-lite`·Prompt `P8`·Schema `S2` 중 하나라도 변경하면 새 후보 버전과 평가 보고서를 만든다.
- 예외 보정 처리량이 추출 요청량을 따라가지 못하는 경우 BACKFILL 작업을 먼저 중지하고 Webhook 실시간 작업을 우선한다.
- 태그 정의·별칭·근거 정책 변경은 데이터 계약과 평가 Dataset 버전을 함께 올린다.
- 장소 동일성 자동 확정 기준(`BR-AIEXTRACT-009`)을 완화하면 Critical 오연결 위험이 직접 올라간다. 기준 완화는 Release holdout 재평가와 restaurant 도메인 소유자 합의 없이 하지 않는다.
- 카테고리 매핑 표 변경은 기준정보 변경으로 취급하고 변경 이력을 남긴다. 매핑 표를 넓혀 `CATEGORY_UNRESOLVED`를 줄이는 변경은 오분류율을 함께 측정한다.

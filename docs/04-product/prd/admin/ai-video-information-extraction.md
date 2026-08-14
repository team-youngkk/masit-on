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
5. 관리자는 후보를 원문 근거와 기존 데이터에 대조해 확정하거나 폐기한다.
6. 확정 요청은 기존 Kakao·YouTube·Visit 검증을 통과해야 정식 저장된다.
7. AI·검증·정식 저장 실패는 정식 데이터 0건으로 끝나고 재검수·재시도 또는 기존 수동 등록으로 복구한다.

상태 전이는 [3차 확장 사용자 흐름](../../user-flows/third-expansion-user-flows.md#3-ai-영상-정보-추출과-관리자-검수)을 따른다.

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
| PR-AIEXTRACT-007 | 실패·상한 초과·모델 장애 시 정식 데이터를 변경하지 않고 수동 등록을 제공한다. | NFR-COST-001, NFR-AVAILABILITY-003 |
| PR-AIEXTRACT-010 | 자막 기반 태그 후보는 정규화·중복·근거 검증 후 관리자 승인 없이 `Visit`과 검색에 연결한다. | FR-AIEXTRACT-007, BR-AIEXTRACT-008 |

## 7. 관리자 화면과 상태

| 화면 상태 | 필수 표시 | 허용 동작 |
|---|---|---|
| 요청 전 | URL, 제공 텍스트, 데이터 전송·저장 안내 | 추출 요청 |
| `QUEUED` | 접수 시각, 버전, 대기 상태 | 새 중복 요청 금지 |
| `RUNNING` | 시작 시각, 진행 중, 취소 미지원 안내 | 상태 새로고침 |
| `SUCCEEDED`·완전 | 모든 후보 필드·태그, 근거, 신뢰도, 버전 | 자동 검증·등록·공개 |
| `SUCCEEDED`·부분 | 누락·`UNKNOWN` 필드·태그, 사용 가능한 근거, 불완전 경고 | 자동 보류·보완 재시도·예외 등록 |
| `FAILED` | 오류 범주, 시도 횟수, 마지막 실패 시각 | 허용 시 수동 재시도·수동 등록 |
| 검증 충돌 | 기존 데이터 후보, Kakao·YouTube 불일치, 정식 저장 0건 | 후보 수정·재검수·폐기 |
| `AUTO_CONFIRMED` | 자동 검증·등록·공개 결과, 버전·시각 | 결과 조회·사후 롤백 |
| `AUTO_BLOCKED` | 차단 사유·근거·시각 | 예외 보정·수동 등록 |
| `AUTO_REJECTED` | 거부 사유·근거·시각 | 새 작업 |

화면 구조는 [3차 확장 와이어프레임](../../wireframes/third-expansion-wireframes.md#4-ai-영상-추출-관리자-검수)을 따른다.

## 8. 부분 추출 정책

- 작업 실행과 출력 Schema 검증에 성공하면 일부 필드가 `UNKNOWN`이어도 작업 상태는 `SUCCEEDED`이고 결과 완전성은 `PARTIAL`로 표시한다.
- 맛집 동일성·주소·방문 근거 등 정식 등록에 필요한 값이 부족하면 자동 검증을 차단하고 부족한 항목을 표시한다.
- 관리자가 보완할 수 있는 범위는 맛집명·주소·메뉴·방문·허용 태그 후보의 값과 근거 위치로 제한하며, 보완값도 기존 외부 검증을 우회하지 않는다.
- 부분 추출을 완전한 성공률로 집계하지 않고 필드별 누락률과 자동 등록 정밀도를 별도로 측정한다.
- 태그 후보도 필드별 후보와 같은 기준으로 근거·자동 판단 여부를 기록하며, 자동 검증 전에는 검색 결과에 반영하지 않는다.

## 9. 오류·재시도·복구

- Prompt Injection·Schema 이탈·과대 입력은 안전한 실패로 종료한다.
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
- 모델·Prompt·Schema 버전별 Token·호출 수와 비용
- 자동 차단·롤백·사후 보정 처리 시간과 수동 등록 전환율

## 12. 완료 조건

- [ ] FR-AIEXTRACT-001~007과 BR-AIEXTRACT-001~008의 작업·중복·버전·태그 후보·자동 등록·예외 보정·감시·Gemini fallback 상태가 검증된다.
- [ ] 정상·부분·오류·중복·재시도·복구·자동 확정·자동 보류·롤백 화면 상태가 인수 테스트를 통과한다.
- [ ] 자동 검증 전 정식 저장·공개 0건과 검증 실패 시 부분 저장 0건을 검증한다.
- [ ] Prompt Injection, 원문·비밀정보 로그 차단과 원본 영상·전체 자막 미보존을 검증한다.
- [ ] 비용 hard stop, 제공자 장애 격리와 기존 수동 등록 fallback을 검증한다.
- [ ] API·데이터·ADR·Workstream·담당자와 운영 절차가 승인된다.

## 13. 운영 리스크와 변경 게이트

- 모델 종료·quota·장애 시 자동 모델 교체 없이 실패·수동 등록으로 전환한다.
- `gemini-3.5-flash-lite`·Prompt `P1`·Schema `S1` 변경은 새 후보 버전과 평가 보고서를 만든다.
- 예외 보정 처리량이 추출 요청량을 따라가지 못하는 경우 BACKFILL 작업을 먼저 중지하고 Webhook 실시간 작업을 우선한다.
- 태그 정의·별칭·근거 정책 변경은 데이터 계약과 평가 Dataset 버전을 함께 올린다.

---
id: ADR-AI-001
title: AI 영상 추출 후보 경계와 제공자 선택 기준
status: Accepted
decision_date: 2026-08-10
owners:
  - 김인안
related_requirements:
  - FR-AIEXTRACT-001
  - FR-AIEXTRACT-002
  - FR-AIEXTRACT-003
  - FR-AIEXTRACT-004
  - FR-AIEXTRACT-005
  - FR-AIEXTRACT-006
  - BR-AIEXTRACT-001
  - BR-AIEXTRACT-002
  - BR-AIEXTRACT-003
  - BR-AIEXTRACT-004
  - BR-AIEXTRACT-005
  - BR-AIEXTRACT-006
  - BR-AIEXTRACT-007
  - NFR-ACCURACY-002
  - NFR-INTEGRITY-006
  - NFR-PRIVACY-006
  - NFR-COST-001
  - NFR-SECURITY-007
related_documents:
  - ../../02-analysis/third-expansion-domain-boundaries.md
  - ../../02-analysis/third-expansion-workstreams.md
  - ../../04-product/prd/admin/ai-video-information-extraction.md
  - ../../08-planning/third-expansion-evaluation-strategy.md
  - ../architecture/arch-002-external-ports-adapters.md
  - ../security/sec-001-secrets-workload-identity.md
  - ../quality/obs-001-logging-observability.md
  - ../adr-backlog.md
  - ../adr-traceability.md
supersedes: []
superseded_by: null
---

# ADR-AI-001 AI 영상 추출 후보 경계와 제공자 선택 기준

## 1. 상태

Accepted. 자동 검증·정식 등록·롤백 경계와 원문 전체 미저장, `gemini-3.5-flash-lite` 사용, global endpoint, Free Tier 전용·유료 호출 금지 정책을 2026-08-14 확정했다. 기존 `gemini-3-flash-preview` 작업 이력은 보존하고 신규 작업부터 새 모델을 사용한다. 관리자 사전 승인은 요구하지 않는다.

## 2. 결정 요약

AI 영상 정보 추출은 자동 검증을 통과한 경우 기존 Restaurant·Creator·Video·Visit와 태그를 자동 생성·공개하고, 불확실한 결과만 보류하는 운영 경계로 둔다. AI 호출은 Provider Port/Adapter를 통해 격리하고, 결과와 자동 판단은 버전이 있는 구조화 Snapshot·감사 이력으로 관리한다.

Google Gemini API Free Tier의 `gemini-3.5-flash-lite`를 사용하고 공개 YouTube URL을 영상 입력으로 전달한다. Gemini 모델 문서상 영상 입력과 구조화 출력을 지원한다. 관리자 보완 텍스트는 Gemini 접근 제한·분석 실패·부분 추출의 fallback으로 사용한다. Prompt `P1`, 결과 Schema `S1`, global endpoint, File API·context caching 미사용을 고정한다. Free Tier quota 소진·결제 연결 요구·모델의 Free Tier 미지원 시에는 호출하지 않고 실패·수동 등록 fallback으로 전환한다.

## 3. 배경

관리자는 신규 영상 추가 화면에서 YouTube URL과 보완 텍스트를 제출하거나, 활성화된 채널의 Webhook으로 접수된 신규 영상 작업을 등록한다. 시스템은 맛집명·메뉴·주소·방문 후보와 근거를 추출하고 자동 검증한다. AI 결과는 확률적이고 잘못된 장소 연결과 방문 환각의 위험이 있으므로, 필수값·외부 기준정보·중복·근거·태그 정규화 검증을 통과한 경우에만 자동 공개한다.

또한 YouTube URL·영상 콘텐츠·입력 텍스트의 외부 제공자 전송은 저작권·개인정보·비용 영향을 가진다. Gemini의 공개 URL 입력은 원본 영상·전체 자막을 애플리케이션에 저장하지 않는 선택지를 제공한다. 운영 계정의 billing·quota와 데이터 처리 설정은 활성화 전에 확인한다.

## 4. 결정 문제

AI 호출 결과를 어디에 저장하고 어떤 조건에서 기존 정식 등록으로 연결할 것인가. 제공자 변경, 모델·Prompt·Schema 변경, 실패·중복·재처리에도 후보의 이력과 검수 결과를 재현할 수 있어야 한다.

## 5. 결정

### 5.1. 후보 경계

- 추출 작업, 후보 Snapshot, 검수 상태, 검수 사유와 감사 이벤트는 AI 후보 경계가 소유한다.
- Webhook과 관리자 신규 추가는 유입 경로만 다르고 동일한 후보·검수·정식 등록 경계를 사용한다. Webhook은 채널·영상 식별과 작업 접수만 수행한다.
- Webhook 작업은 공개 YouTube URL을 Gemini 영상 입력으로 전달하는 것을 기본 경로로 하며, 접근 불가·정책 제한·시간 초과·부분 추출은 관리자 보완 텍스트 재시도로 대체할 수 있다.
- 후보에는 영상 식별자, 입력 텍스트 해시, 후보 필드·태그, 필드별 신뢰도, 근거 위치 또는 `UNKNOWN`, 모델·Prompt·결과 Schema 버전, 생성 시각을 포함한다.
- 원본 영상·자동 수집 전체 자막·전체 응답은 저장하지 않는다. 관리자가 직접 입력한 보완 텍스트는 암호화된 임시 입력으로만 저장하고 작업 종료 후 24시간 이내 삭제한다. 근거는 `TIMESTAMP`(`startMs`·`endMs`) 또는 `TEXT_RANGE`(`startOffset`·`endOffset`·`sourceHash`) 위치만 저장하며 후보·자동 판단·사후 보정 이력은 1년 보존한다.
- 같은 영상·입력 해시·모델·Prompt·Schema 조합의 중복 요청은 같은 작업으로 수렴시킨다.

### 5.2. 제공자 경계

- Application은 AI SDK나 HTTP client를 직접 호출하지 않고 AI Provider Port만 사용한다.
- Adapter는 인증·timeout·rate limit·응답 변환·제공자 오류 분류를 담당한다.
- 모델의 자연어 출력은 허용된 Schema·Enum·필수 필드 검증을 통과한 구조화 후보로만 변환한다.
- 제공자 자동 failover는 초기 범위에서 사용하지 않는다. 승인되지 않은 모델로 조용히 전환하지 않는다.
- n8n 같은 외부 워크플로 도구는 초기 실행 경계에 도입하지 않는다. 작업 실행은 [ADR-EXT-003](ext-003-ai-extraction-async-reliability.md)의 애플리케이션 내부 Worker를 따른다.
- 키·토큰·원문 입력은 로그와 오류 응답에 남기지 않는다.

### 5.3. 정식 등록 경계

Worker가 후보를 자동 확정하기 전에 orchestration이 다음 검증을 순서대로 수행한다.

1. Kakao 장소 동일성 검증
2. YouTube 채널·영상 메타데이터 검증
3. 실제 방문 근거와 Visit 연결 검증
4. 중복·공개 상태·원자성 검증

이 중 하나라도 실패하면 후보·자동 판단 이력만 남기고 Restaurant·Creator·Video·Visit 정식 저장은 0건이어야 한다. 후보가 기존 등록과 중복이면 새 Entity를 만들지 않고 `AUTO_BLOCKED`로 보관하며 관리자 사후 보정·수동 등록 흐름으로 보낸다.

## 6. 고려하지 않은 선택지

- **자동 검증 없는 AI 결과 즉시 정식 저장**: 잘못된 장소 연결과 근거 없는 태그 공개를 허용하므로 제외한다. 자동 검증을 모두 통과한 결과의 관리자 승인 없는 정식 저장·공개는 채택한다.
- **YouTube 원본·자동 수집 자막 전체 저장**: 초기 범위·저작권·보존 경계를 넘으므로 제외한다. 관리자 보완 텍스트는 Worker 복구를 위한 암호화 임시 저장만 허용한다.
- **제공자 SDK를 Application에 직접 의존**: 모델·제공자 변경과 테스트 격리가 어려우므로 제외한다.
- **모델 변경 시 기존 후보 덮어쓰기**: 이전 결과와 회귀를 비교할 수 없으므로 제외한다.

## 7. 비용·품질·보안 게이트

| 게이트 | 확정 기준 | 실행·활성화에 필요한 증거 |
|---|---|---|
| 품질 | `EVAL-AI-*`로 정확도·재현율·자동 등록 정밀도·Critical 오연결을 측정 | [NFR-ACCURACY-002](../../01-requirements/non-functional-requirements.md#nfr-accuracy-002-ai-추출-정확도재현율자동-등록-정밀도) Release holdout 실행 |
| 정합성 | 자동 검증 전 정식 저장 0건, 통과 결과 자동 등록·공개 | 후보·검증·등록 원자성·롤백 통합 테스트 |
| 개인정보·저작권 | 원문·자막 전체 저장 금지, 제공자 전송 범위 최소화 | 운영 계정 처리 설정·로그 점검 |
| 비용 | Free Tier 전용·유료 호출 0원·quota 소진 시 hard stop | billing 미연결·무료 quota·결제 전환 차단과 장애 시 호출 차단 증거 |
| 보안 | Prompt Injection·Schema 이탈·비밀정보 노출 차단 | 악성 입력·출력 검증 테스트 |
| 영상 입력 | 공개 YouTube URL의 Gemini 분석과 timestamp 근거 | 공개·비공개 경계, 분석 실패·부분 추출·한국어 영상 표본 평가 |

## 8. 소유권과 영향

- WS-15 김인안이 후보·자동 등록·예외 보정·기존 등록 연결을 구현한다.
- 이우람이 외부 연동·비동기·운영 장애를 리뷰한다.
- 박진영이 후보 Snapshot·감사·정식 저장 0건과 평가 증거를 독립 검증한다.
- AI Provider Adapter는 [ADR-ARCH-002](../architecture/arch-002-external-ports-adapters.md)의 Port/Adapter 원칙을 따른다.
- 후보 물리 저장·마이그레이션은 Accepted 논리 데이터 계약과 물리 migration 계획을 함께 검증한다.

## 9. 검증 방법과 재검토 조건

- WireMock 또는 테스트 Adapter로 정상·부분·Schema 이탈·timeout·429·제공자 장애를 검증한다.
- 동일 요청 동시성, 재시작 복구, 자동 확정·자동 차단·사후 보정·폐기, 외부 검증 실패의 정식 저장 0건을 검증한다.
- 모델·Prompt·Schema 버전별 평가 결과와 비용을 비교한다.
- 제공자 정책·가격·리전·보존 조건 변경, 목표 미달, Critical 오연결, 운영 비용 초과 시 새 Proposed ADR 또는 rollback 절차를 검토한다.

## 10. 확정 운영 규칙

- 모델 `gemini-3.5-flash-lite`, global endpoint, Prompt `P1`, Schema `S1`을 사용한다.
- quota·장애 시 자동 failover하지 않고 실패·수동 등록 fallback을 사용한다.
- 근거는 timestamp 또는 text range 위치·입력 hash만 저장하며 원문은 저장하지 않는다.
- 호출 timeout·retry·hard stop은 [비동기 신뢰성 ADR](ext-003-ai-extraction-async-reliability.md)과 NFR 수치를 따른다.

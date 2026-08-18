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
  - BR-AIEXTRACT-009
  - BR-AIEXTRACT-010
  - BR-AIEXTRACT-011
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

Accepted. **단, 2026-08-18 결정(5.3절의 장소 동일성 판정 기준, 대표 카테고리 자동 선정, 등록 단위 경계, 후보 수 상한 300)은 `합의 대기` 상태다.**

합의는 이 결정을 담은 [PR #226](https://github.com/team-youngkk/masit-on/pull/226)의 소유자 승인으로 갈음한다. 별도 합의 절차를 두지 않는 이유는 계약 소유자 세 명(김인안·박진영·이우람)이 모두 이 PR의 리뷰어이고, 저장소 ruleset이 작성자를 제외한 2명 승인을 강제해 승인 기록이 곧 합의 근거로 남기 때문이다. restaurant 도메인 소유자 판단이 필요한 장소 동일성 기준 변경도 같은 리뷰에서 다룬다.

세 소유자의 승인이 달리면 병합 직전 커밋에서 이 표시와 다른 문서의 같은 표시를 제거해 Accepted로 확정한다. 승인 없이 병합하지 않는다. 그 앞의 결정은 종전대로 Accepted다.

합의가 불발되면 다음을 함께 되돌린다. 목록에 없는 문서를 남겨 두면 폐기된 결정을 계속 참조하게 되므로 전 범위를 열거한다.

| 문서 | 되돌릴 범위 |
|---|---|
| 요구사항 | `BR-AIEXTRACT-009`·`010`·`011` 전체, `BR-AIEXTRACT-001`의 등록 단위 분해와 후보 수 상한·절삭 표시 항목, `BR-AIEXTRACT-002`의 등록 단위 원자성 개정, `FR-AIEXTRACT-003`의 등록 단위·자동 판정·카테고리 보정 항목 |
| 제품 | `PR-AIEXTRACT-011`~`017`, PRD의 관리자 실행 흐름·등록 단위 화면 상태·후보 절삭 경고·보완 경로 구분, 사용자 흐름의 등록 단위 판정 서술, 와이어프레임의 자동 판정 결과·예외 화면 분리·절삭 배너 |
| API 계약 | `registrationUnits`·`candidateTruncated`·`manualOverrideType`, 등록 단위 일괄 등록(3.6절), `review`의 `unitId`·`supplements`·`ADJUST_CATEGORY`, 최상위 요약 규칙, `recoveryPaths`, `AIEXTRACT_UNIT_ID_REQUIRED`·`AIEXTRACT_UNIT_NOT_FOUND`·`AIEXTRACT_CONCURRENT_REQUEST_CONFLICT` |
| 데이터 계약 | `ai_registration_unit`, `ai_registration_unit_review`, `food_category_mapping`, `ai_candidate_snapshot.candidate_truncated`와 관련 추적표 행 |
| 계획 | `TST-E3-AI-005`~`008`, 손실 분석 9절의 네 결정과 9.1절 |

자동 검증·정식 등록·롤백 경계와 원문 전체 미저장, `gemini-3.5-flash-lite` 사용, global endpoint, Free Tier 전용·유료 호출 금지 정책을 2026-08-14 확정했다. 2026-08-16에는 관리자 보완 텍스트의 검증 가능한 `TEXT_RANGE`를 식당 기준정보 후보에 사용할 수 있도록 Prompt를 `P3`로 올렸고, 실측 범위 산출 편차를 제거하기 위해 서버가 정확한 줄 단위 `referenceSpans`를 제공하는 `P4`, 필드 오연결을 줄이는 `fieldHint`를 추가한 `P5`, 방문 후보 값을 명시적인 완료형 물리 방문 문장으로 제한한 `P6`, 방문 문장의 종결 마침표를 수신 정규화와 일치시킨 `P7`로 올렸다. 운영·개발·공유 데이터베이스에 V4가 아직 적용되지 않았으므로 최종 V4 제약은 `gemini-3.5-flash-lite`만 저장하도록 한다. 기존 Prompt `P1`부터 `P6`까지의 작업과 `gemini-3-flash-preview` 평가 자산은 역사적 이력으로 보존한다. 관리자 사전 승인은 요구하지 않는다. 2026-08-18에는 자동 등록을 실제로 성립시키기 위해 판정 주체를 확정했다. 장소 동일성은 AI가 제출한 Kakao 장소 URL이 아니라 상호명·주소 기반 시스템 검색으로 판정하고, 대표 음식 카테고리는 확정한 Kakao 장소 분류와 메뉴 표현으로 자동 선정하며, 판정과 등록의 단위는 작업이 아니라 장소 단위 등록 단위다.

## 2. 결정 요약

AI 영상 정보 추출은 자동 검증을 통과한 경우 기존 Restaurant·Creator·Video·Visit와 태그를 자동 생성·공개하고, 불확실한 결과만 보류하는 운영 경계로 둔다. 판정 단위는 장소 단위 등록 단위이며, 장소 동일성과 대표 음식 카테고리는 관리자 입력 없이 시스템이 결정한다. AI 호출은 Provider Port/Adapter를 통해 격리하고, 결과와 자동 판단은 버전이 있는 구조화 Snapshot·감사 이력으로 관리한다.

Google Gemini API Free Tier의 `gemini-3.5-flash-lite`를 사용하고 공개 YouTube URL을 영상 입력으로 전달한다. Gemini 모델 문서상 영상 입력과 구조화 출력을 지원한다. 관리자 보완 텍스트는 Gemini 접근 제한·분석 실패·부분 추출의 fallback으로 사용한다. Prompt `P7`, 결과 Schema `S1`, global endpoint, File API·context caching 미사용을 고정한다. Free Tier quota 소진·결제 연결 요구·모델의 Free Tier 미지원 시에는 호출하지 않고 실패·수동 등록 fallback으로 전환한다.

## 3. 배경

관리자는 신규 영상 추가 화면에서 YouTube URL과 보완 텍스트를 제출하거나, 활성화된 채널의 Webhook으로 접수된 신규 영상 작업을 등록한다. 시스템은 맛집명·메뉴·주소·방문 후보와 근거를 추출하고 자동 검증한다. AI 결과는 확률적이고 잘못된 장소 연결과 방문 환각의 위험이 있으므로, 필수값·외부 기준정보·중복·근거·태그 정규화 검증을 통과한 경우에만 자동 공개한다.

또한 YouTube URL·영상 콘텐츠·입력 텍스트의 외부 제공자 전송은 저작권·개인정보·비용 영향을 가진다. Gemini의 공개 URL 입력은 원본 영상·전체 자막을 애플리케이션에 저장하지 않는 선택지를 제공한다. 운영 계정의 billing·quota와 데이터 처리 설정은 활성화 전에 확인한다.

## 4. 결정 문제

AI 호출 결과를 어디에 저장하고 어떤 조건에서 기존 정식 등록으로 연결할 것인가. 제공자 변경, 모델·Prompt·Schema 변경, 실패·중복·재처리에도 후보의 이력과 검수 결과를 재현할 수 있어야 한다.

여기에 더해 장소 동일성과 대표 음식 카테고리를 누가 판정할 것인가를 정해야 한다. AI는 Kakao 장소 식별자를 만들 수 없고 한 영상에 장소가 여러 곳 등장하는 것이 정상이므로, 이 두 판정을 관리자 입력에 의존시키면 자동 등록 경계가 사실상 동작하지 않는다.

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
- Prompt `P7`은 관리자 보완 텍스트를 계속 비신뢰 사용자 데이터로 격리하고, 서버가 라벨을 제외한 정확한 UTF-16 `referenceSpans`와 허용 필드 `fieldHint`를 함께 제공한다. 식당명·메뉴·주소·Kakao 장소 URL만 보완 텍스트의 SHA-256과 정확히 일치하는 범위의 `TEXT_RANGE` 후보로 허용하며, 보완 텍스트의 방문 주장은 자동 확정하지 않는다. 방문 후보 값은 명시적인 완료형 물리 방문 문장으로 제한하고 실제 방문은 영상의 채널 제작자 주장과 `TIMESTAMP`로만 확정한다. 태그는 보완 텍스트를 출처로 삼을 수 없으므로 구조가 유효한 태그의 `TEXT_RANGE` 근거는 응답 전체를 기각하지 않고 그 태그만 `UNKNOWN`으로 낮춰 `AUTO_REJECTED`로 기록한다.

### 5.3. 정식 등록 경계

Worker는 후보를 장소 단위 등록 단위로 나눈 뒤, 등록 단위마다 orchestration이 다음 검증을 순서대로 수행한다.

1. Kakao 장소 동일성 검증
2. 대표 음식 카테고리 결정
3. YouTube 채널·영상 메타데이터 검증
4. 실제 방문 근거와 Visit 연결 검증
5. 중복·공개 상태·원자성 검증

1번의 판정 입력은 AI가 만든 상호명과 주소·위치 표현이다. **AI 후보에 Kakao 장소 URL을 요구하지 않는다.** orchestration이 상호명으로 Kakao 장소를 검색하고, 정규화 상호명 완전일치와 도로명주소 시·구 일치를 함께 만족하는 결과가 정확히 1건일 때만 그 장소로 동일성을 확정한다. 0건은 `PLACE_NOT_FOUND`, 2건 이상은 `PLACE_AMBIGUOUS`로 차단한다. 관리자가 URL을 직접 제출하는 수동 등록 경로의 검증 기준은 그대로 둔다.

2번은 1번에서 확정한 Kakao 장소의 분류 표현을 1순위, AI 메뉴 후보 표현을 2순위 근거로 기준정보 매핑 표에 대조해 공통 10개 값 중 하나를 정한다. 두 근거 모두 대응 값을 찾지 못하면 임의 기본값을 쓰지 않고 `CATEGORY_UNRESOLVED`로 차단한다.

후보 수 상한은 100에서 300으로 올린다. 상한 초과의 정상 동작은 응답 기각이 아니라 모델의 자체 절삭이므로, 절삭이 일어난 결과는 그 사실을 표시해 관리자가 누락을 인지할 수 있게 한다. 후보 수가 상한과 같으면 표시가 없어도 절삭 가능으로 취급한다. 이 변경은 시스템 지시와 결과 Schema를 함께 바꾸므로 구현 시 Prompt를 `P8`, 결과 Schema를 `S2`로 올리고 수신 검증기·`ai_candidate_snapshot`·관리자 응답·와이어프레임을 같은 PR에서 갱신한다. 현재 운영 계약은 `P7`·`S1`이며 이 문서의 다른 절이 서술하는 버전은 배포된 상태를 가리킨다. 근거와 파급 범위는 [후보 손실 분석 9.1절](../../08-planning/third-expansion-ai-candidate-loss-analysis.md)에 있다.

검증 중 하나라도 실패하면 그 등록 단위는 후보·자동 판단 이력만 남기고 Restaurant·Creator·Video·Visit 정식 저장 0건으로 끝난다. 판정과 원자성 경계는 등록 단위이며, 한 단위의 실패가 같은 작업에서 이미 통과한 다른 단위를 되돌리지 않는다. 후보가 기존 등록과 중복이면 새 Entity를 만들지 않고 `AUTO_BLOCKED`로 보관한다. 관리자가 할 수 있는 것은 기존 등록 결과를 확인하는 것(`EXISTING_RESOURCE`)뿐이며, 사후 보정·재추출·재실행·수동 등록으로 전환하는 경로는 없다.

등록 단위의 등록은 Worker가 자동 실행하거나 관리자가 상세 화면에서 실행한다. 두 경로는 실행 주체만 다르고 같은 판정 규칙·같은 orchestration 명령을 사용하며, 관리자 실행이라는 이유로 기준을 완화하지 않는다. 관리자 실행 경로도 맛집·유튜버·영상·방문 관계 4종을 한 번에 등록하고, 단계별 관리자 입력을 요구하지 않는다. `BR-AIEXTRACT-011`이 정의한 예외에서만 보조 입력 경로로 전환한다.

장소 판정 근거와 카테고리 근거 순위, 실행 주체는 감사 이력으로 남긴다. 자동 확정 기준 완화는 Critical 오연결 지표에 직접 영향을 주므로 Release holdout 재평가와 restaurant 도메인 소유자 합의를 거친다.

## 6. 고려하지 않은 선택지

- **자동 검증 없는 AI 결과 즉시 정식 저장**: 잘못된 장소 연결과 근거 없는 태그 공개를 허용하므로 제외한다. 자동 검증을 모두 통과한 결과의 관리자 승인 없는 정식 저장·공개는 채택한다.
- **YouTube 원본·자동 수집 자막 전체 저장**: 초기 범위·저작권·보존 경계를 넘으므로 제외한다. 관리자 보완 텍스트는 Worker 복구를 위한 암호화 임시 저장만 허용한다.
- **제공자 SDK를 Application에 직접 의존**: 모델·제공자 변경과 테스트 격리가 어려우므로 제외한다.
- **모델 변경 시 기존 후보 덮어쓰기**: 이전 결과와 회귀를 비교할 수 없으므로 제외한다.
- **AI 후보에 Kakao 장소 URL 요구**: 모델이 Kakao 장소 식별자를 알 수 있는 경로가 없어 자동 등록이 구조적으로 불가능하므로 제외한다. 상호명·주소 기반 시스템 검색으로 대체한다.
- **Kakao 검색 최상위 결과 자동 채택**: 동명 이업소 오연결 위험이 커 Critical 오연결 0건 목표와 충돌하므로 제외한다. 일치 조건을 만족하는 결과가 정확히 1건일 때만 확정한다.
- **복수 후보 중 대표 1건만 자동 등록**: 한 영상의 나머지 맛집이 수동으로 남아 반복 작업이 사라지지 않으므로 제외한다. 등록 단위별 독립 판정을 채택한다.
- **카테고리 미해결 시 `기타` 기본값 사용**: 오분류를 정상 등록으로 감추므로 제외한다. 근거를 찾지 못하면 차단한다.

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

- 모델 `gemini-3.5-flash-lite`, global endpoint, Prompt `P7`, Schema `S1`을 사용한다.
- Prompt는 2026-08-14에 `P1`에서 `P2`로 올렸다. 송신 요청 Schema가 수신 검증 계약을 표현하지 못해 태그 후보와 완결성 결합에서 응답이 기각되던 결함을 고치면서 시스템 지시와 요청 Schema 표현이 함께 바뀌었기 때문이다. 수신 계약 자체는 바뀌지 않았으므로 결과 Schema는 `S1`을 유지한다. `BR-AIEXTRACT-004`가 요구하는 버전별 재현성을 지키기 위해 라벨을 유지하지 않았고, `P1` 후보 Snapshot은 덮어쓰거나 폐기하지 않는다. `aiextract-golden-v1.0.0` 평가 자산은 `gemini-3-flash-preview`·`P1` 기준의 역사적 fixture로 보존하며 운영 작업 계약에 포함하지 않는다. 근거는 [AI 후보 손실 분석](../../08-planning/third-expansion-ai-candidate-loss-analysis.md)에 있다.
- Prompt는 2026-08-16에 `P2`에서 `P3`로 올렸다. P2 실측에서 영상에 직접 노출되지 않는 도로명주소와 Kakao 장소 URL이 관리자 보완 텍스트에 있어도 누락되어 자동 등록 경로에 진입하지 못했다. P3는 네 식당 기준정보 필드에만 보완 텍스트의 검증 가능한 `TEXT_RANGE`를 허용하고, 방문 근거는 영상 `TIMESTAMP`로 제한한다. 수신 Schema는 기존 `TEXT_RANGE`를 그대로 사용하므로 `S1`을 유지하며, P2 작업과 Snapshot은 생성 당시 의미로 보존한다.
- 같은 날 P3 실측 두 건은 Gemini가 엄격한 범위 계약과 일치하는 후보를 반환하지 않아 `SCHEMA`로 차단됐다. P4는 서버가 보완 텍스트의 비어 있지 않은 줄마다 정확한 UTF-16 `referenceSpans`를 제공해 모델의 범위 계산 편차를 줄인다. 수신 검증은 정확 일치와 방문 `TIMESTAMP` 제한을 그대로 유지하며 P3 작업은 역사적 이력으로 보존한다.
- P4 실측에서는 범위는 정확해졌지만 URL 줄을 주소 필드로 연결하는 변동성이 확인됐다. P5는 명시적 라벨이 있는 줄에 허용된 `fieldHint`를 붙이고 라벨을 제외한 값 범위를 제공한다. P4 작업과 Snapshot은 생성 당시 의미로 보존한다.
- P5 실측에서는 네 기준정보를 정확히 추출했지만 방문 후보가 완료된 물리 방문을 명시하지 않아 차단됐다. P6는 방문 후보 값과 근거 Schema를 분리하고 완료형 물리 방문 문장과 영상 `TIMESTAMP`만 허용한다. P6 실측에서 자동 검증·정식 등록·공개 조회까지 성공했으며 P5 작업과 Snapshot은 생성 당시 의미로 보존한다.
- P6 송신 Schema의 방문 문장 정규식이 종결 마침표와 후행 공백을 거부해, 수신 정규화가 어차피 제거하는 문자 때문에 자연스러운 방문 문장이 제약 디코딩 단계에서 배제될 수 있었다. P7은 그 정규식의 후행 허용 문자를 수신 `normalizeClaim`이 제거하는 범위(공백과 `.`·`。`)와 일치시킨다. `!`와 `?`는 수신에서 차단 문맥으로 다루므로 계속 거부한다. 시스템 지시와 수신 계약은 바뀌지 않으므로 결과 Schema는 `S1`을 유지하며, P6 작업과 Snapshot은 생성 당시 의미로 보존한다.
- quota·장애 시 자동 failover하지 않고 실패·수동 등록 fallback을 사용한다.
- 근거는 timestamp 또는 text range 위치·입력 hash만 저장하며 원문은 저장하지 않는다.
- 호출 timeout·retry·hard stop은 [비동기 신뢰성 ADR](ext-003-ai-extraction-async-reliability.md)과 NFR 수치를 따른다.

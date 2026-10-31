---
title: AI 추출 실패·보류 진단 정보와 관리자 표시 설계
status: Draft
related_documents:
  - README.md
  - ../01-requirements/functional-requirements.md
  - ../01-requirements/business-rules.md
  - ../01-requirements/non-functional-requirements.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../05-specs/data/migration-plan.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../07-adr/integration/ext-003-ai-extraction-async-reliability.md
  - ../troubleshooting/ops-2026-08-22-ai-supplement-validation-reason.md
---

# 이슈 #445 AI 추출 실패·보류 진단 정보와 관리자 표시 설계

> 구현 범위 메모: 현재 변경은 Gemini HTTP 상태와 정규화 범주를 작업·시도에 기록하고 조회 시 `failureDiagnostic`을 구성한다. 단위의 `blockDiagnostic`은 기존 `blockReason`과 해당 단위의 결속 후보에서 파생한다. 단위가 없는 차단의 `snapshotDiagnostic`과 보충 검증의 일시적 `validationDiagnostic`도 기존 안전한 근거에서 파생한다. 아래 5~6절의 `providerCode` 수집과 별도 JSON 진단 컬럼은 향후 계약·구현 검토 항목이며 현재 적용된 것으로 간주하지 않는다.

## 1. 목적과 결정 상태

[이슈 #445](https://github.com/team-youngkk/masit-on/issues/445)의 목표는 관리자가 목록·상세 화면에서 **대표 사유 하나, 확인된 근거, 가능한 해결 방법**을 읽고 다음 행동을 결정하는 것이다. 요청자는 2026-10-30 진단 저장과 관리자 API 확장 방향 및 문서화를 요청했다. 이 문서는 구현 전 계약 변경 검토안이며, 기존 Accepted 계약의 필드·테이블이 이미 변경되었거나 운영에 배포되었다는 뜻이 아니다.

세부 API·데이터 계약은 담당 소유자 검토 후 관련 정본에 반영한다. 기존 API 3.3절의 실패 정보 제한을 확장해야 하므로 이 문서만으로 기존 제한을 폐기하지 않는다. 검토 대상은 아래 필드·허용 코드·대표 사유 선택·저장 및 보존 방식이다.

| 근거 | 반영할 내용 |
|---|---|
| FR-AIEXTRACT-002 | 작업·시도별 실패 사유와 불확실성 표시 |
| FR-AIEXTRACT-003 / BR-AIEXTRACT-011 | 식당별 보류 사유, 기존 상태와 최신 재검증 실패 구분 |
| PRD-ADMIN-002 | 목록 요약, 상세 진단, 부분 등록 및 복구 안내 |
| API-ADMIN-AIEXTRACT-001 / DATA-E3-AI-001 | 진단 객체 추가, 과거 데이터 호환, 안전한 저장 |
| ADR-AI-001 / ADR-EXT-003 | 원문 비저장, 호출·재시도 정책 유지, 비동기 실패 추적 |

## 2. 관찰 결과와 기존 작업

2026-10-30 관리자 화면에서 `UPSTREAM` 1회 실패, `QUOTA_HARD_STOP` 0회 실패, 주소 누락·장소 미발견·외부 조회 실패로 인한 `AUTO_BLOCKED`를 확인했다. 고양이부엌은 주소·위치, 바다수산은 주소 누락으로 표시되었다. 한 작업은 `AUTO_BLOCKED`이지만 실제로 10곳 중 6곳이 등록되었다. 이는 화면 관찰이며 당시 HTTP 응답과 운영 배포 버전은 확인하지 못했다.

로컬 Gemini 어댑터는 여러 HTTP 오류를 `UPSTREAM`으로 합친다. 저장되지 않은 과거 HTTP 상태·제공자 세부 사유는 복원할 수 없다. 운영 배포와 로컬 코드가 같다고 가정하지 않는다.

- [#420](https://github.com/team-youngkk/masit-on/issues/420)은 외부 검증 단계 진단·집계, [#421](https://github.com/team-youngkk/masit-on/issues/421)은 자동 보완·제한 재추출 작업이며 모두 완료 상태다. 구현 전에 최신 develop의 실제 필드·저장 구조를 확인하고 재사용한다.
- [보충 검증 실패 사유 손실 기록](../troubleshooting/ops-2026-08-22-ai-supplement-validation-reason.md)에 따라 `blockReason`과 `validationFailureReason`을 합치지 않는다. 보충값이 있는 재실행은 기존 복구 계약을 따른다.
- 기존 작업트리에는 #421 관련 문서 변경이 있다. 해당 변경을 되돌리거나 이 설계의 구현 결과로 간주하지 않는다.

## 3. 대표 사유 선택 원칙

1. 작업 실행 실패와 등록 단위 보류를 분리한다. `SUCCEEDED` 작업에 보류 단위가 있다고 작업 실패 사유를 만들지 않는다.
2. 작업의 대표 사유는 작업을 최종 실패로 전환한 이벤트 하나다. 재시도 중 먼저 발생한 오류는 해당 시도 이력에 남긴다. 마지막 호출 뒤 앱 quota에 걸렸다면 작업 사유는 앱 quota이고, 이전 시도 사유는 그때의 Provider 오류다.
3. 등록 단위는 기존 판정이 저장한 `blockReason`을 대표 사유로 유지한다. 추가 진단은 해당 판정의 근거이며 판정 순서·등록 기준을 변경하지 않는다.
4. 한 단위의 같은 판정에서 복수 누락 필드가 있으면 사유는 `MISSING_REQUIRED_FIELD` 하나, 근거는 모든 확인된 누락 필드다. 화면 순서는 `restaurantName`, `address`, `location`, `menu`, `visitEvidence`, 그 밖의 허용 필드 코드 순으로 고정한다. 이 순서는 필수 필드 정의를 변경하지 않는다.
5. 서로 다른 단위의 문제를 한 원인으로 합치지 않는다. 작업 목록에는 대표 문구 “등록 보류 4곳”과 사유별 단위 수를 보여주고, 상세에서 각 단위의 대표 사유를 읽는다. 미실행 검증 단계의 문제를 예측해 추가하지 않는다.
6. HTTP 상태만으로 결제 미설정·구독 부재·모델 미지원 등을 확정하지 않는다. 구조화 근거가 없으면 관측 가능한 수준으로 표현한다. 개인 Gemini 구독 여부는 이 진단의 입력이 아니다.

## 4. 진단 객체 제안

`diagnostic`은 기존 상태·오류 필드에 추가하는 선택적 읽기 전용 객체다. 실제 직렬화 위치는 기존 DTO를 확인해 아래 계약으로 통일한다. API 이름은 camelCase, 저장 JSON 키도 동일하게 사용한다.

| 필드 | 형식 | 의미와 제한 |
|---|---|---|
| `version` | integer | 신규 객체는 `1` |
| `reasonCode` | string | 아래 허용 사유 또는 기존 등록 단위 사유. 필수 |
| `service` | enum | `GEMINI`, `KAKAO`, `YOUTUBE`, `APPLICATION`, `UNKNOWN` |
| `stage` | enum | `QUOTA_CHECK`, `PROVIDER_REQUEST`, `RESPONSE_VALIDATION`, `CANDIDATE_VALIDATION`, `PLACE_SEARCH`, `PLACE_VERIFICATION`, `CATEGORY_RESOLUTION`, `VIDEO_LOOKUP`, `VISIT_VALIDATION`, `REGISTRATION`, `WORKER_RECOVERY`, `UNKNOWN` |
| `httpStatus` | integer 또는 null | 실제 받은 100~599 상태. 미응답·내부 오류는 null |
| `providerCode` | string 또는 null | 어댑터별 명시적 허용 목록에 일치하는 구조화 코드만. 자유 문자열 금지 |
| `missingFields` | string 배열 | 같은 단위·Snapshot에서 검증된 필드 코드. 중복 제거, 최대 16개, 근거 없으면 빈 배열 |
| `observedAt` | 날짜·시간 또는 null | 실패 관측 시각. 과거 미기록은 null |
| `traceId` | string 또는 null | 해당 실행에서 서버가 생성한 추적 ID. 없는 과거 값은 생성해 채우지 않음 |

한국어 제목·설명·해결 안내는 코드와 구조화 근거에서 파생하며 DB에 Provider 문장이나 번역 문장을 저장하지 않는다. quota 사용량·시간 구간, 장소 후보 수 등 추가 근거는 기존 안전한 필드가 있는 경우 활용한다. 해당 필드가 없으면 수치를 추정하지 않으며 별도 계약 확정 없이 임의 JSON 키를 추가하지 않는다.

### 4.1 실행 실패 사유와 표시

| `reasonCode` | 확인 조건 | 대표 표시 / 안내 |
|---|---|---|
| `APPLICATION_QUOTA_EXHAUSTED` | 앱의 quota 검사로 실행 중단 | 맛잇온 호출 한도 도달 / 적용 한도·시간 구간 확인 |
| `PROVIDER_AUTHENTICATION_FAILED` | HTTP 401 | Gemini 인증 실패 / API 키 설정 확인. 키 만료·누락은 추가 근거 없이 단정하지 않음 |
| `PROVIDER_PAYMENT_REQUIRED` | HTTP 402 | Gemini API 결제 필요 / API 프로젝트 결제 상태 확인 |
| `PROVIDER_ACCESS_DENIED` | HTTP 403 | Gemini 접근 거부 / 권한·프로젝트 이용 상태 확인 |
| `PROVIDER_CONFIGURATION_OR_POLICY_BLOCKED` | HTTP 403 없이 로컬 설정·정책 검사에서 차단, 또는 과거 세부 기록 없음 | Gemini 호출 차단 / AI 설정·무료 등급 검증 상태 확인. 제공자 접근 거부로 단정하지 않음 |
| `PROVIDER_RATE_LIMITED` | HTTP 429 | Gemini 요청 한도 초과 / 실제 적용 한도 확인. 분당·일일 한도는 근거 없으면 구분하지 않음 |
| `PROVIDER_REQUEST_REJECTED` | HTTP 400 또는 다른 분류가 없는 4xx | Gemini 요청 거부 / 요청·프로젝트 조건 확인 |
| `PROVIDER_RESOURCE_NOT_FOUND` | HTTP 404 | Gemini 모델 또는 자원 미발견 / 모델명·경로 확인. 모델 미발견으로 단정하지 않음 |
| `PROVIDER_SERVER_ERROR` | HTTP 5xx | Gemini 서버 오류 / 기존 정책에 따른 재시도 안내 |
| `EXTERNAL_TIMEOUT` | timeout 예외 또는 HTTP 408 | 해당 서비스 응답 시간 초과 |
| `EXTERNAL_NETWORK_ERROR` | 응답 전 연결·전송 실패로 확인 | 해당 서비스 통신 실패. JSON 파싱 오류를 네트워크 오류로 분류하지 않음 |
| `RESPONSE_INVALID` | 수신 응답 형식·Schema 검증 실패 | 응답 형식 검증 실패 |
| `INTERNAL_PROCESSING_ERROR` | 내부 처리 결함으로 확인 | 내부 처리 오류 / traceId로 운영 확인 |
| `DETAIL_NOT_RECORDED` | 과거 범주만 있고 상세 진단 없음 | 세부 원인 미기록 / 기존 오류 범주와 시도 수 표시 |
| `UNKNOWN_FAILURE` | 새 실행에서 위 조건으로 확인할 수 없음 | 원인 확인 필요 / 확인된 서비스·단계만 표시 |

이 코드는 기존 `error.category`를 대체하거나 자동 재시도 정책을 결정하지 않는다. `providerCode` 초기 허용 후보는 `INVALID_ARGUMENT`, `FAILED_PRECONDITION`, `UNAUTHENTICATED`, `PERMISSION_DENIED`, `NOT_FOUND`, `RESOURCE_EXHAUSTED`, `DEADLINE_EXCEEDED`, `INTERNAL`, `UNAVAILABLE`이며 실제 사용 API의 구조화 응답 fixture로 검증한 값만 활성화한다. `FAILED_PRECONDITION`만 보고 결제 필요로 바꾸지 않는다. 알 수 없는 코드는 null로 버린다.

응답 본문 읽기는 크기·시간 상한을 가진 스트림 처리로 제한한다. 본문이 비정상·초과·미지원이면 HTTP 기반 분류를 보존하고 원문을 남기지 않는다. 어댑터는 응답과 리소스를 반드시 닫는다. 더 세밀한 결제·키·모델 사유를 추가할 때는 구조화 근거와 fixture, 계약 코드가 함께 있어야 한다.

### 4.2 등록 단위 사유와 복구

| 기존 사유 | 표시할 근거 | 복구 안내 기준 |
|---|---|---|
| `MISSING_REQUIRED_FIELD` | 해당 단위의 누락 필드 | 기존 보완 텍스트 재추출·수동 등록 경로 |
| `PLACE_NOT_FOUND` | 장소 판정 단계, 확인 가능한 기존 검색 근거 | 기존 장소 보충·복구 계약 |
| `PLACE_AMBIGUOUS` | 장소 후보를 하나로 확정하지 못함 | 기존 장소 보충·복구 계약 |
| `CATEGORY_UNRESOLVED` | 카테고리 판정 단계 | 기존 카테고리 보충 계약 |
| `VISIT_EVIDENCE_REQUIRED` | 방문 근거 부족 | 기존 재추출·수동 등록 허용 범위 |
| `DUPLICATE_CONFLICT` | 기존 등록 결과 | `EXISTING_RESOURCE`만 복구 안내. 재시도·보충 등록을 새로 허용하지 않음 |
| `EXTERNAL_SERVICE_ERROR` | 확인된 서비스·단계와 timeout/HTTP/통신 사유 | 기존 `recoveryPaths`에 따른 재실행 |

실제 사용 가능한 행동의 정본은 기존 API의 `recoveryPaths`·`requiredSupplements`다. 위 표는 새 권한이나 실행 동작을 추가하지 않는다. `DISCARD` 등 기존 공통 종결 동작은 기존 계약을 유지한다. 내부 결함을 외부 장애로 설명하지 않으며, 필요한 기존 범주 확장은 계약 검토에서 별도로 확정한다.

## 5. API와 화면 제안

- 목록 `items[]`와 작업 상세에 `diagnostic`을 추가한다. 실행 성공·대기·실행 중이고 최종 실패가 없으면 null이다. 목록의 진단은 상세와 같은 최종 실패 이벤트를 가리킨다.
- 상세 `attempts[]`에 `diagnostic`을 추가한다. 실패 시도의 관측 근거를 보존하고, 성공 시도는 null이다. 후속 성공으로 과거 실패 시도를 삭제하지 않는다.
- 상세 `registrationUnits[]`에 `diagnostic`을 추가한다. `reasonCode`는 해당 `blockReason`을 유지한다. 외부 오류의 세부 유형은 별도 `causeCode` 선택 필드로 4.1절 코드 중 실제 확인된 값 하나만 사용하도록 등록 단위 진단에 한해 확장한다.
- 보충 입력 실패 응답에는 기존 `details.validationFailureReason`과 같은 요청의 `details.validationDiagnostic`을 연결한다. 이는 일시적 응답이며 기존 단위 진단을 덮어쓰거나 감사 이력을 새로 만드는 근거가 아니다.
- 작업에 단위가 없는 Snapshot 보류는 별도 `snapshotDiagnostic` 선택 필드로 표시한다. 실행 실패와 혼동하지 않고, 단위가 없는 이유가 확인되지 않으면 미기록으로 표현한다.
- 목록의 보류 집계는 기존 #420 응답을 우선 재사용한다. 없다면 `blockReasonCounts: [{ reasonCode, count }]`를 추가하며 현재 `AUTO_BLOCKED` 단위만 각 1회 집계한다. 실행 실패 작업의 단위 0건을 보류 1건으로 세지 않는다.
- 현재 등록 수는 등록 결과가 유지되는 단위 수다. 롤백·폐기·거부를 등록 성공으로 세지 않는다. 등록 6/10, 보류 4/10이면 “6곳 등록 · 4곳 보류”가 먼저 보인다.
- 화면은 코드와 근거로 설명을 만들고, 알 수 없는 코드·버전은 “지원되지 않는 진단 코드”로 안전하게 표시한다. 상세 정보가 없는 것을 성공·결제 문제로 해석하지 않는다.
- 재시도 가능 표시의 의미를 “기술적으로 일시적인 오류”와 “현재 허용된 사용자 복구 행동”으로 구분한다. 기존 API의 행동 허용 조건이 우선하며 해결되지 않은 결제·권한 오류에 즉시 재시도를 권하지 않는다.

다음은 전체 응답이 아닌 신규 필드 예시다.

```json
{
  "diagnostic": {
    "version": 1,
    "reasonCode": "PROVIDER_ACCESS_DENIED",
    "service": "GEMINI",
    "stage": "PROVIDER_REQUEST",
    "httpStatus": 403,
    "providerCode": "PERMISSION_DENIED",
    "missingFields": [],
    "observedAt": "2026-10-30T11:00:00+09:00",
    "traceId": null
  }
}
```

표시: “Gemini 접근 거부 · HTTP 403 / PERMISSION_DENIED · 세부 원인 미제공”. 안내: “API 프로젝트 권한과 이용 상태를 확인해 주세요.” 결제 미설정이라고 단정하지 않는다.

## 6. 저장·보안·호환 제안

| 대상 | 제안 저장 | 갱신 원칙 |
|---|---|---|
| `ai_extraction_job` | nullable `failure_diagnostic jsonb` | 최종 실패 전환과 같은 트랜잭션·lease 소유 검증으로 저장 |
| `ai_extraction_attempt` | nullable `failure_diagnostic jsonb` | 해당 시도 결과와 원자적으로 저장 |
| `ai_registration_unit` | nullable `block_diagnostic jsonb` | 같은 Snapshot·단위·판정과 원자적으로 저장. 기존 후속 Snapshot 이력 보존 |
| `ai_candidate_snapshot` | nullable `snapshot_diagnostic jsonb` | 단위 없는 후보 판정의 근거. 기존 Snapshot 불변성 유지 |

이 컬럼명은 구현 전 제안이다. #420·#421에 동등 필드가 이미 있으면 중복 컬럼 대신 기존 저장과 API 변환을 사용하고 정본에 확정한다. API에 파생하는 `DETAIL_NOT_RECORDED`를 과거 DB에 일괄 채우지 않는다. 과거 null 행은 그대로 읽고, 기존 범주가 있어도 상세 증거는 만들어내지 않는다.

- 진단 JSON은 버전·허용 키·코드·HTTP 범위·배열 크기를 검증하며 최대 4KiB로 제한한다. 등록 단위의 `causeCode` 외 임의 확장 키를 금지한다. 초과 시 안전한 필수 코드·단계만 보존하고 원문은 폐기한다.
- 새 진단은 비밀정보 없는 메타데이터만 포함한다. 자유 텍스트, 전체 URL의 쿼리, 응답 헤더, stack trace, 토큰, API 키, 프롬프트, 사용자 보완 텍스트를 넣지 않는다.
- DB 저장 실패를 성공으로 응답하지 않는다. 실패 기록 저장 중 장애는 기존 Worker 복구 절차를 따르며, lease를 잃은 Worker가 다른 실행의 진단을 덮어쓰지 못하게 한다.
- 보존 기간은 기존 후보 Snapshot·시도·검수 이력의 1년 정책에 맞춘다. 작업·단위에 복사된 진단도 해당 관측 시각 기준 1년 이내 정리 대상에 포함해 부모 행 잔존으로 무기한 보존되지 않게 한다. 일시적인 보충 검증 응답은 별도 영속화하지 않는다.
- 신규 Flyway 마이그레이션만 사용한다. 번호는 최신 develop과 순서 소유자 확인 뒤 정한다. 새 nullable 필드로 배포하며 구버전 앱·신버전 앱과 과거 데이터의 읽기 호환성을 검증한다.
- 새 인덱스는 이 설계만으로 추가하지 않는다. 집계 조회가 기존 범위를 넘어가면 실제 쿼리·실행 계획으로 별도 검토한다.

## 7. 구현 순서와 정본 반영

| 순서 | 작업 | 완료 산출물 |
|---|---|---|
| 1 | 최신 develop·운영 배포·#420·#421 구현 대조 | 기존 필드 재사용 여부와 누락 지점 |
| 2 | 소유자 검토: 기능 요구사항·BR/NFR·PRD·ADR | 코드 목록, 수집·보존·복구 의미 확정 |
| 3 | API·데이터 계약, 테이블·제약·마이그레이션 계획·추적표 반영 | 본 Draft 제안과 정본의 대응 확인 |
| 4 | Provider 분류 → Worker/단위 저장 → 관리자 조회 | 안전한 진단이 끝까지 유지됨 |
| 5 | 공통 화면 매핑 → 목록·상세·보충 실패 표시 | 대표 사유와 실제 복구 경로 일치 |
| 6 | 회귀 테스트·독립 리뷰·배포 검증 | 아래 완료 기준 증거 |

자동 결제 전환, 모델 변경, quota 상향, 판정 기준 완화, 새 외부 서비스, 자동 재시도 횟수 증가는 제외한다. 문서 작성만으로 테스트 통과·운영 적용을 완료 처리하지 않는다.

## 8. 검증 및 완료 기준

| ID | 시나리오 | 기대 결과 |
|---|---|---|
| DIAG-01 | 400/401/402/403/404/408/429/5xx 응답 | 관측된 범위의 대표 코드·HTTP 상태 보존 |
| DIAG-02 | 같은 400/403에 다른 본문, 미지 코드·비JSON·초과 본문 | 근거 없는 결제·구독 단정 없음, 원문 유출 없음 |
| DIAG-03 | 응답 전 통신 오류·timeout·응답 Schema 오류·내부 결함 | 서로 다른 사유와 정확한 서비스·단계 |
| DIAG-04 | 최초 호출 전 및 재시도 전 앱 quota 중단 | 작업 최종 사유와 각 시도 사유 구분, 횟수 일치 |
| DIAG-05 | 같은 작업의 주소 누락 식당과 정상 식당 | 누락 필드가 해당 단위에만 표시됨 |
| DIAG-06 | 장소 미발견·모호·카테고리·방문 근거·중복 | 기존 blockReason과 recoveryPaths 유지 |
| DIAG-07 | 보충 CONFIRM 후 다른 단계 실패 | 기존 단위 사유 유지, 이번 실패 별도 표시, 보충값 있는 재실행 유지 |
| DIAG-08 | 6/10 등록·4/10 보류, 롤백·폐기 혼합, 단위 0건 | 정확한 현재 등록 수·보류 집계, 전체 실패 오표시 없음 |
| DIAG-09 | 과거 null·UPSTREAM, 미지 진단 버전·코드 | 읽기 호환, 미기록/미지원 안내, 추정 backfill 없음 |
| DIAG-10 | lease 상실·중복 실행·자동 보완 후 성공·저장 실패 | 결과와 진단 원자성, 과거 시도 보존, 성공에 오래된 실패 사유 없음 |
| DIAG-11 | 악성 오류 본문에 키·입력 원문 삽입, 1년 보존 만료 | DB·로그·API·화면 비노출, 복사 진단까지 정리 |
| DIAG-12 | 목록·상세·시도·보충 오류 조회 | 같은 이벤트에 동일 코드·근거, 실제 권한에 맞는 복구 안내 |

Provider 응답은 WireMock과 고정 fixture로 검증하고 실제 Kakao·YouTube·Gemini를 자동화 테스트에서 호출하지 않는다. DB 저장·동시성은 PostgreSQL Testcontainers, 관리자 응답은 MockMvc, 화면 매핑은 프론트 테스트와 브라우저 확인으로 검증한다. 현재 이 표는 실행할 검증 계획이며 결과는 아직 없다.

---
status: ACCEPTED
analysis_date: 2026-08-14
workstream: WS-15
scope: AI 영상 추출 후보 손실 결함 분석
related_documents:
  - third-expansion-ai-evaluation-result.md
  - third-expansion-evaluation-strategy.md
  - third-expansion-task-breakdown.md
  - ../01-requirements/business-rules.md
  - ../01-requirements/functional-requirements.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../05-specs/api/admin/reference-data-api.md
  - ../05-specs/api/admin/visit-registration-api.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
---

# 3차 확장 AI 영상 추출 후보 손실 분석

## 1. 문서 목적

운영 배포된 AI 영상 추출이 성공 상태로 끝나는데도 맛집이 한 건도 등록되지 않는 현상의 원인을 확정하고, 계약 위반에 해당하는 결함과 별도 결정이 필요한 항목을 분리한다. 이 문서는 결함 분석과 수정 범위 확정까지를 다루며, 장소 동일성 판정 기준 변경은 결정 대상으로만 남긴다.

## 2. 요약

운영에서 접수한 AI 추출 작업 2건이 모두 등록 0건으로 끝났다.

| 작업 | 영상 | 실행 상태 | 결과 | 검수 | 등록 |
|---|---|---|---|---|---|
| `e6e7e3e2` | `1o-fwu6Nv2s` | `FAILED` | 미완료 | 미정 | 0건 |
| `6a710cde` | `wjejRtf9Ako` | `SUCCEEDED` | `PARTIAL` | `AUTO_BLOCKED` | 0건 |

두 작업의 실패 원인은 서로 다르며, 둘 다 Provider 품질 문제가 아니라 애플리케이션 계약 결함이다. Gemini는 두 영상 모두에서 근거를 갖춘 후보를 정상 반환했다.

- **결함 A** — Gemini에 보내는 태그 JSON Schema가 API 계약과 다르다. 태그를 하나라도 추출한 영상은 응답 검증에서 `SCHEMA`로 실패한다.
- **결함 B** — 같은 필드에 후보가 2개 이상이면 후보를 Snapshot에 남기지 않고 폐기한다. `BR-AIEXTRACT-001` 위반이다.
- **결정 C** — AI 후보의 `location`에 카카오 장소 URL을 요구한다. 근거 문서가 없고 `BR-AIEXTRACT-001`의 "위치 표현" 정의와 어긋나지만, 대안이 장소 동일성 판정 기준 변경이므로 별도 결정으로 분리한다.

결함 A와 B는 계약 준수 수정이므로 이번 범위에 포함한다. 결정 C는 이번 범위에서 제외한다.

## 3. 실측 근거

Prompt `P2` 변경 전 배포본과 동일한 역사적 계약(`gemini-3.5-flash-lite`, Prompt `P1`, Schema `S1`, global endpoint)으로 두 영상을 직접 호출해 응답 구조를 확인했다. 이 결과는 P1 기준의 사전 실측 증거이며 현재 P2 배포 검증 결과가 아니다. 응답 전문은 `ADR-AI-001` 5.1절에 따라 보존하지 않으며, 아래 집계와 최소 표본만 기록한다.

| 항목 | 영상 A `wjejRtf9Ako` | 영상 B `1o-fwu6Nv2s` |
|---|---|---|
| HTTP 상태 | 200 | 200 |
| 응답 소요 | 69초 | 18초 |
| `VIDEO` modality 토큰 | 355,169 | 70,006 |
| `resultCompleteness` | `COMPLETE` | `COMPLETE` |
| `missingFields` | 없음 | 없음 |
| 후보 총계 | 131건 | 42건 |
| 후보 구성 | 맛집명 56 · 메뉴 53 · 주소 22 | 맛집명·메뉴·주소·위치·방문근거·태그 각 7 |
| 근거 유형 | 전건 `TIMESTAMP` | 전건 `TIMESTAMP` |

영상 B 표본이다. 7개 장소 전부에 상호명·주소·방문 근거와 timestamp가 붙었다.

```
능이버섯백숙  서울 영등포구 문래동4가 9-2   "능이버섯 삼계탕을 주문했고요"
경성모밀      서울 동작구 동작대로29가길 6  "저는 돈까스와 판모밀 세트로 주문했고요"
미타우동      서울 송파구 송파대로49길 31   "텐뿌라 부카케 우동이랑 온센 타마고를 주문했습니다"
```

세 가지가 확인된다.

1. Gemini는 공개 YouTube URL 입력으로 영상을 실제로 분석한다. 프롬프트 토큰의 99.9%가 `VIDEO` modality이고 근거 timestamp가 영상 길이 전체에 분포한다.
2. 두 영상 모두 `COMPLETE`이며 필수 필드 누락이 없다. 운영 작업이 `PARTIAL`·`AUTO_BLOCKED`로 끝난 것은 응답 품질 때문이 아니다.
3. 맛집 소개 영상은 한 영상에 장소가 여러 곳 등장하는 것이 정상이다. 영상 A는 56곳, 영상 B는 7곳이다.

## 4. 결함 A — 태그 outbound Schema가 API 계약과 다르다

### 4.1 증상

태그 후보를 반환한 영상 B는 응답 검증에서 `SCHEMA`로 실패한다. 운영 작업 `e6e7e3e2`의 `실패 범주: SCHEMA · 재시도 불가`가 이에 해당한다.

### 4.2 원인

[API 계약 3.3절](../05-specs/api/admin/ai-video-extraction-api.md)이 정의한 태그 후보는 `value`를 갖지 않고 `rawLabel`·`normalizedCode`·`label`을 갖는다. 수신 검증기(`GeminiHttpVideoExtractionAdapter.validTagCandidate`)와 후보 검증기(`AiCandidateValidator.parseTag`)는 이 계약을 정확히 구현한다.

그러나 Gemini에 전송하는 `responseJsonSchema`는 `value`를 공용 속성으로 선언하고 `rawLabel`·`normalizedCode`·`label`을 필수로 걸지 않는다. 모델은 전송된 Schema를 지켜 태그 후보에 `value`를 담고 나머지 셋을 생략했고, 수신 검증기는 허용 필드 집합 위반으로 응답 전체를 기각했다.

즉 **같은 Schema `S1`을 송신 측과 수신 측이 다르게 표현하고 있다.** 계약 원문은 API 계약 문서이므로 수정 대상은 송신 Schema와 시스템 프롬프트다. 수신 검증기와 API 계약은 바꾸지 않는다.

### 4.3 영향

태그를 추출할 수 있는 모든 영상이 `SCHEMA`로 실패하고 재시도가 불가능하다. 태그를 우연히 추출하지 못한 영상만 다음 단계로 진행한다.

### 4.4 같은 원인의 추가 항목

태그 모양을 고친 뒤 같은 계약으로 다시 호출해 두 항목을 추가로 확인했다. 둘 다 규칙이 시스템 프롬프트 문장으로만 존재하고 송신 Schema에는 표현되지 않은 같은 원인이다.

**완결성과 누락 필드의 결합.** `validS1`은 `COMPLETE`이면 `missingFields`가 비어 있고 `PARTIAL`이면 비어 있지 않을 것을 요구한다. 송신 Schema는 두 필드를 독립적으로 선언하므로 모델이 `PARTIAL`과 빈 `missingFields`를 함께 반환할 수 있고, 그 응답은 `SCHEMA`로 기각된다. 같은 요청을 반복 호출했을 때 `COMPLETE`·빈 목록이 두 번, `PARTIAL`·빈 목록이 한 번 나왔다. 간헐적으로 실패하므로 재현 조건이 드러나지 않는다.

**태그 코드 규격.** `AiExtractionResultProcessorService.isTagAutoConnectable`은 `normalizedCode`가 `[A-Z0-9_]{1,64}`를 만족하고 `tagType`과 밑줄로 시작할 것을 요구한다. 이 규격은 응답 검증 대상이 아니어서 `SCHEMA` 실패로 이어지지는 않지만, 위반한 태그는 `AUTO_REJECT`·`TAG_POLICY`로 처리되어 timestamp 근거가 있는 태그가 조용히 버려진다. 실측에서 소문자와 하이픈이 섞인 로마자 표기(`MENU_NEUNGBEoseot_SAMGYETANG`, `MENU_BOKSUNG-A_BINGSU`)가 태그 22건 중 4건 나타났다.

두 항목 모두 송신 Schema가 규칙을 구조로 표현하게 하는 것으로 해결한다. 수신 검증기와 API 계약은 바꾸지 않는다.

### 4.5 Schema로 표현할 수 없는 상한과 Prompt 버전

수신 검증기는 후보 수 `100`, 누락 필드 수 `20`, 문자열 길이 `4096`을 초과하면 응답 전체를 `SCHEMA`로 기각한다. 이 상한도 송신 Schema에 표현돼 있지 않았다. 실측에서 영상 A가 후보 131건을 반환했으므로 태그 모양을 고쳐도 다장소 영상은 여전히 기각된다.

문자열 길이는 `minLength`·`maxLength`로 표현했고 실제 호출에서 수용됐다. 그런데 **후보 수 상한은 Schema로 표현할 수 없다.**

| 시도 | 결과 |
|---|---|
| `missingFields`에 `maxItems` | HTTP 200 |
| `candidates`에 `maxItems`(양쪽 루트 분기) | HTTP 400 `INVALID_ARGUMENT` |
| `candidates`에 `maxItems`(한쪽 분기만) | HTTP 400 `INVALID_ARGUMENT` |
| 문자열 속성에 `maxLength` | HTTP 200 |

`candidates.items`가 `anyOf`이므로 `maxItems`와 조합하면 요청 자체가 거절된다. 넣으면 `SCHEMA` 실패가 `UPSTREAM`(재시도 불가) 실패로 바뀌어 악화된다. 따라서 후보 수 상한은 시스템 지시 문장으로만 전달하고, `MAX_CANDIDATES` 자체를 올리는 것은 수신 계약 변경이므로 9절 남은 결정으로 둔다. **후보가 100건을 넘는 영상은 여전히 기각될 수 있다.**

시스템 지시와 요청 Schema 표현이 함께 바뀌었으므로 `PROMPT_VERSION`을 `P1`에서 `P2`로 올렸다. `BR-AIEXTRACT-004`가 버전별 재현성을 요구하고 `prompt_version`이 멱등성 키에 포함되므로 라벨을 유지하면 수정 전후 Snapshot이 같은 `P1`으로 섞이고 같은 영상 재요청이 기존 작업으로 수렴한다. 수신 계약은 바뀌지 않았으므로 `SCHEMA_VERSION`은 `S1`을 유지한다. `ADR-AI-001` 10절을 같은 PR에서 갱신했다. `aiextract-golden-v1.0.0` 평가 자산은 `gemini-3-flash-preview`·`P1` 기준 역사적 fixture로 보존한다.

## 5. 결함 B — 복수 후보를 폐기한다

### 5.1 근거 규칙

`BR-AIEXTRACT-001` AI 후보 생성 범위는 다음을 요구한다.

> 근거 구간이 없거나 하나의 장소로 판정할 수 없으면 추정값을 확정하지 않고 `UNKNOWN` 또는 복수 후보로 남긴다.

요구사항은 두 가지를 분리해 규정한다. 확정하지 않는 것과, 후보를 남기는 것이다.

### 5.2 현재 동작

`AiCandidateValidator`는 필수 필드 후보가 2개 이상이면 `MULTIPLE_CANDIDATES` 사유로 자동 확정을 차단한다. 차단은 요구사항에 맞다. 그러나 같은 분기에서 후보를 `selectedCandidates`에 담지 않아 Snapshot의 `candidate_fields`에서 사라진다.

결과적으로 관리자 상세 화면과 `GET /api/admin/ai/video-extractions/{jobId}` 응답의 `candidates`가 빈 배열이 된다. 운영 작업 `6a710cde`가 후보 0건으로 보이는 이유이며, 화면에서 사후 보정할 대상이 존재하지 않는다.

`missingFields`에도 남지 않는다. 후보가 없어서 차단된 것이 아니라 여럿이어서 차단됐기 때문이다. 그래서 상세 화면은 "후보도 없고 누락도 아닌" 상태를 보여준다.

### 5.3 부수 영향

`AdminAiExtractionQueryService.registrationCommand`는 `CONFIRM` 시 저장된 `candidate_fields`를 다시 읽어 등록 명령을 만든다. 후보가 비어 있으면 `location` 값이 없어 `URI.create`에서 실패하거나 외부 검증에서 기각된다. 즉 관리자가 `AUTO_BLOCKED` 결과를 사후 보정하는 경로 자체가 동작하지 않는다.

### 5.4 계약 영향

- 응답 계약: `candidates`는 이미 배열이며 계약 예시도 항목이 둘이다. 같은 `field`가 여러 번 나타나는 것을 금지하는 규칙이 없다. **응답 계약 변경 없음.**
- 데이터 계약: `candidate_fields`의 DB 제약은 `jsonb_typeof(candidate_fields) = 'object'`뿐이다. **스키마 변경 없음.**
- 자동 확정 경로: 단일 후보일 때의 `AUTO_CONFIRMED` 동작은 그대로 유지한다. **동작 변경 없음.**

## 6. 결정 C — AI 경로의 장소 확정 전제

이번 범위에서 제외하되 기록으로 남긴다.

`AiExtractionResultProcessorService`는 AI가 반환한 `location` 값이 `https://place.map.kakao.com/{id}` 형식일 것을 요구한다. 확인 결과 이 요구를 규정한 문서는 없다. 카카오 장소 URL 제약의 근거는 [관리자 기본 데이터 API](../05-specs/api/admin/reference-data-api.md)의 수동 등록 경로이며, 관리자가 직접 확인한 링크를 제출하는 것을 전제로 한다.

`BR-AIEXTRACT-001`은 AI 산출물을 "주소·위치 표현"으로 정의한다. 실측에서 Gemini가 반환한 `여의도역`·`영등포구`는 이 정의에 부합하며, 모델이 카카오 장소 식별자를 알 수 있는 경로는 없다.

같은 경로에 통과 불가 조건이 두 개 더 있다.

| 조건 | 구현 | 실측 값 | 결과 |
|---|---|---|---|
| 메뉴 → 대표 카테고리 | 고정 키워드 완전일치 (`ResolveVerifiedRestaurantReferenceService.MENU_CATEGORY`) | `능이버섯 삼계탕`, `장어덮밥`, `쌀국수`, `간짜장` | 매칭 0건 |
| 후보 주소 대조 | 공백 제거 후 완전일치 | 지번 표기 vs 카카오 도로명 | 불일치 |

카카오 Adapter는 이미 상호명으로 `keyword.json`을 검색하고 URL은 검색 결과 선택에만 사용한다. 따라서 이름·주소 기반 선택으로 바꾸는 것은 구현상 가능하지만, [ADR-AI-001](../07-adr/integration/ai-001-video-extraction-candidate-boundary.md) 5.3절 "Kakao 장소 동일성 검증"의 판정 기준을 바꾸는 결정이므로 restaurant 도메인 소유자 합의가 필요하다.

## 7. 이번 범위와 등록 완료 경로

### 7.1 이번 범위

| 항목 | 대상 | 계약 변경 | 합의 |
|---|---|---|---|
| 결함 A | Gemini 송신 Schema·시스템 프롬프트 | 없음 | 불필요 |
| 결함 B | 후보 검증기·결과 프로세서·상세 응답 매핑 | 없음 | 불필요 |

### 7.2 등록 완료 경로

결함 B를 수정하면 관리자 상세 화면에 후보가 노출된다. 이후 정식 등록은 AI 검수 API를 확장하지 않고 기존 관리자 등록 API를 재사용한다.

```
후보 목록에서 1곳 선택
  → POST /api/admin/restaurant-registration-previews  (상호명·주소 프리필, 카카오 URL·전화번호는 관리자 입력)
  → POST /api/admin/restaurants
  → POST /api/admin/creator-registration-previews → POST /api/admin/creators
  → POST /api/admin/video-registration-previews   → POST /api/admin/videos
  → POST /api/admin/visit-relationships           (visitEvidenceConfirmed = true)
```

이 경로는 관리자가 장소를 확정하므로 결정 C를 우회한다. AI는 "이 영상에 이 장소들이 등장한다"까지만 담당하고 장소 동일성은 사람이 판정한다. `POST /api/admin/ai/video-extractions/{jobId}/review`의 요청 계약을 바꾸지 않는다.

한계를 명시한다. 이 경로로 등록하면 해당 작업의 `registered_restaurant_id`가 `null`로 남고 작업은 `DISCARD`로 종결된다. 후보·검수 이력은 `ADR-AI-001` 5.1절대로 1년 보존되지만, 등록된 정식 데이터와 AI 작업의 연결은 남지 않는다. 추적성이 필요해지면 `review` 계약에 등록 결과를 연결하는 별도 작업으로 다룬다.

프론트엔드 후보 선택 화면은 이 문서 범위에 포함하지 않고 후속 작업으로 분리한다. 백엔드가 후보를 반환하는 것이 선행 조건이다.

## 8. 검증 방법

- 태그 후보를 포함한 `S1` 응답이 `SCHEMA` 실패 없이 후보로 변환되는 것을 WireMock으로 검증한다.
- 송신 Schema가 선언한 후보별 허용 필드 집합이 수신 검증기의 허용 집합과 일치하는 것을 회귀 테스트로 고정한다. `PR #170`이 같은 재발 방지 항목을 남겼으나 태그 후보 모양까지 덮지 못해 이번 결함이 재발했다.
- 송신 Schema가 완결성과 누락 필드의 결합, 태그 코드 규격을 구조로 강제하는 것과, 결합을 위반한 응답이 여전히 `SCHEMA`로 기각되는 것을 함께 검증한다.
- 같은 필드에 후보가 여러 개인 응답이 `AUTO_BLOCKED`로 차단되면서도 `candidate_fields`에 전부 보존되는 것을 검증한다.
- 단일 후보 응답의 `AUTO_CONFIRMED` 자동 등록 동작이 회귀하지 않는 것을 검증한다.
- 상세 조회 응답의 `candidates`에 같은 `field`가 여러 번 나타나는 것을 검증한다.
- 로컬·자동화 테스트에서 실제 Gemini·Kakao·YouTube API를 호출하지 않는다.

## 9. 남은 결정

2026-08-18에 결정 C(장소 동일성), 복수 후보 처리, 카테고리 매핑 세 항목이 결정됐다. 결정 내용은 요구사항·PRD·ADR에 반영했고 이 절에는 결과만 남긴다. `MAX_CANDIDATES` 상한 인상은 이 결정에 포함하지 않았고 아래 남은 결정에 그대로 둔다.

- **결정 C 해소** — 장소 동일성 판정 기준을 변경했다. AI 후보에 Kakao 장소 URL을 요구하지 않고, 시스템이 상호명·주소로 Kakao를 검색해 정규화 상호명 완전일치와 도로명주소 시·구 일치를 함께 만족하는 결과가 정확히 1건일 때만 자동 확정한다. 0건은 `PLACE_NOT_FOUND`, 2건 이상은 `PLACE_AMBIGUOUS`로 차단한다. 근거는 [BR-AIEXTRACT-009](../01-requirements/business-rules.md#br-aiextract-009-장소-동일성-자동-확정)와 [ADR-AI-001 5.3절](../07-adr/integration/ai-001-video-extraction-candidate-boundary.md)이다.
- **복수 후보 처리 결정** — 후보가 여럿이라는 사실만으로 차단하지 않는다. 후보를 장소 단위 등록 단위로 나눠 각각 독립 판정하며, 한 단위의 차단이 다른 단위의 등록을 막지 않는다. 다만 이 결정은 수신 응답의 후보 수 상한을 바꾸지 않는다. 후보 100건을 넘는 영상은 종전대로 `SCHEMA`로 기각되며, 상한 인상 여부는 아래 남은 결정이다.
- **카테고리 매핑 결정** — 고정 키워드 완전일치를 버리고 기준정보 매핑 표로 옮긴다. 1순위 근거는 확정한 Kakao 장소의 분류 표현, 2순위는 AI 메뉴 표현이며 둘 다 실패하면 `CATEGORY_UNRESOLVED`로 차단한다. 근거는 [BR-AIEXTRACT-010](../01-requirements/business-rules.md#br-aiextract-010-대표-음식-카테고리-자동-선정)이다.

아직 남은 결정이다.
- 관리자 수동 등록 결과와 AI 작업의 연결 보존 여부.
- `MAX_CANDIDATES` 상한 인상 여부. 실측 최대가 131건이므로 다장소 영상을 통과시키려면 상한을 올려야 하지만 수신 계약 변경이다. 응답 크기와 처리 비용도 함께 판단한다.
- `candidate_fields`의 복수 후보 배열을 `tr_ai_candidate_snapshot__json_contract` 트리거 검사 대상에 넣을지 여부. 현재 그 컬럼은 최상위 object CHECK만 받으므로 배열 원소의 `confidence` 범위와 `evidence` 형태가 DB 방어선을 통과하지 않는다. 애플리케이션 검증기가 정상 경로를 막고 있어 재현되는 결함은 없으나 심층 방어가 한 겹 줄었다. 트리거 변경은 새 마이그레이션이 필요하므로 Flyway 순서 소유자 합의 대상이다.
- 후보 표현 해석을 `presentation`에서 `application`으로 이관할지 여부. 현재 `AdminAiVideoExtractionController.DetailResponse.candidates()`가 스칼라·배열 두 표현을 해석한다. 이관은 응답 매핑 전체를 건드리므로 이번 결함 범위를 넘는다고 판단했다.

## 10. Prompt P3 보완 텍스트 근거 경계

2026-08-16 로컬 P2 실측에서 공개 YouTube 영상 `LQOYruD7sek`과 서이축산 기준정보를 보완 텍스트로 제출했다. Gemini 호출은 성공했지만 `restaurantName`·`menu`·`visitEvidence`만 반환하고 `address`·`location`을 누락해 작업 `bf17c172-34e6-4655-a0c6-4b4ecfb158c7`이 `SUCCEEDED/PARTIAL/AUTO_BLOCKED`로 끝났다. Kakao 검증 호출과 Restaurant·Creator·Video·Visit 저장은 모두 0건이었다.

P3는 관리자 보완 텍스트를 시스템 지시와 분리된 비신뢰 데이터로 유지하면서 `restaurantName`·`menu`·`address`·`location`에만 SHA-256과 문자 범위가 일치하는 `TEXT_RANGE` 근거를 허용한다. `visitEvidence`는 보완 텍스트 주장으로 만들 수 없고 실제 영상의 `TIMESTAMP`를 요구한다. 잘못된 hash·범위·후보 문자열은 Provider 응답 검증에서 실패하며, 통과한 기준정보도 기존 Kakao 장소 동일성·YouTube 메타데이터·방문 근거·중복·원자성 검증을 우회하지 않는다.

시스템 지시의 후보 생성 의미가 바뀌므로 `BR-AIEXTRACT-004`에 따라 Prompt 버전을 `P3`로 올리고 기존 P1·P2 작업은 역사적 이력으로 보존한다. 근거 Schema의 모양은 바뀌지 않아 결과 Schema는 `S1`을 유지한다.

## 11. Prompt P4 정확 범위 안내

P3 실제 호출 두 건은 보완 텍스트를 전달했지만 모두 공급자 응답의 엄격한 근거 검증에서 `SCHEMA`로 차단됐다. 보안 검증을 부분 문자열 포함으로 완화하지 않고, P4 요청은 비어 있지 않은 각 보완 텍스트 줄의 정확한 UTF-16 `startOffset`·`endOffset`·`text`를 비신뢰 JSON 데이터의 `referenceSpans`로 함께 전달한다. 모델은 일치하는 span을 그대로 복사해야 하며 서버는 기존처럼 SHA-256, 경계, 값의 정확 일치를 재검사한다. 방문 근거는 계속 영상 `TIMESTAMP`만 허용한다.

P4 실측에서는 완전한 다섯 필드 추출까지 도달했지만 방문 문장 형식 변동으로 `AUTO_BLOCKED`됐고, 다른 실행에서는 `location` 누락 또는 주소 값 오연결이 재현됐다. P5는 명시적 라벨의 `fieldHint`와 라벨을 제외한 정확 범위를 함께 제공하고 서버도 후보 필드와 hint를 결속한다. P5 실측은 네 기준정보를 정확히 추출했지만 방문 문장이 명시적 물리 방문 동사 없이 구매 행동만 표현되어 `VISIT_EVIDENCE_REQUIRED`로 차단됐다. 구매·주문·일반 취식은 배달·포장 오탐 위험 때문에 방문 근거로 완화하지 않는다.

P6는 방문 후보의 요청 Schema를 공통 후보와 분리해 값은 완료형 물리 방문 문장, 근거는 영상 `TIMESTAMP`만 반환하도록 제한했다. quota window만 일시적으로 초기화한 로컬 실측에서 작업 `0c0d80f2-6334-47f0-a72d-b77d0e0cff1a`이 `SUCCEEDED/COMPLETE/AUTO_CONFIRMED`로 끝났고 Restaurant·Creator·Video·Visit가 각각 1건 생성되어 모두 `PUBLIC` 상태가 됐다. 공개 맛집 목록과 상세 API에서도 `서이축산` 저장 결과를 확인했다. 실측 후 임시 WireMock mapping과 quota window override는 제거했으며 감사 이력과 정식 등록 데이터는 검증 증거로 보존한다.

## 12. 태그 후보의 출처 없는 TEXT_RANGE 근거

P3 이후 수신 검증은 태그 후보에도 보완 텍스트 범위 검증을 적용했다. 그런데 태그는 보완 텍스트를 출처로 삼을 수 없는 필드라 검증이 항상 실패했고, 태그 하나가 `TEXT_RANGE` 근거를 내면 같은 응답의 정상 기준정보 후보까지 함께 `SCHEMA`로 기각됐다. `SCHEMA`는 재시도하지 않으므로 작업은 곧바로 `FAILED`로 끝나고 `ai_candidate_snapshot`이 남지 않아 관리자가 검토할 근거도 사라졌다. 송신 Schema는 태그 근거에 세 유형을 모두 허용하므로 모델이 이 응답을 만드는 것 자체는 계약 위반이 아니었다.

수신 검증은 태그 근거의 구조 검사(`hasOnlyFields`, 범위, `sourceHash`)를 그대로 유지하고, 구조가 유효한 `TEXT_RANGE` 태그 근거만 `UNKNOWN`으로 낮춘다. `AiCandidateValidator`가 `UNKNOWN` 태그 근거를 이미 `AUTO_REJECTED`·`UNKNOWN_EVIDENCE`로 기록하므로 태그는 연결되지 않고, 나머지 후보는 검증과 자동 확정 경로를 그대로 밟는다. 보완 텍스트가 태그의 출처가 될 수 없다는 경계는 유지되고 관측 가능성만 회복한다.

이 수정만으로는 시스템 지시와 요청 Schema가 바뀌지 않으므로 Prompt 버전을 올리지 않는다. 결과 Schema도 `S1`을 유지한다. 같은 PR에서 Prompt를 `P7`로 올린 것은 13절의 별개 사유 때문이다. 구조가 깨진 태그 근거는 종전대로 응답 전체를 `SCHEMA`로 기각한다.

## 13. 방문 문장 종결 마침표의 송신·수신 불일치

P6 송신 Schema의 방문 후보 값 정규식은 `^.*(?:방문했습니다|…)$`로 완료형 동사가 문자열 끝이기를 요구했다. 그런데 수신 판정의 `normalizeClaim`은 값에서 공백을 모두 제거하고 종결 `.`·`。`를 떼어낸 뒤 같은 동사 집합을 검사한다. 그래서 `"제가 서이축산을 방문했습니다."`처럼 가장 자연스러운 형태가 수신에서는 유효한데 송신 제약 디코딩 단계에서는 배제됐다. 실패가 겉으로 드러나지 않고 모델이 표현을 바꾸도록 밀어내기만 하므로, P4·P5에서 반복 관찰된 방문 문장 형식 변동의 원인 중 하나로 남아 있었다.

P7은 정규식의 후행 허용 문자를 수신이 제거하는 범위와 정확히 맞춰 `[\s.。]*$`로 바꾼다. `!`와 `?`는 `hasBlockingVisitContext`가 의문·감탄을 차단 문맥으로 다루므로 송신에서도 계속 거부한다. 판정 기준 자체는 완화하지 않았고, 수신 계약과 시스템 지시가 바뀌지 않으므로 결과 Schema는 `S1`을 유지한다.

`BR-AIEXTRACT-004`의 버전별 재현성을 지키기 위해 송신 Schema가 바뀐 이번 변경도 Prompt 버전을 `P7`로 올린다. `ux_ai_job__idempotency`가 `prompt_version`을 포함하므로 P6까지 처리한 영상은 재추출 대상이 된다. 무료 quota가 한 번 더 소모되므로 운영에서 켤 때는 `AI_WORKER_PROVIDER_QUOTA_LIMIT`·`AI_WORKER_APPLICATION_QUOTA_LIMIT`를 먼저 확인한다. P6 작업과 Snapshot은 생성 당시 의미로 보존한다.

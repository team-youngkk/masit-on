---
id: API-DISCOVERY-NL-001
title: 자연어 맛집 탐색 API
status: Accepted
related_prd:
  - PRD-DISCOVERY-005
  - PRD-DISCOVERY-001
workstream: WS-14
owner: 양성훈
reviewers:
  - 이우람
related_requirements:
  - FR-NLSEARCH-001
  - FR-NLSEARCH-002
  - FR-NLSEARCH-003
  - FR-NLSEARCH-004
related_business_rules:
  - BR-NLSEARCH-001
  - BR-NLSEARCH-002
  - BR-NLSEARCH-003
related_nfr:
  - NFR-ACCURACY-001
  - NFR-SECURITY-007
  - NFR-PRIVACY-006
  - NFR-COST-001
  - NFR-PERFORMANCE-007
  - NFR-AVAILABILITY-003
  - NFR-OBSERVABILITY-005
  - NFR-TEST-006
related_documents:
  - ../../../04-product/prd/discovery/natural-language-restaurant-discovery.md
  - ../../../04-product/prd/discovery/restaurant-discovery.md
  - ../../../01-requirements/functional-requirements.md
  - ../../../01-requirements/business-rules.md
  - ../../../01-requirements/non-functional-requirements.md
  - ../../../02-analysis/third-expansion-workstreams.md
  - ../../../02-analysis/third-expansion-domain-boundaries.md
  - ../../../07-adr/architecture/arch-005-natural-language-filter-interpretation.md
  - ../../../08-planning/third-expansion-evaluation-strategy.md
  - restaurant-discovery-api.md
  - ../common/response-contract.md
  - ../common/error-contract.md
  - ../common/filtering-contract.md
  - ../common/pagination-contract.md
---

# 자연어 맛집 탐색 API

## 1. 목적과 경계

사용자의 자연어 문장을 기존 맛집 조건으로 해석하고, 기존 공개 맛집 목록을 반환한다. 이 API는 임베딩·벡터 유사도 검색·RAG·자유 형식 챗봇·결과 선정 이유 생성을 제공하지 않는다.

- 입력 원문과 검색 이력은 저장하지 않는다.
- 해석 결과는 `restaurantName`, `district`, `category`, `creatorId`, `tags` 조건으로만 제한한다. `tags`는 [AI 영상 추출 데이터 계약](../../data/third-expansion-ai-video-data-contract.md)의 초기 seed에 있는 관리자 확정 `VisitTag` 코드다.
- 실제 목록 조합·공개 상태·Visit 유효성·정렬·페이지네이션은 [맛집 탐색 API](restaurant-discovery-api.md)를 따른다.
- 직접 지정 필터와 자연어 조건이 같은 종류에서 충돌하면 직접 지정 필터를 적용한다.

## 2. 접근 권한

인증 없이 공개 접근한다. 요청 원문·해석 원문·Provider 응답·비밀정보는 응답과 일반 로그에 포함하지 않는다.

## 3. API 요약

| API ID | Method | Path | 설명 |
|---|---|---|---|
| API-DISCOVERY-NL-001 | POST | `/api/restaurants/natural-language-search` | 자연어 해석과 기존 맛집 목록 조회 |

기존 구조화 필터만 사용하는 경우에는 [API-DISCOVERY-001](restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색)을 사용한다. 자연어 API는 기존 목록 API의 대체 경로가 아니라 입력 보조 경계다.

## 4. API-DISCOVERY-NL-001 자연어 해석과 목록 조회

- Method: `POST`
- Path: `/api/restaurants/natural-language-search`
- 인증: 없음
- 권한: 일반 공개 조회
- 성공 상태: `200 OK`
- 처리 방식: 동기 요청

### Request Body

```json
{
  "sentence": "성수에서 백종원이 방문한 한식집",
  "filters": {
    "query": null,
    "district": null,
    "category": null,
    "creatorId": null,
    "tags": []
  },
  "page": 1,
  "size": 20
}
```

| 필드 | 타입 | 필수 | 설명 | 검증 규칙 |
|---|---|---:|---|---|
| `sentence` | string | 예 | 사용자가 입력한 자연어 문장 | trim 후 1~500자, 공백만 입력 불가 |
| `filters` | object | 아니요 | 기존 구조화 필터. 없으면 빈 객체로 처리 | 허용 필드 외 입력 거부 |
| `filters.query` | string | 아니요 | 맛집 이름 검색 | 최대 100자, 앞뒤 공백 제거 |
| `filters.district` | string | 아니요 | 서울특별시 자치구 | 허용 자치구 1개 |
| `filters.category` | string | 아니요 | 대표 음식 카테고리 | 공통 필터 계약의 허용값 1개 |
| `filters.creatorId` | string | 아니요 | 유튜버 식별자 | 불투명 식별자 1개 |
| `filters.tags` | array[string] | 아니요 | 관리자 확정 태그 코드 목록 | 0~5개, 중복 불가, 활성 태그만 허용 |
| `page` | integer | 아니요 | 결과 페이지 | 기본 `1`, 1 이상 |
| `size` | integer | 아니요 | 결과 크기 | 기본 `20`, `10·20·50`만 허용 |

`filters` 안의 직접 필터는 사용자가 명시한 값으로 간주한다. `filters`가 생략되면 자연어에서 해석된 조건만 적용한다.

### Interpretation Status

| 값 | 의미 | 목록 처리 |
|---|---|---|
| `APPLIED` | 하나 이상의 지원 조건을 해석·적용 | 해석된 조건으로 조회 |
| `PARTIAL` | 일부 조건은 적용했으나 미지원·충돌·미해석 조건이 있음 | 적용된 조건으로만 조회 |
| `FAILED` | 지원 조건을 하나도 해석하지 못함 | 전체 목록으로 대체하지 않고 빈 목록 반환 |

### Success Response

```json
{
  "interpretation": {
    "status": "APPLIED",
    "appliedConditions": {
      "query": null,
      "district": "성동구",
      "category": "한식",
      "creatorId": "creator-id",
      "tags": ["MENU_NAENGMYEON", "OCCASION_SOLO"]
    },
    "ignoredConditions": [],
    "conflicts": [],
    "parserVersion": "P1"
  },
  "results": {
    "items": [],
    "page": {
      "number": 1,
      "size": 20,
      "totalElements": 0,
      "totalPages": 0,
      "hasNext": false
    }
  }
}
```

### Response Field Definitions

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `interpretation` | object | 예 | 자연어 해석 및 필터 병합 결과 |
| `interpretation.status` | enum | 예 | `APPLIED`, `PARTIAL`, `FAILED` |
| `interpretation.appliedConditions` | object | 예 | 실제 목록 조회에 적용한 기존 필터. 값이 없으면 `null` |
| `interpretation.ignoredConditions` | array | 예 | 적용하지 않은 표현과 사유. 없으면 `[]` |
| `interpretation.ignoredConditions[].type` | enum | 예 | `UNSUPPORTED`, `UNRESOLVED`, `CONFLICT` |
| `interpretation.ignoredConditions[].text` | string | 예 | 원문을 그대로 저장·반환하지 않는 정책에 따라 마스킹 또는 짧은 안전 요약 |
| `interpretation.ignoredConditions[].reason` | string | 예 | `UNSUPPORTED_CONDITION`, `UNRESOLVED_VALUE`, `DIRECT_FILTER_WON` 등 |
| `interpretation.conflicts` | array | 예 | 직접 필터가 자연어 조건을 대체한 충돌 목록 |
| `interpretation.conflicts[].field` | enum | 예 | `query`, `district`, `category`, `creatorId`, `tags` |
| `interpretation.conflicts[].resolution` | enum | 예 | `DIRECT_FILTER_WON` |
| `interpretation.parserVersion` | string | 예 | 규칙·사전·정규화 규칙 버전. P1은 `P1` |
| `results` | object | 예 | 기존 맛집 목록 응답 조합 |
| `results.items` | array | 예 | [맛집 탐색 API](restaurant-discovery-api.md)의 목록 항목 |
| `results.page` | object | 예 | 공통 페이지 정보 |

`results`의 항목 필드와 정렬은 [API-DISCOVERY-001](restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색)과 동일하다. `FAILED` 상태의 `results.items`는 `[]`이며 전체 목록을 반환하지 않는다. 유효한 조건의 결과 없음은 `APPLIED` 또는 `PARTIAL`과 `items: []`로 표현한다.

### Ignored Condition Example

```json
{
  "type": "UNSUPPORTED",
  "text": "분위기 좋은",
  "reason": "UNSUPPORTED_CONDITION"
}
```

`text`는 원문을 그대로 보존·반환하지 않고 최대 80자의 마스킹·요약 문자열만 반환한다. 긴 입력 전체나 자막·외부 응답을 응답에 복사하지 않는다.

## 5. Request Rules

1. 서로 다른 조건 종류는 기존 맛집 목록 API와 동일한 AND 의미로 조합한다.
2. `tags` 배열의 모든 태그는 같은 유효한 `Visit`에 연결된 경우에만 만족하며, 태그가 연결된 맛집을 중복 반환하지 않는다.
3. 같은 조건 종류에서 `filters`와 자연어 해석이 충돌하면 `filters` 값을 적용한다.
4. 직접 필터가 대체한 자연어 조건은 `conflicts` 또는 `ignoredConditions`에 표시한다.
5. 공개·활성 맛집, 활성 태그 정의와 공개·유효 Visit 관계만 결과에 포함한다.
6. 자연어 조건을 하나도 적용하지 못하면 전체 목록으로 대체하지 않는다.
7. 결과 선정 이유·생성 답변·관리자 미확정 태그·가격대 조건은 제공하지 않는다.
8. 태그 별칭이 둘 이상의 코드로 해석되면 임의 선택하지 않고 `UNRESOLVED`로 처리한다.

## 6. Error Cases

| 오류 코드 | HTTP 상태 | 발생 조건 |
|---|---:|---|
| `NATURAL_LANGUAGE_EMPTY` | 400 | `sentence`가 없거나 trim 후 비어 있음 |
| `INVALID_FIELD_VALUE` | 400 | 문장 길이, 필터, 페이지, 크기 또는 허용값 검증 실패 |
| `INVALID_IDENTIFIER` | 400 | `creatorId` 형식 오류 |
| `NATURAL_LANGUAGE_RATE_LIMITED` | 429 | 요청 제한 초과 |
| `NATURAL_LANGUAGE_UNAVAILABLE` | 503 | 규칙 사전·해석 구성요소를 사용할 수 없음. 기존 구조화 탐색은 계속 제공 |
| `INTERNAL_SERVER_ERROR` | 500 | 예상하지 못한 내부 오류 |

해석 결과가 `FAILED`인 것은 API 오류가 아니며 `200 OK`로 반환한다. 모든 오류는 [공통 오류 계약](../common/error-contract.md)의 `traceId`를 포함한다.

## 7. 품질·보안·운영 계약

- 조건 집합 exact match와 지원 조건 재현율은 [EVAL-NL](../../../08-planning/third-expansion-evaluation-strategy.md#31-자연어-맛집-탐색)으로 평가한다.
- 구조화 Schema 검증과 허용값 정규화를 통과하지 못한 출력은 `FAILED` 또는 `PARTIAL`로 처리하며 목록 조건으로 전달하지 않는다.
- Prompt Injection·악성 입력·원문 로그 노출을 방지한다.
- 신규 유료 임베딩 호출과 저장은 초기 범위에서 0건이며 태그 검색은 저장된 확정 태그의 정확 일치만 사용한다.
- 내부 처리 p95 800ms 이하, 서버 오류율 1% 미만을 목표로 하며 외부 사용자 네트워크 지연은 제외한다.
- 자연어 API 장애는 `GET /api/restaurants`와 기존 상세 API로 전파되지 않는다.

## 8. API 완료 조건

- [ ] FR-NLSEARCH-001~004와 BR-NLSEARCH-001~003의 정상·빈 결과·충돌·태그 AND·실패 계약 테스트가 있다.
- [ ] `APPLIED·PARTIAL·FAILED`와 직접 필터 우선 상태를 고정한 계약 테스트가 있다.
- [ ] 입력 원문·검색 이력·임베딩 저장 0건과 로그 마스킹을 검증한다.
- [ ] 정확도 목표·Dataset 분할·P1 사전·규칙과 `parserVersion` 변경 절차를 팀이 승인한다.
- [ ] 50명·20 RPS와 200명·80 RPS에서 기존 탐색 격리와 응답 시간을 검증한다.

---

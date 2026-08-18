---
status: SUPERSEDED
design_date: 2026-08-14
superseded_date: 2026-08-18
superseded_by: AI 자동 등록 결정(BR-AIEXTRACT-009, BR-AIEXTRACT-010, ADR-AI-001 5.3)
workstream: WS-15
scope: AI 후보 선택과 카카오 장소 자동 입력
owner_agreement_required: true
related_documents:
  - third-expansion-ai-candidate-loss-analysis.md
  - third-expansion-task-breakdown.md
  - ../01-requirements/business-rules.md
  - ../01-requirements/functional-requirements.md
  - ../03-team/ownership.md
  - ../04-product/prd/admin/admin-data-management.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../05-specs/api/admin/reference-data-api.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../05-specs/api/admin/visit-registration-api.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../07-adr/architecture/arch-002-external-ports-adapters.md
---

# 3차 확장 AI 후보 선택과 카카오 장소 자동 입력 설계

> **이 문서는 2026-08-18에 SUPERSEDED됐다.** 관리자가 후보를 고르고 카테고리를 선택하는 전제가 폐기되고, 장소 동일성과 대표 카테고리를 시스템이 판정하는 자동 등록으로 대체됐다. 현재 계약은 [BR-AIEXTRACT-009](../01-requirements/business-rules.md#br-aiextract-009-장소-동일성-자동-확정), [BR-AIEXTRACT-010](../01-requirements/business-rules.md#br-aiextract-010-대표-음식-카테고리-자동-선정), [ADR-AI-001 5.3절](../07-adr/integration/ai-001-video-extraction-candidate-boundary.md)이다. 아래 내용은 결정 이전 설계안의 역사적 기록으로만 남긴다.
>
> 3절의 관리자 장소 검색 API(`POST /api/admin/restaurant-place-searches`)는 신설하지 않는다. 장소 검색은 관리자 화면이 아니라 orchestration의 자동 판정 경로에서 사용하므로 관리자 API로 노출할 필요가 없다. `PlaceSearchPort` 신설과 Kakao keyword 검색 Adapter 확장은 그 자동 판정 경로의 구현으로 유효하다.

## 1. 문서 목적

[AI 후보 손실 분석](third-expansion-ai-candidate-loss-analysis.md)의 후속이다. 후보가 보존된 뒤 관리자가 그 후보를 골라 실제 정식 등록까지 도달하는 경로를 설계한다. 카카오 장소 링크와 전화번호를 관리자가 손으로 찾아 붙여넣지 않도록 검색 결과로 채워주는 범위까지 포함한다.

이 문서는 `PROPOSED` 상태다. 3절의 새 관리자 API는 API 계약 추가에 해당하므로 [소유권](../03-team/ownership.md)에 따른 소유자 합의 전에 병합하지 않는다.

## 2. 선행 조건과 범위

### 2.1 선행

후보 손실 결함 수정이 먼저 병합돼야 한다. 그 전에는 `GET /api/admin/ai/video-extractions/{jobId}` 응답의 `candidates`가 빈 배열이므로 선택할 대상이 존재하지 않는다.

### 2.2 포함

- 관리자가 이름과 주소 힌트로 카카오 장소 후보를 조회하는 관리자 API 1개.
- AI 작업 상세 화면에서 후보를 하나 골라 맛집·유튜버·영상·방문 관계 등록까지 이어가는 화면 흐름.
- 카카오 검색 결과로 장소 링크·도로명주소·전화번호를 폼에 채워 넣는 동작.

### 2.3 제외

- **AI 또는 시스템이 장소 동일성을 자동 판정해 자동 등록하는 것.** 판정 주체는 관리자로 유지한다. AI 후보의 `location` 값에 카카오 장소 URL을 요구하는 전제(손실 분석 6절의 결정 C)는 여전히 결정 대상으로 남긴다.
- 한 영상의 여러 후보를 한 번에 일괄 등록하는 것. 한 번에 한 곳만 등록한다.
- 메뉴 표현에서 대표 카테고리를 자동으로 정하는 것. 카테고리는 관리자가 공통 10개 값 중 선택한다.
- `POST /api/admin/ai/video-extractions/{jobId}/review` 요청 계약 변경. 정식 등록은 기존 관리자 등록 API로 수행한다.

### 2.4 판정 주체를 유지하는 이유

[ADR-AI-001](../07-adr/integration/ai-001-video-extraction-candidate-boundary.md) 5.3절은 자동 확정 전 Kakao 장소 동일성 검증을 orchestration 책임으로 둔다. 검색 결과를 관리자에게 보여주고 관리자가 고르는 방식은 그 판정 기준을 바꾸지 않는다. 기존 수동 등록에서 관리자가 카카오에서 장소를 찾아 링크를 제출하던 행위를 화면이 대신 찾아주는 것이며, 확정 책임은 그대로 관리자에게 있다. `BR-AIEXTRACT-001`의 "하나의 장소로 판정할 수 없으면 확정하지 않는다"도 유지된다.

## 3. 새 관리자 API

### 3.1 필요성

기존 `POST /api/admin/restaurant-registration-previews`는 관리자가 이미 카카오 장소 URL을 가지고 있다는 전제로 검증만 수행한다. 이름으로 장소 후보를 조회해 링크·전화번호를 얻는 관리자 API는 없다.

`KakaoPlaceVerificationAdapter`는 이미 `/v2/local/search/keyword.json?query={상호명}`으로 검색하고, 제출된 URL은 검색 결과 중 하나를 고르는 데만 사용한다. 검색 능력은 이미 있고 결과를 밖으로 내보내는 경로만 없다.

### 3.2 계약안

- Method: `POST`
- Path: `/api/admin/restaurant-place-searches`
- 인증: JWT Access Token과 `ADMIN` 권한 필수

조회이지만 상호명·주소를 query string에 싣지 않기 위해 `POST`를 쓴다.

```json
{
  "name": "아코",
  "roadAddressHint": "서울 강동구 성내동 12-38"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `name` | string | 예 | 검색할 상호명. 앞뒤 공백 제거 후 1~100자 |
| `roadAddressHint` | string 또는 null | 예 | 후보 정렬에 쓰는 주소 표현. 없으면 `null` |

```json
{
  "items": [
    {
      "placeName": "아코",
      "kakaoPlaceUrl": "https://place.map.kakao.com/example",
      "roadAddress": "서울특별시 강동구 성내동 12-38",
      "phoneNumber": "02-000-0000",
      "district": "강동구"
    }
  ]
}
```

페이지가 필요 없는 최소 선택 목록이므로 `{ "items": [...] }` 형태를 쓴다. 빈 결과도 `200`에 빈 `items`다. 후보가 없다는 것은 오류가 아니다.

`phoneNumber`가 카카오 응답에 없는 장소는 `null`로 내보내고 관리자가 직접 입력한다. 도로명주소가 없는 장소는 등록에 쓸 수 없으므로 `items`에서 제외한다.

| 오류 코드 | HTTP | 조건 |
|---|---:|---|
| `MISSING_REQUIRED_FIELD` | 400 | `name` 누락 |
| `INVALID_FIELD_VALUE` | 400 | `name` 길이 위반 |
| `EXTERNAL_SERVICE_ERROR` | 502 | 카카오 조회 실패·시간 초과·할당량 초과 |

이 API는 자원을 만들지 않고 확정 토큰도 발급하지 않는다. 등록은 기존 미리보기·확정 2단계를 그대로 통과해야 한다.

### 3.3 구현 경계

`PlaceVerificationPort`는 "관리자가 제출한 장소 링크가 가리키는 장소를 확인한다"로 좁게 정의돼 있다. 검색은 목적이 다르므로 같은 Port에 메서드를 덧붙이지 않고 `PlaceSearchPort`를 새로 둔다. 두 Port를 같은 `KakaoPlaceVerificationAdapter` 계열이 구현할 수 있다.

| 계층 | 요소 |
|---|---|
| `restaurant/presentation` | `RestaurantRegistrationController`에 엔드포인트 추가 (`/api/admin` 경계 유지) |
| `restaurant/application/port/in` | `SearchAdminPlaceCandidatesUseCase` |
| `restaurant/application` | 검색 결과 정렬·불완전 문서 제외를 담당하는 서비스 |
| `restaurant/application/port/out` | `PlaceSearchPort` |
| `restaurant/infrastructure/external` | 카카오 keyword 검색 Adapter |

지켜야 할 규칙이다.

- Application은 자신이 소유한 `port.out`만 호출한다. HTTP client를 직접 import하지 않는다.
- 검색은 외부 HTTP 호출이므로 DB 트랜잭션을 열지 않는다.
- Entity를 응답에 노출하지 않는다.
- 검색 결과 매핑에서 도로명주소·링크가 없는 문서는 예외로 만들지 않고 조용히 제외한다. 기존 `toVerifiedPlace`는 필수값이 없으면 예외를 던지므로 검색용 매핑을 분리한다.

## 4. 화면 흐름

AI 작업 상세(`/admin/ai/{jobId}`)에서 시작한다.

```
후보 Snapshot 목록
  같은 field가 여러 번 나타난다. 근거 timestamp와 신뢰도를 함께 보여준다.
  ↓ 관리자가 맛집 후보 1건을 고른다
장소 확정
  POST /api/admin/restaurant-place-searches  (상호명 + AI 주소 후보를 힌트로)
  ↓ 검색 결과에서 1건을 고른다 → 링크·도로명주소·전화번호가 폼에 채워진다
  ↓ 카테고리는 관리자가 공통 10개 값에서 고른다
맛집 등록
  POST /api/admin/restaurant-registration-previews → POST /api/admin/restaurants
유튜버·영상 등록
  작업의 channelId·videoId를 그대로 사용한다
  POST /api/admin/creator-registration-previews → POST /api/admin/creators
  POST /api/admin/video-registration-previews   → POST /api/admin/videos
방문 관계 등록
  AI 방문 근거 문구와 timestamp를 화면에 근거로 띄운다
  POST /api/admin/visit-relationships (visitEvidenceConfirmed = true)
```

`visitEvidenceConfirmed`는 관리자가 근거를 확인하고 직접 선언한다. AI 후보를 그대로 `true`로 옮기지 않는다.

이미 등록된 유튜버·영상은 미리보기가 `DUPLICATE`로 판정하므로 그 단계를 건너뛰고 기존 식별자를 사용한다.

## 5. 계약 영향과 합의 필요 항목

| 항목 | 영향 | 합의 |
|---|---|---|
| 관리자 장소 검색 API 신설 | [관리자 기본 데이터 API](../05-specs/api/admin/reference-data-api.md)에 계약 추가 | **필요** |
| `PlaceSearchPort` 신설과 카카오 Adapter 확장 | restaurant 도메인 내부 | **필요** |
| 카카오 Local keyword 호출량 증가 | 외부 호출 비용·할당량 | **필요** |
| 기존 미리보기·확정 API | 변경 없음 | 불필요 |
| AI 검수 API 요청·응답 | 변경 없음 | 불필요 |
| DB 스키마·Flyway | 변경 없음 | 불필요 |

구현 PR에서 코드와 API 계약 문서를 같은 PR로 변경한다.

## 6. 남는 한계

관리자가 이 경로로 등록하면 해당 AI 작업의 `registered_restaurant_id`는 `null`로 남고 작업은 `DISCARD`로 종결된다. 후보·검수 이력은 1년 보존되지만 등록된 정식 데이터와 AI 작업의 연결은 남지 않는다. 추적성을 남기려면 `review` 계약에 등록 결과를 연결하는 별도 작업이 필요하다.

한 영상에 여러 곳이 나와도 한 번에 한 곳만 등록한다. 나머지 후보를 등록하려면 같은 작업에서 절차를 반복해야 한다.

## 7. 검증 방법

- 장소 검색 API의 정상·빈 결과·`name` 누락·카카오 실패를 검증한다. 도로명주소나 링크가 없는 문서가 `items`에서 제외되고 예외가 되지 않는 것을 확인한다.
- 전화번호가 없는 장소가 `null`로 반환되고 관리자 입력으로 채워지는 것을 확인한다.
- 검색 Adapter는 WireMock으로 검증한다. **실제 Kakao API를 호출하지 않는다.**
- 검색 중 DB 트랜잭션이 열리지 않는 것을 확인한다.
- ArchUnit 규칙(Application의 HTTP client 직접 의존 금지, 도메인 경계)을 통과한다.
- 프론트는 후보 목록 노출, 후보 선택, 검색 결과 프리필, 등록 4단계 진행과 중복 판정 처리를 확인한다.

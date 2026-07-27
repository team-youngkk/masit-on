---
id: API-ADMIN-REFERENCE-001
title: 관리자 기본 데이터 API
status: draft
related_prd:
  - PRD-ADMIN-001
workstream: WS-04
owner: 김인안
reviewers:
  - 박진영
related_requirements:
  - FR-ADMIN-001
  - FR-ADMIN-002
  - FR-ADMIN-003
  - FR-ADMIN-004
related_business_rules:
  - BR-RESTAURANT-003
  - BR-RESTAURANT-004
  - BR-RESTAURANT-005
  - BR-RESTAURANT-006
  - BR-RESTAURANT-007
  - BR-RESTAURANT-008
  - BR-CREATOR-001
  - BR-CREATOR-002
  - BR-CREATOR-003
  - BR-CREATOR-005
  - BR-VIDEO-001
  - BR-VIDEO-002
  - BR-VIDEO-003
  - BR-VIDEO-004
  - BR-VIDEO-005
  - BR-VIDEO-006
  - BR-ADMIN-001
  - BR-ADMIN-002
  - BR-ADMIN-003
  - BR-ADMIN-004
  - BR-ADMIN-005
  - BR-ADMIN-007
  - BR-ADMIN-008
related_nfr:
  - NFR-PERFORMANCE-003
  - NFR-SECURITY-001
  - NFR-SECURITY-002
  - NFR-SECURITY-003
  - NFR-INTEGRITY-001
  - NFR-INTEGRITY-002
  - NFR-INTEGRITY-003
  - NFR-EXTERNAL-003
  - NFR-OBSERVABILITY-001
  - NFR-OBSERVABILITY-002
  - NFR-OBSERVABILITY-003
  - NFR-TEST-001
  - NFR-TEST-002
  - NFR-TEST-003
  - NFR-PRIVACY-001
  - NFR-PRIVACY-002
related_documents:
  - ../../../04-product/prd/admin/admin-data-management.md
  - authentication-api.md
  - ../common/identifier-contract.md
  - ../common/response-contract.md
  - ../common/error-contract.md
  - ../../data/entity-definitions.md
  - ../../data/constraints.md
  - ../../../07-adr/integration/ext-001-reference-verification.md
  - ../../../07-adr/architecture/arch-002-external-ports-adapters.md
  - ../../../01-requirements/functional-requirements.md
  - ../../../02-analysis/mvp-workstreams.md
---

# 관리자 기본 데이터 API

## 1. 문서 목적

관리자가 검증한 맛집, YouTube 채널 단위 유튜버와 YouTube 영상을 방문 관계보다 먼저 등록하는 외부 계약을 정의한다.

## 2. 적용 범위

신규 등록, 필수값·허용값·URL·중복 검증과 외부 정보 확인 결과를 포함한다. 수정·삭제·승인 상태 관리와 원본 영상 업로드는 제외한다.

## 3. 인증 및 권한

모든 API는 `Authorization: Bearer` JWT Access Token과 `ADMIN` 권한을 요구한다. 인증 없음·실패는 `401`, 권한 검증 실패는 `403`이다. 자세한 전달 방식은 [관리자 인증 API](authentication-api.md)를 따른다.

## 4. API 요약

| API ID | Method | Path | 설명 |
|---|---|---|---|
| [API-ADMIN-RESTAURANT-PREVIEW-001](reference-data-api.md#api-admin-restaurant-preview-001-맛집-등록-검증-미리보기) | POST | `/api/admin/restaurant-registration-previews` | 맛집 입력·카카오 장소 검증 미리보기 |
| [API-ADMIN-RESTAURANT-001](reference-data-api.md#api-admin-restaurant-001-맛집-등록-확정) | POST | `/api/admin/restaurants` | 검증된 맛집 등록 |
| [API-ADMIN-CREATOR-PREVIEW-001](reference-data-api.md#api-admin-creator-preview-001-유튜버-등록-검증-미리보기) | POST | `/api/admin/creator-registration-previews` | YouTube 채널 검증 미리보기 |
| [API-ADMIN-CREATOR-001](reference-data-api.md#api-admin-creator-001-유튜버-등록-확정) | POST | `/api/admin/creators` | YouTube 채널 단위 유튜버 등록 |
| [API-ADMIN-VIDEO-PREVIEW-001](reference-data-api.md#api-admin-video-preview-001-영상-등록-검증-미리보기) | POST | `/api/admin/video-registration-previews` | YouTube 영상 검증 미리보기 |
| [API-ADMIN-VIDEO-001](reference-data-api.md#api-admin-video-001-영상-등록-확정) | POST | `/api/admin/videos` | 방문 근거 후보 영상 등록 |

등록 순서는 맛집·유튜버·영상 사이에는 강제하지 않는다. 세 대상이 모두 등록된 뒤 방문 관계를 등록한다.

### 검증 미리보기 공통 규칙

각 자원은 다음 순서로 등록한다.

1. 관리자가 원본 입력을 검증 미리보기 API에 제출한다.
2. 서버가 형식·허용값·외부 정보와 중복을 확인해 정규화된 후보를 반환한다.
3. 관리자가 화면에서 후보를 확인한다.
4. `decision`이 `READY`이면 `confirmationToken`을 해당 생성 API에 제출한다.

미리보기 응답의 `decision`은 `READY`, `DUPLICATE`, `REVIEW_REQUIRED` 중 하나다. `READY`일 때만 `confirmationToken`이 문자열이고, 나머지는 `null`이다. Token은 서버가 생성한 최소 256-bit 불투명 난수이며 JWT의 관리자 식별자와 서버가 저장한 정규화 후보 Snapshot에 묶이고 발급 후 10분에 만료된다. 서버는 원문 대신 SHA-256 해시를 PostgreSQL에 저장한다. `expiresAt`은 RFC 3339 시각이다. 만료·변조·다른 관리자·다른 자원 생성 API 사용은 `409 VERIFICATION_EXPIRED` 또는 `400 INVALID_CONFIRMATION_TOKEN`이며 생성하지 않는다.

Kakao place ID와 YouTube channel/video ID는 서버의 동일성 판정·후보 Snapshot·저장소 유일 키에만 사용하고 관리자 API 응답과 화면에는 노출하지 않는다. 관리자는 정규화된 이름·주소·URL·제목·채널명·썸네일을 확인한다.

Token 소비와 Entity 생성 또는 동시 중복 완료는 한 PostgreSQL 트랜잭션으로 처리한다. 최초 생성은 `201 Created`를 반환하고 조회 가능한 정식 자원 URI가 계약에 존재하면 `Location` 헤더를 함께 반환한다. 생성 완료 뒤 동일 관리자·동일 Token 재시도는 새 Entity를 만들지 않고 최초 성공과 같은 자원 표현을 `200 OK`로 반환한다. 미리보기 뒤 다른 요청이 같은 자원을 먼저 만들었다면 최초와 재시도 모두 같은 `409 DUPLICATE_*`와 기존 자원의 ID·최소 정보를 반환한다. 별도 `replayed` 필드는 추가하지 않고 `201`과 `200`으로 구분하며, 이 결과 재현은 부수 효과를 다시 실행하는 Token 재사용이 아니다. 조회 API가 없는 Creator·Video에 존재하지 않는 상세 경로를 만들기 위해 `Location`을 추가하지 않는다.

## 5. 맛집 등록

### API-ADMIN-RESTAURANT-PREVIEW-001 맛집 등록 검증 미리보기

- Method: `POST`
- Path: `/api/admin/restaurant-registration-previews`
- 인증: JWT Access Token과 `ADMIN` 권한 필수
- 권한: 관리자 등록 권한
- 관련 PRD: [PRD-ADMIN-001](../../../04-product/prd/admin/admin-data-management.md)
- 관련 요구사항: [FR-ADMIN-001](../../../01-requirements/functional-requirements.md#fr-admin-001-관리자-등록-기능-접근), [FR-ADMIN-002](../../../01-requirements/functional-requirements.md#fr-admin-002-맛집-정보-등록)

#### Request Body

```json
{
  "name": "맛집 이름",
  "kakaoPlaceUrl": "https://place.map.kakao.com/example",
  "roadAddress": "서울특별시 마포구 월드컵로 1",
  "detailAddress": null,
  "phoneNumber": "02-000-0000",
  "category": "한식"
}
```

| 필드 | 타입 | 필수 | 설명 | 검증·빈 값 규칙 |
|---|---|---:|---|---|
| `name` | string | 예 | 관리자가 확인한 맛집 이름 | 앞뒤 공백 제거 후 1~100자 |
| `kakaoPlaceUrl` | string | 예 | 동일 장소 확인에 사용한 카카오 장소 링크 | 최대 2,048자, HTTPS 카카오 장소 URL |
| `roadAddress` | string | 예 | 서울특별시 전체 도로명주소 | 앞뒤 공백 제거 후 1~255자, 서울 밖 주소 불가 |
| `detailAddress` | string 또는 null | 예 | 건물명·층·호 등 상세 위치 | 없으면 `null`, 있으면 앞뒤 공백 제거 후 1~200자 |
| `phoneNumber` | string | 예 | 확인된 전화번호 | 7~20자, 숫자·공백·`+`·`-`·`(`·`)`만 허용 |
| `category` | string | 예 | 대표 음식 카테고리 정확히 1개 | 공통 10개 값 중 하나 |

대표 이미지는 확정 요구사항에 없으므로 요청·응답에 포함하지 않는다. 자치구는 전체 도로명주소에 해당하는 값이며 별도 다중 입력을 받지 않는다. `기타` 카테고리도 별도 구체 음식 종류 필드를 받지 않는다.

#### Success Response

- 상태: `200 OK`

```json
{
  "decision": "READY",
  "confirmationToken": "opaque-confirmation-token",
  "expiresAt": "2026-07-24T03:30:00Z",
  "candidate": {
    "name": "맛집 이름",
    "district": "마포구",
    "category": "한식",
    "roadAddress": "서울특별시 마포구 월드컵로 1",
    "detailAddress": null,
    "phoneNumber": "02-000-0000",
    "kakaoPlaceUrl": "https://place.map.kakao.com/example"
  },
  "existingResource": null
}
```

`DUPLICATE`이면 `existingResource`에 기존 맛집의 `id`, `name`, `roadAddress`를 제공한다. `REVIEW_REQUIRED`이면 동일 장소 판단을 완료할 수 없어 두 토큰 필드는 `null`이다. 미리보기는 자원을 생성하거나 공개하지 않는다.

#### Error Cases

| 오류 코드 | HTTP | 조건 |
|---|---:|---|
| `MISSING_REQUIRED_FIELD` | 400 | 필수 필드 누락 |
| `INVALID_FIELD_VALUE` | 400 | URL·서울 주소·카테고리 값 오류 |
| `DUPLICATE_RESTAURANT` | 409 | 생성 확정 직전 동일 장소가 등록됨 |
| `IDENTITY_VERIFICATION_REQUIRED` | 409 | 생성 확정 시 동일 장소 판단 상태가 변경됨 |
| `EXTERNAL_SERVICE_ERROR` | 502 | 등록에 필요한 카카오 확인을 완료할 수 없음 |

중복과 보류는 정상적인 미리보기 판정이므로 서버가 판정 가능한 경우 `200`의 `decision`으로 반환한다. `409`는 생성 API에 잘못된 상태의 토큰을 제출했거나 동시 등록으로 미리보기 이후 상태가 바뀐 경우 사용한다.

### API-ADMIN-RESTAURANT-001 맛집 등록 확정

- Method: `POST`
- Path: `/api/admin/restaurants`
- 인증: JWT Access Token과 `ADMIN` 권한 필수
- 권한: 관리자 등록 권한

```json
{
  "confirmationToken": "opaque-confirmation-token"
}
```

`READY` 미리보기에서 받은 유효 토큰만 허용한다. 최초 성공 시 `201 Created`, `Location: /api/restaurants/{restaurantId}`와 미리보기의 `candidate`에 `id`를 추가한 맛집 객체를 반환한다. 생성 완료 Token 재시도는 같은 객체를 `200 OK`로 반환한다. 동시 중복 완료는 `409 DUPLICATE_RESTAURANT`와 기존 맛집 ID·최소 정보를 반환한다. 성공은 관리자 확인이 완료된 공개 맛집이 생성됐음을 뜻하며 영상·관계 없이 공개 조회에 반영된다.

## 6. 유튜버 등록

### API-ADMIN-CREATOR-PREVIEW-001 유튜버 등록 검증 미리보기

- Method: `POST`
- Path: `/api/admin/creator-registration-previews`
- 인증·권한: JWT Access Token과 `ADMIN` 권한 필수
- 관련 요구사항: [FR-ADMIN-001](../../../01-requirements/functional-requirements.md#fr-admin-001-관리자-등록-기능-접근), [FR-ADMIN-003](../../../01-requirements/functional-requirements.md#fr-admin-003-유튜버-정보-등록)

#### Request Body

```json
{
  "channelUrl": "https://www.youtube.com/channel/example"
}
```

| 필드 | 타입 | 필수 | 설명 | 검증 규칙 |
|---|---|---:|---|---|
| `channelUrl` | string | 예 | 관리자가 입력한 YouTube 채널 링크 | 최대 2,048자 HTTPS URL, 최종 호스트 `youtube.com` 또는 하위 도메인, 공개 채널 확인 |

표시 이름과 외부 채널 식별 정보는 클라이언트가 중복 입력하지 않는다. 서버가 YouTube 조회 결과의 현재 채널명과 정규화된 채널 URL을 후보로 반환하며, 확인 없이 생성 성공으로 처리할 수 없다.

#### Success Response

- 상태: `200 OK`

```json
{
  "decision": "READY",
  "confirmationToken": "opaque-confirmation-token",
  "expiresAt": "2026-07-24T03:30:00Z",
  "candidate": {
    "channelName": "채널명",
    "channelUrl": "https://www.youtube.com/channel/example"
  },
  "existingResource": null
}
```

#### Response Fields

`candidate.channelName`과 `candidate.channelUrl`은 필수이며 빈 값일 수 없다. 중복이면 `decision: DUPLICATE`와 기존 유튜버의 `id`, `channelName`, `channelUrl`을 `existingResource`에 제공한다.

#### Error Cases

| 오류 코드 | HTTP | 조건 |
|---|---:|---|
| `MISSING_REQUIRED_FIELD` | 400 | 채널 링크 누락 |
| `INVALID_FIELD_VALUE` | 400 | URL 형식 오류 또는 유효한 공개 채널을 확인할 수 없음 |
| `DUPLICATE_CREATOR` | 409 | 생성 확정 직전 동일 YouTube 채널이 등록됨 |
| `IDENTITY_VERIFICATION_REQUIRED` | 409 | 생성 확정 시 동일 채널 판단 상태가 변경됨 |
| `EXTERNAL_SERVICE_ERROR` | 502 | 필수 YouTube 확인 실패·시간 초과·할당량 문제 |

### API-ADMIN-CREATOR-001 유튜버 등록 확정

- Method: `POST`
- Path: `/api/admin/creators`
- 인증·권한: JWT Access Token과 `ADMIN` 권한 필수

```json
{
  "confirmationToken": "opaque-confirmation-token"
}
```

최초 성공 시 `201 Created`와 `id`, `channelName`, `channelUrl`을 반환한다. Creator 상세 조회 API가 없으므로 `Location`은 반환하지 않는다. 생성 완료 Token 재시도는 같은 객체를 `200 OK`로 반환한다. 동시 중복 완료는 `409 DUPLICATE_CREATOR`와 기존 유튜버 ID·최소 정보를 반환한다. 생성된 공개 유튜버는 필터 선택 목록과 방문 관계 참조에 사용할 수 있다.

## 7. 영상 등록

### API-ADMIN-VIDEO-PREVIEW-001 영상 등록 검증 미리보기

- Method: `POST`
- Path: `/api/admin/video-registration-previews`
- 인증·권한: JWT Access Token과 `ADMIN` 권한 필수
- 관련 요구사항: [FR-ADMIN-001](../../../01-requirements/functional-requirements.md#fr-admin-001-관리자-등록-기능-접근), [FR-ADMIN-004](../../../01-requirements/functional-requirements.md#fr-admin-004-영상-정보-등록)

#### Request Body

```json
{
  "sourceUrl": "https://www.youtube.com/watch?v=example"
}
```

| 필드 | 타입 | 필수 | 설명 | 검증 규칙 |
|---|---|---:|---|---|
| `sourceUrl` | string | 예 | YouTube 원본 영상 링크 | 최대 2,048자 HTTPS URL, 최종 호스트 `youtube.com`·하위 도메인 또는 `youtu.be`, 영상 확인 |

원본 영상 파일, 제목, 썸네일, 채널명과 게시일은 요청으로 받지 않는다. 게시일은 MVP 외부 계약에 포함하지 않는다.

#### Success Response

- 상태: `200 OK`

```json
{
  "decision": "READY",
  "confirmationToken": "opaque-confirmation-token",
  "expiresAt": "2026-07-24T03:30:00Z",
  "candidate": {
    "title": "영상 제목",
    "thumbnailUrl": "https://i.ytimg.com/example.jpg",
    "channelName": "채널명",
    "sourceUrl": "https://www.youtube.com/watch?v=example"
  },
  "existingResource": null
}
```

`candidate`의 모든 필드는 필수이고 빈 값일 수 없다. 중복이면 `decision: DUPLICATE`와 기존 영상의 `id`, `title`, `channelName`, `sourceUrl`을 `existingResource`에 제공한다.

#### Error Cases

| 오류 코드 | HTTP | 조건 |
|---|---:|---|
| `MISSING_REQUIRED_FIELD` | 400 | 원본 링크 누락 |
| `INVALID_FIELD_VALUE` | 400 | URL 오류 또는 유효 영상·필수 표시 정보 확인 불가 |
| `DUPLICATE_VIDEO` | 409 | 생성 확정 직전 동일 YouTube 원본 영상이 등록됨 |
| `IDENTITY_VERIFICATION_REQUIRED` | 409 | 생성 확정 시 동일 영상 판단 상태가 변경됨 |
| `EXTERNAL_SERVICE_ERROR` | 502 | 필수 YouTube 확인 실패·시간 초과·할당량 문제 |

### API-ADMIN-VIDEO-001 영상 등록 확정

- Method: `POST`
- Path: `/api/admin/videos`
- 인증·권한: JWT Access Token과 `ADMIN` 권한 필수

```json
{
  "confirmationToken": "opaque-confirmation-token"
}
```

최초 성공 시 `201 Created`와 `id`, `title`, `thumbnailUrl`, `channelName`, `sourceUrl`을 반환한다. Video 상세 조회 API가 없으므로 `Location`은 반환하지 않는다. 생성 완료 Token 재시도는 같은 객체를 `200 OK`로 반환한다. 동시 중복 완료는 `409 DUPLICATE_VIDEO`와 기존 영상 ID·최소 정보를 반환한다. 생성된 영상은 방문 관계의 근거 후보이며 등록만으로 특정 맛집과 연결되지 않는다.

## 8. 중복 및 입력 검증

카카오 동일 장소, 동일 YouTube 채널, 동일 YouTube 원본 영상을 각각 중복 기준으로 사용한다. 이름·채널명·영상 제목만으로 병합하지 않는다. 미리보기의 `DUPLICATE`는 기존 자원 최소 정보를 제공하고 생성하지 않으며, `REVIEW_REQUIRED`는 Token을 발급하지 않는다. 미리보기 뒤 동시 요청으로 중복이 생기면 생성 API가 `409 DUPLICATE_*`를 반환하고 기존 자원 정보를 오류의 `resource` 필드로 제공한다. 해당 Token은 `DUPLICATE` 완료 결과와 기존 자원 ID를 기록하므로 재시도에도 같은 결과를 반환한다.

## 9. 공개 상태 처리

클라이언트는 공개·비공개·검증 상태를 입력하지 않는다. `READY` 후보를 관리자가 확인해 생성한 데이터는 즉시 `PUBLIC`로 취급하고 관련 사용자 조회에 반영한다. `DUPLICATE`와 `REVIEW_REQUIRED` 미리보기는 자원을 만들거나 공개하지 않는다. 별도 승인 상태와 수정·삭제 API는 만들지 않는다.

## 10. 오류 응답

공통적으로 `401 AUTHENTICATION_REQUIRED`, `403 FORBIDDEN`, `500 INTERNAL_SERVER_ERROR`가 적용되며 본문은 [공통 오류 계약](../common/error-contract.md)을 따른다. 외부 응답 원문·키·내부 예외는 노출하지 않는다.

## 11. 예제

권장 흐름은 각 검증 미리보기의 `READY` 후보를 관리자가 확인하고, 확인 토큰으로 생성한 `201` 응답 식별자를 방문 관계 등록 API에 전달하는 것이다. `DUPLICATE`이면 `existingResource.id`를 사용한다. 하나의 기본 데이터 등록 실패가 다른 대상의 부분 생성을 암시하지 않는다.

## 12. 관련 요구사항 및 규칙

- [PRD-ADMIN-001](../../../04-product/prd/admin/admin-data-management.md), [WS-04](../../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록), 김인안; 리뷰어 박진영
- [FR-ADMIN-001](../../../01-requirements/functional-requirements.md#fr-admin-001-관리자-등록-기능-접근)~[FR-ADMIN-004](../../../01-requirements/functional-requirements.md#fr-admin-004-영상-정보-등록)
- 메타데이터에 나열한 등록·공개·보안·정합성 규칙

## 13. 확정 사항

- `kakaoPlaceUrl`은 최대 2,048자의 HTTPS URL이며 리디렉션을 해석한 최종 호스트가 `place.map.kakao.com`이어야 한다. 외부 확인 실패·시간 초과·할당량 문제는 `502 EXTERNAL_SERVICE_ERROR`이고 토큰을 발급하지 않는다.
- 문자열·전화번호·URL 길이와 형식은 각 요청 표를 따른다.
- 검증 미리보기 확인 Token은 발급 후 10분에 만료된다. 완료·만료 결과는 24시간 재현하며 최초 생성 `201`, 생성 완료 재시도 `200`, 동시 중복 최초·재시도 `409`를 사용한다.
- `REVIEW_REQUIRED` 미리보기는 서버에 등록 요청으로 저장하지 않는다. 관리자는 출처를 수동 재확인하고 입력을 수정해 새 미리보기를 요청한다. 별도 보류 목록·승인 API는 만들지 않는다.

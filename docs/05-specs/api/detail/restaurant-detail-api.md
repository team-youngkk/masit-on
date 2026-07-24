---
id: API-DETAIL-001
title: 맛집 상세 API
status: draft
related_prd:
  - PRD-DETAIL-001
workstream: WS-02
owner: 박진영
reviewers:
  - 김인안
related_requirements:
  - FR-RESTAURANT-008
  - FR-RESTAURANT-009
  - FR-RESTAURANT-010
  - FR-RESTAURANT-011
  - FR-CREATOR-002
  - FR-VIDEO-001
related_business_rules:
  - BR-RESTAURANT-002
  - BR-RESTAURANT-004
  - BR-RESTAURANT-005
  - BR-RESTAURANT-008
  - BR-CREATOR-004
  - BR-CREATOR-007
  - BR-VIDEO-001
  - BR-VIDEO-004
  - BR-VIDEO-007
  - BR-VIDEO-008
  - BR-VIDEO-009
  - BR-VISIT-004
  - BR-VISIT-005
related_nfr:
  - NFR-PERFORMANCE-001
  - NFR-INTEGRITY-004
  - NFR-RELIABILITY-001
  - NFR-RELIABILITY-003
  - NFR-EXTERNAL-001
  - NFR-EXTERNAL-002
  - NFR-COMPATIBILITY-002
  - NFR-COMPATIBILITY-003
  - NFR-TEST-001
  - NFR-TEST-002
related_documents:
  - ../../../04-product/prd/detail/restaurant-detail.md
  - ../../../04-product/prd/discovery/creator-discovery.md
  - ../common/identifier-contract.md
  - ../common/response-contract.md
  - ../common/error-contract.md
  - ../../data/data-model.md
  - ../../data/relationship-rules.md
  - ../../data/lifecycle-rules.md
  - ../../../02-analysis/mvp-workstreams.md
  - ../../../01-requirements/functional-requirements.md
---

# 맛집 상세 API

## 1. 문서 목적

공개 맛집의 기본 정보, 방문 유튜버와 관련 영상 표시 정보를 한 번의 조회로 제공하는 외부 계약을 정의한다.

## 2. 적용 범위

이름, 대표 음식 카테고리, 전체 도로명주소·상세 위치, 전화번호, 카카오 장소 링크와 공개·유효 관계의 채널·영상 정보를 포함한다. 지도, 지번주소, 영상 게시일, 영상 상세와 원본 재생은 제외한다.

## 3. 접근 권한

인증 없이 공개 접근한다. 비공개·삭제 맛집은 존재 여부를 누설하지 않고 찾을 수 없음으로 처리한다.

## 4. API 요약

| API ID | Method | Path | 설명 |
|---|---|---|---|
| [API-DETAIL-001](restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | GET | `/restaurants/{restaurantId}` | 맛집 상세와 방문 콘텐츠 조회 |

기본 정보와 방문 콘텐츠를 별도 API로 분리하지 않는다. PRD가 한 사용자 흐름과 [WS-02](../../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)의 최종 조합 책임을 확정했고, 한 번의 호출이 MVP 프론트엔드 복잡도를 낮춘다. 콘텐츠 제공자 실패는 응답 내부 상태로 격리한다.

## 5. 맛집 상세 조회

### API-DETAIL-001 맛집 상세 조회

- Method: `GET`
- Path: `/restaurants/{restaurantId}`
- 인증: 없음
- 권한: 일반 공개 조회
- 관련 PRD: [PRD-DETAIL-001](../../../04-product/prd/detail/restaurant-detail.md)
- 관련 요구사항: [FR-RESTAURANT-008](../../../01-requirements/functional-requirements.md#fr-restaurant-008-맛집-기본-정보-조회)~[FR-RESTAURANT-011](../../../01-requirements/functional-requirements.md#fr-restaurant-011-영상-연결이-없는-맛집-상세-조회), [FR-CREATOR-002](../../../01-requirements/functional-requirements.md#fr-creator-002-방문-유튜버-정보-확인), [FR-VIDEO-001](../../../01-requirements/functional-requirements.md#fr-video-001-관련-영상-정보-확인)
- 설명: 한 공개 맛집의 기본 정보와 유효 방문 콘텐츠를 반환한다.

#### Path Parameters

| 이름 | 타입 | 필수 | 설명 | 검증 규칙 |
|---|---|---:|---|---|
| `restaurantId` | Identifier | 예 | 맛집 식별자 | 공통 식별자 계약 적용 |

#### Request Rules

- 맛집 기본 정보는 영상·관계 존재 여부와 독립적으로 조회한다.
- 유튜버와 영상은 요청 맛집에 연결된 공개·유효 관계만 사용하고 각각 식별자 기준으로 중복 제거한다.
- 사용자 조회 과정에서 YouTube·카카오 API를 실시간 필수 호출하지 않는다.

#### Success Response

- 상태: `200 OK`

```json
{
  "id": "restaurant-id",
  "name": "맛집 이름",
  "category": "한식",
  "address": {
    "roadAddress": "서울특별시 마포구 월드컵로 1",
    "detailAddress": null
  },
  "phoneNumber": "02-000-0000",
  "kakaoPlaceUrl": "https://place.map.kakao.com/example",
  "contentStatus": "AVAILABLE",
  "visitedBy": [
    {
      "id": "creator-id",
      "channelName": "채널명",
      "channelUrl": "https://www.youtube.com/channel/example"
    }
  ],
  "videos": [
    {
      "id": "video-id",
      "title": "영상 제목",
      "thumbnailUrl": "https://i.ytimg.com/example.jpg",
      "channelName": "채널명",
      "sourceUrl": "https://www.youtube.com/watch?v=example"
    }
  ]
}
```

#### Response Field Definitions

| 필드 | 타입 | 필수 | 설명 | 빈 값 규칙 |
|---|---|---:|---|---|
| `id` | Identifier | 예 | 맛집 식별자 | `null` 불가 |
| `name` | string | 예 | 맛집 이름 | 빈 문자열 불가 |
| `category` | string | 예 | 대표 음식 카테고리 1개 | `null` 불가 |
| `address` | object | 예 | 위치 표시 정보 | 생략·`null` 불가 |
| `address.roadAddress` | string | 예 | 서울특별시 전체 도로명주소 | 빈 문자열 불가 |
| `address.detailAddress` | string 또는 null | 예 | 건물명·층·호 등 등록된 상세 위치 | 미등록이면 `null`, 빈 문자열 불가 |
| `phoneNumber` | string | 예 | 등록된 전화번호 | 빈 문자열·`null` 불가 |
| `kakaoPlaceUrl` | string | 예 | 등록된 카카오 장소 링크 | 빈 문자열·`null` 불가; 실시간 유효성 보장 아님 |
| `contentStatus` | string | 예 | `AVAILABLE` 또는 `TEMPORARILY_UNAVAILABLE` | 생략 불가 |
| `visitedBy` | array | 예 | 중복 제거한 공개 방문 유튜버 전체 | 없음 또는 콘텐츠 실패 시 `[]` |
| `visitedBy[].id` | Identifier | 예 | 유튜버 식별자 | `null` 불가 |
| `visitedBy[].channelName` | string | 예 | 현재 YouTube 채널명 | 빈 문자열 불가 |
| `visitedBy[].channelUrl` | string | 예 | YouTube 채널 링크 | 빈 문자열 불가; 실시간 유효성 보장 아님 |
| `videos` | array | 예 | 중복 제거한 공개 관련 영상 전체 | 없음 또는 콘텐츠 실패 시 `[]` |
| `videos[].id` | Identifier | 예 | 영상 식별자 | `null` 불가 |
| `videos[].title` | string | 예 | 등록된 영상 제목 | 빈 문자열 불가 |
| `videos[].thumbnailUrl` | string | 예 | 등록된 썸네일 URL | 빈 문자열 불가 |
| `videos[].channelName` | string | 예 | 영상 게시 YouTube 채널명 | 빈 문자열 불가 |
| `videos[].sourceUrl` | string | 예 | YouTube 원본 링크 | 빈 문자열 불가; 영상 원본을 의미하지 않음 |

#### Error Cases

| 오류 코드 | HTTP 상태 | 발생 조건 |
|---|---:|---|
| `INVALID_IDENTIFIER` | 400 | 맛집 식별자 형식 오류 |
| `RESTAURANT_NOT_FOUND` | 404 | 맛집 없음, 비공개 또는 삭제 |
| `INTERNAL_SERVER_ERROR` | 500 | 맛집 기본 정보 제공 실패 또는 예상하지 못한 내부 오류 |

## 6. 상세 응답 구성

단일 응답 조합을 권장한다. PRD가 기본 정보와 콘텐츠를 같은 흐름으로 정의하고 [WS-02](../../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)가 최종 조합을 소유한다. 별도 API 분리는 프론트엔드 호출·오류 조합을 늘리며 현재 응답 크기와 변경 빈도만으로 분리할 근거가 없다.

## 7. 방문 유튜버 및 관련 영상 표현

- 유효 방문 관계가 없으면 `contentStatus: AVAILABLE`, `visitedBy: []`, `videos: []`다.
- 관계는 있으나 공개 영상이 없으면 유효한 공개 유튜버가 관계 요건을 충족하는 범위에서 `visitedBy`를 제공하고 `videos`는 `[]`다. 비공개·삭제 영상만이 근거라면 그 관계도 사용자 조회에서 제외한다.
- 같은 유튜버 또는 영상은 여러 관계가 있어도 한 번만 제공한다.
- 영상 게시일은 MVP 사용자 조회 제외 범위라 반환하지 않는다.

## 8. 예외 및 경계 상황

| 상황 | 응답 |
|---|---|
| 방문 관계 없음 | `200`, `contentStatus: AVAILABLE`, 두 목록 `[]` |
| 공개 관련 영상 없음 | `200`, `videos: []` |
| 저장된 외부 링크의 일시 오류 | 기본 정보와 저장된 링크를 유지하며 상세를 실패시키지 않음. API는 실시간 링크 유효성을 보장하지 않음 |
| 비공개·삭제 유튜버·영상·관계 | 해당 콘텐츠와 그 관계를 제외; 기본 정보 유지 |
| 관계·유튜버·영상 제공자 실패 | `200`, `contentStatus: TEMPORARILY_UNAVAILABLE`, 두 목록 `[]` |
| 맛집 기본 정보 제공자 실패 | `500 INTERNAL_SERVER_ERROR` |
| 맛집 없음·비공개·삭제 | `404 RESTAURANT_NOT_FOUND` |

부분 실패 시 빈 목록을 반환하므로 `contentStatus`가 정상적인 콘텐츠 없음과 실패를 구분한다. 서로 다른 제공자의 일부 성공값만 섞어 반환하지 않는다.

## 9. 오류 응답

[공통 오류 계약](../common/error-contract.md)을 따른다. 외부 링크가 현재 열리지 않는다는 사실만으로 상세 오류를 반환하지 않는다.

## 10. 예제

콘텐츠가 없는 정상 상세:

```json
{
  "id": "restaurant-id",
  "name": "맛집 이름",
  "category": "기타",
  "address": {
    "roadAddress": "서울특별시 종로구 종로 1",
    "detailAddress": "2층"
  },
  "phoneNumber": "02-000-0000",
  "kakaoPlaceUrl": "https://place.map.kakao.com/example",
  "contentStatus": "AVAILABLE",
  "visitedBy": [],
  "videos": []
}
```

## 11. 관련 요구사항 및 규칙

- [PRD-DETAIL-001](../../../04-product/prd/detail/restaurant-detail.md), [WS-02](../../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회), 박진영; 리뷰어 김인안
- [FR-RESTAURANT-008](../../../01-requirements/functional-requirements.md#fr-restaurant-008-맛집-기본-정보-조회)~[FR-RESTAURANT-011](../../../01-requirements/functional-requirements.md#fr-restaurant-011-영상-연결이-없는-맛집-상세-조회), [FR-CREATOR-002](../../../01-requirements/functional-requirements.md#fr-creator-002-방문-유튜버-정보-확인), [FR-VIDEO-001](../../../01-requirements/functional-requirements.md#fr-video-001-관련-영상-정보-확인)
- 메타데이터의 관련 비즈니스 규칙과 NFR

## 12. 확정 사항

- `contentStatus`는 필수이며 `AVAILABLE`, `TEMPORARILY_UNAVAILABLE` 두 값만 사용한다.
- 일시적인 외부 링크 도달 실패를 나타내는 별도 필드는 제공하지 않는다. API는 저장된 링크의 실시간 유효성을 보장하지 않으며, 관리자가 삭제·비공개를 확인한 콘텐츠는 응답에서 제외한다.
- 일반 상세의 애플리케이션 서버 내부 처리 시간 목표는 정상 운영 조건 p95 500ms 이하다. 외부 서비스와 사용자 네트워크 지연은 제외한다.
- 상세 조합의 내부 책임 위치는 후속 아키텍처 결정이며 외부 계약을 바꾸지 않는다.

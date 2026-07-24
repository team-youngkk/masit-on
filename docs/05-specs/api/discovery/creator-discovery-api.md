---
id: API-CREATOR-DISCOVERY-001
title: 유튜버 기반 탐색 API
status: draft
related_prd:
  - PRD-DISCOVERY-002
workstream: WS-03
owner: 이우람
reviewers:
  - 양성훈
related_requirements:
  - FR-CREATOR-001
  - FR-CREATOR-003
related_business_rules:
  - BR-CREATOR-001
  - BR-CREATOR-004
  - BR-CREATOR-005
  - BR-CREATOR-007
  - BR-VIDEO-005
  - BR-VIDEO-009
  - BR-VISIT-001
  - BR-VISIT-002
  - BR-VISIT-003
  - BR-VISIT-004
  - BR-VISIT-005
  - BR-VISIT-006
  - BR-VISIT-007
  - BR-SEARCH-003
  - BR-SEARCH-004
  - BR-SEARCH-005
  - BR-SEARCH-006
  - BR-SEARCH-007
related_nfr:
  - NFR-PERFORMANCE-002
  - NFR-INTEGRITY-004
  - NFR-RELIABILITY-001
  - NFR-RELIABILITY-003
  - NFR-TEST-001
  - NFR-TEST-002
  - NFR-MAINTAINABILITY-001
  - NFR-MAINTAINABILITY-002
related_documents:
  - ../../../04-product/prd/discovery/creator-discovery.md
  - ../../../04-product/prd/discovery/restaurant-discovery.md
  - restaurant-discovery-api.md
  - ../common/identifier-contract.md
  - ../common/response-contract.md
  - ../common/error-contract.md
  - ../common/filtering-contract.md
  - ../../data/relationship-rules.md
  - ../../../01-requirements/functional-requirements.md
  - ../../../02-analysis/mvp-workstreams.md
  - ../../../01-requirements/business-rules.md
---

# 유튜버 기반 탐색 API

## 1. 문서 목적

유튜버 필터 선택 목록과 유효 방문 맛집 판정의 외부 경계를 정의한다. 현재 MVP의 유튜버 관리 단위는 YouTube 채널이다.

## 2. 적용 범위

공개 유튜버의 최소 선택 목록은 별도 조회한다. 특정 유튜버가 방문한 맛집은 별도 목록 API를 만들지 않고 `GET /restaurants?creatorId=...`로 조회한다.

## 3. 접근 권한

인증 없이 공개 접근한다. 비공개·삭제 유튜버는 목록과 필터 결과에서 제외한다.

## 4. API 요약

| API ID | Method | Path | 설명 |
|---|---|---|---|
| [API-CREATOR-DISCOVERY-001](creator-discovery-api.md#api-creator-discovery-001-유튜버-필터-선택-목록) | GET | `/creators` | 유튜버 필터 최소 선택 목록 |
| [API-DISCOVERY-001](restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) | GET | `/restaurants?creatorId=...` | 특정 유튜버의 방문 맛집을 최종 목록으로 조회 |

## 5. 유튜버 필터 선택 목록

### API-CREATOR-DISCOVERY-001 유튜버 필터 선택 목록

- Method: `GET`
- Path: `/creators`
- 인증: 없음
- 권한: 일반 공개 조회
- 관련 PRD: [PRD-DISCOVERY-002](../../../04-product/prd/discovery/creator-discovery.md)
- 관련 요구사항: [FR-CREATOR-003](../../../01-requirements/functional-requirements.md#fr-creator-003-유튜버-필터-선택-목록-조회)
- 설명: 공개 유튜버를 현재 채널명 오름차순으로 반환한다.

#### Query Parameters

없음. 검색, 페이지네이션과 상세 필터를 지원하지 않으며 쿼리 파라미터가 오면 `400 INVALID_REQUEST`다.

#### Success Response

- 상태: `200 OK`

```json
{
  "items": [
    {
      "id": "creator-id",
      "channelName": "채널명"
    }
  ]
}
```

#### Response Field Definitions

| 필드 | 타입 | 필수 | 설명 | 빈 값 규칙 |
|---|---|---:|---|---|
| `items` | array | 예 | 채널명 오름차순의 공개 유튜버 | 없으면 `[]` |
| `items[].id` | Identifier | 예 | 유튜버 식별자 | `null` 불가 |
| `items[].channelName` | string | 예 | 현재 YouTube 채널명 | 빈 문자열 불가 |

#### Error Cases

| 오류 코드 | HTTP 상태 | 발생 조건 |
|---|---:|---|
| `INVALID_REQUEST` | 400 | 지원하지 않는 쿼리 파라미터 전달 |
| `INTERNAL_SERVER_ERROR` | 500 | 예상하지 못한 내부 오류 |

## 6. 유튜버 조건 맛집 조회

[FR-CREATOR-001](../../../01-requirements/functional-requirements.md#fr-creator-001-유튜버-기준-방문-맛집-조회)의 주 외부 API는 [API-DISCOVERY-001](restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색)이다. [WS-03](../../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)은 `creatorId`에 대해 유튜버·영상·관계·맛집이 모두 공개·유효하고, 영상 게시 채널이 유튜버와 일치하며, 실제 방문 근거가 있는 고유 맛집만 판정한다. [WS-01](../../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)은 그 결과를 다른 조건과 AND 조합하고 정렬·페이지 처리한다.

존재하지 않거나 공개되지 않은 유튜버 식별자는 `400 INVALID_FIELD_VALUE`, 유효한 공개 유튜버이지만 관계가 없으면 정상 빈 목록이다.

## 7. 중복·공개 규칙

같은 맛집에 같은 유튜버의 근거 영상이 여러 개여도 맛집은 한 번만 반환한다. 근거 영상 없음, 게시 채널 불일치, 대상 중 하나의 비공개·삭제는 해당 관계를 결과에서 제외한다.

## 8. 오류 응답

[공통 오류 계약](../common/error-contract.md)을 따른다. 선택 목록이 없거나 방문 맛집이 없는 상태는 오류가 아니다.

## 9. 관련 요구사항 및 규칙

- [PRD-DISCOVERY-002](../../../04-product/prd/discovery/creator-discovery.md), [WS-03](../../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색), 이우람
- [FR-CREATOR-001](../../../01-requirements/functional-requirements.md#fr-creator-001-유튜버-기준-방문-맛집-조회), [FR-CREATOR-003](../../../01-requirements/functional-requirements.md#fr-creator-003-유튜버-필터-선택-목록-조회)
- [BR-CREATOR-001](../../../01-requirements/business-rules.md#br-creator-001-유튜버-정보의-의미)·[BR-CREATOR-004](../../../01-requirements/business-rules.md#br-creator-004-유튜버-표시-정보)·[BR-CREATOR-005](../../../01-requirements/business-rules.md#br-creator-005-방문-관계의-유튜버-일치)·[BR-CREATOR-007](../../../01-requirements/business-rules.md#br-creator-007-채널-이용-불가-처리), [BR-VIDEO-005](../../../01-requirements/business-rules.md#br-video-005-실제-방문-근거)·[BR-VIDEO-009](../../../01-requirements/business-rules.md#br-video-009-영상-이용-불가-처리), [BR-VISIT-001](../../../01-requirements/business-rules.md#br-visit-001-방문-관계의-구성)~[BR-VISIT-007](../../../01-requirements/business-rules.md#br-visit-007-등록-완료와-검증-상태), [BR-SEARCH-003](../../../01-requirements/business-rules.md#br-search-003-필터-종류와-단일-선택)~[BR-SEARCH-007](../../../01-requirements/business-rules.md#br-search-007-유튜버-필터의-방문-근거)

## 10. 확정 사항

유튜버 조건을 포함한 모든 검색·필터 조합의 애플리케이션 서버 내부 처리 시간 목표는 정상 운영 조건 p95 800ms 이하로 확정한다. 외부 서비스와 사용자 네트워크 지연은 제외한다.

유튜버 상세, 프로필, 구독자 정보, 선택 목록 검색·페이지네이션과 복수 유튜버 선택은 MVP에 포함하지 않는다.

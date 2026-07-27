---
id: API-ADMIN-VISIT-001
title: 관리자 방문 관계 등록 API
status: draft
related_prd:
  - PRD-ADMIN-001
workstream: WS-04
owner: 김인안
reviewers:
  - 박진영
related_requirements:
  - FR-ADMIN-001
  - FR-VISIT-001
related_business_rules:
  - BR-CREATOR-005
  - BR-VIDEO-004
  - BR-VIDEO-005
  - BR-VIDEO-006
  - BR-VISIT-001
  - BR-VISIT-002
  - BR-VISIT-003
  - BR-VISIT-004
  - BR-VISIT-005
  - BR-VISIT-006
  - BR-VISIT-007
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
  - NFR-OBSERVABILITY-001
  - NFR-TEST-001
  - NFR-TEST-002
  - NFR-TEST-003
related_documents:
  - ../../../04-product/prd/admin/admin-data-management.md
  - authentication-api.md
  - reference-data-api.md
  - ../common/identifier-contract.md
  - ../common/response-contract.md
  - ../common/error-contract.md
  - ../../data/relationship-rules.md
  - ../../data/constraints.md
  - ../../../01-requirements/functional-requirements.md
  - ../../../02-analysis/mvp-workstreams.md
  - ../../../01-requirements/business-rules.md
---

# 관리자 방문 관계 등록 API

## 1. 문서 목적

관리자가 실제 방문을 확인한 영상으로 이미 등록된 맛집·유튜버·영상을 원자적으로 연결하는 외부 계약을 정의한다.

## 2. 적용 범위

신규 관계 등록, 세 참조의 존재, 영상 게시 채널 일치, 실제 방문 근거, 조합 중복과 사용자 조회 반영을 포함한다. 방문일, 별도 검증 상태, 수정·삭제·승인 API는 제외한다.

## 3. 인증 및 권한

`Authorization: Bearer` JWT Access Token과 `ADMIN` 등록 권한이 필수다. [관리자 인증 API](authentication-api.md)를 따른다.

## 4. API 요약

| API ID | Method | Path | 설명 |
|---|---|---|---|
| [API-ADMIN-VISIT-001](visit-registration-api.md#api-admin-visit-001-방문-관계-등록) | POST | `/api/admin/visit-relationships` | 맛집·유튜버·영상 방문 관계 등록 |

표준 영문명 `Visit Relationship`을 복수 자원 경로 `visit-relationships`로 표현한다.

## 5. 방문 관계 등록

### API-ADMIN-VISIT-001 방문 관계 등록

- Method: `POST`
- Path: `/api/admin/visit-relationships`
- 인증: JWT Access Token과 `ADMIN` 권한 필수
- 권한: 관리자 등록 권한
- 관련 PRD: [PRD-ADMIN-001](../../../04-product/prd/admin/admin-data-management.md)
- 관련 요구사항: [FR-ADMIN-001](../../../01-requirements/functional-requirements.md#fr-admin-001-관리자-등록-기능-접근), [FR-VISIT-001](../../../01-requirements/functional-requirements.md#fr-visit-001-맛집유튜버영상-방문-관계-등록)

#### Request Body

```json
{
  "restaurantId": "restaurant-id",
  "creatorId": "creator-id",
  "videoId": "video-id",
  "visitEvidenceConfirmed": true
}
```

| 필드 | 타입 | 필수 | 설명 | 검증·빈 값 규칙 |
|---|---|---:|---|---|
| `restaurantId` | Identifier | 예 | 등록된 맛집 1개 | `null`·빈 값 불가, 존재해야 함 |
| `creatorId` | Identifier | 예 | 등록된 YouTube 채널 단위 유튜버 1개 | `null`·빈 값 불가, 존재해야 함 |
| `videoId` | Identifier | 예 | 실제 방문 근거인 등록 영상 1개 | `null`·빈 값 불가, 존재해야 함 |
| `visitEvidenceConfirmed` | boolean | 예 | 관리자가 실제 방문 장면을 확인했음을 선언 | 반드시 `true` |

방문일, 공개 상태와 별도 검증 상태는 요청하지 않는다. `visitEvidenceConfirmed`는 등록 시점의 관리자 확인 선언이며 별도 상태로 저장·노출하는 승인을 뜻하지 않는다.

#### Success Response

- 상태: `201 Created`

```json
{
  "id": "visit-relationship-id",
  "restaurantId": "restaurant-id",
  "creatorId": "creator-id",
  "videoId": "video-id"
}
```

모든 필드는 필수이고 `null`일 수 없다. 모든 식별자는 불투명 JSON 문자열이다.

#### Error Cases

| 오류 코드 | HTTP | 발생 조건 |
|---|---:|---|
| `MISSING_REQUIRED_FIELD` | 400 | 세 식별자 중 하나 이상 누락 |
| `INVALID_IDENTIFIER` | 400 | 식별자 형식 오류 |
| `RESTAURANT_NOT_FOUND` | 404 | 참조 맛집 없음 |
| `CREATOR_NOT_FOUND` | 404 | 참조 유튜버 없음 |
| `VIDEO_NOT_FOUND` | 404 | 참조 영상 없음 |
| `REFERENCE_NOT_PUBLIC` | 422 | 참조 대상 중 하나 이상이 비공개 상태 |
| `DUPLICATE_VISIT_RELATIONSHIP` | 409 | 동일 맛집·유튜버·영상 조합 존재 |
| `IDENTITY_VERIFICATION_REQUIRED` | 409 | 동일 관계 여부를 판단할 수 없어 보류 |
| `VIDEO_CHANNEL_MISMATCH` | 422 | 영상 게시 채널과 선택 유튜버가 다름 |
| `VISIT_EVIDENCE_INSUFFICIENT` | 422 | `visitEvidenceConfirmed`가 없거나 `true`가 아님 |

## 6. 관계 생성 규칙

- 세 참조가 먼저 등록되어 있어야 한다.
- 영상 게시 YouTube 채널과 유튜버의 채널이 같아야 한다.
- 단순 언급·추천·추정 영상은 사용할 수 없다.
- 한 영상은 실제 방문이 확인된 여러 맛집과 각각 별도 관계를 만들 수 있다.
- 같은 맛집과 유튜버라도 영상이 다르면 별도 관계를 만들 수 있다.
- 하나의 요청이 실패하면 관계가 일부만 남거나 조회에 부분 반영돼서는 안 된다.

## 7. 참조 및 중복 검증

참조 없음은 대상별 `404`, 동일 세 대상 조합은 `409`다. 동시 요청에서도 관계 하나만 생성한다. 중복 또는 판단 보류 응답은 새 관계를 만들지 않는다. 실제 방문은 자동 판정하지 않고 권한이 확인된 관리자의 명시적 `true` 선언을 등록 조건으로 사용한다.

## 8. 공개 상태 처리

관리자는 요청에서 공개 상태를 지정하지 않는다. 맛집·유튜버·영상이 모두 공개 상태일 때만 새 관계를 만들 수 있으며 하나라도 비공개이면 `422 REFERENCE_NOT_PUBLIC`로 거부한다. 생성 성공한 관계는 즉시 공개되며 일반 사용자의 유튜버 필터와 맛집 상세에 반영된다.

## 9. 오류 응답

공통 `401`, `403`, `500`과 [공통 오류 계약](../common/error-contract.md)을 적용한다. 검증 실패·중복·참조 없음 시 어떤 관계도 생성하지 않는다.

## 10. 예제

관리자는 영상에서 실제 방문과 게시 채널 일치를 확인한 뒤 세 등록 API의 식별자를 한 요청에 전달한다. 성공 후 해당 관계가 모두 공개 조건을 충족하면 `GET /api/restaurants?creatorId=...`와 `GET /api/restaurants/{restaurantId}`에 반영된다.

## 11. 관련 요구사항 및 규칙

- [PRD-ADMIN-001](../../../04-product/prd/admin/admin-data-management.md), [WS-04](../../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록), 김인안; 리뷰어 박진영
- [FR-ADMIN-001](../../../01-requirements/functional-requirements.md#fr-admin-001-관리자-등록-기능-접근), [FR-VISIT-001](../../../01-requirements/functional-requirements.md#fr-visit-001-맛집유튜버영상-방문-관계-등록)
- [BR-CREATOR-005](../../../01-requirements/business-rules.md#br-creator-005-방문-관계의-유튜버-일치), [BR-VIDEO-004](../../../01-requirements/business-rules.md#br-video-004-영상과-방문-관계의-다대상-연결)~[BR-VIDEO-006](../../../01-requirements/business-rules.md#br-video-006-게시일과-방문일의-구분), [BR-VISIT-001](../../../01-requirements/business-rules.md#br-visit-001-방문-관계의-구성)~[BR-VISIT-007](../../../01-requirements/business-rules.md#br-visit-007-등록-완료와-검증-상태), [BR-ADMIN-001](../../../01-requirements/business-rules.md#br-admin-001-관리자-권한-검증)~[BR-ADMIN-005](../../../01-requirements/business-rules.md#br-admin-005-mvp-관리-기능의-경계)·[BR-ADMIN-007](../../../01-requirements/business-rules.md#br-admin-007-동시-등록의-고유성)·[BR-ADMIN-008](../../../01-requirements/business-rules.md#br-admin-008-보류-요청의-처리)

## 12. 확정 사항

- 비공개 참조 대상으로는 관계를 만들 수 없다.
- 동일 관계 판단이 불가능한 요청은 저장하지 않고 `409 IDENTITY_VERIFICATION_REQUIRED`로 거부한다. 관리자가 세 참조를 다시 확인한 뒤 재요청하며 별도 보류 목록 API는 만들지 않는다.

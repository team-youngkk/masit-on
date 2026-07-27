---
related_documents:
  - ../README.md
  - ../../01-requirements/functional-requirements.md
  - ../../01-requirements/business-rules.md
  - common/identifier-contract.md
  - common/response-contract.md
  - common/error-contract.md
  - common/pagination-contract.md
  - common/filtering-contract.md
  - ../api-traceability.md
  - common/date-time-contract.md
  - discovery/restaurant-discovery-api.md
  - ../../02-analysis/mvp-workstreams.md
  - discovery/creator-discovery-api.md
  - detail/restaurant-detail-api.md
  - admin/reference-data-api.md
  - admin/visit-registration-api.md
  - admin/authentication-api.md
  - ../../00-overview/scope.md
  - ../api-review.md
  - ../../07-adr/platform/web-003-routing-boundary.md
---

# 맛잇온 API 계약

## 1. 문서 목적

1차 MVP의 공개 맛집 탐색·상세 조회와 관리자 데이터 등록에 필요한 외부 HTTP 계약을 정의한다. 내부 계층, 저장 기술과 외부 서비스 연계 구현은 범위 밖이다.

## 2. API 설계 원칙

- 자원 목록의 조건인 검색·필터는 `GET /api/restaurants`에 통합한다.
- 외부 백엔드 API는 버전 없는 `/api` 접두사를 사용하고 일반 사용자 조회와 관리자 변경은 `/api/admin` 경계로 분리한다.
- 도메인 엔티티마다 CRUD를 만들지 않고 PRD 사용자 흐름 단위로 구성한다.
- 응답은 화면에 필요한 필드만 포함하고 비공개·삭제 데이터를 노출하지 않는다.
- 정상 빈 목록은 `200`과 `[]`, 존재하지 않는 단일 자원은 `404`로 구분한다.
- MVP에 확정되지 않은 수정·삭제·승인·상세·추천 API는 제공하지 않는다.

## 3. 공통 계약

- [식별자 계약](common/identifier-contract.md)
- [응답 계약](common/response-contract.md)
- [오류 계약](common/error-contract.md)
- [페이지네이션 계약](common/pagination-contract.md)
- [검색·필터 계약](common/filtering-contract.md)
- [날짜·시간 계약](common/date-time-contract.md)

## 4. 기능별 API 문서

| 기능 | 문서 | 주 Workstream |
|---|---|---|
| 맛집 목록·검색·필터 | [맛집 탐색 API](discovery/restaurant-discovery-api.md) | [WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) |
| 유튜버 선택·관계 판정 | [유튜버 기반 탐색 API](discovery/creator-discovery-api.md) | [WS-03](../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) |
| 맛집 기본 정보·방문 콘텐츠 | [맛집 상세 API](detail/restaurant-detail-api.md) | [WS-02](../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) |
| 맛집·유튜버·영상 등록 | [관리자 기본 데이터 API](admin/reference-data-api.md) | [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |
| 방문 관계 등록 | [관리자 방문 관계 등록 API](admin/visit-registration-api.md) | [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |
| 관리자 JWT 인증 | [관리자 인증 API](admin/authentication-api.md) | [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |

## 5. 인증 및 접근 범위

`GET /api/restaurants`, `GET /api/creators`, `GET /api/restaurants/{restaurantId}`는 인증 없이 사용한다. 로그인과 재발급의 구체적 예외를 제외한 `/api/admin/**` 등록 API는 Spring Security가 검증하는 JWT Access Token과 `ADMIN` 권한을 요구한다. 인증 정보가 없거나 유효하지 않으면 `401`, 인증됐으나 관리자 권한이 없으면 `403`을 사용한다. 화면·API·운영 경로 소유권과 matcher 순서는 [ADR-WEB-003](../../07-adr/platform/web-003-routing-boundary.md)을 따른다.

## 6. API 변경 절차

### 사용자 동작 또는 범위 변경

1. [scope.md](../../00-overview/scope.md)와 기능 PRD를 검토한다.
2. 기능 요구사항과 비즈니스 규칙을 수정한다.
3. API 계약을 수정한다.
4. [api-traceability.md](../api-traceability.md)를 갱신한다.
5. 데이터 모델과 구현 영향을 검토한다.

### API 계약만 변경

1. 관련 PRD와 요구사항 영향을 확인한다.
2. API 문서를 수정한다.
3. 영향받는 Workstream 담당자가 리뷰한다.
4. 프론트엔드 계약을 갱신한다.
5. 구현과 테스트를 수정한다.
6. [api-traceability.md](../api-traceability.md)를 갱신한다.

내부 구현만 바뀌고 외부 동작이 같으면 API 계약을 수정하지 않는다. 기술 선택은 아키텍처 문서와 ADR에서 관리한다.

## 7. 호환성 원칙

필드 제거·이름 변경·타입 변경, 필수 입력 추가, 기존 상태 코드 의미 변경은 호환되지 않는 변경이다. 선택 응답 필드 추가도 클라이언트의 미지 필드 허용을 전제로 리뷰한다. `/api`는 화면과 백엔드를 구분하는 접두사이며 버전이 아니다. 확정된 버전 ADR이 없으므로 `/v1` 같은 경로 버전 접두사는 도입하지 않는다.

## 8. 확정된 주요 결정

외부 식별자는 불투명 문자열, 관리자는 Spring Security JWT Access Token과 Redis Refresh Token, 방문 관계는 `Visit Relationship`과 `/api/admin/visit-relationships`, 외부 데이터 등록은 검증 미리보기와 확인 토큰의 2단계 흐름을 사용한다. 페이지 번호는 1부터 시작하고 상세 콘텐츠 부분 실패는 필수 `contentStatus`로 구분한다. 모든 오류 응답에는 서버 생성 `traceId`를 포함한다. 전체 결정 목록은 [api-review.md](../api-review.md)를 따른다.

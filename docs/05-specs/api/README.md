---
related_documents:
  - ../README.md
  - ../../00-overview/scope.md
  - ../../01-requirements/functional-requirements.md
  - ../../01-requirements/business-rules.md
  - ../api-traceability.md
  - common/identifier-contract.md
  - common/response-contract.md
  - common/error-contract.md
  - common/pagination-contract.md
  - common/authentication-contract.md
  - common/second-expansion-contract.md
---

# 맛잇온 API 계약

## 1. 목적과 원칙

제품 요구사항과 비즈니스 규칙을 HTTP 경로·입출력·권한·오류 계약으로 연결한다. 화면 구현이나 내부 패키지 구조를 이 문서에서 정하지 않는다.

- 공개 조회는 `/api`, 관리자 명령은 `/api/admin`, 현재 회원의 개인 자원은 `/api/me` 경계로 구분한다.
- 정상 빈 목록은 `200 OK`와 `items: []`, 없는 단일 자원은 기능별 `404`를 사용한다.
- 성공 응답은 공통 `data` 래퍼 없이 자원 자체를 반환하고 페이지 목록만 본문에 `page`를 둔다.
- 오류는 공통 오류 본문과 서버가 생성한 `traceId`를 사용한다.
- 요구사항과 범위를 승인한 뒤 경로를 확정하며 계약 변경 시 추적표와 소비자 테스트를 함께 갱신한다.

## 2. 공통 계약

- [식별자 계약](common/identifier-contract.md)
- [응답 계약](common/response-contract.md)
- [오류 계약](common/error-contract.md)
- [페이지네이션 계약](common/pagination-contract.md)
- [필터 계약](common/filtering-contract.md)
- [날짜·시간 계약](common/date-time-contract.md)
- [인증 계약](common/authentication-contract.md)
- [검증 참여자 제한 공개 계약](common/validation-access-contract.md)
- [2차 확장 공통 계약](common/second-expansion-contract.md)

## 3. MVP·1차 확장 API

| 기능 | 문서 |
|---|---|
| 맛집 탐색 | [맛집 탐색 API](discovery/restaurant-discovery-api.md) |
| 유튜버 기반 탐색 | [유튜버 기반 탐색 API](discovery/creator-discovery-api.md) |
| 맛집·유튜버 상세 | [맛집 상세 API](detail/restaurant-detail-api.md), [유튜버 상세 API](detail/creator-detail-api.md) |
| 관리자 인증·기본 데이터·방문 관계 | [관리자 인증](admin/authentication-api.md), [기본 데이터](admin/reference-data-api.md), [방문 관계](admin/visit-registration-api.md) |
| 회원 인증 | [회원 인증 API](account/member-authentication-api.md) |
| 찜·최근 본 맛집 | [개인 맛집 관리 API](personal/personal-restaurant-api.md) |
| 지도 탐색 | [지도 탐색 API](discovery/map-discovery-api.md) |

## 4. 2차 확장 API

| 기능 | 문서 | Workstream |
|---|---|---|
| 개인 컬렉션 | [개인 컬렉션 API](personal/personal-collection-api.md) | WS-09 |
| 인기 맛집 | [인기 맛집 API](discovery/popular-restaurant-api.md) | WS-10 |
| 공개·관리자 큐레이션 | [큐레이션 API](curation/curation-api.md) | WS-11 |
| 사용자 제보·신고와 관리자 검토 | [사용자 제보·신고 API](participation/submission-report-api.md) | WS-12 |
| 사용자 알림 | [사용자 알림 API](notification/notification-api.md) | WS-13 |

컬렉션 직접 순서 변경은 승인 범위가 아니므로 경로를 제공하지 않는다. 제보·신고 처리 결과 알림은 회원이 시작한 요청의 필수 서비스 내 고지이므로 알림 설정 변경·동의·해지 경로도 제공하지 않는다.

## 5. 변경 절차

1. 범위와 PRD를 확인한다.
2. 기능 요구사항·비즈니스 규칙을 먼저 수정한다.
3. 기능 API와 공통 계약을 수정한다.
4. [API 추적표](../api-traceability.md), 데이터 계약과 Workstream 영향을 갱신한다.
5. 계약·통합 테스트와 소비자 화면을 수정한다.

호환되지 않는 필드 제거·이름 변경·의미 변경은 버전 정책 또는 별도 ADR 승인 없이 적용하지 않는다.

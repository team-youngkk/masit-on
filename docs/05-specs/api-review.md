---
related_documents:
  - ../01-requirements/requirements-review.md
  - api/README.md
  - api-traceability.md
  - api/discovery/restaurant-discovery-api.md
  - api/discovery/creator-discovery-api.md
  - api/detail/restaurant-detail-api.md
  - api/admin/authentication-api.md
  - api/admin/reference-data-api.md
  - api/admin/visit-registration-api.md
  - data/data-review.md
  - ../02-analysis/mvp-workstreams.md
  - ../04-product/prd/discovery/creator-discovery.md
  - ../01-requirements/functional-requirements.md
  - ../04-product/prd/admin/admin-data-management.md
  - ../04-product/prd/discovery/restaurant-discovery.md
  - ../04-product/prd/detail/restaurant-detail.md
  - ../01-requirements/non-functional-requirements.md
  - ../07-adr/security/auth-003-confirmation-token.md
  - ../07-adr/platform/web-006-unified-login-rbac-route.md
---

# 맛잇온 API 계약 검토

## 1. 검토 목적

API를 임의 정책 결정 없이 구현·프론트엔드 연동 가능한 상태로 만들기 위해 충돌, 차단 결정, 후속 데이터 모델·운영 결정을 분리한다.

## 2. 검토 결과 요약

- 일반 조회는 `/api/restaurants`, `/api/creators`, `/api/restaurants/{restaurantId}` 세 API 경로로 과도한 분리 없이 구성했다.
- 관리자 등록은 하나의 PRD·[WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 안에서 기본 데이터와 방문 관계 두 단계로 나눴다.
- 기존 Critical 차단 항목 4개와 후속 화면·API 라우팅 차단 항목 1개는 API 계약과 ADR 결정으로 해소했다.
- 외부 식별자는 불투명 JSON 문자열, 관리자 인증·인가는 Spring Security와 JWT를 사용한다.
- 방문 관계 표준명과 경로는 `Visit Relationship`, `/api/admin/visit-relationships`로 확정했다.
- 외부 데이터 등록은 검증 미리보기에서 PostgreSQL 저장형 확인 Token을 발급하고 생성 요청에서 원자적으로 소비하는 2단계 흐름을 사용한다.
- 페이지 번호는 1부터 시작하는 것으로 확정했다.
- 기능 요구사항과 상위 범위의 직접 충돌은 발견하지 않았으며, [PRD-DISCOVERY-002](../04-product/prd/discovery/creator-discovery.md)에 있던 [FR-CREATOR-003](../01-requirements/functional-requirements.md#fr-creator-003-유튜버-필터-선택-목록-조회) 불일치 리스크 문구도 정정했다.

## 3. 해결한 API 차단 항목

### RV-API-001 식별자 JSON 표현

- 중요도: Critical
- 현재 상태: 결정 완료
- 관련 PRD: 전체 기능 PRD
- 관련 요구사항: 식별자를 입력·반환하는 모든 요구사항
- 영향 API: 모든 기능 API
- 결정: 모든 외부 자원 식별자는 비어 있지 않은 불투명 JSON 문자열로 전달한다. 생성 알고리즘과 저장 타입은 후속 설계로 분리한다.
- 영향: 프론트엔드는 식별자를 분석하지 않고 문자열로 보관·전달한다.
- 결정 시점: 2026-07-24 API 계약에서 완료

### RV-API-002 관리자 인증 전달 계약

- 중요도: Critical
- 현재 상태: 결정 완료
- 관련 PRD: [PRD-ADMIN-001](../04-product/prd/admin/admin-data-management.md)
- 관련 요구사항: [FR-ADMIN-001](../01-requirements/functional-requirements.md#fr-admin-001-관리자-등록-기능-접근)
- 영향 API: 모든 `/api/admin` API
- 결정: 사전 발급 `loginId`·비밀번호로 JWT Access Token과 Refresh Token을 발급한다. Access Token은 메모리에 유지해 `Authorization: Bearer` 헤더로 전달하고, Redis 8.8에 저장한 Refresh Token은 `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/admin/auth` 쿠키로만 전달·회전한다.
- 영향: 로그인·토큰 재발급·로그아웃과 모든 관리자 API의 인증·인가 전달 계약이 확정됐다.
- 결정 시점: 2026-07-24 API 계약에서 완료

### RV-API-003 방문 관계 자원 영문명과 경로

- 중요도: Critical
- 현재 상태: 결정 완료
- 관련 PRD: [PRD-ADMIN-001](../04-product/prd/admin/admin-data-management.md)
- 관련 요구사항: [FR-VISIT-001](../01-requirements/functional-requirements.md#fr-visit-001-맛집유튜버영상-방문-관계-등록)
- 영향 API: [API-ADMIN-VISIT-001](api/admin/visit-registration-api.md#api-admin-visit-001-방문-관계-등록)
- 결정: 표준 영문명은 `Visit Relationship`, 컬렉션 경로는 `/api/admin/visit-relationships`를 사용한다.
- 영향: 용어집과 [API-ADMIN-VISIT-001](api/admin/visit-registration-api.md#api-admin-visit-001-방문-관계-등록) 경로를 함께 확정했다.
- 결정 시점: 2026-07-24 API 계약에서 완료

### RV-API-004 외부 조회와 관리자 확인 HTTP 흐름

- 중요도: Critical
- 현재 상태: 결정 완료
- 관련 PRD: [PRD-ADMIN-001](../04-product/prd/admin/admin-data-management.md)
- 관련 요구사항: [FR-ADMIN-002](../01-requirements/functional-requirements.md#fr-admin-002-맛집-정보-등록), [FR-ADMIN-003](../01-requirements/functional-requirements.md#fr-admin-003-유튜버-정보-등록), [FR-ADMIN-004](../01-requirements/functional-requirements.md#fr-admin-004-영상-정보-등록)
- 영향 API: 세 기본 데이터 등록 API
- 결정: 자원별 검증 미리보기 POST가 `READY`, `DUPLICATE`, `REVIEW_REQUIRED`와 정규화 후보를 반환한다. 관리자는 `READY` 후보를 확인한 뒤 JWT의 관리자 식별자에 묶인 `confirmationToken`으로 생성한다. Token은 PostgreSQL에 해시·후보 Snapshot을 저장하고 Entity 생성과 원자적으로 소비한다.
- 영향: 외부 조회 결과 확인, 중복 기존 자원 재사용, 보류와 최종 생성 경계가 분리됐다. 최초 생성은 `201`, 생성 완료 재시도는 기존 자원의 `200`, 동시 중복 최초·재시도는 같은 `409`다.
- 결정 시점: 2026-07-24 API 흐름 확정, 2026-07-27 [ADR-AUTH-003](../07-adr/security/auth-003-confirmation-token.md)으로 Token 저장·재시도 계약 확정

## 4. 데이터 모델 전 결정 항목

### RV-API-005 공개 상태의 등록 초기값

- 중요도: High
- 현재 상태: 결정 완료
- 관련 PRD: [PRD-ADMIN-001](../04-product/prd/admin/admin-data-management.md)
- 관련 요구사항: [FR-ADMIN-002](../01-requirements/functional-requirements.md#fr-admin-002-맛집-정보-등록)~[FR-ADMIN-004](../01-requirements/functional-requirements.md#fr-admin-004-영상-정보-등록), [FR-VISIT-001](../01-requirements/functional-requirements.md#fr-visit-001-맛집유튜버영상-방문-관계-등록)
- 영향 API: 모든 관리자 등록·공개 조회 API
- 결정: `READY` 후보를 관리자가 확인해 생성한 기본 데이터는 즉시 `PUBLIC`로 취급한다. 미리보기와 보류는 자원을 만들지 않는다.
- 영향: 생성 성공 후 관련 공개 조회에 즉시 반영한다. 공개 상태 입력 필드는 두지 않는다.
- 결정 시점: 2026-07-24 API 계약에서 완료

### RV-API-006 비공개 참조 대상과 관계 생성

- 중요도: High
- 현재 상태: 결정 완료
- 관련 PRD: [PRD-ADMIN-001](../04-product/prd/admin/admin-data-management.md)
- 관련 요구사항: [FR-VISIT-001](../01-requirements/functional-requirements.md#fr-visit-001-맛집유튜버영상-방문-관계-등록)
- 영향 API: [API-ADMIN-VISIT-001](api/admin/visit-registration-api.md#api-admin-visit-001-방문-관계-등록), [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회)
- 결정: 맛집·유튜버·영상이 모두 공개일 때만 새 관계를 만들며 하나라도 비공개이면 `422 REFERENCE_NOT_PUBLIC`로 거부한다.
- 영향: 생성 성공한 관계는 즉시 공개 조회에 반영되고 비공개 참조의 잠복 관계를 만들지 않는다.
- 결정 시점: 2026-07-24 API 계약에서 완료

### RV-API-007 중복·보류 결과의 식별 정보

- 중요도: High
- 현재 상태: 결정 완료
- 관련 PRD: [PRD-ADMIN-001](../04-product/prd/admin/admin-data-management.md)
- 관련 요구사항: [FR-ADMIN-002](../01-requirements/functional-requirements.md#fr-admin-002-맛집-정보-등록)~[FR-ADMIN-004](../01-requirements/functional-requirements.md#fr-admin-004-영상-정보-등록), [FR-VISIT-001](../01-requirements/functional-requirements.md#fr-visit-001-맛집유튜버영상-방문-관계-등록)
- 영향 API: 모든 관리자 등록 API
- 결정: 미리보기의 `DUPLICATE`와 생성 시 동시성 `409`에 기존 자원의 식별자와 최소 표시 정보를 제공한다. 관리자 전용 별도 목록 API는 만들지 않는다.
- 영향: 관리자는 기존 식별자를 다음 등록 단계에 바로 사용할 수 있다.
- 결정 시점: 2026-07-24 API 계약에서 완료

## 5. 프론트엔드 연동 전 결정 항목

### RV-API-008 페이지 번호 시작 기준

- 중요도: High
- 현재 상태: 결정 완료
- 관련 PRD: [PRD-DISCOVERY-001](../04-product/prd/discovery/restaurant-discovery.md)
- 관련 요구사항: [FR-RESTAURANT-001](../01-requirements/functional-requirements.md#fr-restaurant-001-맛집-목록-조회), [FR-RESTAURANT-006](../01-requirements/functional-requirements.md#fr-restaurant-006-페이지-단위-조회)
- 영향 API: [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색)
- 결정: 페이지 번호는 1부터 시작하고 기본값은 1이다. 0 이하는 `400 INVALID_FIELD_VALUE`다.
- 영향: 화면 페이지 번호와 API 번호를 변환하지 않고 그대로 사용한다.
- 결정 시점: 2026-07-24 API 계약에서 완료

### RV-API-009 상세 콘텐츠 부분 실패 표현

- 중요도: High
- 현재 상태: 결정 완료
- 관련 PRD: [PRD-DETAIL-001](../04-product/prd/detail/restaurant-detail.md)
- 관련 요구사항: [FR-CREATOR-002](../01-requirements/functional-requirements.md#fr-creator-002-방문-유튜버-정보-확인), 공통 기능 규칙 5.2
- 영향 API: [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회)
- 결정: 필수 `contentStatus`에 `AVAILABLE`, `TEMPORARILY_UNAVAILABLE` 두 값만 사용한다.
- 영향: `AVAILABLE`과 빈 배열은 정상적인 콘텐츠 없음, `TEMPORARILY_UNAVAILABLE`과 빈 배열은 재시도 가능한 제공자 실패다.
- 결정 시점: 2026-07-24 API 계약에서 완료

### RV-API-010 입력 길이와 형식 상한

- 중요도: High
- 현재 상태: 결정 완료
- 관련 PRD: [PRD-DISCOVERY-001](../04-product/prd/discovery/restaurant-discovery.md), [PRD-ADMIN-001](../04-product/prd/admin/admin-data-management.md)
- 관련 요구사항: [FR-RESTAURANT-002](../01-requirements/functional-requirements.md#fr-restaurant-002-맛집-이름-검색), [FR-ADMIN-002](../01-requirements/functional-requirements.md#fr-admin-002-맛집-정보-등록)~[FR-ADMIN-004](../01-requirements/functional-requirements.md#fr-admin-004-영상-정보-등록)
- 영향 API: 검색과 모든 관리자 등록 API
- 결정: 검색어·맛집명·기타 음식명은 각각 100자, 도로명주소 255자, 상세 위치 200자, URL 2,048자다. 전화번호는 7~20자이며 숫자·공백·`+`·`-`·괄호만 허용한다. 로그인 ID는 100자, 비밀번호는 12~128자다.
- 영향: 프론트엔드와 서버가 같은 경계값으로 검증하고 초과값은 `400 INVALID_FIELD_VALUE`로 처리한다.
- 결정 시점: 2026-07-24 API 계약에서 완료

### RV-API-011 요청 추적 식별자

- 중요도: Medium
- 현재 상태: 결정 완료
- 관련 PRD: 전체
- 관련 요구사항: [NFR-OBSERVABILITY-001](../01-requirements/non-functional-requirements.md#nfr-observability-001-요청-추적과-오류-분류)
- 영향 API: 공통 오류 응답
- 결정: 모든 오류 응답에 서버가 생성한 비어 있지 않은 불투명 문자열 `traceId`를 필수로 포함하고 같은 값을 로그에 기록한다.
- 영향: 프론트엔드는 구조를 해석하지 않고 오류 문의·관측 상관관계에 그대로 사용한다.
- 결정 시점: 2026-07-24 API 계약에서 완료

## 6. API별 검토 사항

### RV-API-012 유튜버 선택 목록 PRD 리스크 문구

- 중요도: Medium
- 현재 상태: 결정 완료
- 관련 PRD: [PRD-DISCOVERY-002](../04-product/prd/discovery/creator-discovery.md) 제18장
- 관련 요구사항: [FR-CREATOR-003](../01-requirements/functional-requirements.md#fr-creator-003-유튜버-필터-선택-목록-조회)
- 영향 API: [API-CREATOR-DISCOVERY-001](api/discovery/creator-discovery-api.md#api-creator-discovery-001-유튜버-필터-선택-목록)
- 결정: 불일치 리스크 문구를 제거하고 [FR-CREATOR-003](../01-requirements/functional-requirements.md#fr-creator-003-유튜버-필터-선택-목록-조회)의 최소·비페이지 선택 목록 경계 유지 위험으로 교체했다.
- 영향: PRD와 기능 요구사항, [API-CREATOR-DISCOVERY-001](api/discovery/creator-discovery-api.md#api-creator-discovery-001-유튜버-필터-선택-목록)의 범위가 일치한다.
- 결정 시점: 2026-07-24 문서 동기화에서 완료

### RV-API-013 외부 링크 오류의 사용자 표시

- 중요도: Medium
- 현재 상태: 결정 완료
- 관련 PRD: [PRD-DETAIL-001](../04-product/prd/detail/restaurant-detail.md)
- 관련 요구사항: [FR-RESTAURANT-008](../01-requirements/functional-requirements.md#fr-restaurant-008-맛집-기본-정보-조회), [FR-VIDEO-001](../01-requirements/functional-requirements.md#fr-video-001-관련-영상-정보-확인)
- 영향 API: [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회)
- 결정: 외부 링크의 실시간 상태 필드는 제공하지 않는다. 일시 실패에도 저장된 링크와 기본 상세를 유지하고, 관리자가 삭제·비공개를 확인한 콘텐츠만 응답에서 제외한다.
- 영향: 프론트엔드는 링크의 실시간 성공을 보장받지 않으며 링크 실패를 맛집 상세 실패로 해석하지 않는다.
- 결정 시점: 2026-07-24 API 계약에서 완료

### RV-API-014 화면·API·운영 경로 경계

- 중요도: Critical
- 현재 상태: 결정 완료
- 관련 요구사항: [FR-RESTAURANT-001](../01-requirements/functional-requirements.md#fr-restaurant-001-맛집-목록-조회), [FR-RESTAURANT-008](../01-requirements/functional-requirements.md#fr-restaurant-008-맛집-기본-정보-조회), [FR-ADMIN-001](../01-requirements/functional-requirements.md#fr-admin-001-관리자-등록-기능-접근), [NFR-AVAILABILITY-001](../01-requirements/non-functional-requirements.md#nfr-availability-001-상태-확인과-장애-구분)
- 영향 API: 모든 외부 API와 관리자 인증 API
- 결정: 모든 백엔드 API는 버전 없는 `/api` 접두사를 사용한다. Nginx는 `/api/**`만 Spring Boot, 나머지 외부 경로는 Next.js로 전달하고 `/internal/**`은 인터넷에서 차단한다. 통합 로그인·재발급 matcher를 포괄 관리자 matcher보다 먼저 평가한다.
- 영향: 화면과 API 경로 충돌을 제거하고, 통합 로그인·재발급 예외와 내부 상태 확인 경계를 명시했다.
- 결정 시점: 2026-08-18 [ADR-WEB-006](../07-adr/platform/web-006-unified-login-rbac-route.md)으로 현재 경계를 재확정했다. 2026-07-27 [ADR-WEB-003](../07-adr/platform/web-003-routing-boundary.md)의 최초 결정은 WEB-006이 전체 대체한 역사적 근거다.

### RV-API-015 맛집 상세 콘텐츠 정렬

- 중요도: Medium
- 현재 상태: 결정 완료 (2026-07-27)
- 관련 PRD: [PRD-DETAIL-001](../04-product/prd/detail/restaurant-detail.md)
- 관련 요구사항: [FR-CREATOR-002](../01-requirements/functional-requirements.md#fr-creator-002-방문-유튜버-정보-확인), [FR-VIDEO-001](../01-requirements/functional-requirements.md#fr-video-001-관련-영상-정보-확인)
- 영향 API: [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회)
- 결정: `visitedBy`는 `channelName`·Creator ID, `videos`는 `title`·Video ID 오름차순으로 안정 정렬한다.
- 영향: 게시일·방문일 최신순을 도입하지 않고 같은 데이터에서 동일한 배열 순서를 보장한다.
- 근거: 2026-07-27 사용자 승인

### RV-API-016 관리자 미리보기의 외부 제공자 ID 표시

- 중요도: High
- 현재 상태: 결정 완료 (2026-07-27)
- 관련 ADR: [ADR-EXT-001](../07-adr/integration/ext-001-reference-verification.md)
- 영향 API: [API-ADMIN-RESTAURANT-PREVIEW-001](api/admin/reference-data-api.md#api-admin-restaurant-preview-001-맛집-등록-검증-미리보기), [API-ADMIN-CREATOR-PREVIEW-001](api/admin/reference-data-api.md#api-admin-creator-preview-001-유튜버-등록-검증-미리보기), [API-ADMIN-VIDEO-PREVIEW-001](api/admin/reference-data-api.md#api-admin-video-preview-001-영상-등록-검증-미리보기)
- 결정: 외부 제공자 ID를 API·화면에 노출하지 않고 서버의 동일성 판정·후보 Snapshot·저장소 유일 키에만 사용한다.
- 관리자 확인: 정규화된 이름·주소·URL·제목·채널명·썸네일로 수행한다.
- 보안 경계: 외부 ID는 비밀정보는 아니지만 내부 구현 정보로 취급해 공개·관리자 응답과 업무 로그에서 제외한다.
- 근거: 2026-07-27 사용자 승인, [ADR-EXT-001](../07-adr/integration/ext-001-reference-verification.md) 갱신

## 7. 과도한 분리 및 결합 검토

- `/api/restaurants` 하나에 목록·검색·세 필터를 통합한 것은 모두 같은 자원 목록의 조건이므로 적절하다.
- 유튜버 최소 선택 목록은 반환 모델·페이지 정책이 달라 `/api/creators`로 분리했다. 유튜버 방문 맛집용 별도 목록 API는 만들지 않았다.
- 맛집 상세는 PRD 사용자 흐름과 [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) 책임에 맞춰 기본 정보·방문 콘텐츠를 한 응답으로 결합했다. 부분 실패 상태로 외부 장애를 격리한다.
- 관리자 기본 데이터 세 POST는 참조 선행 데이터를 각각 생성해야 해 분리하되 하나의 문서·Workstream에 유지했다.
- 방문 관계 등록은 세 자원의 원자적 조합이라 기본 데이터 등록과 별도 엔드포인트가 적절하다.
- 엔티티별 수정·삭제·목록·상세 CRUD는 추가하지 않았다.

## 8. 우선순위별 결정 목록

| 우선순위 | 항목 |
|---|---|
| 완료 | [RV-API-001](api-review.md#rv-api-001-식별자-json-표현)~[RV-API-016](api-review.md#rv-api-016-관리자-미리보기의-외부-제공자-id-표시) |

## 9. 권장 결정 순서

1. 완료: 방문 관계 표준 영문명, 외부 식별자, 관리자 인증, 외부 조회·확인 흐름을 계약에 반영했다.
2. 완료: 공개·중복·보류·비공개 참조 관계 정책을 확정했다.
3. 완료: 1-based 페이지, 입력 상한과 상세 부분 실패 필드를 확정했다.
4. 완료: 요청 추적, 외부 링크 상태 표현과 화면·API·운영 경로 경계를 확정했다.
5. 완료: 상세 콘텐츠 정렬과 tie-breaker를 확정했다.
6. 완료: 외부 제공자 ID는 내부 동일성 값으로만 사용하도록 API와 ADR을 동기화했다.

## 10. API 계약 완료 기준

- [RV-API-001](api-review.md#rv-api-001-식별자-json-표현)~[RV-API-016](api-review.md#rv-api-016-관리자-미리보기의-외부-제공자-id-표시)이 모두 결정 완료다.
- 모든 요청·응답 식별자 타입과 관리자 인증 전달 방식이 프론트엔드에 명확하다.
- 등록 확인·중복·보류·공개 반영 흐름이 끝까지 호출 가능하다.
- 페이지·필터·빈 결과·404·부분 실패·외부 장애·원자성 계약 테스트가 작성 가능하다.
- MVP 제외 기능이나 내부 데이터베이스·ORM·클래스 설계가 API에 추가되지 않았다.

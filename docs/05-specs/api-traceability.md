---
related_documents:
  - ../01-requirements/functional-requirements.md
  - ../01-requirements/business-rules.md
  - ../01-requirements/non-functional-requirements.md
  - ../03-team/ownership.md
  - ../04-product/traceability.md
  - api/README.md
  - data/data-traceability.md
  - ../07-adr/adr-traceability.md
  - ../04-product/prd/discovery/restaurant-discovery.md
  - api/discovery/restaurant-discovery-api.md
  - ../02-analysis/mvp-workstreams.md
  - ../04-product/prd/discovery/creator-discovery.md
  - api/discovery/creator-discovery-api.md
  - ../04-product/prd/detail/restaurant-detail.md
  - api/detail/restaurant-detail-api.md
  - ../08-planning/second-expansion-test-matrix.md
  - ../08-planning/expansion-2-implementation-plan.md
  - ../08-planning/expansion-2-task-breakdown.md
  - ../04-product/prd/admin/admin-data-management.md
  - api/admin/authentication-api.md
  - api/admin/reference-data-api.md
  - api/admin/visit-registration-api.md
  - ../04-product/prd/00-product-overview.md
  - ../02-analysis/first-expansion-workstreams.md
  - ../04-product/prd/account/member-authentication.md
  - api/account/member-authentication-api.md
  - ../04-product/prd/personal/personal-restaurant-management.md
  - api/personal/personal-restaurant-api.md
  - ../04-product/prd/discovery/map-discovery.md
  - api/discovery/map-discovery-api.md
  - ../04-product/prd/detail/creator-detail.md
  - api/detail/creator-detail-api.md
  - ../02-analysis/second-expansion-workstreams.md
  - ../04-product/prd/personal/personal-collection.md
  - api/personal/personal-collection-api.md
  - ../04-product/prd/discovery/popular-restaurants.md
  - api/discovery/popular-restaurant-api.md
  - ../04-product/prd/curation/admin-curation.md
  - api/curation/curation-api.md
  - ../04-product/prd/participation/user-submission-report.md
  - api/participation/submission-report-api.md
  - ../04-product/prd/notification/user-notification.md
  - api/notification/notification-api.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - api/admin/ai-video-extraction-api.md
  - api/discovery/natural-language-restaurant-discovery-api.md
  - api/discovery/restaurant-course-recommendation-api.md
  - data/third-expansion-ai-video-data-contract.md
  - ../02-analysis/third-expansion-workstreams.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../07-adr/integration/ext-003-ai-extraction-async-reliability.md
  - ../08-planning/third-expansion-evaluation-strategy.md
  - ../08-planning/third-expansion-test-matrix.md
  - ../08-planning/third-expansion-task-breakdown.md
---

# 맛잇온 API 추적성

## 1. 문서 목적

MVP와 확장 단계의 PRD, 기능 요구사항, 비즈니스 규칙, NFR, Workstream과 담당자를 외부 API 계약에 연결한다. API로 노출되는 모든 기능 요구사항은 하나의 주 API 또는 명시적인 내부 생성 계약을 가진다.

## 2. PRD → API 매핑

| PRD ID | 기능 PRD | 주 API 문서 | 관련 API ID | Workstream | 담당자 |
|---|---|---|---|---|---|
| [PRD-DISCOVERY-001](../04-product/prd/discovery/restaurant-discovery.md) | 맛집 탐색 | [api/discovery/restaurant-discovery-api.md](api/discovery/restaurant-discovery-api.md) | [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | 양성훈 |
| [PRD-DISCOVERY-002](../04-product/prd/discovery/creator-discovery.md) | 유튜버 기반 탐색 | [api/discovery/creator-discovery-api.md](api/discovery/creator-discovery-api.md) | [API-CREATOR-DISCOVERY-001](api/discovery/creator-discovery-api.md#api-creator-discovery-001-유튜버-필터-선택-목록), [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) | [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | 이우람 |
| [PRD-DETAIL-001](../04-product/prd/detail/restaurant-detail.md) | 맛집 상세 및 콘텐츠 조회 | [api/detail/restaurant-detail-api.md](api/detail/restaurant-detail-api.md) | [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | 박진영 |
| [PRD-ADMIN-001](../04-product/prd/admin/admin-data-management.md) | 관리자 데이터 등록 | [api/admin/authentication-api.md](api/admin/authentication-api.md), [api/admin/reference-data-api.md](api/admin/reference-data-api.md), [api/admin/visit-registration-api.md](api/admin/visit-registration-api.md) | [API-ADMIN-AUTH-001](api/admin/authentication-api.md#api-admin-auth-001-관리자-로그인)~[API-ADMIN-AUTH-003](api/admin/authentication-api.md#api-admin-auth-003-관리자-로그아웃), 기본 데이터 미리보기·생성 API, [API-ADMIN-VISIT-001](api/admin/visit-registration-api.md#api-admin-visit-001-방문-관계-등록) | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 김인안 |

[PRD-PRODUCT-001](../04-product/prd/00-product-overview.md)은 전체 제품 범위를 제공하며 하나의 주 API에만 매핑하지 않는다.

### 2.1 1차 확장 PRD → API 매핑

| PRD ID | 기능 PRD | 주 API 문서 | 관련 API ID | Workstream | 담당자 |
|---|---|---|---|---|---|
| [PRD-ACCOUNT-001](../04-product/prd/account/member-authentication.md) | 사용자 계정·인증 | [api/account/member-authentication-api.md](api/account/member-authentication-api.md) | [API-MEMBER-AUTH-001](api/account/member-authentication-api.md#api-member-auth-001-회원가입)~[API-MEMBER-AUTH-010](api/account/member-authentication-api.md#api-member-auth-010-회원-탈퇴) | [WS-05](../02-analysis/first-expansion-workstreams.md#4-ws-05-사용자-계정인증) | 김인안 |
| [PRD-PERSONAL-001](../04-product/prd/personal/personal-restaurant-management.md) | 개인 맛집 관리 | [api/personal/personal-restaurant-api.md](api/personal/personal-restaurant-api.md) | [API-PERSONAL-001](api/personal/personal-restaurant-api.md#api-personal-001-맛집-찜-추가)~[API-PERSONAL-006](api/personal/personal-restaurant-api.md#api-personal-006-최근-본-맛집-개별-삭제) | [WS-06](../02-analysis/first-expansion-workstreams.md#5-ws-06-개인-맛집-관리) | 박진영 |
| [PRD-DISCOVERY-003](../04-product/prd/discovery/map-discovery.md) | 지도 탐색 | [api/discovery/map-discovery-api.md](api/discovery/map-discovery-api.md) | [API-MAP-001](api/discovery/map-discovery-api.md#2-api-map-001-지도-맛집-마커-조회) | [WS-07](../02-analysis/first-expansion-workstreams.md#6-ws-07-지도-탐색) | 양성훈 |
| [PRD-DETAIL-002](../04-product/prd/detail/creator-detail.md) | 유튜버 상세 | [api/detail/creator-detail-api.md](api/detail/creator-detail-api.md) | [API-CREATOR-DETAIL-001](api/detail/creator-detail-api.md#api-creator-detail-001-유튜버-기본-상세-조회)~[API-CREATOR-DETAIL-003](api/detail/creator-detail-api.md#api-creator-detail-003-유튜버-근거-영상-조회) | [WS-08](../02-analysis/first-expansion-workstreams.md#7-ws-08-유튜버-상세) | 이우람 |

### 2.2 2차 확장 PRD → API 매핑

| PRD ID | 기능 PRD | 주 API 문서 | 관련 API ID | Workstream | 담당자 |
|---|---|---|---|---|---|
| [PRD-COLLECTION-001](../04-product/prd/personal/personal-collection.md) | 개인 컬렉션 | [개인 컬렉션 API](api/personal/personal-collection-api.md) | API-COLLECTION-001~008 | [WS-09](../02-analysis/second-expansion-workstreams.md#4-ws-09-개인-컬렉션) | 박진영 |
| [PRD-DISCOVERY-004](../04-product/prd/discovery/popular-restaurants.md) | 인기 맛집 | [인기 맛집 API](api/discovery/popular-restaurant-api.md) | API-POPULAR-001 | [WS-10](../02-analysis/second-expansion-workstreams.md#5-ws-10-인기-맛집) | 양성훈 |
| [PRD-CURATION-001](../04-product/prd/curation/admin-curation.md) | 관리자 큐레이션 | [큐레이션 API](api/curation/curation-api.md) | API-CURATION-001~009 | [WS-11](../02-analysis/second-expansion-workstreams.md#6-ws-11-관리자-큐레이션) | 김인안 |
| [PRD-PARTICIPATION-001](../04-product/prd/participation/user-submission-report.md) | 사용자 제보·신고 | [사용자 제보·신고 API](api/participation/submission-report-api.md) | API-SUBMISSION-001~003, API-REPORT-001~003, 관리자 검토 API | [WS-12](../02-analysis/second-expansion-workstreams.md#7-ws-12-제보신고-검토) | 김인안 |
| [PRD-NOTIFICATION-001](../04-product/prd/notification/user-notification.md) | 사용자 알림 | [사용자 알림 API](api/notification/notification-api.md) | API-NOTIFICATION-001~004 | [WS-13](../02-analysis/second-expansion-workstreams.md#8-ws-13-사용자-알림) | 이우람 |

### 2.3 3차 확장 PRD → API 매핑

| PRD ID | 기능 PRD | 주 API 문서 | 관련 API ID | Workstream | 담당자 |
|---|---|---|---|---|---|
| [PRD-DISCOVERY-005](../04-product/prd/discovery/natural-language-restaurant-discovery.md) | 자연어 맛집 탐색 | [자연어 맛집 탐색 API](api/discovery/natural-language-restaurant-discovery-api.md) | API-DISCOVERY-NL-001 | [WS-14](../02-analysis/third-expansion-workstreams.md#5-ws-14-자연어-맛집-탐색) | 양성훈 |
| [PRD-ADMIN-002](../04-product/prd/admin/ai-video-information-extraction.md) | AI 영상 정보 추출 | [관리자 AI 영상 추출 API](api/admin/ai-video-extraction-api.md) | API-ADMIN-AIEXTRACT-001, API-ADMIN-AIEXTRACT-WEBHOOK-001~002 | [WS-15](../02-analysis/third-expansion-workstreams.md#6-ws-15-ai-영상-정보-추출) | 김인안 |
| [PRD-DISCOVERY-006](../04-product/prd/discovery/restaurant-course-recommendation.md) | 맛집 코스 추천 | [맛집 코스 추천 API](api/discovery/restaurant-course-recommendation-api.md) | API-DISCOVERY-COURSE-001 | [WS-16](../02-analysis/third-expansion-workstreams.md#7-ws-16-맛집-코스-추천) | 이우람 |

3차 확장 세 기능의 API 계약은 Accepted 상태로 연결됐다. 실제 구현 전에는 각 기능의 계약 테스트·외부 계정 연결·평가·운영 게이트 증거를 확보한다.

## 3. 기능 요구사항 → API 매핑

| 요구사항 ID | 기능 | 주 API | 보조 API | 검증 방식 | 담당자 |
|---|---|---|---|---|---|
| [FR-RESTAURANT-001](../01-requirements/functional-requirements.md#fr-restaurant-001-맛집-목록-조회) | 맛집 목록 조회 | [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) | 없음 | 공개·영상 없음·빈 목록 계약 테스트 | 양성훈 |
| [FR-RESTAURANT-002](../01-requirements/functional-requirements.md#fr-restaurant-002-맛집-이름-검색) | 맛집 이름 검색 | [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) | 없음 | 부분 일치·공백·영문 대소문자 테스트 | 양성훈 |
| [FR-RESTAURANT-003](../01-requirements/functional-requirements.md#fr-restaurant-003-지역별-필터) | 지역별 필터 | [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) | 없음 | 서울 자치구 허용·거부 테스트 | 양성훈 |
| [FR-RESTAURANT-004](../01-requirements/functional-requirements.md#fr-restaurant-004-음식-카테고리별-필터) | 음식 카테고리 필터 | [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) | 없음 | 10개 허용값·복수 거부 테스트 | 양성훈 |
| [FR-CREATOR-001](../01-requirements/functional-requirements.md#fr-creator-001-유튜버-기준-방문-맛집-조회) | 유튜버 기준 방문 맛집 | [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) | [API-CREATOR-DISCOVERY-001](api/discovery/creator-discovery-api.md#api-creator-discovery-001-유튜버-필터-선택-목록) | 유효 관계·공개 상태·고유 결과 통합 테스트 | 이우람 |
| [FR-CREATOR-003](../01-requirements/functional-requirements.md#fr-creator-003-유튜버-필터-선택-목록-조회) | 유튜버 필터 선택 목록 | [API-CREATOR-DISCOVERY-001](api/discovery/creator-discovery-api.md#api-creator-discovery-001-유튜버-필터-선택-목록) | 없음 | 최소 필드·채널명 정렬·빈 목록 테스트 | 이우람 |
| [FR-RESTAURANT-005](../01-requirements/functional-requirements.md#fr-restaurant-005-검색-및-필터-조건-조합) | 검색·필터 조합 | [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) | 없음 | 모든 허용 조건 AND 조합 테스트 | 양성훈 |
| [FR-RESTAURANT-006](../01-requirements/functional-requirements.md#fr-restaurant-006-페이지-단위-조회) | 페이지 단위 조회 | [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) | 없음 | 허용 크기·범위 밖·메타데이터 테스트 | 양성훈 |
| [FR-RESTAURANT-007](../01-requirements/functional-requirements.md#fr-restaurant-007-기본-정렬-적용) | 기본 정렬 | [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) | 없음 | 이름·주소 순서와 페이지 안정성 테스트 | 양성훈 |
| [FR-RESTAURANT-008](../01-requirements/functional-requirements.md#fr-restaurant-008-맛집-기본-정보-조회) | 맛집 기본 정보 | [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 없음 | 공개·404·외부 링크 장애 테스트 | 박진영 |
| [FR-RESTAURANT-009](../01-requirements/functional-requirements.md#fr-restaurant-009-지역-정보-확인) | 지역 정보 | [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 없음 | 전체 도로명주소·선택 상세 위치 테스트 | 박진영 |
| [FR-RESTAURANT-010](../01-requirements/functional-requirements.md#fr-restaurant-010-음식-카테고리-확인) | 음식 카테고리 확인 | [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 없음 | 대표 카테고리 정확히 1개 테스트 | 박진영 |
| [FR-RESTAURANT-011](../01-requirements/functional-requirements.md#fr-restaurant-011-영상-연결이-없는-맛집-상세-조회) | 영상 없는 맛집 상세 | [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 없음 | 기본 정보와 빈 콘텐츠 목록 테스트 | 박진영 |
| [FR-CREATOR-002](../01-requirements/functional-requirements.md#fr-creator-002-방문-유튜버-정보-확인) | 방문 유튜버 정보 | [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) | 중복 제거·공개 관계·부분 실패 테스트 | 박진영 |
| [FR-VIDEO-001](../01-requirements/functional-requirements.md#fr-video-001-관련-영상-정보-확인) | 관련 영상 정보 | [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 없음 | 필드·중복·외부 링크 격리 테스트 | 박진영 |
| [FR-ADMIN-001](../01-requirements/functional-requirements.md#fr-admin-001-관리자-등록-기능-접근) | 관리자 등록 접근 | [API-ADMIN-AUTH-001](api/admin/authentication-api.md#api-admin-auth-001-관리자-로그인) | [API-ADMIN-AUTH-002](api/admin/authentication-api.md#api-admin-auth-002-관리자-토큰-재발급)·[API-ADMIN-AUTH-003](api/admin/authentication-api.md#api-admin-auth-003-관리자-로그아웃), 나머지 모든 `/api/admin` API | 로그인·JWT 검증·Refresh 회전·권한 테스트 | 김인안 |
| [FR-ADMIN-002](../01-requirements/functional-requirements.md#fr-admin-002-맛집-정보-등록) | 맛집 등록 | [API-ADMIN-RESTAURANT-001](api/admin/reference-data-api.md#api-admin-restaurant-001-맛집-등록-확정) | [API-ADMIN-RESTAURANT-PLACE-SEARCH-001](api/admin/reference-data-api.md#api-admin-restaurant-place-search-001-맛집-장소-검색), [API-ADMIN-RESTAURANT-PREVIEW-001](api/admin/reference-data-api.md#api-admin-restaurant-preview-001-맛집-등록-검증-미리보기), [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 장소 후보 검색·외부 확인·관리자 확정·중복·서울·조회 반영 테스트 | 김인안 |
| [FR-ADMIN-003](../01-requirements/functional-requirements.md#fr-admin-003-유튜버-정보-등록) | 유튜버 등록 | [API-ADMIN-CREATOR-001](api/admin/reference-data-api.md#api-admin-creator-001-유튜버-등록-확정) | [API-ADMIN-CREATOR-PREVIEW-001](api/admin/reference-data-api.md#api-admin-creator-preview-001-유튜버-등록-검증-미리보기), [API-CREATOR-DISCOVERY-001](api/discovery/creator-discovery-api.md#api-creator-discovery-001-유튜버-필터-선택-목록) | 외부 확인·관리자 확정·동일 채널·조회 반영 테스트 | 김인안 |
| [FR-ADMIN-004](../01-requirements/functional-requirements.md#fr-admin-004-영상-정보-등록) | 영상 등록 | [API-ADMIN-VIDEO-001](api/admin/reference-data-api.md#api-admin-video-001-영상-등록-확정) | [API-ADMIN-VIDEO-PREVIEW-001](api/admin/reference-data-api.md#api-admin-video-preview-001-영상-등록-검증-미리보기), [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 외부 확인·관리자 확정·동일 영상·원본 미저장 테스트 | 김인안 |
| [FR-VISIT-001](../01-requirements/functional-requirements.md#fr-visit-001-맛집유튜버영상-방문-관계-등록) | 방문 관계 등록 | [API-ADMIN-VISIT-001](api/admin/visit-registration-api.md#api-admin-visit-001-방문-관계-등록) | [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 참조·채널 일치·근거·중복·원자성 통합 테스트 | 김인안 |

API 직접 노출 없음: [BR-ADMIN-006](../01-requirements/business-rules.md#br-admin-006-잘못-등록된-데이터의-정정-원칙)의 정정 구현, 공개 상태 저장 구조, 동시성 보장 방식과 외부 동일성 식별값 저장 방식은 외부 API 기능이 아니라 후속 내부 설계다. 다만 그 결과는 공개 제외·중복 오류 계약으로 관찰된다.

### 3.1 1차 확장 요구사항 → API 매핑

| 요구사항 ID | 기능 | 주 API | 보조 API | 검증 방식 | 담당자 |
|---|---|---|---|---|---|
| [FR-MEMBER-001](../01-requirements/functional-requirements.md#fr-member-001-이메일-회원가입) | 이메일 회원가입 | [API-MEMBER-AUTH-001](api/account/member-authentication-api.md#api-member-auth-001-회원가입) | 없음 | 동시 가입 수렴, 계정 상태 비노출 테스트 | 김인안 |
| [FR-MEMBER-002](../01-requirements/functional-requirements.md#fr-member-002-가입-이메일-인증) | 가입 이메일 인증 | [API-MEMBER-AUTH-002](api/account/member-authentication-api.md#api-member-auth-002-가입-이메일-인증) | [API-MEMBER-AUTH-003](api/account/member-authentication-api.md#api-member-auth-003-인증-메일-재발송) | 8자 코드 생성·정규화·제출 제한·단일 소비·만료 테스트 | 김인안 |
| [FR-MEMBER-003](../01-requirements/functional-requirements.md#fr-member-003-비밀번호-재설정) | 비밀번호 재설정 | [API-MEMBER-AUTH-004](api/account/member-authentication-api.md#api-member-auth-004-비밀번호-재설정-요청) | [API-MEMBER-AUTH-005](api/account/member-authentication-api.md#api-member-auth-005-비밀번호-재설정-완료) | 계정 열거 방지·재설정 직렬화 테스트 | 김인안 |
| [FR-MEMBER-004](../01-requirements/functional-requirements.md#fr-member-004-회원-탈퇴) | 회원 탈퇴 | [API-MEMBER-AUTH-010](api/account/member-authentication-api.md#api-member-auth-010-회원-탈퇴) | [API-PERSONAL-001](api/personal/personal-restaurant-api.md#api-personal-001-맛집-찜-추가)~[API-PERSONAL-006](api/personal/personal-restaurant-api.md#api-personal-006-최근-본-맛집-개별-삭제) | 탈퇴 확인·취소·개인화 데이터 정리 테스트 | 김인안 |
| [FR-MEMBER-005](../01-requirements/functional-requirements.md#fr-member-005-현재-사용자-정보-조회) | 현재 사용자 정보 조회 | [API-MEMBER-AUTH-009](api/account/member-authentication-api.md#api-member-auth-009-현재-사용자-정보) | 없음 | 캐시 금지·401 테스트 | 김인안 |
| [FR-AUTH-001](../01-requirements/functional-requirements.md#fr-auth-001-로그인과-활성-세션-발급) | 로그인과 활성 세션 발급 | [API-MEMBER-AUTH-006](api/account/member-authentication-api.md#api-member-auth-006-로그인) | 없음 | 최대 3세션·Refresh Cookie 발급 테스트 | 김인안 |
| [FR-AUTH-002](../01-requirements/functional-requirements.md#fr-auth-002-access-token-재발급) | Access Token 재발급 | [API-MEMBER-AUTH-007](api/account/member-authentication-api.md#api-member-auth-007-access-token-재발급) | 없음 | 회전·재사용 탐지 테스트 | 김인안 |
| [FR-AUTH-003](../01-requirements/functional-requirements.md#fr-auth-003-로그아웃과-다중-로그인-세션-관리) | 로그아웃과 다중 로그인 세션 관리 | [API-MEMBER-AUTH-008](api/account/member-authentication-api.md#api-member-auth-008-로그아웃) | 없음 | `sid` 즉시 폐기·Redis 장애 fail-closed 테스트 | 김인안 |
| [FR-FAVORITE-001](../01-requirements/functional-requirements.md#fr-favorite-001-맛집-찜-추가) | 맛집 찜 추가 | [API-PERSONAL-001](api/personal/personal-restaurant-api.md#api-personal-001-맛집-찜-추가) | 없음 | 중복 찜 동시성 테스트 | 박진영 |
| [FR-FAVORITE-002](../01-requirements/functional-requirements.md#fr-favorite-002-맛집-찜-해제) | 맛집 찜 해제 | [API-PERSONAL-002](api/personal/personal-restaurant-api.md#api-personal-002-맛집-찜-해제) | 없음 | 다른 회원 접근 거부 테스트 | 박진영 |
| [FR-FAVORITE-003](../01-requirements/functional-requirements.md#fr-favorite-003-맛집별-현재-회원-찜-상태-확인) | 맛집별 현재 회원 찜 상태 확인 | [API-PERSONAL-003](api/personal/personal-restaurant-api.md#api-personal-003-맛집별-현재-회원-찜-상태-조회) | 없음 | 비공개 맛집 숨김 테스트 | 박진영 |
| [FR-FAVORITE-004](../01-requirements/functional-requirements.md#fr-favorite-004-찜-목록-조회) | 찜 목록 조회 | [API-PERSONAL-004](api/personal/personal-restaurant-api.md#api-personal-004-찜-목록-조회) | 없음 | 최신순 페이지·빈 목록 테스트 | 박진영 |
| [FR-RECENT-001](../01-requirements/functional-requirements.md#fr-recent-001-최근-본-맛집-기록) | 최근 본 맛집 기록 | [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 없음 | `GREATEST` upsert·50건 상한 테스트 | 박진영 |
| [FR-RECENT-002](../01-requirements/functional-requirements.md#fr-recent-002-최근-본-맛집-목록-조회) | 최근 본 맛집 목록 조회 | [API-PERSONAL-005](api/personal/personal-restaurant-api.md#api-personal-005-최근-본-맛집-목록-조회) | 없음 | 최신순 페이지·빈 목록 테스트 | 박진영 |
| [FR-RECENT-003](../01-requirements/functional-requirements.md#fr-recent-003-최근-본-맛집-개별-삭제) | 최근 본 맛집 개별 삭제 | [API-PERSONAL-006](api/personal/personal-restaurant-api.md#api-personal-006-최근-본-맛집-개별-삭제) | 없음 | 다른 회원 접근 거부 테스트 | 박진영 |
| [FR-MAP-001](../01-requirements/functional-requirements.md#fr-map-001-kakao-지도와-맛집-마커-표시) | Kakao 지도와 맛집 마커 표시 | [API-MAP-001](api/discovery/map-discovery-api.md#2-api-map-001-지도-맛집-마커-조회) | 없음 | 좌표 CHECK·공개 상태·200개 상한 테스트 | 양성훈 |
| [FR-MAP-002](../01-requirements/functional-requirements.md#fr-map-002-지도-영역과-탐색-조건-조합-조회) | 지도 이동과 탐색 결과 유지 | [API-MAP-001](api/discovery/map-discovery-api.md#2-api-map-001-지도-맛집-마커-조회) | [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) | 필터 AND·이동 시 요청 0건·결과 유지·429 테스트 | 양성훈 |
| [FR-CREATOR-004](../01-requirements/functional-requirements.md#fr-creator-004-유튜버-상세-정보-조회) | 유튜버 상세 정보 조회 | [API-CREATOR-DETAIL-001](api/detail/creator-detail-api.md#api-creator-detail-001-유튜버-기본-상세-조회) | 없음 | 공개·404·null 표시값 테스트 | 이우람 |
| [FR-CREATOR-005](../01-requirements/functional-requirements.md#fr-creator-005-유튜버의-방문-맛집-목록-조회) | 유튜버의 방문 맛집 목록 조회 | [API-CREATOR-DETAIL-002](api/detail/creator-detail-api.md#api-creator-detail-002-유튜버-방문-맛집-조회) | 없음 | 중복 제거·페이지·빈 목록 테스트 | 이우람 |
| [FR-CREATOR-006](../01-requirements/functional-requirements.md#fr-creator-006-유튜버의-근거-영상-목록-조회) | 유튜버의 근거 영상 목록 조회 | [API-CREATOR-DETAIL-003](api/detail/creator-detail-api.md#api-creator-detail-003-유튜버-근거-영상-조회) | 없음 | 중복 제거·페이지·외부 API 미호출 테스트 | 이우람 |

### 3.2 2차 확장 요구사항 → API 매핑

| 요구사항 ID | 주 API | 검증 방식 | 담당자 |
|---|---|---|---|
| FR-COLLECTION-001~003 | API-COLLECTION-001, 004~005 | 멱등 생성·20개 상한·이름 변경·연쇄 삭제 계약 테스트 | 박진영 |
| FR-COLLECTION-004 | API-COLLECTION-002~003 | 소유권 은닉·고정 정렬·비공개 맛집 숨김 테스트 | 박진영 |
| FR-COLLECTION-005~006 | API-COLLECTION-006~008 | 공개 상태·추가 옵션·중복·100개 상한·실패 후 재조회·반복 제거 테스트 | 박진영 |
| FR-POPULAR-001 | API-POPULAR-001 | 현재 찜 수·최소 1·상위 20·동점 안정 정렬 테스트 | 양성훈 |
| FR-CURATION-001~003 | API-CURATION-003~009 | 관리자 권한·20/5개 상한·완전 교체·게시 전이·감사 테스트 | 김인안 |
| FR-CURATION-004 | API-CURATION-001~002 | 비게시 404·구성 순서·비공개 항목 숨김 테스트 | 김인안 |
| FR-SUBMISSION-001~003 | API-SUBMISSION-001~003, API-ADMIN-SUBMISSION-001~003 | 유형별 입력·중복·합산 제한·상태 전이·실제 조치 분리 테스트 | 김인안 |
| FR-REPORT-001~003 | API-REPORT-001~003, API-ADMIN-REPORT-001~003 | 대상 존재·신고 유형·자동 비공개 없음·상태 전이 테스트 | 김인안 |
| FR-NOTIFICATION-001 | 외부 생성 API 없음, 관리자 상태 전이 API | 상태-알림 원자성·요청/상태 고유성 실패 주입 테스트 | 이우람 |
| FR-NOTIFICATION-002~004 | API-NOTIFICATION-001~004 | 소유권·정확한 미읽음 수·개별/전체 멱등 읽음 테스트 | 이우람 |

컬렉션 직접 순서 변경과 알림 설정 변경은 승인 범위에서 제외돼 API에 매핑하지 않는다.

### 3.3 3차 확장 요구사항 → API 매핑

| 요구사항 ID | 주 API | 보조 API | 검증 방식 | 담당자 |
|---|---|---|---|---|
| FR-AIEXTRACT-001·FR-AIEXTRACT-005 | API-ADMIN-AIEXTRACT-001 | API-ADMIN-AIEXTRACT-WEBHOOK-001~002 | URL 검증·관리자 신규 추가·멱등 접수·202 응답 계약 테스트 | 김인안 |
| FR-AIEXTRACT-002 | API-ADMIN-AIEXTRACT-001 | 없음 | 목록·상세·부분 결과·실패·페이지 계약 테스트 | 김인안 |
| FR-AIEXTRACT-003 | API-ADMIN-AIEXTRACT-001 | 기존 관리자 등록·방문 API | 자동 확정·자동 차단·사후 보정·롤백·정식 Entity 원자성 테스트 | 김인안 |
| FR-AIEXTRACT-004 | API-ADMIN-AIEXTRACT-WEBHOOK-001~002 | API-ADMIN-AIEXTRACT-001 | 구독 확인·신규 영상 Atom·중복 알림·AI 호출 격리 테스트 | 김인안 |
| FR-AIEXTRACT-006 | API-ADMIN-AIEXTRACT-001 | API-ADMIN-AIEXTRACT-WEBHOOK-001~002 | 채널 활성화·해지·renewal 실패 상태 테스트 | 김인안 |
| FR-NLSEARCH-001 | API-DISCOVERY-NL-001 | API-DISCOVERY-001 | 자연어 해석·적용 조건·기존 목록 응답 계약 테스트 | 양성훈 |
| FR-NLSEARCH-002 | API-DISCOVERY-NL-001 | API-DISCOVERY-001 | 직접 필터 우선·AND 조합·충돌 요약 테스트 | 양성훈 |
| FR-NLSEARCH-003 | API-DISCOVERY-NL-001 | 없음 | 빈 결과·`PARTIAL`·`FAILED`·전체 목록 대체 금지 테스트 | 양성훈 |
| FR-NLSEARCH-004 | API-DISCOVERY-NL-001 | API-DISCOVERY-001 | 확정 태그 코드·Visit 공개 상태·여러 태그 AND 계약 테스트 | 양성훈 |
| FR-AIEXTRACT-007 | API-ADMIN-AIEXTRACT-001 | API-DISCOVERY-NL-001 | 태그 후보 자동 판단·사후 보정·`VisitTag` 연결·검증 전 검색 제외 테스트 | 김인안 |
| FR-COURSE-001 | API-DISCOVERY-COURSE-001 | API-DISCOVERY-001 | 2~5개·중복·공개·좌표·출발점 검증 테스트 | 이우람 |
| FR-COURSE-002 | API-DISCOVERY-COURSE-001 | 없음 | 자동차 순서·구간 거리/시간·30km·만료 테스트 | 이우람 |
| FR-COURSE-003 | API-DISCOVERY-COURSE-001 | 없음 | timeout·429·5xx·부분 실패·추정 금지 테스트 | 이우람 |

### 3.4 2차 확장 API → 데이터·ADR·테스트·Task 검증

| API 계약군 | 데이터 | ADR 또는 명시적 보류 | Workstream | 테스트 | E2 Task |
|---|---|---|---|---|---|
| `API-COLLECTION-001~008` | `personal_collection`, `collection_restaurant`, `idempotency_record` | 기존 인증·PostgreSQL·Flyway ADR; 공유·직접 정렬 제외 | WS-09 | [`TST-E2-COL-001`](../08-planning/second-expansion-test-matrix.md), `TST-E2-SEC-001` | [`E2-T01`](../08-planning/expansion-2-task-breakdown.md)~`E2-T03`, `E2-T13`, `E2-T15` |
| `API-POPULAR-001` | 기존 `favorite`, 결과 비저장 | [ADR-DATA-011](../07-adr/data/data-011-popular-restaurant-request-time-aggregation.md); Snapshot·Batch·Redis 비활성 | WS-10 | [`TST-E2-POP-001`](../08-planning/second-expansion-test-matrix.md), `TST-E2-PERF-001` | [`E2-T01`](../08-planning/expansion-2-task-breakdown.md), `E2-T02`, `E2-T04`, `E2-T05`, `E2-T13`, `E2-T15` |
| `API-CURATION-001~009` | `curation`, `curation_restaurant` | 기존 관리자 인증·PostgreSQL ADR; 예약·추천·이미지 제외 | WS-11 | [`TST-E2-CUR-001`](../08-planning/second-expansion-test-matrix.md), `TST-E2-PERF-001`, `TST-E2-SEC-001` | [`E2-T01`](../08-planning/expansion-2-task-breakdown.md), `E2-T02`, `E2-T06`, `E2-T07`, `E2-T13`, `E2-T15` |
| `API-SUBMISSION-*`, `API-ADMIN-SUBMISSION-*` | `submission`, `moderation_history`, `idempotency_record` | [ADR-DATA-012](../07-adr/data/data-012-second-expansion-retention-cleanup.md), [ADR-NOTIFY-002](../07-adr/integration/notify-002-in-app-notification-reliability.md) | WS-12 | [`TST-E2-SUB-001`](../08-planning/second-expansion-test-matrix.md), `TST-E2-ATOMIC-001`, `TST-E2-LIFE-001` | [`E2-T01`](../08-planning/expansion-2-task-breakdown.md), `E2-T02`, `E2-T08`, `E2-T09`, `E2-T11`, `E2-T13`, `E2-T15` |
| `API-REPORT-*`, `API-ADMIN-REPORT-*` | `report`, `moderation_history`, `idempotency_record` | [ADR-DATA-012](../07-adr/data/data-012-second-expansion-retention-cleanup.md), [ADR-NOTIFY-002](../07-adr/integration/notify-002-in-app-notification-reliability.md) | WS-12 | [`TST-E2-REP-001`](../08-planning/second-expansion-test-matrix.md), `TST-E2-ATOMIC-001`, `TST-E2-LIFE-001` | [`E2-T01`](../08-planning/expansion-2-task-breakdown.md), `E2-T02`, `E2-T08`, `E2-T09`, `E2-T11`, `E2-T13`, `E2-T15` |
| 상태 전이 내부 생성, `API-NOTIFICATION-001~004` | `notification` | [ADR-NOTIFY-002](../07-adr/integration/notify-002-in-app-notification-reliability.md), [ADR-DATA-012](../07-adr/data/data-012-second-expansion-retention-cleanup.md); FCM Post-MVP | WS-12·WS-13 | [`TST-E2-ATOMIC-001`](../08-planning/second-expansion-test-matrix.md), `TST-E2-NOT-001`, `TST-E2-LIFE-001` | [`E2-T01`](../08-planning/expansion-2-task-breakdown.md), `E2-T02`, `E2-T10`, `E2-T11`, `E2-T13`, `E2-T14`, `E2-T15` |

전체 계약군은 `TST-E2-SEC-001`, `TST-E2-E2E-001`과 `E2-T13`, `E2-T14`, `E2-T15`의 교차 회귀 대상이다. 외부 푸시 API와 `E2-T12`는 현재 없다.

## 4. 비즈니스 규칙 → API 매핑

| 규칙 ID | 규칙 | 적용 API | 요청 검증 | 응답 영향 | 담당자 |
|---|---|---|---|---|---|
| [BR-RESTAURANT-002](../01-requirements/business-rules.md#br-restaurant-002-영상과-독립된-맛집)·[BR-RESTAURANT-008](../01-requirements/business-rules.md#br-restaurant-008-맛집-공개-조건) | 영상 독립성과 공개 조건 | [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 없음 | 영상 없어도 노출, 비공개 제외 | 양성훈·박진영 |
| [BR-RESTAURANT-003](../01-requirements/business-rules.md#br-restaurant-003-맛집-최소-등록-정보)~[BR-RESTAURANT-007](../01-requirements/business-rules.md#br-restaurant-007-동일-상호의-지점-구분) | 최소 정보·카테고리·지역·중복·지점 | [API-ADMIN-RESTAURANT-PLACE-SEARCH-001](api/admin/reference-data-api.md#api-admin-restaurant-place-search-001-맛집-장소-검색), [API-ADMIN-RESTAURANT-PREVIEW-001](api/admin/reference-data-api.md#api-admin-restaurant-preview-001-맛집-등록-검증-미리보기), [API-ADMIN-RESTAURANT-001](api/admin/reference-data-api.md#api-admin-restaurant-001-맛집-등록-확정) | 장소 후보 필수값, 서울 주소, 단일 카테고리, 카카오 동일성 | 검색 빈 결과 200·외부 실패 502, 미리보기 판정, 최초 201·완료 재시도 200·중복 409 또는 입력 400 | 김인안 |
| [BR-CREATOR-001](../01-requirements/business-rules.md#br-creator-001-유튜버-정보의-의미)~[BR-CREATOR-005](../01-requirements/business-rules.md#br-creator-005-방문-관계의-유튜버-일치) | 채널 관리 단위·최소 정보·중복·표시·일치 | [API-ADMIN-CREATOR-001](api/admin/reference-data-api.md#api-admin-creator-001-유튜버-등록-확정), [API-CREATOR-DISCOVERY-001](api/discovery/creator-discovery-api.md#api-creator-discovery-001-유튜버-필터-선택-목록), [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회), [API-ADMIN-VISIT-001](api/admin/visit-registration-api.md#api-admin-visit-001-방문-관계-등록) | 채널 URL·동일성·게시 채널 일치 | 현재 채널명, 중복 제거, 409·422 | 이우람·김인안·박진영 |
| [BR-CREATOR-007](../01-requirements/business-rules.md#br-creator-007-채널-이용-불가-처리) | 이용 불가 채널 제외 | [API-CREATOR-DISCOVERY-001](api/discovery/creator-discovery-api.md#api-creator-discovery-001-유튜버-필터-선택-목록), [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 없음 | 유튜버·관계 제외, 맛집 기본 유지 | 이우람·박진영 |
| [BR-VIDEO-001](../01-requirements/business-rules.md#br-video-001-영상의-의미와-보관-범위)~[BR-VIDEO-006](../01-requirements/business-rules.md#br-video-006-게시일과-방문일의-구분) | 영상 최소 정보·동일성·관계·실제 방문·날짜 구분 | [API-ADMIN-VIDEO-001](api/admin/reference-data-api.md#api-admin-video-001-영상-등록-확정), [API-ADMIN-VISIT-001](api/admin/visit-registration-api.md#api-admin-visit-001-방문-관계-등록), [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 원본 URL, 중복, 실제 방문, 게시 채널 | 영상 필드, 409·422, 방문일 미노출 | 김인안·박진영 |
| [BR-VIDEO-007](../01-requirements/business-rules.md#br-video-007-외부-링크-장애의-격리)~[BR-VIDEO-009](../01-requirements/business-rules.md#br-video-009-영상-이용-불가-처리) | 링크 장애·표시 변경·이용 불가 | [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 없음 | 기본 상세 유지, 무효 영상 제외 | 박진영 |
| [BR-VISIT-001](../01-requirements/business-rules.md#br-visit-001-방문-관계의-구성)~[BR-VISIT-007](../01-requirements/business-rules.md#br-visit-007-등록-완료와-검증-상태) | 세 대상 관계·근거·중복·유효성·날짜·검증 | [API-ADMIN-VISIT-001](api/admin/visit-registration-api.md#api-admin-visit-001-방문-관계-등록), [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 세 참조·조합·근거·채널 일치 | 201·404·409·422, 공개 관계만 조회 | 김인안·이우람·박진영 |
| [BR-SEARCH-001](../01-requirements/business-rules.md#br-search-001-검색-대상과-일치-기준)~[BR-SEARCH-009](../01-requirements/business-rules.md#br-search-009-기본-정렬) | 검색·필터·고유성·빈 결과·페이지·정렬 | [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) | 쿼리 허용값과 단일 값 | AND 결과, 빈 목록, 안정 페이지 | 양성훈·이우람 |
| [BR-ADMIN-001](../01-requirements/business-rules.md#br-admin-001-관리자-권한-검증)~[BR-ADMIN-005](../01-requirements/business-rules.md#br-admin-005-mvp-관리-기능의-경계)·[BR-ADMIN-007](../01-requirements/business-rules.md#br-admin-007-동시-등록의-고유성)·[BR-ADMIN-008](../01-requirements/business-rules.md#br-admin-008-보류-요청의-처리) | 권한·검증·정합성·반영·MVP 경계·동시성·보류 | 인증 및 모든 관리자 등록 API | JWT·ADMIN 권한, 필수값, 미리보기, 확인 토큰, 중복 | 401·403·409 및 공개 조회 반영 | 김인안 |
| [BR-ADMIN-006](../01-requirements/business-rules.md#br-admin-006-잘못-등록된-데이터의-정정-원칙) | 잘못된 데이터 정정 | API 직접 노출 없음 | 수정·삭제 API 없음 | 비공개된 대상은 조회 제외 | 김인안 |
| [BR-PUBLICATION-001](../01-requirements/business-rules.md#br-publication-001-일반-사용자-공개-범위)~[BR-PUBLICATION-008](../01-requirements/business-rules.md#br-publication-008-상태-변경의-일관성) | 공개·비공개·삭제와 일관성 | 모든 공개 조회, 모든 등록 결과 | 관리자 등록 공개 정책 | 비공개·삭제 제외, 기본 맛집 유지 | 전체 Workstream |

### 4.1 2차 확장 비즈니스 규칙 → API 매핑

| 규칙 ID | 적용 API | 핵심 검증 | 담당자 |
|---|---|---|---|
| BR-COLLECTION-001~005 | API-COLLECTION-001~008 | 소유권 은닉, 20/100 상한, 추가 상태, 고정 정렬, 공개 상태, 탈퇴 정리 | 박진영 |
| BR-POPULAR-001~003 | API-POPULAR-001 | 현재 찜 한 신호, 상위 20 안정 정렬, 커밋 후 반영 | 양성훈 |
| BR-CURATION-001~004 | API-CURATION-001~009 | 관리자 경계, 5/20 상한, 수동 순서, 공개 숨김, 원자적 즉시 반영 | 김인안 |
| BR-SUBMISSION-001~004 | 제보 회원·관리자 API | 유형별 대상, 열린 중복, 합산 5건, 상태·보존 | 김인안 |
| BR-REPORT-001~004 | 신고 회원·관리자 API | 기존 대상, 신고 유형, 자동 비공개 없음, 상태·보존 | 김인안 |
| BR-NOTIFICATION-001~004 | 관리자 상태 전이, API-NOTIFICATION-001~004 | 원자 생성, 고유성, 소유권, 읽음, 보존, 채널 제외 | 이우람 |

### 4.2 3차 확장 비즈니스 규칙 → API 매핑

| 규칙 ID | 적용 API | 핵심 검증 | 담당자 |
|---|---|---|---|
| BR-AIEXTRACT-001~004·008 | API-ADMIN-AIEXTRACT-001 | 후보·태그 범위·자동 검증 전 저장 금지·통과 후 무승인 공개·동일 영상 멱등성·Provider/Prompt/Schema 버전 | WS-15 |
| BR-AIEXTRACT-005~007 | API-ADMIN-AIEXTRACT-001·API-ADMIN-AIEXTRACT-WEBHOOK-001~002 | 유입 경로 수렴·채널 상태·Gemini URL 입력과 관리자 텍스트 fallback | WS-15 |
| BR-NLSEARCH-001~003 | API-DISCOVERY-NL-001·API-DISCOVERY-001 | 직접 필터 우선·태그 AND·공개·활성 결과·전체 목록 대체 금지 | WS-14 |
| BR-NLSEARCH-003 | API-DISCOVERY-NL-001·API-DISCOVERY-001 | 활성 TagDefinition·확정 VisitTag·공개 Visit·태그 AND·중복 제거 | WS-14 |
| BR-AIEXTRACT-008 | API-ADMIN-AIEXTRACT-001 | 허용 태그 정의·근거·자동 결정·사후 보정·검증 전 공개 금지 | WS-15 |
| BR-AIEXTRACT-009~010 | API-ADMIN-AIEXTRACT-001 | 등록 단위별 판정·상호명·주소 기반 장소 자동 확정·카테고리 자동 선정·`registrationUnits` 응답과 차단 사유 코드 | WS-15 |
| BR-AIEXTRACT-011 | API-ADMIN-AIEXTRACT-001 | 등록 단위 일괄 등록 실행, 4종 원자 등록과 자원 재사용, 예외 7종의 `recoveryPaths` 매핑, `CONFIRM`·`ADJUST_CATEGORY` 허용 상태 | WS-15 |
| BR-COURSE-001~004 | API-DISCOVERY-COURSE-001 | 2~5개·첫 출발점 고정·좌표 필수·30km·만료·부분 결과 금지 | WS-16 |

## 5. NFR → API 검증 매핑

| NFR ID | 품질 요구사항 | 적용 API | 검증 방법 | 검증 책임 |
|---|---|---|---|---|
| [NFR-PERFORMANCE-001](../01-requirements/non-functional-requirements.md#nfr-performance-001-일반-조회-응답-시간)·[NFR-PERFORMANCE-002](../01-requirements/non-functional-requirements.md#nfr-performance-002-검색필터-조합-응답-시간)·[NFR-PERFORMANCE-004](../01-requirements/non-functional-requirements.md#nfr-performance-004-페이지-크기-및-조회량-제한) | 조회·조합 성능과 페이지 제한 | [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 대표 데이터 부하, 경계값·응답 크기 검사 | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)·[WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)·[WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) |
| [NFR-PERFORMANCE-003](../01-requirements/non-functional-requirements.md#nfr-performance-003-관리자-등록-응답-시간) | 관리자 등록 응답 | 모든 관리자 API | 외부 시간 분리 부하 테스트 | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |
| [NFR-SECURITY-001](../01-requirements/non-functional-requirements.md#nfr-security-001-공개-조회와-관리자-접근-통제) | 공개 조회·관리자 통제 | 모든 API, [API-ADMIN-AUTH-001](api/admin/authentication-api.md#api-admin-auth-001-관리자-로그인)~[API-ADMIN-AUTH-003](api/admin/authentication-api.md#api-admin-auth-003-관리자-로그아웃) | JWT 없음·만료·서명 오류·Refresh 재사용·권한 없음·정상 테스트 | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 및 공통 인증 담당 |
| [NFR-SECURITY-002](../01-requirements/non-functional-requirements.md#nfr-security-002-입력-및-웹-공격-방어)·[NFR-SECURITY-003](../01-requirements/non-functional-requirements.md#nfr-security-003-비밀정보와-오류-정보-보호) | 입력·비밀·오류 보호 | 모든 API | 악성 입력, 비밀·스택 노출 검사 | 각 담당자 |
| [NFR-DEPLOYMENT-004](../01-requirements/non-functional-requirements.md#nfr-deployment-004-단계별-실행-및-초기-운영-배포-복잡도-제한) | 검증 참여자 제한 공개와 정식 공개 제거 | [API-VALIDATION-001](api/common/validation-access-contract.md#api-validation-001-검증-참여자-로그인)~[API-VALIDATION-002](api/common/validation-access-contract.md#api-validation-002-검증-참여자-세션-종료), 전체 화면·API 진입 | 쿠키·Redis·Nginx 통합, 회원·관리자 Bearer 동시 사용, 반복 인증창 0회, 제거 리허설 | [OPS-VALIDATION](../02-analysis/first-expansion-workstreams.md#ops-validation-공통-운영배포-트랙) 이우람 / 김인안 |
| [NFR-INTEGRITY-001](../01-requirements/non-functional-requirements.md#nfr-integrity-001-참조-및-필수값-정합성)~[NFR-INTEGRITY-004](../01-requirements/non-functional-requirements.md#nfr-integrity-004-외부-링크와-내부-데이터-분리) | 참조·중복·원자성·외부 링크 분리 | 관리자 API, [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 통합·동시성·실패 주입 테스트 | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록), [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) |
| [NFR-RELIABILITY-001](../01-requirements/non-functional-requirements.md#nfr-reliability-001-오류-격리와-공통-오류-정책)·[NFR-RELIABILITY-003](../01-requirements/non-functional-requirements.md#nfr-reliability-003-사용자-오류-메시지와-기능-분리) | 공통 오류와 부분 실패 격리 | 모든 API, 특히 [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 오류 계약·제공자 장애 테스트 | 각 담당자, [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) |
| [NFR-EXTERNAL-001](../01-requirements/non-functional-requirements.md#nfr-external-001-영상-원본과-외부-링크-분리)~[NFR-EXTERNAL-003](../01-requirements/non-functional-requirements.md#nfr-external-003-링크-검증과-외부-인증정보) | 원본·외부 호출·링크 검증 분리 | [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회), 기본 데이터 등록 API | 외부 장애 모의·저장 자료·URL 검사 | [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)·[WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |
| [NFR-OBSERVABILITY-001](../01-requirements/non-functional-requirements.md#nfr-observability-001-요청-추적과-오류-분류)~[NFR-OBSERVABILITY-003](../01-requirements/non-functional-requirements.md#nfr-observability-003-로그-품질과-민감정보-차단) | 요청 추적·분류·민감정보 차단 | 모든 API | 로그 상관관계·표본 검사 | 공통 운영 담당·[WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |
| [NFR-COMPATIBILITY-002](../01-requirements/non-functional-requirements.md#nfr-compatibility-002-응답-형식과-문자-처리)·[NFR-COMPATIBILITY-003](../01-requirements/non-functional-requirements.md#nfr-compatibility-003-모바일-응답-크기) | UTF-8·일관 형식·모바일 크기 | 공개 조회 API | 계약·한글 왕복·최대 응답 검사 | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)·[WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) |
| [NFR-TEST-001](../01-requirements/non-functional-requirements.md#nfr-test-001-자동화-테스트-계층)~[NFR-TEST-003](../01-requirements/non-functional-requirements.md#nfr-test-003-배포-품질-게이트) | 자동화·변경·품질 게이트 | 모든 API | 요구사항 추적 계약·통합 테스트 | 전체 Workstream |
| [NFR-MAINTAINABILITY-001](../01-requirements/non-functional-requirements.md#nfr-maintainability-001-책임과-의존성-경계)·[NFR-MAINTAINABILITY-002](../01-requirements/non-functional-requirements.md#nfr-maintainability-002-공통-정책과-규칙-배치) | 책임·공통 정책 경계 | 탐색 API와 공통 계약 | 의존성·계약 중복 검사 | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)·[WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) |
| [NFR-PRIVACY-001](../01-requirements/non-functional-requirements.md#nfr-privacy-001-mvp-개인정보-최소화)·[NFR-PRIVACY-002](../01-requirements/non-functional-requirements.md#nfr-privacy-002-인증정보와-외부-키-보호) | 개인정보 최소화·비밀 보호 | 관리자 API | 필드·로그·설정 검사 | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |

### 5.1 2차 확장 NFR → API 검증 매핑

| NFR | 관련 API | 검증 방식 | 책임 |
|---|---|---|---|
| NFR-PERFORMANCE-006 | API-POPULAR-001, API-CURATION-001~002 | 현재 데이터 집계와 공개 목록 최대 조건 부하 테스트 | WS-10·11 |
| NFR-SECURITY-006 | 제보·신고 API, 개인 컬렉션 API, 큐레이션 API | 악성 입력·URL·일일 제한·동시 우회 테스트, `SafeTextPolicy` 거부 회귀 | WS-09·11·12 |
| NFR-INTEGRITY-005 | 관리자 상태 전이, 알림 생성 | 상태와 알림 동일 트랜잭션 실패 주입·고유성 테스트 | WS-12·13 |
| NFR-RELIABILITY-004 | 인기·알림 API | 현재 찜 일관성·보존 작업 실패 격리 테스트 | WS-10·13 |
| NFR-OBSERVABILITY-004 | 큐레이션·제보·신고 관리자 API | 감사 이력과 traceId 상관관계 검사 | WS-11·12 |
| NFR-PRIVACY-005 | 컬렉션·제보·신고·알림 | 타 회원 접근·보존·식별 제거·탈퇴 정리 테스트 | WS-09·12·13 |
| NFR-TEST-005 | 2차 확장 전체 API | 계약·통합·동시성·권한 회귀 테스트 | WS-09~13 |

### 5.2 3차 확장 NFR → API 검증 매핑

| NFR | 관련 API | 검증 방식 | 책임 |
|---|---|---|---|
| NFR-ACCURACY-002·NFR-INTEGRITY-006 | API-ADMIN-AIEXTRACT-001 | 골든 데이터 정확도·재현율·환각·잘못된 장소 연결·자동 등록 정밀도 평가 | WS-15·QUALITY-EVAL |
| NFR-PRIVACY-006 | API-ADMIN-AIEXTRACT-001·API-ADMIN-AIEXTRACT-WEBHOOK-001~002 | 원문·자막·Provider 응답·비밀정보 비저장과 로그 마스킹 검사 | WS-15 |
| NFR-EXTERNAL-005 | API-ADMIN-AIEXTRACT-001 | Gemini timeout·429·5xx·비용 hard stop·재시도·fallback 테스트 | WS-15 |
| NFR-RELIABILITY-005·NFR-AVAILABILITY-003 | API-ADMIN-AIEXTRACT-001·API-ADMIN-AIEXTRACT-WEBHOOK-001~002 | lease 만료 복구·중복 Webhook·Provider 장애 격리·기존 탐색 회귀 테스트 | WS-15·인프라 리뷰 |
| NFR-ACCURACY-001·NFR-PERFORMANCE-007 | API-DISCOVERY-NL-001·API-DISCOVERY-001 | 골든 데이터 exact match·재현율·p95·기존 탐색 회귀 테스트 | WS-14·QUALITY-EVAL |
| NFR-COST-001·NFR-EXTERNAL-005·NFR-AVAILABILITY-003 | API-DISCOVERY-COURSE-001 | quota hard stop·요청당 호출 1회·timeout·429·5xx·기능 격리 테스트 | WS-16·인프라 리뷰 |

## 6. Workstream → API 매핑

| Workstream | 소유 API | 협업 경계 |
|---|---|---|
| [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) | [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)의 유튜버 유효 맛집 판정을 최종 목록과 조합 |
| [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)과 관계 유효성 정책 공유, [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 등록 결과 소비 |
| [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | [API-CREATOR-DISCOVERY-001](api/discovery/creator-discovery-api.md#api-creator-discovery-001-유튜버-필터-선택-목록), [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색)의 `creatorId` 의미 | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)이 정렬·페이지·다른 조건 조합 |
| [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | [API-ADMIN-AUTH-001](api/admin/authentication-api.md#api-admin-auth-001-관리자-로그인)~[API-ADMIN-AUTH-003](api/admin/authentication-api.md#api-admin-auth-003-관리자-로그아웃), 기본 데이터 미리보기·생성 API, [API-ADMIN-VISIT-001](api/admin/visit-registration-api.md#api-admin-visit-001-방문-관계-등록) | 등록 결과를 [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)~[WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)이 인수 검증 |
| [OPS-VALIDATION](../02-analysis/first-expansion-workstreams.md#ops-validation-공통-운영배포-트랙) | [API-VALIDATION-001](api/common/validation-access-contract.md#api-validation-001-검증-참여자-로그인)~[API-VALIDATION-002](api/common/validation-access-contract.md#api-validation-002-검증-참여자-세션-종료) | 모든 제품 API보다 앞선 임시 진입 경계이며 정식 공개 시 전체 제거 |
| [WS-09](../02-analysis/second-expansion-workstreams.md#4-ws-09-개인-컬렉션) | API-COLLECTION-001~008 | 회원·Restaurant 공개 상태·탈퇴 생명주기 사용 |
| [WS-10](../02-analysis/second-expansion-workstreams.md#5-ws-10-인기-맛집) | API-POPULAR-001 | WS-06 Favorite 원본을 읽고 변경하지 않음 |
| [WS-11](../02-analysis/second-expansion-workstreams.md#6-ws-11-관리자-큐레이션) | API-CURATION-001~009 | 관리자 인증과 Restaurant 공개 판정 사용 |
| [WS-12](../02-analysis/second-expansion-workstreams.md#7-ws-12-제보신고-검토) | 제보·신고 회원·관리자 API | WS-13 알림과 같은 트랜잭션, 기존 관리자 실제 조치 흐름 사용 |
| [WS-13](../02-analysis/second-expansion-workstreams.md#8-ws-13-사용자-알림) | API-NOTIFICATION-001~004 | WS-12 상태를 변경하지 않고 알림 생성·읽음 소유 |

| [WS-15](../02-analysis/third-expansion-workstreams.md#6-ws-15-ai-영상-정보-추출) | API-ADMIN-AIEXTRACT-001·API-ADMIN-AIEXTRACT-WEBHOOK-001~002 | 비동기 Job·후보 Snapshot·자동 등록·예외 보정·채널 감시 소유 |
| [WS-14](../02-analysis/third-expansion-workstreams.md#5-ws-14-자연어-맛집-탐색) | API-DISCOVERY-NL-001 | 기존 API-DISCOVERY-001에 구조화 조건을 전달하고 해석 실패를 격리 |
| [WS-16](../02-analysis/third-expansion-workstreams.md#7-ws-16-맛집-코스-추천) | API-DISCOVERY-COURSE-001 | 선택 맛집 좌표를 Route Provider Port로 전달하고 결과를 비저장 반환 |

## 7. 담당자 → API 매핑

| 담당자 | 최종 책임 API | 기본 리뷰 관계 |
|---|---|---|
| 양성훈 | [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), API-POPULAR-001 | MVP 탐색은 이우람, 인기는 박진영 리뷰 |
| 박진영 | [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회), API-COLLECTION-001~008 | 김인안 리뷰 |
| 이우람 | [API-CREATOR-DISCOVERY-001](api/discovery/creator-discovery-api.md#api-creator-discovery-001-유튜버-필터-선택-목록), `creatorId` 판정 계약, API-NOTIFICATION-001~004, API-VALIDATION-001~002 | 탐색은 양성훈, 알림·검증 세션은 김인안 리뷰 |
| 김인안 | 관리자 인증·검증 미리보기·생성·방문 관계, API-CURATION-001~009, 제보·신고 회원·관리자 API | 인증·제보·신고는 이우람, 등록은 박진영, 큐레이션은 양성훈 리뷰 |

## 8. 미매핑 항목

- MVP 기능 요구사항 20개, 1차 확장 20개와 2차 확장 21개(3.2절)는 주 API 또는 명시된 내부 생성 계약에 매핑됐다. 3차 확장 요구사항도 Accepted 자연어·AI·코스 API에 매핑됐으며, 계약 테스트·평가·외부 Provider 계정 연결·물리 migration은 실행 게이트다.
- MVP 제외 기능은 API에 매핑하지 않았다.
- [PRD-PRODUCT-001](../04-product/prd/00-product-overview.md)은 전체 범위 문서라 개별 API 주 매핑이 없다.
- Critical 차단 항목이었던 식별자 타입, 인증 전달, 방문 관계 경로, 외부 확인 흐름과 후속 화면·API 라우팅 경계는 확정돼 매핑에 반영됐다.

## 9. 변경 영향 추적

요구사항·규칙 ID가 추가·삭제·변경되면 이 문서의 주 API, 보조 API, 검증 방식과 담당자를 함께 갱신한다. API 필드·경로·상태 코드 변경은 해당 PRD와 프론트엔드, 소비·제공 Workstream, 계약 테스트와 후속 데이터 모델 영향을 검토한다.

## 10. 3차 확장 API → 테스트·Task 추적

| API | 핵심 테스트 | 평가·운영 증거 | E3 Task |
|---|---|---|---|
| [API-DISCOVERY-NL-001](api/discovery/natural-language-restaurant-discovery-api.md) | `TST-E3-NL-001~002`, `TST-E3-SEC-001`, `TST-E3-E2E-001` | `EVAL-NL-001~007`, p95·로그 마스킹 | `E3-T01~02`, `E3-T11~13` |
| [API-ADMIN-AIEXTRACT-001](api/admin/ai-video-extraction-api.md), Webhook `001~002` | `TST-E3-AI-001~004`, `TST-E3-DATA-001`, `TST-E3-SEC-001` | [`EVAL-AI-001~010` 계약 자산·dry-run·HOLD 기록](../08-planning/third-expansion-ai-evaluation-result.md), Worker·quota·정식 저장 0건 | `E3-T03~08`, `E3-T11~13` |
| [API-DISCOVERY-COURSE-001](api/discovery/restaurant-course-recommendation-api.md) | `TST-E3-COURSE-001~003`, `TST-E3-PERF-001`, `TST-E3-E2E-001` | `EVAL-COURSE-001~005`, Mobility quota·호출 수 | `E3-T09~13` |

세 API의 계약 상태는 Accepted지만 테스트·외부 계정·운영 증거가 연결되기 전에는 API 구현 완료로 판정하지 않는다.

### 10.1 E3-T13 최종 게이트 증거

세 API의 보안 자동화와 성능 시나리오 준비·실행 보류 상태는 [E3-T13 최종 게이트 판정](../08-planning/third-expansion-final-gate-result.md)에 기록한다. 같은 문서 1.1절 개정에 따라 제한 공개 범위의 활성화는 증거 수집을 위해 허용하며, 자연어·코스의 운영 동급 부하 결과와 AI 외부 계정·quota 증거가 연결되기 전에는 일반 공개 활성화를 판정하지 않는다.

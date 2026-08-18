---
related_documents:
  - ../00-overview/scope.md
  - ../01-requirements/functional-requirements.md
  - ../01-requirements/business-rules.md
  - ../01-requirements/non-functional-requirements.md
  - ../02-analysis/mvp-workstreams.md
  - ../02-analysis/third-expansion-workstreams.md
  - ../02-analysis/third-expansion-domain-boundaries.md
  - ../03-team/ownership.md
  - prd/00-product-overview.md
  - ../05-specs/api-traceability.md
  - ../05-specs/data/data-traceability.md
  - prd/discovery/restaurant-discovery.md
  - prd/detail/restaurant-detail.md
  - prd/discovery/creator-discovery.md
  - prd/admin/admin-data-management.md
  - ../05-specs/api/discovery/restaurant-discovery-api.md
  - ../05-specs/api/detail/restaurant-detail-api.md
  - ../02-analysis/first-expansion-workstreams.md
  - prd/account/member-authentication.md
  - prd/personal/personal-restaurant-management.md
  - prd/discovery/map-discovery.md
  - prd/detail/creator-detail.md
  - prd/personal/personal-collection.md
  - prd/discovery/popular-restaurants.md
  - prd/curation/admin-curation.md
  - ../02-analysis/second-expansion-workstreams.md
  - prd/participation/user-submission-report.md
  - prd/notification/user-notification.md
  - ../08-planning/second-expansion-test-matrix.md
  - ../08-planning/expansion-2-implementation-plan.md
  - ../08-planning/expansion-2-task-breakdown.md
  - prd/discovery/natural-language-restaurant-discovery.md
  - prd/admin/ai-video-information-extraction.md
  - prd/discovery/restaurant-course-recommendation.md
  - user-flows/third-expansion-user-flows.md
  - wireframes/third-expansion-wireframes.md
  - ../08-planning/third-expansion-scope-and-terminology.md
  - ../08-planning/third-expansion-evaluation-strategy.md
  - ../08-planning/third-expansion-test-matrix.md
  - ../08-planning/third-expansion-task-breakdown.md
  - ../07-adr/architecture/arch-005-natural-language-filter-interpretation.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../07-adr/integration/ext-003-ai-extraction-async-reliability.md
  - ../07-adr/integration/route-001-kakao-mobility-course-routing.md
---

# 맛잇온 PRD 추적성

## 1. 문서 목적

맛잇온 MVP와 단계별 확장의 기능 요구사항, 비즈니스 규칙, NFR, Workstream과 담당자를 PRD에 연결한다. 원문 정의는 각 기준 문서가 소유하며 이 문서는 배정과 변경 영향을 관리한다.

## 2. 기능 PRD 목록

| PRD ID | 문서 | Workstream | 담당자 | 기본 리뷰어 |
|---|---|---|---|---|
| [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | [맛집 탐색](prd/discovery/restaurant-discovery.md) | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | 양성훈 | 이우람 |
| [PRD-DETAIL-001](prd/detail/restaurant-detail.md) | [맛집 상세 및 콘텐츠 조회](prd/detail/restaurant-detail.md) | [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | 박진영 | 김인안 |
| [PRD-DISCOVERY-002](prd/discovery/creator-discovery.md) | [유튜버 기반 탐색](prd/discovery/creator-discovery.md) | [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | 이우람 | 양성훈 |
| [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [관리자 데이터 등록](prd/admin/admin-data-management.md) | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 김인안 | 박진영 |
| [PRD-DISCOVERY-005](prd/discovery/natural-language-restaurant-discovery.md) | [자연어 맛집 탐색](prd/discovery/natural-language-restaurant-discovery.md) | [WS-14](../02-analysis/third-expansion-workstreams.md#5-ws-14-자연어-맛집-탐색) | 양성훈 | 이우람 |
| [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md) | [AI 영상 정보 추출](prd/admin/ai-video-information-extraction.md) | [WS-15](../02-analysis/third-expansion-workstreams.md#6-ws-15-ai-영상-정보-추출) | 김인안 | 박진영 |
| [PRD-DISCOVERY-006](prd/discovery/restaurant-course-recommendation.md) | [맛집 코스 추천](prd/discovery/restaurant-course-recommendation.md) | [WS-16](../02-analysis/third-expansion-workstreams.md#7-ws-16-맛집-코스-추천) | 이우람 | 양성훈 |

## 3. 요구사항 → PRD 매핑

| 요구사항 ID | 기능 | 주 PRD | 보조 PRD | Workstream | 담당자 |
|---|---|---|---|---|---|
| [FR-RESTAURANT-001](../01-requirements/functional-requirements.md#fr-restaurant-001-맛집-목록-조회) | 맛집 목록 조회 | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | [PRD-DISCOVERY-002](prd/discovery/creator-discovery.md), [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | 양성훈 |
| [FR-RESTAURANT-002](../01-requirements/functional-requirements.md#fr-restaurant-002-맛집-이름-검색) | 맛집 이름 검색 | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | 양성훈 |
| [FR-RESTAURANT-003](../01-requirements/functional-requirements.md#fr-restaurant-003-지역별-필터) | 지역별 필터 | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | 양성훈 |
| [FR-RESTAURANT-004](../01-requirements/functional-requirements.md#fr-restaurant-004-음식-카테고리별-필터) | 음식 카테고리별 필터 | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | 양성훈 |
| [FR-CREATOR-001](../01-requirements/functional-requirements.md#fr-creator-001-유튜버-기준-방문-맛집-조회) | 유튜버 기준 방문 맛집 조회 | [PRD-DISCOVERY-002](prd/discovery/creator-discovery.md) | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md), [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | 이우람 |
| [FR-CREATOR-003](../01-requirements/functional-requirements.md#fr-creator-003-유튜버-필터-선택-목록-조회) | 유튜버 필터 선택 목록 조회 | [PRD-DISCOVERY-002](prd/discovery/creator-discovery.md) | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md), [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | 이우람 |
| [FR-RESTAURANT-005](../01-requirements/functional-requirements.md#fr-restaurant-005-검색-및-필터-조건-조합) | 검색 및 필터 조건 조합 | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | [PRD-DISCOVERY-002](prd/discovery/creator-discovery.md) | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | 양성훈 |
| [FR-RESTAURANT-006](../01-requirements/functional-requirements.md#fr-restaurant-006-페이지-단위-조회) | 페이지 단위 조회 | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | 없음 | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | 양성훈 |
| [FR-RESTAURANT-007](../01-requirements/functional-requirements.md#fr-restaurant-007-기본-정렬-적용) | 기본 정렬 적용 | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | 없음 | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | 양성훈 |
| [FR-RESTAURANT-008](../01-requirements/functional-requirements.md#fr-restaurant-008-맛집-기본-정보-조회) | 맛집 기본 정보 조회 | [PRD-DETAIL-001](prd/detail/restaurant-detail.md) | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | 박진영 |
| [FR-RESTAURANT-009](../01-requirements/functional-requirements.md#fr-restaurant-009-지역-정보-확인) | 지역 정보 확인 | [PRD-DETAIL-001](prd/detail/restaurant-detail.md) | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | 박진영 |
| [FR-RESTAURANT-010](../01-requirements/functional-requirements.md#fr-restaurant-010-음식-카테고리-확인) | 음식 카테고리 확인 | [PRD-DETAIL-001](prd/detail/restaurant-detail.md) | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | 박진영 |
| [FR-RESTAURANT-011](../01-requirements/functional-requirements.md#fr-restaurant-011-영상-연결이-없는-맛집-상세-조회) | 영상 연결이 없는 맛집 상세 조회 | [PRD-DETAIL-001](prd/detail/restaurant-detail.md) | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | 박진영 |
| [FR-CREATOR-002](../01-requirements/functional-requirements.md#fr-creator-002-방문-유튜버-정보-확인) | 방문 유튜버 정보 확인 | [PRD-DETAIL-001](prd/detail/restaurant-detail.md) | [PRD-DISCOVERY-002](prd/discovery/creator-discovery.md), [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | 박진영 |
| [FR-VIDEO-001](../01-requirements/functional-requirements.md#fr-video-001-관련-영상-정보-확인) | 관련 영상 정보 확인 | [PRD-DETAIL-001](prd/detail/restaurant-detail.md) | [PRD-DISCOVERY-002](prd/discovery/creator-discovery.md), [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | 박진영 |
| [FR-ADMIN-001](../01-requirements/functional-requirements.md#fr-admin-001-관리자-등록-기능-접근) | 관리자 등록 기능 접근 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | 없음 | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 김인안 |
| [FR-ADMIN-002](../01-requirements/functional-requirements.md#fr-admin-002-맛집-정보-등록) | 맛집 정보 등록 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md), [PRD-DETAIL-001](prd/detail/restaurant-detail.md) | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 김인안 |
| [FR-ADMIN-003](../01-requirements/functional-requirements.md#fr-admin-003-유튜버-정보-등록) | 유튜버 정보 등록 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [PRD-DISCOVERY-002](prd/discovery/creator-discovery.md), [PRD-DETAIL-001](prd/detail/restaurant-detail.md) | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 김인안 |
| [FR-ADMIN-004](../01-requirements/functional-requirements.md#fr-admin-004-영상-정보-등록) | 영상 정보 등록 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [PRD-DISCOVERY-002](prd/discovery/creator-discovery.md), [PRD-DETAIL-001](prd/detail/restaurant-detail.md) | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 김인안 |
| [FR-VISIT-001](../01-requirements/functional-requirements.md#fr-visit-001-맛집유튜버영상-방문-관계-등록) | 맛집·유튜버·영상 방문 관계 등록 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md), [PRD-DISCOVERY-002](prd/discovery/creator-discovery.md), [PRD-DETAIL-001](prd/detail/restaurant-detail.md) | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 김인안 |

기능 요구사항 20개는 각각 정확히 하나의 주 PRD에 배정됐다. 보조 PRD는 데이터 또는 계약 영향을 뜻하며 요구사항 완료 책임을 중복시키지 않는다.

### 3.1 1차 확장 PRD 목록

| PRD ID | 문서 | Workstream | 담당자 | 기본 리뷰어 |
|---|---|---|---|---|
| [PRD-ACCOUNT-001](prd/account/member-authentication.md) | [사용자 계정·인증](prd/account/member-authentication.md) | [WS-05](../02-analysis/first-expansion-workstreams.md#4-ws-05-사용자-계정인증) | 김인안 | 이우람 |
| [PRD-PERSONAL-001](prd/personal/personal-restaurant-management.md) | [개인 맛집 관리](prd/personal/personal-restaurant-management.md) | [WS-06](../02-analysis/first-expansion-workstreams.md#5-ws-06-개인-맛집-관리) | 박진영 | 김인안 |
| [PRD-DISCOVERY-003](prd/discovery/map-discovery.md) | [지도 탐색](prd/discovery/map-discovery.md) | [WS-07](../02-analysis/first-expansion-workstreams.md#6-ws-07-지도-탐색) | 양성훈 | 박진영 |
| [PRD-DETAIL-002](prd/detail/creator-detail.md) | [유튜버 상세](prd/detail/creator-detail.md) | [WS-08](../02-analysis/first-expansion-workstreams.md#7-ws-08-유튜버-상세) | 이우람 | 박진영 |

### 3.2 1차 확장 요구사항 → PRD 매핑

| 요구사항 ID | 기능 | 주 PRD | 보조 PRD | Workstream | 담당자 |
|---|---|---|---|---|---|
| [FR-MEMBER-001](../01-requirements/functional-requirements.md#fr-member-001-이메일-회원가입) | 이메일 회원가입 | [PRD-ACCOUNT-001](prd/account/member-authentication.md) | 없음 | [WS-05](../02-analysis/first-expansion-workstreams.md#4-ws-05-사용자-계정인증) | 김인안 |
| [FR-MEMBER-002](../01-requirements/functional-requirements.md#fr-member-002-가입-이메일-인증) | 가입 이메일 인증 | [PRD-ACCOUNT-001](prd/account/member-authentication.md) | 없음 | [WS-05](../02-analysis/first-expansion-workstreams.md#4-ws-05-사용자-계정인증) | 김인안 |
| [FR-MEMBER-003](../01-requirements/functional-requirements.md#fr-member-003-비밀번호-재설정) | 비밀번호 재설정 | [PRD-ACCOUNT-001](prd/account/member-authentication.md) | 없음 | [WS-05](../02-analysis/first-expansion-workstreams.md#4-ws-05-사용자-계정인증) | 김인안 |
| [FR-MEMBER-004](../01-requirements/functional-requirements.md#fr-member-004-회원-탈퇴) | 회원 탈퇴 | [PRD-ACCOUNT-001](prd/account/member-authentication.md) | [PRD-PERSONAL-001](prd/personal/personal-restaurant-management.md) | [WS-05](../02-analysis/first-expansion-workstreams.md#4-ws-05-사용자-계정인증) | 김인안 |
| [FR-MEMBER-005](../01-requirements/functional-requirements.md#fr-member-005-현재-사용자-정보-조회) | 현재 사용자 정보 조회 | [PRD-ACCOUNT-001](prd/account/member-authentication.md) | 없음 | [WS-05](../02-analysis/first-expansion-workstreams.md#4-ws-05-사용자-계정인증) | 김인안 |
| [FR-AUTH-001](../01-requirements/functional-requirements.md#fr-auth-001-로그인과-활성-세션-발급) | 로그인과 활성 세션 발급 | [PRD-ACCOUNT-001](prd/account/member-authentication.md) | 없음 | [WS-05](../02-analysis/first-expansion-workstreams.md#4-ws-05-사용자-계정인증) | 김인안 |
| [FR-AUTH-002](../01-requirements/functional-requirements.md#fr-auth-002-access-token-재발급) | Access Token 재발급 | [PRD-ACCOUNT-001](prd/account/member-authentication.md) | 없음 | [WS-05](../02-analysis/first-expansion-workstreams.md#4-ws-05-사용자-계정인증) | 김인안 |
| [FR-AUTH-003](../01-requirements/functional-requirements.md#fr-auth-003-로그아웃과-다중-로그인-세션-관리) | 로그아웃과 다중 로그인 세션 관리 | [PRD-ACCOUNT-001](prd/account/member-authentication.md) | 없음 | [WS-05](../02-analysis/first-expansion-workstreams.md#4-ws-05-사용자-계정인증) | 김인안 |
| [FR-FAVORITE-001](../01-requirements/functional-requirements.md#fr-favorite-001-맛집-찜-추가) | 맛집 찜 추가 | [PRD-PERSONAL-001](prd/personal/personal-restaurant-management.md) | [PRD-ACCOUNT-001](prd/account/member-authentication.md) | [WS-06](../02-analysis/first-expansion-workstreams.md#5-ws-06-개인-맛집-관리) | 박진영 |
| [FR-FAVORITE-002](../01-requirements/functional-requirements.md#fr-favorite-002-맛집-찜-해제) | 맛집 찜 해제 | [PRD-PERSONAL-001](prd/personal/personal-restaurant-management.md) | [PRD-ACCOUNT-001](prd/account/member-authentication.md) | [WS-06](../02-analysis/first-expansion-workstreams.md#5-ws-06-개인-맛집-관리) | 박진영 |
| [FR-FAVORITE-003](../01-requirements/functional-requirements.md#fr-favorite-003-맛집별-현재-회원-찜-상태-확인) | 맛집별 현재 회원 찜 상태 확인 | [PRD-PERSONAL-001](prd/personal/personal-restaurant-management.md) | [PRD-DETAIL-001](prd/detail/restaurant-detail.md) | [WS-06](../02-analysis/first-expansion-workstreams.md#5-ws-06-개인-맛집-관리) | 박진영 |
| [FR-FAVORITE-004](../01-requirements/functional-requirements.md#fr-favorite-004-찜-목록-조회) | 찜 목록 조회 | [PRD-PERSONAL-001](prd/personal/personal-restaurant-management.md) | 없음 | [WS-06](../02-analysis/first-expansion-workstreams.md#5-ws-06-개인-맛집-관리) | 박진영 |
| [FR-RECENT-001](../01-requirements/functional-requirements.md#fr-recent-001-최근-본-맛집-기록) | 최근 본 맛집 기록 | [PRD-PERSONAL-001](prd/personal/personal-restaurant-management.md) | [PRD-DETAIL-001](prd/detail/restaurant-detail.md) | [WS-06](../02-analysis/first-expansion-workstreams.md#5-ws-06-개인-맛집-관리) | 박진영 |
| [FR-RECENT-002](../01-requirements/functional-requirements.md#fr-recent-002-최근-본-맛집-목록-조회) | 최근 본 맛집 목록 조회 | [PRD-PERSONAL-001](prd/personal/personal-restaurant-management.md) | 없음 | [WS-06](../02-analysis/first-expansion-workstreams.md#5-ws-06-개인-맛집-관리) | 박진영 |
| [FR-RECENT-003](../01-requirements/functional-requirements.md#fr-recent-003-최근-본-맛집-개별-삭제) | 최근 본 맛집 개별 삭제 | [PRD-PERSONAL-001](prd/personal/personal-restaurant-management.md) | 없음 | [WS-06](../02-analysis/first-expansion-workstreams.md#5-ws-06-개인-맛집-관리) | 박진영 |
| [FR-MAP-001](../01-requirements/functional-requirements.md#fr-map-001-kakao-지도와-맛집-마커-표시) | Kakao 지도와 맛집 마커 표시 | [PRD-DISCOVERY-003](prd/discovery/map-discovery.md) | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | [WS-07](../02-analysis/first-expansion-workstreams.md#6-ws-07-지도-탐색) | 양성훈 |
| [FR-MAP-002](../01-requirements/functional-requirements.md#fr-map-002-지도-이동과-탐색-결과-유지) | 지도 이동과 탐색 결과 유지 | [PRD-DISCOVERY-003](prd/discovery/map-discovery.md) | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md), [PRD-DISCOVERY-002](prd/discovery/creator-discovery.md) | [WS-07](../02-analysis/first-expansion-workstreams.md#6-ws-07-지도-탐색) | 양성훈 |
| [FR-CREATOR-004](../01-requirements/functional-requirements.md#fr-creator-004-유튜버-상세-정보-조회) | 유튜버 상세 정보 조회 | [PRD-DETAIL-002](prd/detail/creator-detail.md) | [PRD-DISCOVERY-002](prd/discovery/creator-discovery.md) | [WS-08](../02-analysis/first-expansion-workstreams.md#7-ws-08-유튜버-상세) | 이우람 |
| [FR-CREATOR-005](../01-requirements/functional-requirements.md#fr-creator-005-유튜버의-방문-맛집-목록-조회) | 유튜버의 방문 맛집 목록 조회 | [PRD-DETAIL-002](prd/detail/creator-detail.md) | [PRD-DETAIL-001](prd/detail/restaurant-detail.md) | [WS-08](../02-analysis/first-expansion-workstreams.md#7-ws-08-유튜버-상세) | 이우람 |
| [FR-CREATOR-006](../01-requirements/functional-requirements.md#fr-creator-006-유튜버의-근거-영상-목록-조회) | 유튜버의 근거 영상 목록 조회 | [PRD-DETAIL-002](prd/detail/creator-detail.md) | [PRD-DETAIL-001](prd/detail/restaurant-detail.md) | [WS-08](../02-analysis/first-expansion-workstreams.md#7-ws-08-유튜버-상세) | 이우람 |

1차 확장 기능 요구사항 20개도 MVP와 동일하게 각각 정확히 하나의 주 PRD에 배정됐다. Workstream·담당자 배정은 [1차 확장 Workstream](../02-analysis/first-expansion-workstreams.md) 2절과 일치한다.

### 3.3 2차 확장 PRD 목록

| PRD ID | 문서 | Workstream | 담당자 | 기본 리뷰어 |
|---|---|---|---|---|
| [PRD-COLLECTION-001](prd/personal/personal-collection.md) | [개인 컬렉션](prd/personal/personal-collection.md) | [WS-09](../02-analysis/second-expansion-workstreams.md#4-ws-09-개인-컬렉션) | 박진영 | 김인안 |
| [PRD-DISCOVERY-004](prd/discovery/popular-restaurants.md) | [인기 맛집](prd/discovery/popular-restaurants.md) | [WS-10](../02-analysis/second-expansion-workstreams.md#5-ws-10-인기-맛집) | 양성훈 | 박진영 |
| [PRD-CURATION-001](prd/curation/admin-curation.md) | [관리자 큐레이션](prd/curation/admin-curation.md) | [WS-11](../02-analysis/second-expansion-workstreams.md#6-ws-11-관리자-큐레이션) | 김인안 | 양성훈 |
| [PRD-PARTICIPATION-001](prd/participation/user-submission-report.md) | [사용자 제보와 신고](prd/participation/user-submission-report.md) | [WS-12](../02-analysis/second-expansion-workstreams.md#7-ws-12-제보신고-검토) | 김인안 | 이우람 |
| [PRD-NOTIFICATION-001](prd/notification/user-notification.md) | [사용자 알림](prd/notification/user-notification.md) | [WS-13](../02-analysis/second-expansion-workstreams.md#8-ws-13-사용자-알림) | 이우람 | 김인안 |

인기 집계와 큐레이션은 생성 흐름·도메인·최종 책임자가 달라 별도 PRD와 Workstream으로 분리한다.

### 3.4 2차 확장 요구사항 → PRD 매핑

| 요구사항 ID | 기능 | 주 PRD | 보조 PRD | Workstream | 담당자 |
|---|---|---|---|---|---|
| [FR-COLLECTION-001](../01-requirements/functional-requirements.md#fr-collection-001-개인-컬렉션-생성) | 개인 컬렉션 생성 | [PRD-COLLECTION-001](prd/personal/personal-collection.md) | [PRD-ACCOUNT-001](prd/account/member-authentication.md) | [WS-09](../02-analysis/second-expansion-workstreams.md#4-ws-09-개인-컬렉션) | 박진영 |
| [FR-COLLECTION-002](../01-requirements/functional-requirements.md#fr-collection-002-개인-컬렉션-이름-변경) | 개인 컬렉션 이름 변경 | [PRD-COLLECTION-001](prd/personal/personal-collection.md) | 없음 | WS-09 | 박진영 |
| [FR-COLLECTION-003](../01-requirements/functional-requirements.md#fr-collection-003-개인-컬렉션-삭제) | 개인 컬렉션 삭제 | [PRD-COLLECTION-001](prd/personal/personal-collection.md) | 없음 | WS-09 | 박진영 |
| [FR-COLLECTION-004](../01-requirements/functional-requirements.md#fr-collection-004-개인-컬렉션-조회) | 개인 컬렉션 조회 | [PRD-COLLECTION-001](prd/personal/personal-collection.md) | 없음 | WS-09 | 박진영 |
| [FR-COLLECTION-005](../01-requirements/functional-requirements.md#fr-collection-005-컬렉션-맛집-추가) | 컬렉션 맛집 추가 | [PRD-COLLECTION-001](prd/personal/personal-collection.md) | [PRD-DETAIL-001](prd/detail/restaurant-detail.md) | WS-09 | 박진영 |
| [FR-COLLECTION-006](../01-requirements/functional-requirements.md#fr-collection-006-컬렉션-맛집-제거) | 컬렉션 맛집 제거 | [PRD-COLLECTION-001](prd/personal/personal-collection.md) | 없음 | WS-09 | 박진영 |
| [FR-POPULAR-001](../01-requirements/functional-requirements.md#fr-popular-001-인기-맛집-조회) | 인기 맛집 조회 | [PRD-DISCOVERY-004](prd/discovery/popular-restaurants.md) | [PRD-PERSONAL-001](prd/personal/personal-restaurant-management.md) | [WS-10](../02-analysis/second-expansion-workstreams.md#5-ws-10-인기-맛집) | 양성훈 |
| [FR-CURATION-001](../01-requirements/functional-requirements.md#fr-curation-001-관리자-큐레이션-등록) | 관리자 큐레이션 등록 | [PRD-CURATION-001](prd/curation/admin-curation.md) | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [WS-11](../02-analysis/second-expansion-workstreams.md#6-ws-11-관리자-큐레이션) | 김인안 |
| [FR-CURATION-002](../01-requirements/functional-requirements.md#fr-curation-002-관리자-큐레이션-수정) | 관리자 큐레이션 수정 | [PRD-CURATION-001](prd/curation/admin-curation.md) | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | WS-11 | 김인안 |
| [FR-CURATION-003](../01-requirements/functional-requirements.md#fr-curation-003-관리자-큐레이션-공개-관리) | 관리자 큐레이션 공개 관리 | [PRD-CURATION-001](prd/curation/admin-curation.md) | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | WS-11 | 김인안 |
| [FR-CURATION-004](../01-requirements/functional-requirements.md#fr-curation-004-공개-큐레이션-조회) | 공개 큐레이션 조회 | [PRD-CURATION-001](prd/curation/admin-curation.md) | 없음 | WS-11 | 김인안 |
| [FR-SUBMISSION-001](../01-requirements/functional-requirements.md#fr-submission-001-사용자-제보-등록) | 사용자 제보 등록 | [PRD-PARTICIPATION-001](prd/participation/user-submission-report.md) | [PRD-ACCOUNT-001](prd/account/member-authentication.md) | [WS-12](../02-analysis/second-expansion-workstreams.md#7-ws-12-제보신고-검토) | 김인안 |
| [FR-SUBMISSION-002](../01-requirements/functional-requirements.md#fr-submission-002-사용자-제보-조회) | 사용자 제보 조회 | [PRD-PARTICIPATION-001](prd/participation/user-submission-report.md) | 없음 | WS-12 | 김인안 |
| [FR-SUBMISSION-003](../01-requirements/functional-requirements.md#fr-submission-003-관리자-제보-검토) | 관리자 제보 검토 | [PRD-PARTICIPATION-001](prd/participation/user-submission-report.md) | [PRD-ADMIN-001](prd/admin/admin-data-management.md), [PRD-NOTIFICATION-001](prd/notification/user-notification.md) | WS-12 | 김인안 |
| [FR-REPORT-001](../01-requirements/functional-requirements.md#fr-report-001-사용자-신고-등록) | 사용자 신고 등록 | [PRD-PARTICIPATION-001](prd/participation/user-submission-report.md) | [PRD-ACCOUNT-001](prd/account/member-authentication.md) | WS-12 | 김인안 |
| [FR-REPORT-002](../01-requirements/functional-requirements.md#fr-report-002-사용자-신고-조회) | 사용자 신고 조회 | [PRD-PARTICIPATION-001](prd/participation/user-submission-report.md) | 없음 | WS-12 | 김인안 |
| [FR-REPORT-003](../01-requirements/functional-requirements.md#fr-report-003-관리자-신고-검토) | 관리자 신고 검토 | [PRD-PARTICIPATION-001](prd/participation/user-submission-report.md) | [PRD-ADMIN-001](prd/admin/admin-data-management.md), [PRD-NOTIFICATION-001](prd/notification/user-notification.md) | WS-12 | 김인안 |
| [FR-NOTIFICATION-001](../01-requirements/functional-requirements.md#fr-notification-001-처리-상태-알림-생성) | 처리 상태 알림 생성 | [PRD-NOTIFICATION-001](prd/notification/user-notification.md) | [PRD-PARTICIPATION-001](prd/participation/user-submission-report.md) | [WS-13](../02-analysis/second-expansion-workstreams.md#8-ws-13-사용자-알림) | 이우람 |
| [FR-NOTIFICATION-002](../01-requirements/functional-requirements.md#fr-notification-002-사용자-알림-목록-조회) | 사용자 알림 목록 조회 | [PRD-NOTIFICATION-001](prd/notification/user-notification.md) | 없음 | WS-13 | 이우람 |
| [FR-NOTIFICATION-003](../01-requirements/functional-requirements.md#fr-notification-003-사용자-알림-개별-읽음) | 사용자 알림 개별 읽음 | [PRD-NOTIFICATION-001](prd/notification/user-notification.md) | 없음 | WS-13 | 이우람 |
| [FR-NOTIFICATION-004](../01-requirements/functional-requirements.md#fr-notification-004-사용자-알림-전체-읽음) | 사용자 알림 전체 읽음 | [PRD-NOTIFICATION-001](prd/notification/user-notification.md) | 없음 | WS-13 | 이우람 |

2차 확장 기능 요구사항 21개는 각각 정확히 하나의 주 PRD에 배정됐다.

### 3.5 3차 확장 PRD 목록

| PRD ID | 문서 | Workstream | 담당자 | 기본 리뷰어 |
|---|---|---|---|---|
| [PRD-DISCOVERY-005](prd/discovery/natural-language-restaurant-discovery.md) | [자연어 맛집 탐색](prd/discovery/natural-language-restaurant-discovery.md) | [WS-14](../02-analysis/third-expansion-workstreams.md#5-ws-14-자연어-맛집-탐색) | 양성훈 | 이우람 |
| [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md) | [AI 영상 정보 추출](prd/admin/ai-video-information-extraction.md) | [WS-15](../02-analysis/third-expansion-workstreams.md#6-ws-15-ai-영상-정보-추출) | 김인안 | 박진영 |
| [PRD-DISCOVERY-006](prd/discovery/restaurant-course-recommendation.md) | [맛집 코스 추천](prd/discovery/restaurant-course-recommendation.md) | [WS-16](../02-analysis/third-expansion-workstreams.md#7-ws-16-맛집-코스-추천) | 이우람 | 양성훈 |

### 3.6 3차 확장 요구사항 → PRD 매핑

| 요구사항 ID | 기능 | 주 PRD | 보조 PRD | Workstream | 담당자 |
|---|---|---|---|---|---|
| [FR-NLSEARCH-001](../01-requirements/functional-requirements.md#fr-nlsearch-001-자연어-검색-요청과-결과-조회) | 자연어 검색 요청과 결과 조회 | [PRD-DISCOVERY-005](prd/discovery/natural-language-restaurant-discovery.md) | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | [WS-14](../02-analysis/third-expansion-workstreams.md#5-ws-14-자연어-맛집-탐색) | 양성훈 |
| [FR-NLSEARCH-002](../01-requirements/functional-requirements.md#fr-nlsearch-002-자연어-조건과-직접-필터-조합) | 자연어 조건과 직접 필터 조합 | [PRD-DISCOVERY-005](prd/discovery/natural-language-restaurant-discovery.md) | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | [WS-14](../02-analysis/third-expansion-workstreams.md#5-ws-14-자연어-맛집-탐색) | 양성훈 |
| [FR-NLSEARCH-003](../01-requirements/functional-requirements.md#fr-nlsearch-003-빈-결과와-해석-실패) | 빈 결과와 해석 실패 | [PRD-DISCOVERY-005](prd/discovery/natural-language-restaurant-discovery.md) | 없음 | [WS-14](../02-analysis/third-expansion-workstreams.md#5-ws-14-자연어-맛집-탐색) | 양성훈 |
| [FR-NLSEARCH-004](../01-requirements/functional-requirements.md#fr-nlsearch-004-확정-태그-조건과-결과-조회) | 확정 태그 조건과 결과 조회 | [PRD-DISCOVERY-005](prd/discovery/natural-language-restaurant-discovery.md) | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | [WS-14](../02-analysis/third-expansion-workstreams.md#5-ws-14-자연어-맛집-탐색) | 양성훈 |
| [FR-AIEXTRACT-001](../01-requirements/functional-requirements.md#fr-aiextract-001-ai-영상-추출-작업-요청) | AI 영상 추출 작업 요청 | [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md) | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [WS-15](../02-analysis/third-expansion-workstreams.md#6-ws-15-ai-영상-정보-추출) | 김인안 |
| [FR-AIEXTRACT-002](../01-requirements/functional-requirements.md#fr-aiextract-002-추출-상태와-결과-조회) | 추출 상태와 결과 조회 | [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md) | 없음 | [WS-15](../02-analysis/third-expansion-workstreams.md#6-ws-15-ai-영상-정보-추출) | 김인안 |
| [FR-AIEXTRACT-003](../01-requirements/functional-requirements.md#fr-aiextract-003-자동-확정예외-보정폐기) | 자동 확정·예외 보정·폐기 | [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md) | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [WS-15](../02-analysis/third-expansion-workstreams.md#6-ws-15-ai-영상-정보-추출) | 김인안 |
| [FR-AIEXTRACT-004](../01-requirements/functional-requirements.md#fr-aiextract-004-신규-영상-webhook-감지와-작업-등록) | 신규 영상 Webhook 감지와 작업 등록 | [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md) | 없음 | [WS-15](../02-analysis/third-expansion-workstreams.md#6-ws-15-ai-영상-정보-추출) | 김인안 |
| [FR-AIEXTRACT-005](../01-requirements/functional-requirements.md#fr-aiextract-005-관리자-신규-영상-추가) | 관리자 신규 영상 추가 | [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md) | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [WS-15](../02-analysis/third-expansion-workstreams.md#6-ws-15-ai-영상-정보-추출) | 김인안 |
| [FR-AIEXTRACT-006](../01-requirements/functional-requirements.md#fr-aiextract-006-webhook-감시-채널-관리) | Webhook 감시 채널 관리 | [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md) | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [WS-15](../02-analysis/third-expansion-workstreams.md#6-ws-15-ai-영상-정보-추출) | 김인안 |
| [FR-AIEXTRACT-007](../01-requirements/functional-requirements.md#fr-aiextract-007-ai-태그-후보-생성과-확정) | AI 태그 후보 생성과 확정 | [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md) | [PRD-DISCOVERY-005](prd/discovery/natural-language-restaurant-discovery.md) | [WS-15](../02-analysis/third-expansion-workstreams.md#6-ws-15-ai-영상-정보-추출) | 김인안 |
| [FR-COURSE-001](../01-requirements/functional-requirements.md#fr-course-001-코스-후보-입력) | 코스 후보 입력 | [PRD-DISCOVERY-006](prd/discovery/restaurant-course-recommendation.md) | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | [WS-16](../02-analysis/third-expansion-workstreams.md#7-ws-16-맛집-코스-추천) | 이우람 |
| [FR-COURSE-002](../01-requirements/functional-requirements.md#fr-course-002-이동-순서와-경로-조회) | 이동 순서와 경로 조회 | [PRD-DISCOVERY-006](prd/discovery/restaurant-course-recommendation.md) | 없음 | [WS-16](../02-analysis/third-expansion-workstreams.md#7-ws-16-맛집-코스-추천) | 이우람 |
| [FR-COURSE-003](../01-requirements/functional-requirements.md#fr-course-003-외부-경로-실패-시-대체-결과) | 외부 경로 실패 시 대체 결과 | [PRD-DISCOVERY-006](prd/discovery/restaurant-course-recommendation.md) | 없음 | [WS-16](../02-analysis/third-expansion-workstreams.md#7-ws-16-맛집-코스-추천) | 이우람 |

3차 확장 기능 요구사항 14개는 각각 정확히 하나의 주 PRD와 WS-14~WS-16에 배정됐다.

## 4. 비즈니스 규칙 → PRD 매핑

| 규칙 ID | 규칙 | 주 PRD | 영향 PRD | 담당자 |
|---|---|---|---|---|
| [BR-RESTAURANT-001](../01-requirements/business-rules.md#br-restaurant-001-맛집의-의미) | 맛집의 의미 | [PRD-PRODUCT-001](prd/00-product-overview.md) | [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회), [ADMIN-001](prd/admin/admin-data-management.md) | 김인안 |
| [BR-RESTAURANT-002](../01-requirements/business-rules.md#br-restaurant-002-영상과-독립된-맛집) | 영상과 독립된 맛집 | [PRD-DETAIL-001](prd/detail/restaurant-detail.md) | [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [ADMIN-001](prd/admin/admin-data-management.md) | 박진영 |
| [BR-RESTAURANT-003](../01-requirements/business-rules.md#br-restaurant-003-맛집-최소-등록-정보) | 맛집 최소 등록 정보 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 김인안 |
| [BR-RESTAURANT-004](../01-requirements/business-rules.md#br-restaurant-004-대표-음식-카테고리) | 대표 음식 카테고리 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 김인안 |
| [BR-RESTAURANT-005](../01-requirements/business-rules.md#br-restaurant-005-맛집의-지역-소속) | 맛집의 지역 소속 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 김인안 |
| [BR-RESTAURANT-006](../01-requirements/business-rules.md#br-restaurant-006-맛집-중복-판단) | 맛집 중복 판단 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) | 김인안 |
| [BR-RESTAURANT-007](../01-requirements/business-rules.md#br-restaurant-007-동일-상호의-지점-구분) | 동일 상호의 지점 구분 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) | 김인안 |
| [BR-RESTAURANT-008](../01-requirements/business-rules.md#br-restaurant-008-맛집-공개-조건) | 맛집 공개 조건 | [PRD-PRODUCT-001](prd/00-product-overview.md) | [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회), [ADMIN-001](prd/admin/admin-data-management.md) | 김인안 |
| [BR-RESTAURANT-009](../01-requirements/business-rules.md#br-restaurant-009-맛집-이름-변경) | 맛집 이름 변경 | [PRD-PRODUCT-001](prd/00-product-overview.md) | [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [ADMIN-001](prd/admin/admin-data-management.md) | 김인안 |
| [BR-RESTAURANT-010](../01-requirements/business-rules.md#br-restaurant-010-주소-변경과-장소-이전) | 주소 변경과 장소 이전 | [PRD-PRODUCT-001](prd/00-product-overview.md) | [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회), [ADMIN-001](prd/admin/admin-data-management.md) | 김인안 |
| [BR-RESTAURANT-011](../01-requirements/business-rules.md#br-restaurant-011-폐업과-장기-운영-중단) | 폐업과 장기 운영 중단 | [PRD-PRODUCT-001](prd/00-product-overview.md) | [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회), [ADMIN-001](prd/admin/admin-data-management.md) | 김인안 |
| [BR-CREATOR-001](../01-requirements/business-rules.md#br-creator-001-유튜버-정보의-의미) | 유튜버 정보의 의미 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [DISCOVERY-002](prd/discovery/creator-discovery.md), [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 김인안 |
| [BR-CREATOR-002](../01-requirements/business-rules.md#br-creator-002-유튜버-최소-등록-정보) | 유튜버 최소 등록 정보 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [DISCOVERY-002](prd/discovery/creator-discovery.md), [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 김인안 |
| [BR-CREATOR-003](../01-requirements/business-rules.md#br-creator-003-동일-채널-중복-판단) | 동일 채널 중복 판단 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [DISCOVERY-002](prd/discovery/creator-discovery.md), [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 김인안 |
| [BR-CREATOR-004](../01-requirements/business-rules.md#br-creator-004-유튜버-표시-정보) | 유튜버 표시 정보 | [PRD-DETAIL-001](prd/detail/restaurant-detail.md) | [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [DISCOVERY-002](prd/discovery/creator-discovery.md) | 박진영 |
| [BR-CREATOR-005](../01-requirements/business-rules.md#br-creator-005-방문-관계의-유튜버-일치) | 방문 관계의 유튜버 일치 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [DISCOVERY-002](prd/discovery/creator-discovery.md), [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 김인안 |
| [BR-CREATOR-006](../01-requirements/business-rules.md#br-creator-006-채널명-변경과-동일성-유지) | 채널명 변경과 동일성 유지 | [PRD-PRODUCT-001](prd/00-product-overview.md) | [DISCOVERY-002](prd/discovery/creator-discovery.md), [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회), [ADMIN-001](prd/admin/admin-data-management.md) | 김인안 |
| [BR-CREATOR-007](../01-requirements/business-rules.md#br-creator-007-채널-이용-불가-처리) | 채널 이용 불가 처리 | [PRD-DISCOVERY-002](prd/discovery/creator-discovery.md) | [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회), [ADMIN-001](prd/admin/admin-data-management.md) | 이우람 |
| [BR-VIDEO-001](../01-requirements/business-rules.md#br-video-001-영상의-의미와-보관-범위) | 영상의 의미와 보관 범위 | [PRD-PRODUCT-001](prd/00-product-overview.md) | [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회), [ADMIN-001](prd/admin/admin-data-management.md) | 김인안 |
| [BR-VIDEO-002](../01-requirements/business-rules.md#br-video-002-영상-최소-등록-정보) | 영상 최소 등록 정보 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 김인안 |
| [BR-VIDEO-003](../01-requirements/business-rules.md#br-video-003-영상-식별-및-중복-판단) | 영상 식별 및 중복 판단 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 김인안 |
| [BR-VIDEO-004](../01-requirements/business-rules.md#br-video-004-영상과-방문-관계의-다대상-연결) | 영상과 방문 관계의 다대상 연결 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 김인안 |
| [BR-VIDEO-005](../01-requirements/business-rules.md#br-video-005-실제-방문-근거) | 실제 방문 근거 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [DISCOVERY-002](prd/discovery/creator-discovery.md), [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 김인안 |
| [BR-VIDEO-006](../01-requirements/business-rules.md#br-video-006-게시일과-방문일의-구분) | 게시일과 방문일의 구분 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [DISCOVERY-002](prd/discovery/creator-discovery.md) | 김인안 |
| [BR-VIDEO-007](../01-requirements/business-rules.md#br-video-007-외부-링크-장애의-격리) | 외부 링크 장애의 격리 | [PRD-DETAIL-001](prd/detail/restaurant-detail.md) | [ADMIN-001](prd/admin/admin-data-management.md) | 박진영 |
| [BR-VIDEO-008](../01-requirements/business-rules.md#br-video-008-영상-표시-정보-변경) | 영상 표시 정보 변경 | [PRD-DETAIL-001](prd/detail/restaurant-detail.md) | [ADMIN-001](prd/admin/admin-data-management.md) | 김인안 |
| [BR-VIDEO-009](../01-requirements/business-rules.md#br-video-009-영상-이용-불가-처리) | 영상 이용 불가 처리 | [PRD-DISCOVERY-002](prd/discovery/creator-discovery.md) | [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회), [ADMIN-001](prd/admin/admin-data-management.md) | 이우람 |
| [BR-VISIT-001](../01-requirements/business-rules.md#br-visit-001-방문-관계의-구성) | 방문 관계의 구성 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [DISCOVERY-002](prd/discovery/creator-discovery.md), [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 김인안 |
| [BR-VISIT-002](../01-requirements/business-rules.md#br-visit-002-방문-근거-필수) | 방문 근거 필수 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [DISCOVERY-002](prd/discovery/creator-discovery.md), [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 김인안 |
| [BR-VISIT-003](../01-requirements/business-rules.md#br-visit-003-방문-관계-중복-판단) | 방문 관계 중복 판단 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [DISCOVERY-002](prd/discovery/creator-discovery.md), [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 김인안 |
| [BR-VISIT-004](../01-requirements/business-rules.md#br-visit-004-방문-관계의-연결-범위) | 방문 관계의 연결 범위 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [DISCOVERY-002](prd/discovery/creator-discovery.md), [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 김인안 |
| [BR-VISIT-005](../01-requirements/business-rules.md#br-visit-005-방문-관계의-조회-유효성) | 방문 관계의 조회 유효성 | [PRD-DISCOVERY-002](prd/discovery/creator-discovery.md) | [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회), [ADMIN-001](prd/admin/admin-data-management.md) | 이우람 |
| [BR-VISIT-006](../01-requirements/business-rules.md#br-visit-006-방문-날짜-관리-제외) | 방문 날짜 관리 제외 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [DISCOVERY-002](prd/discovery/creator-discovery.md) | 김인안 |
| [BR-VISIT-007](../01-requirements/business-rules.md#br-visit-007-등록-완료와-검증-상태) | 등록 완료와 검증 상태 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [DISCOVERY-002](prd/discovery/creator-discovery.md), [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 김인안 |
| [BR-SEARCH-001](../01-requirements/business-rules.md#br-search-001-검색-대상과-일치-기준) | 검색 대상과 일치 기준 | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | 없음 | 양성훈 |
| [BR-SEARCH-002](../01-requirements/business-rules.md#br-search-002-검색어-공백-처리) | 검색어 공백 처리 | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | 없음 | 양성훈 |
| [BR-SEARCH-003](../01-requirements/business-rules.md#br-search-003-필터-종류와-단일-선택) | 필터 종류와 단일 선택 | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | [DISCOVERY-002](prd/discovery/creator-discovery.md) | 양성훈 |
| [BR-SEARCH-004](../01-requirements/business-rules.md#br-search-004-검색과-필터-조합) | 검색과 필터 조합 | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | [DISCOVERY-002](prd/discovery/creator-discovery.md) | 양성훈 |
| [BR-SEARCH-005](../01-requirements/business-rules.md#br-search-005-조회-결과의-고유성) | 조회 결과의 고유성 | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | [DISCOVERY-002](prd/discovery/creator-discovery.md), [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 양성훈 |
| [BR-SEARCH-006](../01-requirements/business-rules.md#br-search-006-빈-조회-결과) | 빈 조회 결과 | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | [DISCOVERY-002](prd/discovery/creator-discovery.md) | 양성훈 |
| [BR-SEARCH-007](../01-requirements/business-rules.md#br-search-007-유튜버-필터의-방문-근거) | 유튜버 필터의 방문 근거 | [PRD-DISCOVERY-002](prd/discovery/creator-discovery.md) | [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) | 이우람 |
| [BR-SEARCH-008](../01-requirements/business-rules.md#br-search-008-페이지-단위-조회) | 페이지 단위 조회 | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | 없음 | 양성훈 |
| [BR-SEARCH-009](../01-requirements/business-rules.md#br-search-009-기본-정렬) | 기본 정렬 | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | 없음 | 양성훈 |
| [BR-ADMIN-001](../01-requirements/business-rules.md#br-admin-001-관리자-권한-검증) | 관리자 권한 검증 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [PRODUCT-001](prd/00-product-overview.md) | 김인안 |
| [BR-ADMIN-002](../01-requirements/business-rules.md#br-admin-002-등록-전-사실-검증) | 등록 전 사실 검증 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | 전체 조회 PRD | 김인안 |
| [BR-ADMIN-003](../01-requirements/business-rules.md#br-admin-003-등록-정합성-검증) | 등록 정합성 검증 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | 전체 조회 PRD | 김인안 |
| [BR-ADMIN-004](../01-requirements/business-rules.md#br-admin-004-검증-후-등록-및-조회-반영) | 검증 후 등록 및 조회 반영 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | 전체 조회 PRD | 김인안 |
| [BR-ADMIN-005](../01-requirements/business-rules.md#br-admin-005-mvp-관리-기능의-경계) | MVP 관리 기능의 경계 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [PRODUCT-001](prd/00-product-overview.md) | 김인안 |
| [BR-ADMIN-006](../01-requirements/business-rules.md#br-admin-006-잘못-등록된-데이터의-정정-원칙) | 잘못 등록된 데이터의 정정 원칙 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | 전체 조회 PRD | 김인안 |
| [BR-ADMIN-007](../01-requirements/business-rules.md#br-admin-007-동시-등록의-고유성) | 동시 등록의 고유성 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | 전체 조회 PRD | 김인안 |
| [BR-ADMIN-008](../01-requirements/business-rules.md#br-admin-008-보류-요청의-처리) | 보류 요청의 처리 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | 전체 조회 PRD | 김인안 |
| [BR-PUBLICATION-001](../01-requirements/business-rules.md#br-publication-001-일반-사용자-공개-범위) | 일반 사용자 공개 범위 | [PRD-PRODUCT-001](prd/00-product-overview.md) | 전체 기능 PRD | 이우람 |
| [BR-PUBLICATION-002](../01-requirements/business-rules.md#br-publication-002-비공개-데이터의-접근) | 비공개 데이터의 접근 | [PRD-PRODUCT-001](prd/00-product-overview.md) | [ADMIN-001](prd/admin/admin-data-management.md) | 김인안 |
| [BR-PUBLICATION-003](../01-requirements/business-rules.md#br-publication-003-맛집-상태와-연결-정보-노출) | 맛집 상태와 연결 정보 노출 | [PRD-PRODUCT-001](prd/00-product-overview.md) | [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [DISCOVERY-002](prd/discovery/creator-discovery.md), [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 박진영 |
| [BR-PUBLICATION-004](../01-requirements/business-rules.md#br-publication-004-유튜버-상태와-관계-노출) | 유튜버 상태와 관계 노출 | [PRD-PRODUCT-001](prd/00-product-overview.md) | [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [DISCOVERY-002](prd/discovery/creator-discovery.md), [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 이우람 |
| [BR-PUBLICATION-005](../01-requirements/business-rules.md#br-publication-005-영상-상태와-관계-노출) | 영상 상태와 관계 노출 | [PRD-PRODUCT-001](prd/00-product-overview.md) | [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [DISCOVERY-002](prd/discovery/creator-discovery.md), [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 이우람 |
| [BR-PUBLICATION-006](../01-requirements/business-rules.md#br-publication-006-관계-상태와-맛집-기본-조회) | 관계 상태와 맛집 기본 조회 | [PRD-PRODUCT-001](prd/00-product-overview.md) | 전체 조회 PRD | 이우람 |
| [BR-PUBLICATION-007](../01-requirements/business-rules.md#br-publication-007-외부-영상-삭제의-영향-범위) | 외부 영상 삭제의 영향 범위 | [PRD-PRODUCT-001](prd/00-product-overview.md) | [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회), [ADMIN-001](prd/admin/admin-data-management.md) | 박진영 |
| [BR-PUBLICATION-008](../01-requirements/business-rules.md#br-publication-008-상태-변경의-일관성) | 상태 변경의 일관성 | [PRD-PRODUCT-001](prd/00-product-overview.md) | 전체 기능 PRD | 김인안 |

표의 축약 ID [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [DISCOVERY-002](prd/discovery/creator-discovery.md), [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회), [ADMIN-001](prd/admin/admin-data-management.md), [PRODUCT-001](prd/00-product-overview.md)은 각각 동일 접두사의 `PRD-` ID를 뜻한다.

### 4.1 2차 확장 비즈니스 규칙 → PRD 매핑

| 규칙 ID | 규칙 | 주 PRD | 영향 PRD | 담당자 |
|---|---|---|---|---|
| [BR-COLLECTION-001](../01-requirements/business-rules.md#br-collection-001-소유권과-비공개-경계) | 소유권과 비공개 경계 | [PRD-COLLECTION-001](prd/personal/personal-collection.md) | [PRD-ACCOUNT-001](prd/account/member-authentication.md) | 박진영 |
| [BR-COLLECTION-002](../01-requirements/business-rules.md#br-collection-002-맛집-관계의-고유성과-상한) | 맛집 관계의 고유성과 상한 | [PRD-COLLECTION-001](prd/personal/personal-collection.md) | 없음 | 박진영 |
| [BR-COLLECTION-003](../01-requirements/business-rules.md#br-collection-003-정렬과-직접-순서-변경-제외) | 정렬과 직접 순서 변경 제외 | [PRD-COLLECTION-001](prd/personal/personal-collection.md) | 없음 | 박진영 |
| [BR-COLLECTION-004](../01-requirements/business-rules.md#br-collection-004-맛집-공개-상태) | 맛집 공개 상태 | [PRD-COLLECTION-001](prd/personal/personal-collection.md) | [PRD-PRODUCT-001](prd/00-product-overview.md) | 박진영 |
| [BR-COLLECTION-005](../01-requirements/business-rules.md#br-collection-005-삭제와-회원-탈퇴) | 삭제와 회원 탈퇴 | [PRD-COLLECTION-001](prd/personal/personal-collection.md) | [PRD-ACCOUNT-001](prd/account/member-authentication.md) | 박진영 |
| [BR-POPULAR-001](../01-requirements/business-rules.md#br-popular-001-인기-신호와-집계-기간) | 인기 신호와 집계 기간 | [PRD-DISCOVERY-004](prd/discovery/popular-restaurants.md) | [PRD-PERSONAL-001](prd/personal/personal-restaurant-management.md) | 양성훈 |
| [BR-POPULAR-002](../01-requirements/business-rules.md#br-popular-002-포함-대상과-안정-정렬) | 포함 대상과 안정 정렬 | [PRD-DISCOVERY-004](prd/discovery/popular-restaurants.md) | 없음 | 양성훈 |
| [BR-POPULAR-003](../01-requirements/business-rules.md#br-popular-003-실시간-집계와-반영) | 실시간 집계와 반영 | [PRD-DISCOVERY-004](prd/discovery/popular-restaurants.md) | [PRD-PERSONAL-001](prd/personal/personal-restaurant-management.md) | 양성훈 |
| [BR-CURATION-001](../01-requirements/business-rules.md#br-curation-001-소유권과-게시-상태) | 소유권과 게시 상태 | [PRD-CURATION-001](prd/curation/admin-curation.md) | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | 김인안 |
| [BR-CURATION-002](../01-requirements/business-rules.md#br-curation-002-구성과-정렬-상한) | 구성과 정렬 상한 | [PRD-CURATION-001](prd/curation/admin-curation.md) | 없음 | 김인안 |
| [BR-CURATION-003](../01-requirements/business-rules.md#br-curation-003-맛집-공개-상태와-경고) | 맛집 공개 상태와 경고 | [PRD-CURATION-001](prd/curation/admin-curation.md) | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | 김인안 |
| [BR-CURATION-004](../01-requirements/business-rules.md#br-curation-004-게시-중-수정-반영) | 게시 중 수정 반영 | [PRD-CURATION-001](prd/curation/admin-curation.md) | 없음 | 김인안 |
| [BR-SUBMISSION-001](../01-requirements/business-rules.md#br-submission-001-대상과-근거) | 제보 대상과 근거 | [PRD-PARTICIPATION-001](prd/participation/user-submission-report.md) | 없음 | 김인안 |
| [BR-SUBMISSION-002](../01-requirements/business-rules.md#br-submission-002-중복과-요청-제한) | 제보 중복과 요청 제한 | [PRD-PARTICIPATION-001](prd/participation/user-submission-report.md) | 없음 | 김인안 |
| [BR-SUBMISSION-003](../01-requirements/business-rules.md#br-submission-003-상태-전이와-실제-등록) | 상태 전이와 실제 등록 | [PRD-PARTICIPATION-001](prd/participation/user-submission-report.md) | [PRD-ADMIN-001](prd/admin/admin-data-management.md), [PRD-NOTIFICATION-001](prd/notification/user-notification.md) | 김인안 |
| [BR-SUBMISSION-004](../01-requirements/business-rules.md#br-submission-004-소유권과-보존) | 제보 소유권과 보존 | [PRD-PARTICIPATION-001](prd/participation/user-submission-report.md) | [PRD-ACCOUNT-001](prd/account/member-authentication.md) | 김인안 |
| [BR-REPORT-001](../01-requirements/business-rules.md#br-report-001-대상과-근거) | 신고 대상과 근거 | [PRD-PARTICIPATION-001](prd/participation/user-submission-report.md) | 없음 | 김인안 |
| [BR-REPORT-002](../01-requirements/business-rules.md#br-report-002-중복과-요청-제한) | 신고 중복과 요청 제한 | [PRD-PARTICIPATION-001](prd/participation/user-submission-report.md) | 없음 | 김인안 |
| [BR-REPORT-003](../01-requirements/business-rules.md#br-report-003-상태-전이와-공개-상태) | 상태 전이와 공개 상태 | [PRD-PARTICIPATION-001](prd/participation/user-submission-report.md) | [PRD-ADMIN-001](prd/admin/admin-data-management.md), [PRD-NOTIFICATION-001](prd/notification/user-notification.md) | 김인안 |
| [BR-REPORT-004](../01-requirements/business-rules.md#br-report-004-소유권과-보존) | 신고 소유권과 보존 | [PRD-PARTICIPATION-001](prd/participation/user-submission-report.md) | [PRD-ACCOUNT-001](prd/account/member-authentication.md) | 김인안 |
| [BR-NOTIFICATION-001](../01-requirements/business-rules.md#br-notification-001-상태-전이와-원자적-생성) | 상태 전이와 원자적 생성 | [PRD-NOTIFICATION-001](prd/notification/user-notification.md) | [PRD-PARTICIPATION-001](prd/participation/user-submission-report.md) | 이우람 |
| [BR-NOTIFICATION-002](../01-requirements/business-rules.md#br-notification-002-소유권과-읽음) | 소유권과 읽음 | [PRD-NOTIFICATION-001](prd/notification/user-notification.md) | 없음 | 이우람 |
| [BR-NOTIFICATION-003](../01-requirements/business-rules.md#br-notification-003-보존과-회원-탈퇴) | 보존과 회원 탈퇴 | [PRD-NOTIFICATION-001](prd/notification/user-notification.md) | [PRD-ACCOUNT-001](prd/account/member-authentication.md) | 이우람 |
| [BR-NOTIFICATION-004](../01-requirements/business-rules.md#br-notification-004-채널과-동의-경계) | 채널과 동의 경계 | [PRD-NOTIFICATION-001](prd/notification/user-notification.md) | 없음 | 이우람 |

2차 확장 비즈니스 규칙 24개도 각각 정확히 하나의 주 PRD에 배정됐다.

### 4.2 3차 확장 비즈니스 규칙 → PRD 매핑

| 규칙 ID | 규칙 | 주 PRD | 영향 PRD | 담당자 |
|---|---|---|---|---|
| [BR-NLSEARCH-001](../01-requirements/business-rules.md#br-nlsearch-001-직접-지정-필터-우선) | 직접 지정 필터 우선 | [PRD-DISCOVERY-005](prd/discovery/natural-language-restaurant-discovery.md) | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | 양성훈 |
| [BR-NLSEARCH-002](../01-requirements/business-rules.md#br-nlsearch-002-검색-결과의-공개와-생명주기) | 검색 결과의 공개와 생명주기 | [PRD-DISCOVERY-005](prd/discovery/natural-language-restaurant-discovery.md) | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | 양성훈 |
| [BR-NLSEARCH-003](../01-requirements/business-rules.md#br-nlsearch-003-태그-검색과-공개-visit-기준) | 태그 검색과 공개 Visit 기준 | [PRD-DISCOVERY-005](prd/discovery/natural-language-restaurant-discovery.md) | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | 양성훈 |
| [BR-AIEXTRACT-001](../01-requirements/business-rules.md#br-aiextract-001-ai-후보-생성-범위) | AI 후보 생성 범위 | [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md) | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | 김인안 |
| [BR-AIEXTRACT-002](../01-requirements/business-rules.md#br-aiextract-002-자동-검증-없는-정식-저장-금지) | 자동 검증 없는 정식 저장 금지·통과 시 무승인 공개 | [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md) | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | 김인안 |
| [BR-AIEXTRACT-003](../01-requirements/business-rules.md#br-aiextract-003-동일-영상-중복-추출) | 동일 영상 중복 추출 | [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md) | 없음 | 김인안 |
| [BR-AIEXTRACT-004](../01-requirements/business-rules.md#br-aiextract-004-모델prompt결과-schema-버전) | 모델·Prompt·결과 Schema 버전 | [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md) | 없음 | 김인안 |
| [BR-AIEXTRACT-005](../01-requirements/business-rules.md#br-aiextract-005-영상-유입-경로와-작업-수렴) | 영상 유입 경로와 작업 수렴 | [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md) | 없음 | 김인안 |
| [BR-AIEXTRACT-006](../01-requirements/business-rules.md#br-aiextract-006-webhook-감시-채널-상태) | Webhook 감시 채널 상태 | [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md) | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | 김인안 |
| [BR-AIEXTRACT-007](../01-requirements/business-rules.md#br-aiextract-007-gemini-영상-입력과-fallback) | Gemini 영상 입력과 fallback | [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md) | 없음 | 김인안 |
| [BR-AIEXTRACT-008](../01-requirements/business-rules.md#br-aiextract-008-태그-후보-자동-등록과-공개) | 태그 후보 통제와 공개 | [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md) | [PRD-DISCOVERY-005](prd/discovery/natural-language-restaurant-discovery.md) | 김인안 |
| [BR-AIEXTRACT-009](../01-requirements/business-rules.md#br-aiextract-009-장소-동일성-자동-확정) | 장소 동일성 자동 확정 | [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md) | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | 김인안 |
| [BR-AIEXTRACT-010](../01-requirements/business-rules.md#br-aiextract-010-대표-음식-카테고리-자동-선정) | 대표 음식 카테고리 자동 선정 | [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md) | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | 김인안 |
| [BR-AIEXTRACT-011](../01-requirements/business-rules.md#br-aiextract-011-등록-단위-일괄-등록과-예외-전환) | 등록 단위 일괄 등록과 예외 전환 | [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md) | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | 김인안 |
| [BR-COURSE-001](../01-requirements/business-rules.md#br-course-001-코스-맛집-수와-출발점) | 코스 맛집 수와 출발점 | [PRD-DISCOVERY-006](prd/discovery/restaurant-course-recommendation.md) | 없음 | 이우람 |
| [BR-COURSE-002](../01-requirements/business-rules.md#br-course-002-좌표-없는-맛집-처리) | 좌표 없는 맛집 처리 | [PRD-DISCOVERY-006](prd/discovery/restaurant-course-recommendation.md) | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | 이우람 |
| [BR-COURSE-003](../01-requirements/business-rules.md#br-course-003-경로-결과-만료와-재조회) | 경로 결과 만료와 재조회 | [PRD-DISCOVERY-006](prd/discovery/restaurant-course-recommendation.md) | 없음 | 이우람 |
| [BR-COURSE-004](../01-requirements/business-rules.md#br-course-004-외부-api-실패와-부분-결과) | 외부 API 실패와 부분 결과 | [PRD-DISCOVERY-006](prd/discovery/restaurant-course-recommendation.md) | 없음 | 이우람 |
| [BR-COURSE-005](../01-requirements/business-rules.md#br-course-005-실제-경로-형상-표시와-저하) | 실제 경로 형상 표시와 저하 | [PRD-DISCOVERY-006](prd/discovery/restaurant-course-recommendation.md) | 없음 | 이우람 |

3차 확장 비즈니스 규칙 10개도 각각 정확히 하나의 주 PRD에 배정됐다.

## 5. NFR → PRD 매핑

| NFR ID | 품질 요구사항 | 공통 적용 여부 | 적용 PRD | 검증 책임 |
|---|---|---|---|---|
| [NFR-PERFORMANCE-001](../01-requirements/non-functional-requirements.md#nfr-performance-001-일반-조회-응답-시간) | 일반 조회 응답 시간 | 아니요 | [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 양성훈·박진영 |
| [NFR-PERFORMANCE-002](../01-requirements/non-functional-requirements.md#nfr-performance-002-검색필터-조합-응답-시간) | 검색·필터 조합 응답 시간 | 아니요 | [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [DISCOVERY-002](prd/discovery/creator-discovery.md) | 양성훈, 준수: 이우람 |
| [NFR-PERFORMANCE-003](../01-requirements/non-functional-requirements.md#nfr-performance-003-관리자-등록-응답-시간) | 관리자 등록 응답 시간 | 아니요 | [ADMIN-001](prd/admin/admin-data-management.md) | 김인안 |
| [NFR-PERFORMANCE-004](../01-requirements/non-functional-requirements.md#nfr-performance-004-페이지-크기-및-조회량-제한) | 페이지 크기 및 조회량 제한 | 아니요 | [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) | 양성훈 |
| [NFR-SECURITY-001](../01-requirements/non-functional-requirements.md#nfr-security-001-공개-조회와-관리자-접근-통제) | 공개 조회와 관리자 접근 통제 | 예 | [PRODUCT-001](prd/00-product-overview.md), 전체 기능 PRD | 김인안 |
| [NFR-SECURITY-002](../01-requirements/non-functional-requirements.md#nfr-security-002-입력-및-웹-공격-방어) | 입력 및 웹 공격 방어 | 예 | 전체 기능 PRD | 각 Workstream 담당자 |
| [NFR-SECURITY-003](../01-requirements/non-functional-requirements.md#nfr-security-003-비밀정보와-오류-정보-보호) | 비밀정보와 오류 정보 보호 | 예 | [PRODUCT-001](prd/00-product-overview.md), 전체 기능 PRD | 이우람 |
| [NFR-INTEGRITY-001](../01-requirements/non-functional-requirements.md#nfr-integrity-001-참조-및-필수값-정합성) | 참조 및 필수값 정합성 | 아니요 | [ADMIN-001](prd/admin/admin-data-management.md) | 김인안 |
| [NFR-INTEGRITY-002](../01-requirements/non-functional-requirements.md#nfr-integrity-002-중복-및-동시-등록-방지) | 중복 및 동시 등록 방지 | 아니요 | [ADMIN-001](prd/admin/admin-data-management.md) | 김인안 |
| [NFR-INTEGRITY-003](../01-requirements/non-functional-requirements.md#nfr-integrity-003-등록-원자성과-공개-상태-일관성) | 등록 원자성과 공개 상태 일관성 | 아니요 | [ADMIN-001](prd/admin/admin-data-management.md), 전체 조회 PRD | 김인안 |
| [NFR-INTEGRITY-004](../01-requirements/non-functional-requirements.md#nfr-integrity-004-외부-링크와-내부-데이터-분리) | 외부 링크와 내부 데이터 분리 | 아니요 | [DISCOVERY-002](prd/discovery/creator-discovery.md), [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회), [ADMIN-001](prd/admin/admin-data-management.md) | 박진영 |
| [NFR-RELIABILITY-001](../01-requirements/non-functional-requirements.md#nfr-reliability-001-오류-격리와-공통-오류-정책) | 오류 격리와 공통 오류 정책 | 예 | [PRODUCT-001](prd/00-product-overview.md), 전체 기능 PRD | 양성훈 |
| [NFR-RELIABILITY-002](../01-requirements/non-functional-requirements.md#nfr-reliability-002-저장소-장애-및-재시도-통제) | 저장소 장애 및 재시도 통제 | 예 | [PRODUCT-001](prd/00-product-overview.md), 전체 기능 PRD | 각 Workstream 담당자 |
| [NFR-RELIABILITY-003](../01-requirements/non-functional-requirements.md#nfr-reliability-003-사용자-오류-메시지와-기능-분리) | 사용자 오류 메시지와 기능 분리 | 예 | 전체 기능 PRD | 각 Workstream 담당자 |
| [NFR-AVAILABILITY-001](../01-requirements/non-functional-requirements.md#nfr-availability-001-상태-확인과-장애-구분) | 상태 확인과 장애 구분 | 예 | [PRODUCT-001](prd/00-product-overview.md) | 이우람 |
| [NFR-AVAILABILITY-002](../01-requirements/non-functional-requirements.md#nfr-availability-002-초기-운영-배포-가용성과-수동-복구) | 초기 운영 배포 가용성과 수동 복구 | 운영 배포 | [PRODUCT-001](prd/00-product-overview.md) | 이우람 |
| [NFR-EXTERNAL-001](../01-requirements/non-functional-requirements.md#nfr-external-001-영상-원본과-외부-링크-분리) | 영상 원본과 외부 링크 분리 | 아니요 | [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회), [ADMIN-001](prd/admin/admin-data-management.md) | 박진영 |
| [NFR-EXTERNAL-002](../01-requirements/non-functional-requirements.md#nfr-external-002-외부-호출-실패와-변경-격리) | 외부 호출 실패와 변경 격리 | 아니요 | [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회), [DISCOVERY-002](prd/discovery/creator-discovery.md), [ADMIN-001](prd/admin/admin-data-management.md) | 박진영 |
| [NFR-EXTERNAL-003](../01-requirements/non-functional-requirements.md#nfr-external-003-링크-검증과-외부-인증정보) | 링크 검증과 외부 인증정보 | 아니요 | [ADMIN-001](prd/admin/admin-data-management.md) | 김인안 |
| [NFR-OBSERVABILITY-001](../01-requirements/non-functional-requirements.md#nfr-observability-001-요청-추적과-오류-분류) | 요청 추적과 오류 분류 | 예 | [PRODUCT-001](prd/00-product-overview.md), 전체 기능 PRD | 이우람 |
| [NFR-OBSERVABILITY-002](../01-requirements/non-functional-requirements.md#nfr-observability-002-운영-지표와-생명주기-기록) | 운영 지표와 생명주기 기록 | 예 | [PRODUCT-001](prd/00-product-overview.md), 전체 기능 PRD | 이우람 |
| [NFR-OBSERVABILITY-003](../01-requirements/non-functional-requirements.md#nfr-observability-003-로그-품질과-민감정보-차단) | 로그 품질과 민감정보 차단 | 예 | [PRODUCT-001](prd/00-product-overview.md), 전체 기능 PRD | 이우람 |
| [NFR-COMPATIBILITY-001](../01-requirements/non-functional-requirements.md#nfr-compatibility-001-웹모바일-브라우저-호환성) | 웹·모바일 브라우저 호환성 | 예 | [PRODUCT-001](prd/00-product-overview.md), 전체 조회 PRD | 각 Workstream 담당자 |
| [NFR-COMPATIBILITY-002](../01-requirements/non-functional-requirements.md#nfr-compatibility-002-응답-형식과-문자-처리) | 응답 형식과 문자 처리 | 예 | [PRODUCT-001](prd/00-product-overview.md), 전체 기능 PRD | 양성훈 |
| [NFR-COMPATIBILITY-003](../01-requirements/non-functional-requirements.md#nfr-compatibility-003-모바일-응답-크기) | 모바일 응답 크기 | 아니요 | 전체 조회 PRD | 각 조회 Workstream 담당자 |
| [NFR-TEST-001](../01-requirements/non-functional-requirements.md#nfr-test-001-자동화-테스트-계층) | 자동화 테스트 계층 | 예 | 전체 기능 PRD | 각 Workstream 담당자 |
| [NFR-TEST-002](../01-requirements/non-functional-requirements.md#nfr-test-002-변경외부-의존성성능-검증) | 변경·외부 의존성·성능 검증 | 예 | 전체 기능 PRD | 각 Workstream 담당자 |
| [NFR-TEST-003](../01-requirements/non-functional-requirements.md#nfr-test-003-배포-품질-게이트) | 배포 품질 게이트 | 예 | [PRODUCT-001](prd/00-product-overview.md), 전체 기능 PRD | 이우람 |
| [NFR-DEPLOYMENT-001](../01-requirements/non-functional-requirements.md#nfr-deployment-001-재현-가능한-빌드와-환경-분리) | 재현 가능한 빌드와 환경 분리 | 예 | [PRODUCT-001](prd/00-product-overview.md) | 이우람 |
| [NFR-DEPLOYMENT-002](../01-requirements/non-functional-requirements.md#nfr-deployment-002-배포-전후-검증) | 배포 전후 검증 | 예 | [PRODUCT-001](prd/00-product-overview.md) | 이우람 |
| [NFR-DEPLOYMENT-003](../01-requirements/non-functional-requirements.md#nfr-deployment-003-버전-추적과-복구-절차) | 버전 추적과 복구 절차 | 예 | [PRODUCT-001](prd/00-product-overview.md) | 이우람 |
| [NFR-DEPLOYMENT-004](../01-requirements/non-functional-requirements.md#nfr-deployment-004-단계별-실행-및-초기-운영-배포-복잡도-제한) | 단계별 실행 및 초기 운영 배포 복잡도 제한 | 예 | [PRODUCT-001](prd/00-product-overview.md) | 이우람 |
| [NFR-MAINTAINABILITY-001](../01-requirements/non-functional-requirements.md#nfr-maintainability-001-책임과-의존성-경계) | 책임과 의존성 경계 | 예 | [PRODUCT-001](prd/00-product-overview.md), 전체 기능 PRD | 각 Workstream 담당자 |
| [NFR-MAINTAINABILITY-002](../01-requirements/non-functional-requirements.md#nfr-maintainability-002-공통-정책과-규칙-배치) | 공통 정책과 규칙 배치 | 예 | [PRODUCT-001](prd/00-product-overview.md), 전체 기능 PRD | 각 Workstream 담당자 |
| [NFR-MAINTAINABILITY-003](../01-requirements/non-functional-requirements.md#nfr-maintainability-003-추적성과-운영-복잡도) | 추적성과 운영 복잡도 | 예 | [PRODUCT-001](prd/00-product-overview.md), 전체 기능 PRD | 각 Workstream 담당자 |
| [NFR-PRIVACY-001](../01-requirements/non-functional-requirements.md#nfr-privacy-001-mvp-개인정보-최소화) | MVP 개인정보 최소화 | 예 | [PRODUCT-001](prd/00-product-overview.md), 전체 기능 PRD | 각 Workstream 담당자 |
| [NFR-PRIVACY-002](../01-requirements/non-functional-requirements.md#nfr-privacy-002-인증정보와-외부-키-보호) | 인증정보와 외부 키 보호 | 예 | [PRODUCT-001](prd/00-product-overview.md), [ADMIN-001](prd/admin/admin-data-management.md) | 이우람 |
| [NFR-PRIVACY-003](../01-requirements/non-functional-requirements.md#nfr-privacy-003-회원-개인정보-최소-수집과-생명주기) | 회원 개인정보 최소 수집과 생명주기 | 예 | [PRD-ACCOUNT-001](prd/account/member-authentication.md), [PRD-PERSONAL-001](prd/personal/personal-restaurant-management.md) | WS-05·WS-06, 박진영 리뷰 |
| [NFR-PRIVACY-004](../01-requirements/non-functional-requirements.md#nfr-privacy-004-위치와-행동-데이터-최소화) | 위치와 행동 데이터 최소화 | 예 | [PRD-PERSONAL-001](prd/personal/personal-restaurant-management.md), [PRD-DISCOVERY-003](prd/discovery/map-discovery.md) | WS-06·WS-07, 김인안 리뷰 |
| [NFR-PERFORMANCE-006](../01-requirements/non-functional-requirements.md#nfr-performance-006-2차-확장-공개-조회와-인기-집계-성능) | 2차 확장 공개 조회와 인기 집계 성능 | 아니요 | [PRD-DISCOVERY-004](prd/discovery/popular-restaurants.md), [PRD-CURATION-001](prd/curation/admin-curation.md) | WS-10 양성훈, WS-11 김인안 |
| [NFR-SECURITY-006](../01-requirements/non-functional-requirements.md#nfr-security-006-사용자-입력과-제보신고-남용-방지) | 사용자 입력과 제보·신고 남용 방지 | 아니요 | [PRD-PARTICIPATION-001](prd/participation/user-submission-report.md) | WS-12 김인안 |
| [NFR-INTEGRITY-005](../01-requirements/non-functional-requirements.md#nfr-integrity-005-처리-상태와-알림-원자성) | 처리 상태와 알림 원자성 | 아니요 | [PRD-PARTICIPATION-001](prd/participation/user-submission-report.md), [PRD-NOTIFICATION-001](prd/notification/user-notification.md) | WS-12 김인안·WS-13 이우람 |
| [NFR-RELIABILITY-004](../01-requirements/non-functional-requirements.md#nfr-reliability-004-실시간-집계와-서비스-내-알림-복구-경계) | 실시간 집계와 서비스 내 알림 복구 경계 | 아니요 | [PRD-DISCOVERY-004](prd/discovery/popular-restaurants.md), [PRD-NOTIFICATION-001](prd/notification/user-notification.md) | WS-10 양성훈·WS-13 이우람 |
| [NFR-OBSERVABILITY-004](../01-requirements/non-functional-requirements.md#nfr-observability-004-관리자-검토-감사-이력) | 관리자 검토 감사 이력 | 아니요 | [PRD-CURATION-001](prd/curation/admin-curation.md), [PRD-PARTICIPATION-001](prd/participation/user-submission-report.md) | WS-11·WS-12 김인안 |
| [NFR-PRIVACY-005](../01-requirements/non-functional-requirements.md#nfr-privacy-005-2차-확장-개인정보-보존과-회원-탈퇴) | 2차 확장 개인정보 보존과 회원 탈퇴 | 아니요 | [PRD-COLLECTION-001](prd/personal/personal-collection.md), [PRD-PARTICIPATION-001](prd/participation/user-submission-report.md), [PRD-NOTIFICATION-001](prd/notification/user-notification.md) | WS-09 박진영·WS-12 김인안·WS-13 이우람 |
| [NFR-TEST-005](../01-requirements/non-functional-requirements.md#nfr-test-005-2차-확장-보안정합성성능-검증) | 2차 확장 보안·정합성·성능 검증 | 예 | 2차 확장 기능 PRD 전체 | WS-09~WS-13 담당자 |
| [NFR-ACCURACY-001](../01-requirements/non-functional-requirements.md#nfr-accuracy-001-자연어-검색-정확도와-평가-데이터) | 자연어 검색 정확도와 평가 데이터 | 아니요 | [PRD-DISCOVERY-005](prd/discovery/natural-language-restaurant-discovery.md) | 양성훈, 박진영(품질 검증) |
| [NFR-ACCURACY-002](../01-requirements/non-functional-requirements.md#nfr-accuracy-002-ai-추출-정확도재현율자동-등록-정밀도) | AI 추출 정확도·재현율·자동 등록 정밀도 | 아니요 | [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md) | 김인안, 박진영(품질 검증) |
| [NFR-INTEGRITY-006](../01-requirements/non-functional-requirements.md#nfr-integrity-006-ai-환각과-잘못된-장소-연결-방지) | AI 환각과 잘못된 장소 연결 방지 | 아니요 | [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md) | 김인안, 박진영(품질 검증) |
| [NFR-SECURITY-007](../01-requirements/non-functional-requirements.md#nfr-security-007-prompt-injection과-악성-ai-입력-방어) | Prompt Injection과 악성 AI 입력 방어 | 아니요 | [PRD-DISCOVERY-005](prd/discovery/natural-language-restaurant-discovery.md), [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md) | 양성훈·김인안, 박진영(평가 검증) |
| [NFR-PRIVACY-006](../01-requirements/non-functional-requirements.md#nfr-privacy-006-ai-입력저작권자막-보존-경계) | AI 입력·저작권·자막 보존 경계 | 예 | 3차 확장 기능 PRD 전체 | WS-14~WS-16 담당자, 김인안(개인정보 검토) |
| [NFR-COST-001](../01-requirements/non-functional-requirements.md#nfr-cost-001-ai임베딩mobility-호출-비용-상한) | AI·임베딩·Mobility 호출 비용 상한 | 예 | 3차 확장 기능 PRD 전체 | 김인안·이우람, 박진영(출시 게이트) |
| [NFR-EXTERNAL-005](../01-requirements/non-functional-requirements.md#nfr-external-005-ai와-mobility-timeoutrate-limit재시도) | AI와 Mobility timeout·rate limit·재시도 | 아니요 | [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md), [PRD-DISCOVERY-006](prd/discovery/restaurant-course-recommendation.md) | 김인안·이우람, 박진영(검증) |
| [NFR-PERFORMANCE-007](../01-requirements/non-functional-requirements.md#nfr-performance-007-자연어-검색과-경로-응답-시간) | 자연어 검색과 경로 응답 시간 | 예 | 3차 확장 기능 PRD 전체 | 양성훈·이우람, 박진영(성능 검증) |
| [NFR-RELIABILITY-005](../01-requirements/non-functional-requirements.md#nfr-reliability-005-ai-비동기-작업-복구) | AI 비동기 작업 복구 | 아니요 | [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md) | 김인안, 이우람(인프라 리뷰) |
| [NFR-AVAILABILITY-003](../01-requirements/non-functional-requirements.md#nfr-availability-003-ai-모델외부-api-장애-격리) | AI 모델·외부 API 장애 격리 | 예 | 3차 확장 기능 PRD 전체 | 김인안·이우람, 박진영(출시 게이트) |
| [NFR-OBSERVABILITY-005](../01-requirements/non-functional-requirements.md#nfr-observability-005-ai검색경로-로그-민감정보-차단) | AI·검색·경로 로그 민감정보 차단 | 예 | 3차 확장 기능 PRD 전체 | 이우람, 김인안(민감정보 검토) |
| [NFR-TEST-006](../01-requirements/non-functional-requirements.md#nfr-test-006-3차-확장-품질과-완료-게이트) | 3차 확장 품질과 완료 게이트 | 예 | 3차 확장 기능 PRD 전체 | 박진영, WS-14~WS-16 담당자 |

공통 NFR의 주 문서는 [PRD-PRODUCT-001](prd/00-product-overview.md)이다. `전체 조회 PRD`는 [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [DISCOVERY-002](prd/discovery/creator-discovery.md)와 [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회)을 뜻한다. `전체 기능 PRD`는 MVP 기능 PRD를, `2차 확장 기능 PRD 전체`는 3.3절의 다섯 PRD를, `3차 확장 기능 PRD 전체`는 3.5절의 세 PRD를 뜻한다.

## 6. Workstream → PRD 매핑

| Workstream | 주 PRD | 제공 계약 | 의존 PRD |
|---|---|---|---|
| [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) 맛집 탐색 | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | 최종 검색·필터·정렬·페이지 목록 | [DISCOVERY-002](prd/discovery/creator-discovery.md), [ADMIN-001](prd/admin/admin-data-management.md) |
| [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) 맛집 상세 및 콘텐츠 조회 | [PRD-DETAIL-001](prd/detail/restaurant-detail.md) | 다중 영역 상세 표시 조합 | [DISCOVERY-002](prd/discovery/creator-discovery.md), [ADMIN-001](prd/admin/admin-data-management.md) |
| [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) 유튜버 기반 탐색 | [PRD-DISCOVERY-002](prd/discovery/creator-discovery.md) | 유효 방문 관계 기반 맛집 판정 | [ADMIN-001](prd/admin/admin-data-management.md) |
| [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 관리자 데이터 등록 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | 검증된 기본 데이터와 관계 | 없음 |

### 6.1 2차 확장 Workstream

| Workstream | 주 PRD | 제공 계약 | 의존 PRD |
|---|---|---|---|
| [WS-09](../02-analysis/second-expansion-workstreams.md#4-ws-09-개인-컬렉션) | [PRD-COLLECTION-001](prd/personal/personal-collection.md) | 개인 컬렉션 소유권·구성 | [PRD-ACCOUNT-001](prd/account/member-authentication.md), [PRD-DETAIL-001](prd/detail/restaurant-detail.md) |
| [WS-10](../02-analysis/second-expansion-workstreams.md#5-ws-10-인기-맛집) | [PRD-DISCOVERY-004](prd/discovery/popular-restaurants.md) | 현재 찜 기반 인기 조회 | [PRD-PERSONAL-001](prd/personal/personal-restaurant-management.md) |
| [WS-11](../02-analysis/second-expansion-workstreams.md#6-ws-11-관리자-큐레이션) | [PRD-CURATION-001](prd/curation/admin-curation.md) | 큐레이션 편집·게시 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) |
| [WS-12](../02-analysis/second-expansion-workstreams.md#7-ws-12-제보신고-검토) | [PRD-PARTICIPATION-001](prd/participation/user-submission-report.md) | 접수·검토·처리 상태 | [PRD-ACCOUNT-001](prd/account/member-authentication.md), [PRD-ADMIN-001](prd/admin/admin-data-management.md), [PRD-NOTIFICATION-001](prd/notification/user-notification.md) |
| [WS-13](../02-analysis/second-expansion-workstreams.md#8-ws-13-사용자-알림) | [PRD-NOTIFICATION-001](prd/notification/user-notification.md) | 처리 상태 알림·읽음 | [PRD-PARTICIPATION-001](prd/participation/user-submission-report.md) |

### 6.2 3차 확장 Workstream

| Workstream | 주 PRD | 제공 계약 | 의존 PRD |
|---|---|---|---|
| [WS-14](../02-analysis/third-expansion-workstreams.md#5-ws-14-자연어-맛집-탐색) | [PRD-DISCOVERY-005](prd/discovery/natural-language-restaurant-discovery.md) | 자연어 조건 해석·적용 조건 요약 | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) |
| [WS-15](../02-analysis/third-expansion-workstreams.md#6-ws-15-ai-영상-정보-추출) | [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md) | 비동기 추출·자동 등록·예외 보정 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) |
| [WS-16](../02-analysis/third-expansion-workstreams.md#7-ws-16-맛집-코스-추천) | [PRD-DISCOVERY-006](prd/discovery/restaurant-course-recommendation.md) | 선택 맛집의 자동차 순서·경로 | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) |

교차 품질 검증과 최종 출시 증거는 제품 기능 Workstream과 분리한 [QUALITY-EVAL](../02-analysis/third-expansion-workstreams.md#8-quality-eval-교차-품질-트랙)이 소유한다.

## 7. 담당자 → PRD 매핑

| 담당자 | 최종 책임 PRD | 협업·리뷰 PRD |
|---|---|---|
| 양성훈 | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | [DISCOVERY-002](prd/discovery/creator-discovery.md) 리뷰, [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) 식별자, [ADMIN-001](prd/admin/admin-data-management.md) 목록 반영 |
| 박진영 | [PRD-DETAIL-001](prd/detail/restaurant-detail.md) | [ADMIN-001](prd/admin/admin-data-management.md) 리뷰, [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) 표시 계약, [DISCOVERY-002](prd/discovery/creator-discovery.md) 관계 정책 |
| 이우람 | [PRD-DISCOVERY-002](prd/discovery/creator-discovery.md) | [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) 리뷰, [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) 관계 계약, [ADMIN-001](prd/admin/admin-data-management.md) 인증·Visit 계약 |
| 김인안 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) 리뷰, [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색)·[DISCOVERY-002](prd/discovery/creator-discovery.md) 등록 반영 |

### 7.1 2차 확장 담당자

| 담당자 | 최종 책임 PRD | 협업·리뷰 PRD |
|---|---|---|
| 박진영 | [PRD-COLLECTION-001](prd/personal/personal-collection.md) | 김인안 기본 리뷰, 양성훈 공개 Restaurant 계약 리뷰 |
| 양성훈 | [PRD-DISCOVERY-004](prd/discovery/popular-restaurants.md) | 박진영 기본 리뷰 |
| 김인안 | [PRD-CURATION-001](prd/curation/admin-curation.md), [PRD-PARTICIPATION-001](prd/participation/user-submission-report.md) | 양성훈·이우람 기본 리뷰 |
| 이우람 | [PRD-NOTIFICATION-001](prd/notification/user-notification.md) | 김인안 기본 리뷰 |

### 7.2 3차 확장 담당자

| 담당자 | 최종 책임 PRD·트랙 | 협업·리뷰 책임 |
|---|---|---|
| 양성훈 | [PRD-DISCOVERY-005](prd/discovery/natural-language-restaurant-discovery.md), WS-14 | WS-16 기본 리뷰, 자연어 골든 데이터 정답 작성 |
| 김인안 | [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md), WS-15 | 자동 등록 예외 보정·개인정보 경계, AI 추출 정답 작성 |
| 이우람 | [PRD-DISCOVERY-006](prd/discovery/restaurant-course-recommendation.md), WS-16 | WS-14 기본 리뷰, 외부 연동·비동기 복구 검토 |
| 박진영 | [QUALITY-EVAL](../02-analysis/third-expansion-workstreams.md#8-quality-eval-교차-품질-트랙) | WS-15 기본 리뷰, 골든 데이터·성능·출시 게이트 독립 검증 |

## 8. 미매핑 항목 검토

- 주 PRD 없는 MVP·1차·2차·3차 확장 기능 요구사항: 없음 (3.2절·3.4절·3.6절에서 확장 FR을 확인)
- 여러 주 PRD에 중복 배정된 요구사항: 없음
- 담당자 또는 Workstream 없는 기능 PRD: 없음. 3차 확장 PRD는 WS-14~WS-16과 담당자·기본 리뷰어가 배정됐다.
- 기능 요구사항 없이 생성된 기능 PRD: 없음
- MVP 제외 기능을 구현 대상으로 포함한 PRD: 없음. [NFR-PRIVACY-003](../01-requirements/non-functional-requirements.md#nfr-privacy-003-회원-개인정보-최소-수집과-생명주기)과 [NFR-PRIVACY-004](../01-requirements/non-functional-requirements.md#nfr-privacy-004-위치와-행동-데이터-최소화)은 1차 확장 회원·개인화·지도 범위의 완료 기준으로 추적한다.
- 상위 제품 PRD와 기능 PRD 범위 충돌: 없음
- 상세 결정: 인증은 Spring Security JWT·Redis Refresh Token, 상세 조합은 `com.masiton.orchestration.application.query`, 외부 API timeout은 연결 2초·전체 응답 5초를 사용하고 성능 수치는 비기능 요구사항을 따른다.

## 9. 변경 영향 추적

### 제품 전체 범위 변경

1. [프로젝트 범위](../00-overview/scope.md)를 수정한다.
2. [제품 개요 PRD](prd/00-product-overview.md)를 수정한다.
3. 영향 기능 PRD를 수정한다.
4. 기능 요구사항과 Workstream을 수정한다.
5. 이 문서의 모든 관련 매핑을 갱신한다.
6. 역할과 일정 영향을 검토한다.

### 기능 내부 변경

1. 해당 기능 PRD를 수정한다.
2. [기능 요구사항](../01-requirements/functional-requirements.md)과 [비즈니스 규칙](../01-requirements/business-rules.md)을 검토한다.
3. API 계약과 테스트 영향을 확인한다.
4. 이 문서의 요구사항·규칙·NFR 매핑을 갱신한다.
5. 보조·영향 PRD 담당자의 리뷰를 받는다.

### 기술 구현 변경

사용자 동작과 제품 범위가 변하지 않으면 PRD는 수정하지 않는다. API 계약, 데이터 모델, 아키텍처 또는 ADR만 수정하며, 사용자 동작이 달라질 때 PRD와 이 문서를 함께 갱신한다.

## 10. 2차 확장 종단 추적 검증

아래 표는 제품 관점의 최종 수직 추적 기준선이다. 요구사항별 상세 테스트는 [2차 확장 테스트 추적표](../08-planning/second-expansion-test-matrix.md), 실행 순서는 [2차 확장 구현 계획](../08-planning/expansion-2-implementation-plan.md), 완료 판정은 [2차 확장 Task 분해](../08-planning/expansion-2-task-breakdown.md)를 따른다.

| Scope 기능 | FR·BR·NFR | 주 PRD | API | 데이터 | ADR 또는 명시적 보류 | WS | 테스트 | E2 Task |
|---|---|---|---|---|---|---|---|---|
| 개인 컬렉션 | `FR-COLLECTION-001~006`, `BR-COLLECTION-001~005`, `NFR-PRIVACY-005` | [PRD-COLLECTION-001](prd/personal/personal-collection.md) | [개인 컬렉션 API](../05-specs/api/personal/personal-collection-api.md) | `personal_collection`, `collection_restaurant`, `idempotency_record` | 기존 인증·PostgreSQL·Flyway ADR 적용; 공유·직접 정렬·이미지는 범위 제외 | [WS-09](../02-analysis/second-expansion-workstreams.md#4-ws-09-개인-컬렉션) | [`TST-E2-COL-001`](../08-planning/second-expansion-test-matrix.md) | [`E2-T01`](../08-planning/expansion-2-task-breakdown.md)~`E2-T03`, `E2-T13`, `E2-T15` |
| 인기 맛집 | `FR-POPULAR-001`, `BR-POPULAR-001~003`, `NFR-PERFORMANCE-006`, `NFR-RELIABILITY-004` | [PRD-DISCOVERY-004](prd/discovery/popular-restaurants.md) | [인기 맛집 API](../05-specs/api/discovery/popular-restaurant-api.md) | 기존 `favorite` 요청 시 집계, 결과 비저장 | [ADR-DATA-011](../07-adr/data/data-011-popular-restaurant-request-time-aggregation.md); Snapshot·Batch·Redis는 명시적 비활성 | [WS-10](../02-analysis/second-expansion-workstreams.md#5-ws-10-인기-맛집) | [`TST-E2-POP-001`](../08-planning/second-expansion-test-matrix.md), `TST-E2-PERF-001` | [`E2-T01`](../08-planning/expansion-2-task-breakdown.md), `E2-T02`, `E2-T04`, `E2-T05`, `E2-T13`, `E2-T15` |
| 관리자 큐레이션 | `FR-CURATION-001~004`, `BR-CURATION-001~004`, `NFR-PERFORMANCE-006`, `NFR-OBSERVABILITY-004` | [PRD-CURATION-001](prd/curation/admin-curation.md) | [큐레이션 API](../05-specs/api/curation/curation-api.md) | `curation`, `curation_restaurant` | 기존 관리자 인증·PostgreSQL ADR 적용; 예약 게시·추천·이미지는 범위 제외 | [WS-11](../02-analysis/second-expansion-workstreams.md#6-ws-11-관리자-큐레이션) | [`TST-E2-CUR-001`](../08-planning/second-expansion-test-matrix.md), `TST-E2-PERF-001` | [`E2-T01`](../08-planning/expansion-2-task-breakdown.md), `E2-T02`, `E2-T06`, `E2-T07`, `E2-T13`, `E2-T15` |
| 사용자 제보 | `FR-SUBMISSION-001~003`, `BR-SUBMISSION-001~004`, `NFR-SECURITY-006`, `NFR-OBSERVABILITY-004`, `NFR-PRIVACY-005` | [PRD-PARTICIPATION-001](prd/participation/user-submission-report.md) | [제보·신고 API](../05-specs/api/participation/submission-report-api.md) | `submission`, `moderation_history`, `idempotency_record` | [ADR-DATA-012](../07-adr/data/data-012-second-expansion-retention-cleanup.md), 상태 알림은 [ADR-NOTIFY-002](../07-adr/integration/notify-002-in-app-notification-reliability.md) | [WS-12](../02-analysis/second-expansion-workstreams.md#7-ws-12-제보신고-검토) | [`TST-E2-SUB-001`](../08-planning/second-expansion-test-matrix.md), `TST-E2-ATOMIC-001`, `TST-E2-LIFE-001` | [`E2-T01`](../08-planning/expansion-2-task-breakdown.md), `E2-T02`, `E2-T08`, `E2-T09`, `E2-T11`, `E2-T13`, `E2-T15` |
| 사용자 신고 | `FR-REPORT-001~003`, `BR-REPORT-001~004`, `NFR-SECURITY-006`, `NFR-OBSERVABILITY-004`, `NFR-PRIVACY-005` | [PRD-PARTICIPATION-001](prd/participation/user-submission-report.md) | [제보·신고 API](../05-specs/api/participation/submission-report-api.md) | `report`, `moderation_history`, `idempotency_record` | [ADR-DATA-012](../07-adr/data/data-012-second-expansion-retention-cleanup.md), 상태 알림은 [ADR-NOTIFY-002](../07-adr/integration/notify-002-in-app-notification-reliability.md) | [WS-12](../02-analysis/second-expansion-workstreams.md#7-ws-12-제보신고-검토) | [`TST-E2-REP-001`](../08-planning/second-expansion-test-matrix.md), `TST-E2-ATOMIC-001`, `TST-E2-LIFE-001` | [`E2-T01`](../08-planning/expansion-2-task-breakdown.md), `E2-T02`, `E2-T08`, `E2-T09`, `E2-T11`, `E2-T13`, `E2-T15` |
| 서비스 내 사용자 알림 | `FR-NOTIFICATION-001~004`, `BR-NOTIFICATION-001~004`, `NFR-INTEGRITY-005`, `NFR-RELIABILITY-004`, `NFR-PRIVACY-005` | [PRD-NOTIFICATION-001](prd/notification/user-notification.md) | [알림 API](../05-specs/api/notification/notification-api.md) | `notification`; `NotificationPreference`·`DeviceToken` 비저장 | [ADR-NOTIFY-002](../07-adr/integration/notify-002-in-app-notification-reliability.md), [ADR-DATA-012](../07-adr/data/data-012-second-expansion-retention-cleanup.md); FCM은 [ADR-NOTIFY-001](../07-adr/adr-backlog.md#adr-notify-001-fcm-푸시-알림) Post-MVP | [WS-13](../02-analysis/second-expansion-workstreams.md#8-ws-13-사용자-알림) | [`TST-E2-NOT-001`](../08-planning/second-expansion-test-matrix.md), `TST-E2-ATOMIC-001`, `TST-E2-LIFE-001` | [`E2-T01`](../08-planning/expansion-2-task-breakdown.md), `E2-T02`, `E2-T10`, `E2-T11`, `E2-T13`, `E2-T14`, `E2-T15` |

공통 `NFR-TEST-005`는 모든 행에 적용하며 `TST-E2-SEC-001`, `TST-E2-E2E-001`과 `E2-T13`, `E2-T14`, `E2-T15`에서 최종 판정한다. 푸시용 `E2-T12`는 현재 생성하지 않는다. 따라서 2차 확장 21개 FR은 모두 `Scope → 요구사항 → PRD → API → 데이터 → ADR/보류 → Workstream → 테스트 → E2 Task` 경로를 가진다.

## 11. 3차 확장 제품 추적 기준선

| Scope 기능 | FR·BR·주요 NFR | 주 PRD | 사용자 흐름 | 와이어프레임 | 평가·테스트 | API·데이터·ADR·Task |
|---|---|---|---|---|---|---|
| 자연어 맛집 탐색 | `FR-NLSEARCH-001~004`, `BR-NLSEARCH-001~003`, `NFR-ACCURACY-001`, `NFR-PERFORMANCE-007` | [PRD-DISCOVERY-005](prd/discovery/natural-language-restaurant-discovery.md) | [3차 확장 사용자 흐름 2절](user-flows/third-expansion-user-flows.md#2-자연어-맛집-탐색) | [3차 확장 와이어프레임 3절](wireframes/third-expansion-wireframes.md#3-자연어-맛집-탐색) | [`EVAL-NL-*`](../08-planning/third-expansion-evaluation-strategy.md#31-자연어-맛집-탐색), [`TST-E3-NL-*`](../08-planning/third-expansion-test-matrix.md), `E3-T01~02`, `E3-T11~13` | [자연어 API](../05-specs/api/discovery/natural-language-restaurant-discovery-api.md), [해석 ADR](../07-adr/architecture/arch-005-natural-language-filter-interpretation.md), [`E3-T01~02`, `E3-T11~13`](../08-planning/third-expansion-task-breakdown.md) |
| AI 영상 정보 추출 | `FR-AIEXTRACT-001~007`, `BR-AIEXTRACT-001~011`, `NFR-ACCURACY-002`, `NFR-INTEGRITY-006`, `NFR-RELIABILITY-005` | [PRD-ADMIN-002](prd/admin/ai-video-information-extraction.md) | [3차 확장 사용자 흐름 3절](user-flows/third-expansion-user-flows.md#3-ai-영상-정보-추출과-자동-등록예외-보정) | [3차 확장 와이어프레임 4절](wireframes/third-expansion-wireframes.md#4-ai-영상-추출-자동-등록예외-보정) | [`EVAL-AI-*`](../08-planning/third-expansion-evaluation-strategy.md#32-ai-영상-정보-추출), [`TST-E3-AI-*`](../08-planning/third-expansion-test-matrix.md), [120건 계약 자산·dry-run·HOLD 기록](../08-planning/third-expansion-ai-evaluation-result.md), `E3-T03~08`, `E3-T11~13` | [AI API](../05-specs/api/admin/ai-video-extraction-api.md), [AI 데이터 계약](../05-specs/data/third-expansion-ai-video-data-contract.md), [AI·비동기 ADR](../07-adr/integration/ai-001-video-extraction-candidate-boundary.md), [`E3-T03~08`, `E3-T11~13`](../08-planning/third-expansion-task-breakdown.md) |
| 맛집 코스 추천 | `FR-COURSE-001~004`, `BR-COURSE-001~005`, `NFR-EXTERNAL-005`, `NFR-PERFORMANCE-007` | [PRD-DISCOVERY-006](prd/discovery/restaurant-course-recommendation.md) | [3차 확장 사용자 흐름 4절](user-flows/third-expansion-user-flows.md#4-맛집-코스-추천) | [3차 확장 와이어프레임 5절](wireframes/third-expansion-wireframes.md#5-맛집-코스-추천) | [`EVAL-COURSE-*`](../08-planning/third-expansion-evaluation-strategy.md#33-맛집-코스-추천), [`TST-E3-COURSE-*`](../08-planning/third-expansion-test-matrix.md), `E3-T09~13` | [코스 API](../05-specs/api/discovery/restaurant-course-recommendation-api.md), [Mobility ADR](../07-adr/integration/route-001-kakao-mobility-course-routing.md), [`E3-T09~13`](../08-planning/third-expansion-task-breakdown.md) |

세 기능은 `Scope → 요구사항 → PRD → 사용자 흐름 → 와이어프레임 → API·데이터·ADR → 평가·테스트 → E3 Task`까지 구조적으로 연결됐다. 실제 계약 테스트·평가 실행·브라우저 인수·외부 계정 연결·부하 측정이 최종 완료 증거이며, 문서 승인 상태를 구현 완료로 해석하지 않는다.

### 11.1 E3-T13 최종 게이트 증거

현재 기준 커밋의 자동화 회귀·부하 시나리오 자산·운영 및 평가 보류 사유는 [E3-T13 최종 게이트 판정](../08-planning/third-expansion-final-gate-result.md)에 기록한다. 같은 문서 1.1절 개정에 따라 제한 공개 범위의 활성화는 증거 수집을 위해 허용하고, 실제 Release 평가, 운영 계정·quota, 좌표 보강률, 브라우저·부하 결과가 연결되기 전까지 **일반 공개 판정은 `HOLD`**다.

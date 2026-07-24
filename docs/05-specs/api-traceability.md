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
  - ../04-product/prd/admin/admin-data-management.md
  - api/admin/authentication-api.md
  - api/admin/reference-data-api.md
  - api/admin/visit-registration-api.md
  - ../04-product/prd/00-product-overview.md
---

# 맛잇온 API 추적성

## 1. 문서 목적

1차 MVP의 PRD, 기능 요구사항, 비즈니스 규칙, NFR, Workstream과 담당자를 외부 API 계약에 연결한다. API로 노출되는 모든 MVP 기능 요구사항은 하나의 주 API를 가진다.

## 2. PRD → API 매핑

| PRD ID | 기능 PRD | 주 API 문서 | 관련 API ID | Workstream | 담당자 |
|---|---|---|---|---|---|
| [PRD-DISCOVERY-001](../04-product/prd/discovery/restaurant-discovery.md) | 맛집 탐색 | [api/discovery/restaurant-discovery-api.md](api/discovery/restaurant-discovery-api.md) | [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | 양성훈 |
| [PRD-DISCOVERY-002](../04-product/prd/discovery/creator-discovery.md) | 유튜버 기반 탐색 | [api/discovery/creator-discovery-api.md](api/discovery/creator-discovery-api.md) | [API-CREATOR-DISCOVERY-001](api/discovery/creator-discovery-api.md#api-creator-discovery-001-유튜버-필터-선택-목록), [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) | [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | 이우람 |
| [PRD-DETAIL-001](../04-product/prd/detail/restaurant-detail.md) | 맛집 상세 및 콘텐츠 조회 | [api/detail/restaurant-detail-api.md](api/detail/restaurant-detail-api.md) | [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | 박진영 |
| [PRD-ADMIN-001](../04-product/prd/admin/admin-data-management.md) | 관리자 데이터 등록 | [api/admin/authentication-api.md](api/admin/authentication-api.md), [api/admin/reference-data-api.md](api/admin/reference-data-api.md), [api/admin/visit-registration-api.md](api/admin/visit-registration-api.md) | [API-ADMIN-AUTH-001](api/admin/authentication-api.md#api-admin-auth-001-관리자-로그인)~[API-ADMIN-AUTH-003](api/admin/authentication-api.md#api-admin-auth-003-관리자-로그아웃), 기본 데이터 미리보기·생성 API, [API-ADMIN-VISIT-001](api/admin/visit-registration-api.md#api-admin-visit-001-방문-관계-등록) | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 김인안 |

[PRD-PRODUCT-001](../04-product/prd/00-product-overview.md)은 전체 제품 범위를 제공하며 하나의 주 API에만 매핑하지 않는다.

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
| [FR-ADMIN-001](../01-requirements/functional-requirements.md#fr-admin-001-관리자-등록-기능-접근) | 관리자 등록 접근 | [API-ADMIN-AUTH-001](api/admin/authentication-api.md#api-admin-auth-001-관리자-로그인) | [API-ADMIN-AUTH-002](api/admin/authentication-api.md#api-admin-auth-002-관리자-토큰-재발급)·[API-ADMIN-AUTH-003](api/admin/authentication-api.md#api-admin-auth-003-관리자-로그아웃), 나머지 모든 `/admin` API | 로그인·JWT 검증·Refresh 회전·권한 테스트 | 김인안 |
| [FR-ADMIN-002](../01-requirements/functional-requirements.md#fr-admin-002-맛집-정보-등록) | 맛집 등록 | [API-ADMIN-RESTAURANT-001](api/admin/reference-data-api.md#api-admin-restaurant-001-맛집-등록-확정) | [API-ADMIN-RESTAURANT-PREVIEW-001](api/admin/reference-data-api.md#api-admin-restaurant-preview-001-맛집-등록-검증-미리보기), [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 외부 확인·관리자 확정·중복·서울·조회 반영 테스트 | 김인안 |
| [FR-ADMIN-003](../01-requirements/functional-requirements.md#fr-admin-003-유튜버-정보-등록) | 유튜버 등록 | [API-ADMIN-CREATOR-001](api/admin/reference-data-api.md#api-admin-creator-001-유튜버-등록-확정) | [API-ADMIN-CREATOR-PREVIEW-001](api/admin/reference-data-api.md#api-admin-creator-preview-001-유튜버-등록-검증-미리보기), [API-CREATOR-DISCOVERY-001](api/discovery/creator-discovery-api.md#api-creator-discovery-001-유튜버-필터-선택-목록) | 외부 확인·관리자 확정·동일 채널·조회 반영 테스트 | 김인안 |
| [FR-ADMIN-004](../01-requirements/functional-requirements.md#fr-admin-004-영상-정보-등록) | 영상 등록 | [API-ADMIN-VIDEO-001](api/admin/reference-data-api.md#api-admin-video-001-영상-등록-확정) | [API-ADMIN-VIDEO-PREVIEW-001](api/admin/reference-data-api.md#api-admin-video-preview-001-영상-등록-검증-미리보기), [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 외부 확인·관리자 확정·동일 영상·원본 미저장 테스트 | 김인안 |
| [FR-VISIT-001](../01-requirements/functional-requirements.md#fr-visit-001-맛집유튜버영상-방문-관계-등록) | 방문 관계 등록 | [API-ADMIN-VISIT-001](api/admin/visit-registration-api.md#api-admin-visit-001-방문-관계-등록) | [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 참조·채널 일치·근거·중복·원자성 통합 테스트 | 김인안 |

API 직접 노출 없음: [BR-ADMIN-006](../01-requirements/business-rules.md#br-admin-006-잘못-등록된-데이터의-정정-원칙)의 정정 구현, 공개 상태 저장 구조, 동시성 보장 방식과 외부 동일성 식별값 저장 방식은 외부 API 기능이 아니라 후속 내부 설계다. 다만 그 결과는 공개 제외·중복 오류 계약으로 관찰된다.

## 4. 비즈니스 규칙 → API 매핑

| 규칙 ID | 규칙 | 적용 API | 요청 검증 | 응답 영향 | 담당자 |
|---|---|---|---|---|---|
| [BR-RESTAURANT-002](../01-requirements/business-rules.md#br-restaurant-002-영상과-독립된-맛집)·[BR-RESTAURANT-008](../01-requirements/business-rules.md#br-restaurant-008-맛집-공개-조건) | 영상 독립성과 공개 조건 | [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 없음 | 영상 없어도 노출, 비공개 제외 | 양성훈·박진영 |
| [BR-RESTAURANT-003](../01-requirements/business-rules.md#br-restaurant-003-맛집-최소-등록-정보)~[BR-RESTAURANT-007](../01-requirements/business-rules.md#br-restaurant-007-동일-상호의-지점-구분) | 최소 정보·카테고리·지역·중복·지점 | [API-ADMIN-RESTAURANT-PREVIEW-001](api/admin/reference-data-api.md#api-admin-restaurant-preview-001-맛집-등록-검증-미리보기), [API-ADMIN-RESTAURANT-001](api/admin/reference-data-api.md#api-admin-restaurant-001-맛집-등록-확정) | 필수값, 서울 주소, 단일 카테고리, 카카오 동일성 | 미리보기 판정, 201 또는 400·409 | 김인안 |
| [BR-CREATOR-001](../01-requirements/business-rules.md#br-creator-001-유튜버-정보의-의미)~[BR-CREATOR-005](../01-requirements/business-rules.md#br-creator-005-방문-관계의-유튜버-일치) | 채널 관리 단위·최소 정보·중복·표시·일치 | [API-ADMIN-CREATOR-001](api/admin/reference-data-api.md#api-admin-creator-001-유튜버-등록-확정), [API-CREATOR-DISCOVERY-001](api/discovery/creator-discovery-api.md#api-creator-discovery-001-유튜버-필터-선택-목록), [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회), [API-ADMIN-VISIT-001](api/admin/visit-registration-api.md#api-admin-visit-001-방문-관계-등록) | 채널 URL·동일성·게시 채널 일치 | 현재 채널명, 중복 제거, 409·422 | 이우람·김인안·박진영 |
| [BR-CREATOR-007](../01-requirements/business-rules.md#br-creator-007-채널-이용-불가-처리) | 이용 불가 채널 제외 | [API-CREATOR-DISCOVERY-001](api/discovery/creator-discovery-api.md#api-creator-discovery-001-유튜버-필터-선택-목록), [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 없음 | 유튜버·관계 제외, 맛집 기본 유지 | 이우람·박진영 |
| [BR-VIDEO-001](../01-requirements/business-rules.md#br-video-001-영상의-의미와-보관-범위)~[BR-VIDEO-006](../01-requirements/business-rules.md#br-video-006-게시일과-방문일의-구분) | 영상 최소 정보·동일성·관계·실제 방문·날짜 구분 | [API-ADMIN-VIDEO-001](api/admin/reference-data-api.md#api-admin-video-001-영상-등록-확정), [API-ADMIN-VISIT-001](api/admin/visit-registration-api.md#api-admin-visit-001-방문-관계-등록), [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 원본 URL, 중복, 실제 방문, 게시 채널 | 영상 필드, 409·422, 방문일 미노출 | 김인안·박진영 |
| [BR-VIDEO-007](../01-requirements/business-rules.md#br-video-007-외부-링크-장애의-격리)~[BR-VIDEO-009](../01-requirements/business-rules.md#br-video-009-영상-이용-불가-처리) | 링크 장애·표시 변경·이용 불가 | [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 없음 | 기본 상세 유지, 무효 영상 제외 | 박진영 |
| [BR-VISIT-001](../01-requirements/business-rules.md#br-visit-001-방문-관계의-구성)~[BR-VISIT-007](../01-requirements/business-rules.md#br-visit-007-등록-완료와-검증-상태) | 세 대상 관계·근거·중복·유효성·날짜·검증 | [API-ADMIN-VISIT-001](api/admin/visit-registration-api.md#api-admin-visit-001-방문-관계-등록), [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 세 참조·조합·근거·채널 일치 | 201·404·409·422, 공개 관계만 조회 | 김인안·이우람·박진영 |
| [BR-SEARCH-001](../01-requirements/business-rules.md#br-search-001-검색-대상과-일치-기준)~[BR-SEARCH-009](../01-requirements/business-rules.md#br-search-009-기본-정렬) | 검색·필터·고유성·빈 결과·페이지·정렬 | [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) | 쿼리 허용값과 단일 값 | AND 결과, 빈 목록, 안정 페이지 | 양성훈·이우람 |
| [BR-ADMIN-001](../01-requirements/business-rules.md#br-admin-001-관리자-권한-검증)~[BR-ADMIN-005](../01-requirements/business-rules.md#br-admin-005-mvp-관리-기능의-경계)·[BR-ADMIN-007](../01-requirements/business-rules.md#br-admin-007-동시-등록의-고유성)·[BR-ADMIN-008](../01-requirements/business-rules.md#br-admin-008-보류-요청의-처리) | 권한·검증·정합성·반영·MVP 경계·동시성·보류 | 인증 및 모든 관리자 등록 API | JWT·ADMIN 권한, 필수값, 미리보기, 확인 토큰, 중복 | 401·403·409 및 공개 조회 반영 | 김인안 |
| [BR-ADMIN-006](../01-requirements/business-rules.md#br-admin-006-잘못-등록된-데이터의-정정-원칙) | 잘못된 데이터 정정 | API 직접 노출 없음 | 수정·삭제 API 없음 | 비공개된 대상은 조회 제외 | 김인안 |
| [BR-PUBLICATION-001](../01-requirements/business-rules.md#br-publication-001-일반-사용자-공개-범위)~[BR-PUBLICATION-008](../01-requirements/business-rules.md#br-publication-008-상태-변경의-일관성) | 공개·비공개·삭제와 일관성 | 모든 공개 조회, 모든 등록 결과 | 관리자 등록 공개 정책 | 비공개·삭제 제외, 기본 맛집 유지 | 전체 Workstream |

## 5. NFR → API 검증 매핑

| NFR ID | 품질 요구사항 | 적용 API | 검증 방법 | 검증 책임 |
|---|---|---|---|---|
| [NFR-PERFORMANCE-001](../01-requirements/non-functional-requirements.md#nfr-performance-001-일반-조회-응답-시간)·[NFR-PERFORMANCE-002](../01-requirements/non-functional-requirements.md#nfr-performance-002-검색필터-조합-응답-시간)·[NFR-PERFORMANCE-004](../01-requirements/non-functional-requirements.md#nfr-performance-004-페이지-크기-및-조회량-제한) | 조회·조합 성능과 페이지 제한 | [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 대표 데이터 부하, 경계값·응답 크기 검사 | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)·[WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)·[WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) |
| [NFR-PERFORMANCE-003](../01-requirements/non-functional-requirements.md#nfr-performance-003-관리자-등록-응답-시간) | 관리자 등록 응답 | 모든 관리자 API | 외부 시간 분리 부하 테스트 | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |
| [NFR-SECURITY-001](../01-requirements/non-functional-requirements.md#nfr-security-001-공개-조회와-관리자-접근-통제) | 공개 조회·관리자 통제 | 모든 API, [API-ADMIN-AUTH-001](api/admin/authentication-api.md#api-admin-auth-001-관리자-로그인)~[API-ADMIN-AUTH-003](api/admin/authentication-api.md#api-admin-auth-003-관리자-로그아웃) | JWT 없음·만료·서명 오류·Refresh 재사용·권한 없음·정상 테스트 | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 및 공통 인증 담당 |
| [NFR-SECURITY-002](../01-requirements/non-functional-requirements.md#nfr-security-002-입력-및-웹-공격-방어)·[NFR-SECURITY-003](../01-requirements/non-functional-requirements.md#nfr-security-003-비밀정보와-오류-정보-보호) | 입력·비밀·오류 보호 | 모든 API | 악성 입력, 비밀·스택 노출 검사 | 각 담당자 |
| [NFR-INTEGRITY-001](../01-requirements/non-functional-requirements.md#nfr-integrity-001-참조-및-필수값-정합성)~[NFR-INTEGRITY-004](../01-requirements/non-functional-requirements.md#nfr-integrity-004-외부-링크와-내부-데이터-분리) | 참조·중복·원자성·외부 링크 분리 | 관리자 API, [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 통합·동시성·실패 주입 테스트 | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록), [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) |
| [NFR-RELIABILITY-001](../01-requirements/non-functional-requirements.md#nfr-reliability-001-오류-격리와-공통-오류-정책)·[NFR-RELIABILITY-003](../01-requirements/non-functional-requirements.md#nfr-reliability-003-사용자-오류-메시지와-기능-분리) | 공통 오류와 부분 실패 격리 | 모든 API, 특히 [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 오류 계약·제공자 장애 테스트 | 각 담당자, [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) |
| [NFR-EXTERNAL-001](../01-requirements/non-functional-requirements.md#nfr-external-001-영상-원본과-외부-링크-분리)~[NFR-EXTERNAL-003](../01-requirements/non-functional-requirements.md#nfr-external-003-링크-검증과-외부-인증정보) | 원본·외부 호출·링크 검증 분리 | [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회), 기본 데이터 등록 API | 외부 장애 모의·저장 자료·URL 검사 | [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)·[WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |
| [NFR-OBSERVABILITY-001](../01-requirements/non-functional-requirements.md#nfr-observability-001-요청-추적과-오류-분류)~[NFR-OBSERVABILITY-003](../01-requirements/non-functional-requirements.md#nfr-observability-003-로그-품질과-민감정보-차단) | 요청 추적·분류·민감정보 차단 | 모든 API | 로그 상관관계·표본 검사 | 공통 운영 담당·[WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |
| [NFR-COMPATIBILITY-002](../01-requirements/non-functional-requirements.md#nfr-compatibility-002-응답-형식과-문자-처리)·[NFR-COMPATIBILITY-003](../01-requirements/non-functional-requirements.md#nfr-compatibility-003-모바일-응답-크기) | UTF-8·일관 형식·모바일 크기 | 공개 조회 API | 계약·한글 왕복·최대 응답 검사 | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)·[WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) |
| [NFR-TEST-001](../01-requirements/non-functional-requirements.md#nfr-test-001-자동화-테스트-계층)~[NFR-TEST-003](../01-requirements/non-functional-requirements.md#nfr-test-003-배포-품질-게이트) | 자동화·변경·품질 게이트 | 모든 API | 요구사항 추적 계약·통합 테스트 | 전체 Workstream |
| [NFR-MAINTAINABILITY-001](../01-requirements/non-functional-requirements.md#nfr-maintainability-001-책임과-의존성-경계)·[NFR-MAINTAINABILITY-002](../01-requirements/non-functional-requirements.md#nfr-maintainability-002-공통-정책과-규칙-배치) | 책임·공통 정책 경계 | 탐색 API와 공통 계약 | 의존성·계약 중복 검사 | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)·[WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) |
| [NFR-PRIVACY-001](../01-requirements/non-functional-requirements.md#nfr-privacy-001-mvp-개인정보-최소화)·[NFR-PRIVACY-002](../01-requirements/non-functional-requirements.md#nfr-privacy-002-인증정보와-외부-키-보호) | 개인정보 최소화·비밀 보호 | 관리자 API | 필드·로그·설정 검사 | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |

## 6. Workstream → API 매핑

| Workstream | 소유 API | 협업 경계 |
|---|---|---|
| [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) | [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)의 유튜버 유효 맛집 판정을 최종 목록과 조합 |
| [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)과 관계 유효성 정책 공유, [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 등록 결과 소비 |
| [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | [API-CREATOR-DISCOVERY-001](api/discovery/creator-discovery-api.md#api-creator-discovery-001-유튜버-필터-선택-목록), [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색)의 `creatorId` 의미 | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)이 정렬·페이지·다른 조건 조합 |
| [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | [API-ADMIN-AUTH-001](api/admin/authentication-api.md#api-admin-auth-001-관리자-로그인)~[API-ADMIN-AUTH-003](api/admin/authentication-api.md#api-admin-auth-003-관리자-로그아웃), 기본 데이터 미리보기·생성 API, [API-ADMIN-VISIT-001](api/admin/visit-registration-api.md#api-admin-visit-001-방문-관계-등록) | 등록 결과를 [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)~[WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)이 인수 검증 |

## 7. 담당자 → API 매핑

| 담당자 | 최종 책임 API | 기본 리뷰 관계 |
|---|---|---|
| 양성훈 | [API-DISCOVERY-001](api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) | 이우람 리뷰 |
| 박진영 | [API-DETAIL-001](api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 김인안 리뷰 |
| 이우람 | [API-CREATOR-DISCOVERY-001](api/discovery/creator-discovery-api.md#api-creator-discovery-001-유튜버-필터-선택-목록)과 `creatorId` 판정 계약 | 양성훈 리뷰 |
| 김인안 | 관리자 인증·검증 미리보기·생성·방문 관계 API | 인증은 이우람, 등록은 박진영 리뷰 |

## 8. 미매핑 항목

- 기능 요구사항 20개는 모두 주 API에 매핑됐다.
- MVP 제외 기능은 API에 매핑하지 않았다.
- [PRD-PRODUCT-001](../04-product/prd/00-product-overview.md)은 전체 범위 문서라 개별 API 주 매핑이 없다.
- Critical 차단 항목이었던 식별자 타입, 인증 전달, 방문 관계 경로와 외부 확인 흐름은 확정돼 매핑에 반영됐다.

## 9. 변경 영향 추적

요구사항·규칙 ID가 추가·삭제·변경되면 이 문서의 주 API, 보조 API, 검증 방식과 담당자를 함께 갱신한다. API 필드·경로·상태 코드 변경은 해당 PRD와 프론트엔드, 소비·제공 Workstream, 계약 테스트와 후속 데이터 모델 영향을 검토한다.

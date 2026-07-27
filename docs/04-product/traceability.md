---
related_documents:
  - ../00-overview/scope.md
  - ../01-requirements/functional-requirements.md
  - ../01-requirements/business-rules.md
  - ../01-requirements/non-functional-requirements.md
  - ../02-analysis/mvp-workstreams.md
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
---

# 맛잇온 PRD 추적성

## 1. 문서 목적

맛잇온 1차 MVP의 기능 요구사항, 비즈니스 규칙, NFR, Workstream과 담당자를 PRD에 연결한다. 원문 정의는 각 기준 문서가 소유하며 이 문서는 배정과 변경 영향을 관리한다.

## 2. 기능 PRD 목록

| PRD ID | 문서 | Workstream | 담당자 | 기본 리뷰어 |
|---|---|---|---|---|
| [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | [맛집 탐색](prd/discovery/restaurant-discovery.md) | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | 양성훈 | 이우람 |
| [PRD-DETAIL-001](prd/detail/restaurant-detail.md) | [맛집 상세 및 콘텐츠 조회](prd/detail/restaurant-detail.md) | [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | 박진영 | 김인안 |
| [PRD-DISCOVERY-002](prd/discovery/creator-discovery.md) | [유튜버 기반 탐색](prd/discovery/creator-discovery.md) | [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | 이우람 | 양성훈 |
| [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [관리자 데이터 등록](prd/admin/admin-data-management.md) | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 김인안 | 박진영 |

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
| [NFR-AVAILABILITY-002](../01-requirements/non-functional-requirements.md#nfr-availability-002-mvp-가용성과-수동-복구) | MVP 가용성과 수동 복구 | 예 | [PRODUCT-001](prd/00-product-overview.md) | 이우람 |
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
| [NFR-DEPLOYMENT-004](../01-requirements/non-functional-requirements.md#nfr-deployment-004-mvp-배포-복잡도-제한) | MVP 배포 복잡도 제한 | 예 | [PRODUCT-001](prd/00-product-overview.md) | 이우람 |
| [NFR-MAINTAINABILITY-001](../01-requirements/non-functional-requirements.md#nfr-maintainability-001-책임과-의존성-경계) | 책임과 의존성 경계 | 예 | [PRODUCT-001](prd/00-product-overview.md), 전체 기능 PRD | 각 Workstream 담당자 |
| [NFR-MAINTAINABILITY-002](../01-requirements/non-functional-requirements.md#nfr-maintainability-002-공통-정책과-규칙-배치) | 공통 정책과 규칙 배치 | 예 | [PRODUCT-001](prd/00-product-overview.md), 전체 기능 PRD | 각 Workstream 담당자 |
| [NFR-MAINTAINABILITY-003](../01-requirements/non-functional-requirements.md#nfr-maintainability-003-추적성과-운영-복잡도) | 추적성과 운영 복잡도 | 예 | [PRODUCT-001](prd/00-product-overview.md), 전체 기능 PRD | 각 Workstream 담당자 |
| [NFR-PRIVACY-001](../01-requirements/non-functional-requirements.md#nfr-privacy-001-mvp-개인정보-최소화) | MVP 개인정보 최소화 | 예 | [PRODUCT-001](prd/00-product-overview.md), 전체 기능 PRD | 각 Workstream 담당자 |
| [NFR-PRIVACY-002](../01-requirements/non-functional-requirements.md#nfr-privacy-002-인증정보와-외부-키-보호) | 인증정보와 외부 키 보호 | 예 | [PRODUCT-001](prd/00-product-overview.md), [ADMIN-001](prd/admin/admin-data-management.md) | 이우람 |
| [NFR-PRIVACY-003](../01-requirements/non-functional-requirements.md#nfr-privacy-003-회원-기능-도입-시-재검토) | 회원 기능 도입 시 재검토 | 예 | [PRODUCT-001](prd/00-product-overview.md) | MVP 제외, 이우람 리뷰 |

공통 NFR의 주 문서는 [PRD-PRODUCT-001](prd/00-product-overview.md)이다. `전체 조회 PRD`는 [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색), [DISCOVERY-002](prd/discovery/creator-discovery.md)와 [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회)을 뜻하며, `전체 기능 PRD`는 네 기능 PRD 전체를 뜻한다.

## 6. Workstream → PRD 매핑

| Workstream | 주 PRD | 제공 계약 | 의존 PRD |
|---|---|---|---|
| [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) 맛집 탐색 | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | 최종 검색·필터·정렬·페이지 목록 | [DISCOVERY-002](prd/discovery/creator-discovery.md), [ADMIN-001](prd/admin/admin-data-management.md) |
| [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) 맛집 상세 및 콘텐츠 조회 | [PRD-DETAIL-001](prd/detail/restaurant-detail.md) | 다중 영역 상세 표시 조합 | [DISCOVERY-002](prd/discovery/creator-discovery.md), [ADMIN-001](prd/admin/admin-data-management.md) |
| [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) 유튜버 기반 탐색 | [PRD-DISCOVERY-002](prd/discovery/creator-discovery.md) | 유효 방문 관계 기반 맛집 판정 | [ADMIN-001](prd/admin/admin-data-management.md) |
| [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 관리자 데이터 등록 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | 검증된 기본 데이터와 관계 | 없음 |

## 7. 담당자 → PRD 매핑

| 담당자 | 최종 책임 PRD | 협업·리뷰 PRD |
|---|---|---|
| 양성훈 | [PRD-DISCOVERY-001](prd/discovery/restaurant-discovery.md) | [DISCOVERY-002](prd/discovery/creator-discovery.md) 리뷰, [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) 식별자, [ADMIN-001](prd/admin/admin-data-management.md) 목록 반영 |
| 박진영 | [PRD-DETAIL-001](prd/detail/restaurant-detail.md) | [ADMIN-001](prd/admin/admin-data-management.md) 리뷰, [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) 표시 계약, [DISCOVERY-002](prd/discovery/creator-discovery.md) 관계 정책 |
| 이우람 | [PRD-DISCOVERY-002](prd/discovery/creator-discovery.md) | [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) 리뷰, [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) 관계 계약, [ADMIN-001](prd/admin/admin-data-management.md) 인증·Visit 계약 |
| 김인안 | [PRD-ADMIN-001](prd/admin/admin-data-management.md) | [API-DETAIL-001](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) 리뷰, [API-DISCOVERY-001](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색)·[DISCOVERY-002](prd/discovery/creator-discovery.md) 등록 반영 |

## 8. 미매핑 항목 검토

- 주 PRD 없는 MVP 기능 요구사항: 없음
- 여러 주 PRD에 중복 배정된 요구사항: 없음
- 담당자 또는 Workstream 없는 기능 PRD: 없음
- 기능 요구사항 없이 생성된 기능 PRD: 없음
- MVP 제외 기능을 구현 대상으로 포함한 PRD: 없음. [NFR-PRIVACY-003](../01-requirements/non-functional-requirements.md#nfr-privacy-003-회원-기능-도입-시-재검토)은 범위 재검토 표지로만 유지한다.
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

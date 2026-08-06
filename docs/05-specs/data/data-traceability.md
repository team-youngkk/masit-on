---
related_documents:
  - ../../01-requirements/functional-requirements.md
  - ../../01-requirements/business-rules.md
  - ../../03-team/ownership.md
  - ../../04-product/traceability.md
  - ../api-traceability.md
  - data-model.md
  - entity-definitions.md
  - relationship-rules.md
  - constraints.md
  - ../../07-adr/adr-traceability.md
  - ../../08-planning/second-expansion-test-matrix.md
  - ../../08-planning/expansion-2-implementation-plan.md
  - ../../08-planning/expansion-2-task-breakdown.md
  - ../../04-product/prd/discovery/restaurant-discovery.md
  - ../../04-product/prd/discovery/creator-discovery.md
  - ../../04-product/prd/detail/restaurant-detail.md
  - ../../04-product/prd/admin/admin-data-management.md
  - ../api/admin/authentication-api.md
  - ../../02-analysis/mvp-workstreams.md
  - ../api/admin/reference-data-api.md
  - ../api/admin/visit-registration-api.md
  - ../api/discovery/restaurant-discovery-api.md
  - ../api/discovery/creator-discovery-api.md
  - ../api/detail/restaurant-detail-api.md
  - ../../07-adr/security/auth-003-confirmation-token.md
  - physical-data-model.md
  - table-definitions.md
  - constraint-mapping.md
  - index-strategy.md
  - second-expansion-data-contract.md
  - ../../02-analysis/second-expansion-workstreams.md
  - ../api/personal/personal-collection-api.md
  - ../api/discovery/popular-restaurant-api.md
  - ../api/curation/curation-api.md
  - ../api/participation/submission-report-api.md
  - ../api/notification/notification-api.md
  - migration-plan.md
  - seed-data-plan.md
  - ../../07-adr/data/data-007-uuid-v4-identifiers.md
  - ../../07-adr/data/data-008-publication-lifecycle-soft-delete.md
---

# 맛잇온 데이터 추적성

## 1. 문서 목적

PRD, 기능·비기능 요구사항, 비즈니스 규칙, API와 Workstream이 어떤 영속 데이터와 제약으로 충족되는지 추적한다. 응답 필드가 저장값인지 관계 조합·파생값인지도 구분한다.

## 2. PRD → 데이터 개념 매핑

| PRD | 사용자·관리자 결과 | 주 데이터 | 관계·조합 데이터 |
|---|---|---|---|
| [PRD-DISCOVERY-001](../../04-product/prd/discovery/restaurant-discovery.md) | 맛집 목록·이름·지역·카테고리 탐색 | Restaurant, Region, FoodCategory | Visit, Creator |
| [PRD-DISCOVERY-002](../../04-product/prd/discovery/creator-discovery.md) | 유튜버 선택 및 방문 맛집 탐색 | Creator | Visit, Restaurant, Video 공개 유효성 |
| [PRD-DETAIL-001](../../04-product/prd/detail/restaurant-detail.md) | 맛집 기본 정보와 방문 콘텐츠 | Restaurant | Region, FoodCategory, Visit, Creator, Video |
| [PRD-ADMIN-001](../../04-product/prd/admin/admin-data-management.md) | 인증된 관리자 검증·등록 | AdminAccount, AdminRefreshToken | Restaurant, Creator, Video, Visit |

## 3. 기능 요구사항 → 데이터 개념 매핑

| 요구사항 | 데이터 모델 반영 | 주요 제약·파생 |
|---|---|---|
| [FR-RESTAURANT-001](../../01-requirements/functional-requirements.md#fr-restaurant-001-맛집-목록-조회)·[FR-RESTAURANT-002](../../01-requirements/functional-requirements.md#fr-restaurant-002-맛집-이름-검색)·[FR-RESTAURANT-005](../../01-requirements/functional-requirements.md#fr-restaurant-005-검색-및-필터-조건-조합)~[FR-RESTAURANT-007](../../01-requirements/functional-requirements.md#fr-restaurant-007-기본-정렬-적용) | Restaurant | publication 필터, 이름 검색, 고유 결과·정렬·페이지는 조회 책임 |
| [FR-RESTAURANT-003](../../01-requirements/functional-requirements.md#fr-restaurant-003-지역별-필터)·[FR-RESTAURANT-009](../../01-requirements/functional-requirements.md#fr-restaurant-009-지역-정보-확인) | Region, Restaurant | 서울 자치구 1개 참조 |
| [FR-RESTAURANT-004](../../01-requirements/functional-requirements.md#fr-restaurant-004-음식-카테고리별-필터)·[FR-RESTAURANT-010](../../01-requirements/functional-requirements.md#fr-restaurant-010-음식-카테고리-확인) | FoodCategory, Restaurant | 대표 카테고리 정확히 1개 |
| [FR-RESTAURANT-008](../../01-requirements/functional-requirements.md#fr-restaurant-008-맛집-기본-정보-조회)·[FR-RESTAURANT-011](../../01-requirements/functional-requirements.md#fr-restaurant-011-영상-연결이-없는-맛집-상세-조회) | Restaurant | Visit 없이 기본 상세 조회 |
| [FR-CREATOR-001](../../01-requirements/functional-requirements.md#fr-creator-001-유튜버-기준-방문-맛집-조회)·[FR-CREATOR-002](../../01-requirements/functional-requirements.md#fr-creator-002-방문-유튜버-정보-확인) | Creator, Visit, Video | 공개·유효 관계와 채널 일치, 중복 제거 |
| [FR-CREATOR-003](../../01-requirements/functional-requirements.md#fr-creator-003-유튜버-필터-선택-목록-조회) | Creator | 공개 Creator 최소 선택 정보 |
| [FR-VIDEO-001](../../01-requirements/functional-requirements.md#fr-video-001-관련-영상-정보-확인) | Video, Creator, Visit | 공개 관련 영상, 외부 장애 격리 |
| [FR-ADMIN-001](../../01-requirements/functional-requirements.md#fr-admin-001-관리자-등록-기능-접근) | AdminAccount, AdminRefreshToken | 사전 발급 활성 계정, JWT·Refresh 회전·ADMIN 권한 |
| [FR-ADMIN-002](../../01-requirements/functional-requirements.md#fr-admin-002-맛집-정보-등록) | Restaurant, Region, FoodCategory | 카카오 동일성, 서울 주소, 단일 카테고리, 원자적 공개 생성 |
| [FR-ADMIN-003](../../01-requirements/functional-requirements.md#fr-admin-003-유튜버-정보-등록) | Creator | 외부 채널 ID 유일, 채널 단위 생성 |
| [FR-ADMIN-004](../../01-requirements/functional-requirements.md#fr-admin-004-영상-정보-등록) | Video, Creator | 외부 영상 ID 유일, 게시 채널 필수, 원본 미저장 |
| [FR-VISIT-001](../../01-requirements/functional-requirements.md#fr-visit-001-맛집유튜버영상-방문-관계-등록) | Visit, Restaurant, Creator, Video | 세 참조·실제 근거·채널 일치·복합 유일·원자성 |

## 4. 비즈니스 규칙 → 제약조건 매핑

| 규칙 ID | 규칙 | 데이터 모델 반영 | 저장소 제약 | 애플리케이션 검증 |
|---|---|---|---:|---:|
| [BR-RESTAURANT-002](../../01-requirements/business-rules.md#br-restaurant-002-영상과-독립된-맛집) | 영상과 독립된 맛집 | Restaurant와 Visit 선택 관계 | 참조 방향 | 필요 |
| [BR-RESTAURANT-003](../../01-requirements/business-rules.md#br-restaurant-003-맛집-최소-등록-정보)~[BR-RESTAURANT-005](../../01-requirements/business-rules.md#br-restaurant-005-맛집의-지역-소속) | 최소 정보·카테고리·지역 | 필수 속성, Region·FoodCategory 1개 | 필요 | 필요 |
| [BR-RESTAURANT-006](../../01-requirements/business-rules.md#br-restaurant-006-맛집-중복-판단)·[BR-RESTAURANT-007](../../01-requirements/business-rules.md#br-restaurant-007-동일-상호의-지점-구분) | 카카오 동일성·지점 구분 | kakaoPlaceIdentity 유일, 이름 비유일 | 필요 | 필요 |
| [BR-CREATOR-001](../../01-requirements/business-rules.md#br-creator-001-유튜버-정보의-의미)~[BR-CREATOR-003](../../01-requirements/business-rules.md#br-creator-003-동일-채널-중복-판단) | 채널 관리 단위·최소 정보·중복 | externalChannelId 유일 | 필요 | 필요 |
| [BR-CREATOR-005](../../01-requirements/business-rules.md#br-creator-005-방문-관계의-유튜버-일치) | Visit 채널 일치 | Video 게시 Creator와 Visit.Creator 일치 | 복합 FK | 필요 |
| [BR-VIDEO-001](../../01-requirements/business-rules.md#br-video-001-영상의-의미와-보관-범위)~[BR-VIDEO-003](../../01-requirements/business-rules.md#br-video-003-영상-식별-및-중복-판단) | 원본 미저장·필수 메타·중복 | Video 메타, externalVideoId 유일 | 필요 | 필요 |
| [BR-VIDEO-004](../../01-requirements/business-rules.md#br-video-004-영상과-방문-관계의-다대상-연결)·[BR-VIDEO-005](../../01-requirements/business-rules.md#br-video-005-실제-방문-근거) | 다대상·실제 방문 | Video 1:N Visit, 생성 전 확인 | 참조 필요 | 필요 |
| [BR-VIDEO-006](../../01-requirements/business-rules.md#br-video-006-게시일과-방문일의-구분) | 게시일·방문일 구분 | Visit 방문일 없음, Video 게시일 선택 | 해당 없음 | 필요 |
| [BR-VISIT-001](../../01-requirements/business-rules.md#br-visit-001-방문-관계의-구성)~[BR-VISIT-004](../../01-requirements/business-rules.md#br-visit-004-방문-관계의-연결-범위) | 삼항 구성·근거·중복·범위 | 세 필수 참조, 복합 유일 | 필요 | 필요 |
| [BR-VISIT-005](../../01-requirements/business-rules.md#br-visit-005-방문-관계의-조회-유효성)·[BR-PUBLICATION-001](../../01-requirements/business-rules.md#br-publication-001-일반-사용자-공개-범위)~[BR-PUBLICATION-008](../../01-requirements/business-rules.md#br-publication-008-상태-변경의-일관성) | 조회 공개 유효성 | publication·lifecycle·외부 상태 | CHECK 허용값·조합 | 필요 |
| [BR-VISIT-006](../../01-requirements/business-rules.md#br-visit-006-방문-날짜-관리-제외)·[BR-VISIT-007](../../01-requirements/business-rules.md#br-visit-007-등록-완료와-검증-상태) | 방문일·검증 상태 제외 | 속성 미생성, 생성 완료가 검증 완료 | 해당 없음 | 필요 |
| [BR-ADMIN-003](../../01-requirements/business-rules.md#br-admin-003-등록-정합성-검증)·[BR-ADMIN-007](../../01-requirements/business-rules.md#br-admin-007-동시-등록의-고유성) | 정합성·동시성 | 유일·참조·원자성 | 필요 | 필요 |
| [BR-ADMIN-008](../../01-requirements/business-rules.md#br-admin-008-보류-요청의-처리) | 보류 요청 | 핵심 엔티티·보류 레코드 미생성 | 해당 없음 | 필요 |

## 5. API 요청 → 데이터 변경 매핑

| API ID | 요청 목적 | 생성·변경 데이터 | 필수 참조 | 원자성 범위 | 담당 Workstream |
|---|---|---|---|---|---|
| [API-ADMIN-AUTH-001](../api/admin/authentication-api.md#api-admin-auth-001-관리자-로그인) | 관리자 로그인 | AdminRefreshToken 생성·기존 활성 Token 폐기 | AdminAccount | 계정당 활성 Token 전환 | [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |
| [API-ADMIN-AUTH-002](../api/admin/authentication-api.md#api-admin-auth-002-관리자-토큰-재발급) | 토큰 재발급 | 기존 Token 폐기·AdminRefreshToken 회전 | AdminAccount, AdminRefreshToken | 검증·회전 원자성 | [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |
| [API-ADMIN-AUTH-003](../api/admin/authentication-api.md#api-admin-auth-003-관리자-로그아웃) | 로그아웃 | AdminRefreshToken 폐기 | AdminRefreshToken | 현재 Token 하나 | [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |
| [API-ADMIN-RESTAURANT-PREVIEW-001](../api/admin/reference-data-api.md#api-admin-restaurant-preview-001-맛집-등록-검증-미리보기) | 외부 장소·입력 검증 | 핵심 Entity 변경 없음, `READY`이면 ConfirmationToken 기술 행 생성 | Region, FoodCategory 기준 | Token 발급 행 하나 | [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |
| [API-ADMIN-RESTAURANT-001](../api/admin/reference-data-api.md#api-admin-restaurant-001-맛집-등록-확정) | 맛집 생성 | Restaurant와 필수 참조 연결 | Region, FoodCategory | Restaurant 한 건 전체 | [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) / Restaurant |
| [API-ADMIN-CREATOR-PREVIEW-001](../api/admin/reference-data-api.md#api-admin-creator-preview-001-유튜버-등록-검증-미리보기) | 외부 채널 검증 | 핵심 Entity 변경 없음, `READY`이면 ConfirmationToken 기술 행 생성 | 없음 | Token 발급 행 하나 | [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |
| [API-ADMIN-CREATOR-001](../api/admin/reference-data-api.md#api-admin-creator-001-유튜버-등록-확정) | Creator 생성 | Creator | 없음 | Creator 한 건 전체 | [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) / Creator |
| [API-ADMIN-VIDEO-PREVIEW-001](../api/admin/reference-data-api.md#api-admin-video-preview-001-영상-등록-검증-미리보기) | 외부 영상·게시 채널 검증 | 핵심 Entity 변경 없음, `READY`이면 ConfirmationToken 기술 행 생성 | 게시 채널 후보 | Token 발급 행 하나 | [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |
| [API-ADMIN-VIDEO-001](../api/admin/reference-data-api.md#api-admin-video-001-영상-등록-확정) | Video 생성 | Video와 게시 채널 외부 식별 | 없음(내부 Creator 연결 선택) | Video 한 건 전체 | [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) / Video |
| [API-ADMIN-VISIT-001](../api/admin/visit-registration-api.md#api-admin-visit-001-방문-관계-등록) | 방문 관계 생성 | Visit, 필요 시 Video.Creator 연결 | Restaurant, Creator, Video | 채널 연결 해소·검증·복합 중복·저장 전체 | [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) / Visit |

확인 Token은 PostgreSQL에 SHA-256 해시·관리자·자원 종류·후보 스키마 버전·JSONB Snapshot과 결과 상태를 저장한다. 10분 만료, 원자적 소비와 완료·만료 결과 24시간 재현은 [ADR-AUTH-003](../../07-adr/security/auth-003-confirmation-token.md)을 따른다. `REVIEW_REQUIRED`는 등록 요청으로 저장하지 않는다.

## 6. API 응답 → 데이터 조회 매핑

| API ID | 응답 영역 | 주 데이터 | 조합 데이터 | 파생 필드 | 조회 책임 |
|---|---|---|---|---|---|
| [API-DISCOVERY-001](../api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색) | 맛집 목록 | Restaurant | Region, FoodCategory, 공개 Visit·Creator | `visitedBy` 최대 3명, `remainingVisitedByCount`, page | [WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색), 관계 판정 [WS-03](../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) |
| [API-CREATOR-DISCOVERY-001](../api/discovery/creator-discovery-api.md#api-creator-discovery-001-유튜버-필터-선택-목록) | 유튜버 선택 목록 | Creator | 없음 | 채널명 정렬 | [WS-03](../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) |
| [API-DETAIL-001](../api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 맛집 기본 정보 | Restaurant | Region, FoodCategory | address DTO | [WS-02](../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) |
| [API-DETAIL-001](../api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 방문 유튜버 | Visit | Creator, Video 공개 유효성 | Creator 식별자 중복 제거 | [WS-02](../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회), 판정 Visit |
| [API-DETAIL-001](../api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회) | 관련 영상 | Visit | Video, Creator | Video 식별자 중복 제거, `contentStatus` | [WS-02](../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) |
| API-ADMIN-*-PREVIEW-001 | 후보·중복 판정 | 외부 확인 결과 | 기존 핵심 데이터 | `decision`, token, expiry, candidate DTO | [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |
| API-ADMIN-*-001 | 생성 결과 | 생성 엔티티 | 표준 표시값 | 응답 DTO 조합 | [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록)와 소유 도메인 |
| [API-ADMIN-AUTH-001](../api/admin/authentication-api.md#api-admin-auth-001-관리자-로그인)·[API-ADMIN-AUTH-002](../api/admin/authentication-api.md#api-admin-auth-002-관리자-토큰-재발급) | 인증·재발급 | AdminRefreshToken | AdminAccount 활성 여부 | JWT Access Token, 만료 시간 | [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |

`contentStatus`, 페이지 메타데이터, 후보 `decision`, `remainingVisitedByCount`는 엔티티에 저장하지 않는다.

## 7. Workstream → 데이터 소유권 매핑

| Workstream | 변경 소유 | 조회·의존 데이터 | 책임 경계 |
|---|---|---|---|
| [WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) 맛집 탐색 | Restaurant 조회 규칙 | Region, FoodCategory, Visit·Creator 판정 결과 | Visit 규칙을 재구현하지 않음 |
| [WS-02](../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) 상세 및 콘텐츠 | 상세 조합 | Restaurant, Visit, Creator, Video | 기본 데이터와 관계를 임의 변경하지 않음 |
| [WS-03](../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) 유튜버 기반 탐색 | Visit 관계 판정 계약 | Creator, Video, Restaurant 상태 | 최종 Restaurant 페이지 조합은 [WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) |
| [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 관리자 등록 | 인증·등록 흐름 조율 | AdminAccount·AdminRefreshToken 및 네 소유 도메인 | 도메인 고유·정합성 규칙을 우회하지 않음 |

## 8. 물리 설계 라우팅

| 결정 | 구현 문서 | 승인·근거 |
|---|---|---|
| PostgreSQL 테이블·컬럼·타입 | [table-definitions.md](table-definitions.md) | [ADR-DATA-001](../../07-adr/data/data-001-postgresql.md) |
| UUID 내부 식별자 | [physical-data-model.md](physical-data-model.md#2-확정-물리-컨벤션) | [ADR-DATA-007](../../07-adr/data/data-007-uuid-v4-identifiers.md) Accepted |
| 공개·논리 삭제 상태 | [constraint-mapping.md](constraint-mapping.md#5-상태-전환-불변식) | [ADR-DATA-008](../../07-adr/data/data-008-publication-lifecycle-soft-delete.md) Accepted |
| 외부 자원 동일성 | [constraint-mapping.md](constraint-mapping.md#1-논리-규칙-물리-제약) | [ADR-EXT-001](../../07-adr/integration/ext-001-reference-verification.md) |
| 채널 일치·참조·유일성 | [constraint-mapping.md](constraint-mapping.md) | 논리 규칙 + PostgreSQL 복합 FK·UK |
| 조회 인덱스 | [index-strategy.md](index-strategy.md) | API 조회·성능 NFR |
| 스키마 배포·기준 데이터 | [migration-plan.md](migration-plan.md), [seed-data-plan.md](seed-data-plan.md) | [ADR-DATA-004](../../07-adr/data/data-004-flyway.md) |

## 9. 1차 확장 데이터 추적

| 범위 | 요구사항·API | 소유 데이터 | 생명주기·제약 | Workstream |
|---|---|---|---|---|
| 회원 기반 | `FR-MEMBER-001`~`005`, `FR-AUTH-001`~`003`, 회원 인증 API | `member_account`, `member_action_token`, `member_session_revocation`, `member_action_mail_outbox`, `member_deletion_job`, `member_session_revocation_recovery`, 회원 Redis | 이메일 고유성, Action Token 일회성, 비동기 메일 재시도, `sid` 만료까지 폐기 표식·보상 보존 | WS-05 |
| 찜 | `FR-FAVORITE-001`~`004`, `API-PERSONAL-001`~`004` | `favorite` | `(member_id, restaurant_id)` PK, 회원 삭제 cascade, 맛집 물리 삭제 전 정리 | WS-06 |
| 최근 본 맛집 | `FR-RECENT-001`~`003`, `API-PERSONAL-005`~`006`, 공개 상세 부수효과 | `recent_restaurant_view` | 복합 PK upsert·최신 시각순·50건 상한, 주기 cleanup Command의 30일 물리 삭제, GET은 읽기 전용 | WS-06 |
| 지도 탐색 | `FR-MAP-001`~`002`, `API-MAP-001` | `restaurant.latitude`, `restaurant.longitude` | nullable WGS84 쌍, 범위 CHECK, 좌표 없음은 지도에서만 제외 | WS-07 |
| 유튜버 상세 | `FR-CREATOR-004`~`006`, `API-CREATOR-DETAIL-001`~`003` | `creator.profile_image_url`, `description`, `handle`과 기존 Creator·Visit·Video | 선택값은 null 또는 유효한 값, 사용자 조회 중 외부 API 호출 없음 | WS-08 |
| 검증 참여자 제한 공개 | `API-VALIDATION-001`~`002`, `ADR-DEPLOY-003` | Redis `auth:verification:` 세션·실패 제한 | 128-bit 이상 세션 원문의 SHA-256 해시, 7일 고정 만료, 회원·관리자 인증과 분리, 정식 공개 시 전체 제거 | [OPS-VALIDATION](../../02-analysis/first-expansion-workstreams.md#ops-validation-공통-운영배포-트랙) |

각 물리 계약은 [테이블 정의](table-definitions.md#13-1차-확장-v3v5-데이터-계약), [제약조건](constraints.md), [인덱스 전략](index-strategy.md#5-1차-확장-인덱스), [생명주기 규칙](lifecycle-rules.md#101-회원-개인화-관계-정리), [마이그레이션 계획](migration-plan.md#9-1차-확장-전진-마이그레이션-순서)을 함께 따른다.

## 10. 2차 확장 데이터 추적

| 범위 | 요구사항·API | 저장·파생 데이터 | 핵심 제약·생명주기 | Workstream |
|---|---|---|---|---|
| 개인 컬렉션 | `FR-COLLECTION-001~006`, `API-COLLECTION-001~008` | `personal_collection`, `collection_restaurant` | 회원 소유, 복합 PK, 20/100 상한, 고정 정렬, 탈퇴 CASCADE | WS-09 |
| 인기 맛집 | `FR-POPULAR-001`, `API-POPULAR-001` | 기존 `favorite` 실시간 집계, 순위 비저장 | 현재 찜 1건 이상, 상위 20, Restaurant 공개 상태 | WS-10 |
| 큐레이션 | `FR-CURATION-001~004`, `API-CURATION-001~009` | `curation`, `curation_restaurant` | `DRAFT/PUBLISHED`, 메인 5·구성 20, 위치 고유, 관리자 감사 | WS-11 |
| 제보 | `FR-SUBMISSION-001~003`, 회원·관리자 제보 API | `submission`, `moderation_history` | 열린 지문 중복, 합산 일일 제한, 상태 이력, 1년 뒤 회원 연결 제거 | WS-12 |
| 신고 | `FR-REPORT-001~003`, 회원·관리자 신고 API | `report`, `moderation_history` | 열린 대상·유형 중복, 자동 비공개 없음, 상태 이력, 1년 뒤 회원 연결 제거 | WS-12 |
| 사용자 알림 | `FR-NOTIFICATION-001~004`, `API-NOTIFICATION-001~004` | `notification`, 미읽음 수 파생 | 요청·상태 고유, 상태와 원자 저장, 90일/최신 200개 중 넓은 보존, 탈퇴 CASCADE | WS-13 |
| 생성 멱등성 | 2차 확장 공통 API 계약 | `idempotency_record` | 주체·scope·키 해시 고유, 성공과 원자 저장, 24시간 삭제 | 공통 인증/플랫폼 |

전체 컬럼·인덱스·동시성·삭제 정책과 V3 순서는 [2차 확장 데이터 계약](second-expansion-data-contract.md)을 따른다. `PopularityMetric/Snapshot`, `NotificationPreference`, `DeviceToken`은 승인 범위에 없어 미매핑이 아니라 명시적인 비저장 개념이다.

### 10.1 2차 확장 데이터 → ADR·테스트·Task 검증

| 데이터 범위 | 소유 요구사항·API | ADR 또는 명시적 보류 | Workstream | 테스트 | E2 Task |
|---|---|---|---|---|---|
| `personal_collection`, `collection_restaurant` | `FR-COLLECTION-001~006`, `API-COLLECTION-001~008` | 기존 인증·PostgreSQL·Flyway ADR; 공유·순서 열 제외 | WS-09 | [`TST-E2-COL-001`](../../08-planning/second-expansion-test-matrix.md), `TST-E2-LIFE-001` | [`E2-T01`](../../08-planning/expansion-2-task-breakdown.md)~`E2-T03`, `E2-T15` |
| 기존 `favorite` 요청 시 집계 | `FR-POPULAR-001`, `API-POPULAR-001` | [ADR-DATA-011](../../07-adr/data/data-011-popular-restaurant-request-time-aggregation.md); 집계 테이블·Snapshot·캐시 비저장 | WS-10 | [`TST-E2-POP-001`](../../08-planning/second-expansion-test-matrix.md), `TST-E2-PERF-001` | [`E2-T01`](../../08-planning/expansion-2-task-breakdown.md), `E2-T02`, `E2-T04`, `E2-T05`, `E2-T15` |
| `curation`, `curation_restaurant` | `FR-CURATION-001~004`, `API-CURATION-001~009` | 기존 관리자 인증·PostgreSQL ADR; 예약·추천·이미지 열 제외 | WS-11 | [`TST-E2-CUR-001`](../../08-planning/second-expansion-test-matrix.md), `TST-E2-PERF-001` | [`E2-T01`](../../08-planning/expansion-2-task-breakdown.md), `E2-T02`, `E2-T06`, `E2-T07`, `E2-T15` |
| `submission`, `moderation_history` | `FR-SUBMISSION-001~003`, 회원·관리자 제보 API | [ADR-DATA-012](../../07-adr/data/data-012-second-expansion-retention-cleanup.md), [ADR-NOTIFY-002](../../07-adr/integration/notify-002-in-app-notification-reliability.md) | WS-12 | [`TST-E2-SUB-001`](../../08-planning/second-expansion-test-matrix.md), `TST-E2-ATOMIC-001`, `TST-E2-LIFE-001` | [`E2-T01`](../../08-planning/expansion-2-task-breakdown.md), `E2-T02`, `E2-T08`, `E2-T09`, `E2-T11`, `E2-T15` |
| `report`, `moderation_history` | `FR-REPORT-001~003`, 회원·관리자 신고 API | [ADR-DATA-012](../../07-adr/data/data-012-second-expansion-retention-cleanup.md), [ADR-NOTIFY-002](../../07-adr/integration/notify-002-in-app-notification-reliability.md) | WS-12 | [`TST-E2-REP-001`](../../08-planning/second-expansion-test-matrix.md), `TST-E2-ATOMIC-001`, `TST-E2-LIFE-001` | [`E2-T01`](../../08-planning/expansion-2-task-breakdown.md), `E2-T02`, `E2-T08`, `E2-T09`, `E2-T11`, `E2-T15` |
| `notification` | `FR-NOTIFICATION-001~004`, `API-NOTIFICATION-001~004` | [ADR-NOTIFY-002](../../07-adr/integration/notify-002-in-app-notification-reliability.md), [ADR-DATA-012](../../07-adr/data/data-012-second-expansion-retention-cleanup.md); Preference·DeviceToken 비저장 | WS-13 | [`TST-E2-NOT-001`](../../08-planning/second-expansion-test-matrix.md), `TST-E2-ATOMIC-001`, `TST-E2-LIFE-001` | [`E2-T01`](../../08-planning/expansion-2-task-breakdown.md), `E2-T02`, `E2-T10`, `E2-T11`, `E2-T14`, `E2-T15` |
| `idempotency_record` | 2차 확장 생성 API 공통 계약 | [ADR-DATA-012](../../07-adr/data/data-012-second-expansion-retention-cleanup.md); 24시간 독립 cleanup | 공통 인증/플랫폼 | 각 기능 계약 테스트, `TST-E2-LIFE-001` | [`E2-T01`](../../08-planning/expansion-2-task-breakdown.md), `E2-T02`, `E2-T15` |

V3 전진 적용과 전체 FK·UNIQUE·CHECK·인덱스는 `TST-E2-E2E-001`, `E2-T14`, `E2-T15`에서 최종 회귀한다. `DeviceToken`·`NotificationPreference`와 푸시용 `E2-T12`는 현재 없다.

## 11. 미매핑 항목

- Restaurant 설명·대표 이미지·영업 정보는 확정 요구사항/API가 없어 저장 모델에서 제외했다.
- Creator 구독자 수·조회 수 같은 통계와 Video 게시일의 외부 API 노출, Visit 방문일·검증 상태·검증자는 저장 모델에서 제외하거나 선택 데이터다. V6 상세 표시 필드인 Creator 프로필 이미지·소개·handle은 저장 계약에 포함한다.
- 수정·삭제·승인·보류 목록 API가 없으므로 관련 운영 전환은 API 변경으로 만들지 않았다.
- 로그인 실패 제한 카운터는 저장 방식이 미정이다. 확인 Token은 PostgreSQL 단기 기술 테이블로 확정됐지만 핵심 도메인 ERD에는 포함하지 않는다.

## 12. 변경 영향 추적

- 지역 단계·범위 변경: Region, Restaurant, 탐색/등록 API와 [BR-RESTAURANT-005](../../01-requirements/business-rules.md#br-restaurant-005-맛집의-지역-소속)를 함께 검토한다.
- 다중 카테고리 변경: Restaurant–FoodCategory 카디널리티, 필터 API와 [BR-RESTAURANT-004](../../01-requirements/business-rules.md#br-restaurant-004-대표-음식-카테고리)를 함께 변경한다.
- Creator를 개인 단위로 변경: Creator·Video·Visit 식별과 모든 유튜버 API를 재설계한다.
- 복수 근거·방문일 도입: Visit 모델, 복합 유일성, 관리자 요청과 상세 응답을 재검토한다.
- 공개·삭제 정책 변경: 네 핵심 데이터, 모든 공개 조회와 운영 정정 흐름을 함께 검토한다.
- 외부 동기화 도입: Creator·Video 상태·이력, 외부 호출 NFR과 운영 책임을 추가한다.

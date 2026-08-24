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
  - third-expansion-ai-video-data-contract.md
  - ../api/admin/ai-video-extraction-api.md
  - ../api/discovery/natural-language-restaurant-discovery-api.md
  - ../api/discovery/restaurant-course-recommendation-api.md
  - ../../02-analysis/third-expansion-workstreams.md
  - ../../04-product/prd/admin/ai-video-information-extraction.md
  - ../../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../../07-adr/integration/ext-003-ai-extraction-async-reliability.md
  - ../../07-adr/architecture/arch-005-natural-language-filter-interpretation.md
  - ../../07-adr/integration/route-001-kakao-mobility-course-routing.md
  - ../../08-planning/third-expansion-evaluation-strategy.md
  - ../../08-planning/third-expansion-test-matrix.md
  - ../../08-planning/third-expansion-task-breakdown.md
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
| [PRD-ADMIN-001](../../04-product/prd/admin/admin-data-management.md) | 인증된 관리자 검증·등록 | `MemberAccount(role=ADMIN)`, `AuthSession` | Restaurant, Creator, Video, Visit |
| [PRD-ADMIN-002](../../04-product/prd/admin/ai-video-information-extraction.md) | AI 추출·상태 조회·자동 등록·예외 보정 | `ai_extraction_job`, `ai_candidate_snapshot`, `ai_extraction_attempt`, `youtube_channel_watch` | 자동 검증 통과 시 기존 Restaurant, Creator, Video, Visit와 VisitTag를 무승인 연결·공개 |
| [PRD-DISCOVERY-005](../../04-product/prd/discovery/natural-language-restaurant-discovery.md) | 자연어 조건 해석·기존 목록 조회 | 신규 영속 데이터 없음 | Restaurant, Region, FoodCategory, Creator, Visit 기존 조회 조합 |
| [PRD-DISCOVERY-006](../../04-product/prd/discovery/restaurant-course-recommendation.md) | 선택 맛집의 자동차 순서·경로 조회 | 신규 영속 데이터 없음 | Restaurant 좌표 조회·Route Provider 응답 조합 |

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
| [FR-ADMIN-001](../../01-requirements/functional-requirements.md#fr-admin-001-관리자-등록-기능-접근)·[FR-AUTH-004](../../01-requirements/functional-requirements.md#fr-auth-004-통합-로그인과-rbac) | `MemberAccount(role=ADMIN)`, `AuthSession` | 운영 절차로 부여한 ADMIN 역할, 통합 JWT·Refresh 회전·현재 역할 재검증 |
| [FR-ADMIN-002](../../01-requirements/functional-requirements.md#fr-admin-002-맛집-정보-등록) | Restaurant, Region, FoodCategory | 카카오 동일성, 서울 주소, 단일 카테고리, 원자적 공개 생성 |
| [FR-ADMIN-003](../../01-requirements/functional-requirements.md#fr-admin-003-유튜버-정보-등록) | Creator | 외부 채널 ID 유일, 채널 단위 생성 |
| [FR-ADMIN-004](../../01-requirements/functional-requirements.md#fr-admin-004-영상-정보-등록) | Video, Creator | 외부 영상 ID 유일, 게시 채널 필수, 원본 미저장 |
| [FR-VISIT-001](../../01-requirements/functional-requirements.md#fr-visit-001-맛집유튜버영상-방문-관계-등록) | Visit, Restaurant, Creator, Video | 세 참조·실제 근거·채널 일치·복합 유일·원자성 |
| [FR-AIEXTRACT-001](../../01-requirements/functional-requirements.md#fr-aiextract-001-ai-영상-추출-작업-요청)·[FR-AIEXTRACT-002](../../01-requirements/functional-requirements.md#fr-aiextract-002-추출-상태와-결과-조회)·[FR-AIEXTRACT-003](../../01-requirements/functional-requirements.md#fr-aiextract-003-자동-확정예외-보정폐기)·[FR-AIEXTRACT-007](../../01-requirements/functional-requirements.md#fr-aiextract-007-ai-태그-후보-생성과-확정) | `ai_extraction_job`, `ai_candidate_snapshot`, `ai_extraction_attempt`, `tag_definition`, `visit_tag` | Job 상태·후보 버전·태그 후보·근거 구간·자동 등록 상태·시도 이력·자동 확정 Visit 연결 |
| [FR-AIEXTRACT-004](../../01-requirements/functional-requirements.md#fr-aiextract-004-신규-영상-webhook-감지와-작업-등록)·[FR-AIEXTRACT-005](../../01-requirements/functional-requirements.md#fr-aiextract-005-관리자-신규-영상-추가) | `ai_extraction_job`, `youtube_channel_watch` | Webhook·관리자 요청 수렴, URL·입력 hash·Provider/Prompt/Schema 버전 멱등성 |
| [FR-AIEXTRACT-006](../../01-requirements/functional-requirements.md#fr-aiextract-006-webhook-감시-채널-관리) | `youtube_channel_watch` | Creator·YouTube channel 고유성, 활성·구독·갱신·오류 상태 |
| [FR-NLSEARCH-001](../../01-requirements/functional-requirements.md#fr-nlsearch-001-자연어-검색-요청과-결과-조회)·[FR-NLSEARCH-002](../../01-requirements/functional-requirements.md#fr-nlsearch-002-자연어-조건과-직접-필터-조합)·[FR-NLSEARCH-004](../../01-requirements/functional-requirements.md#fr-nlsearch-004-확정-태그-조건과-결과-조회) | `tag_definition`, `visit_tag` 조회 | 해석 조건은 요청 범위 값이며 기존 Restaurant·Region·FoodCategory·Creator·Visit와 확정 태그 조회를 사용 |
| [FR-NLSEARCH-003](../../01-requirements/functional-requirements.md#fr-nlsearch-003-빈-결과와-해석-실패) | 신규 검색 이력 없음 | `APPLIED·PARTIAL·FAILED`와 빈 목록은 응답 파생값, 원문·검색 이력 비저장 |
| [FR-COURSE-001](../../01-requirements/functional-requirements.md#fr-course-001-코스-후보-입력)·[FR-COURSE-002](../../01-requirements/functional-requirements.md#fr-course-002-이동-순서와-경로-조회) | 신규 영속 데이터 없음 | 공개 Restaurant 좌표 조회와 기존 `favorite` 관계를 인증된 개인 찜 API로 명시적으로 조회한 뒤 외부 Route 응답을 요청 범위에서 조합 |
| [FR-COURSE-003](../../01-requirements/functional-requirements.md#fr-course-003-외부-경로-실패-시-대체-결과) | 신규 영속 데이터 없음 | 실패 범주·입력 순서·최소 표시 정보만 오류 응답으로 반환 |

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
| [BR-AUTH-009](../../01-requirements/business-rules.md#br-auth-009-통합-계정과-rbac)·[BR-AUTH-010](../../01-requirements/business-rules.md#br-auth-010-보안-속성-변경과-즉시-폐기) | 통합 계정·RBAC·즉시 폐기 | `member_account.role`, Redis `auth:session:` | `MEMBER|ADMIN` CHECK, role NOT NULL DEFAULT MEMBER | 공개 role 입력 거부, 변경 시 전 세션 폐기 |
| [BR-AIEXTRACT-001](../../01-requirements/business-rules.md#br-aiextract-001-ai-후보-생성-범위)·[BR-AIEXTRACT-002](../../01-requirements/business-rules.md#br-aiextract-002-자동-검증-없는-정식-저장-금지) | AI 후보와 자동 등록 경계 | 자동 검증 전 Snapshot만 저장, 통과 시 핵심 Entity와 VisitTag 원자 생성 | 자동 등록 상태 CHECK, 통과 시 기존 등록 흐름 | 필요 |
| [BR-AIEXTRACT-003](../../01-requirements/business-rules.md#br-aiextract-003-동일-영상-중복-추출)·[BR-AIEXTRACT-004](../../01-requirements/business-rules.md#br-aiextract-004-모델prompt결과-schema-버전) | 중복·버전 관리 | Job 멱등 unique, Attempt·Snapshot 버전 이력 | 복합 unique, 버전 NN | 필요 |
| [BR-AIEXTRACT-005](../../01-requirements/business-rules.md#br-aiextract-005-영상-유입-경로와-작업-수렴)·[BR-AIEXTRACT-006](../../01-requirements/business-rules.md#br-aiextract-006-webhook-감시-채널-상태)·[BR-AIEXTRACT-007](../../01-requirements/business-rules.md#br-aiextract-007-gemini-영상-입력과-fallback) | 유입 경로·채널·Gemini fallback | Job source/priority, ChannelWatch, 원문 비저장 | lease·활성 상태·Provider 시도 이력 | 필요 |
| [BR-AIEXTRACT-008](../../01-requirements/business-rules.md#br-aiextract-008-태그-후보-자동-등록과-공개) | 태그 후보 통제·공개 | TagDefinition 허용 코드, VisitTag 확정 연결 | 태그 코드·Visit 연결 unique, 공개 상태 조합 | 필요 |
| [BR-AIEXTRACT-009](../../01-requirements/business-rules.md#br-aiextract-009-장소-동일성-자동-확정)·[BR-AIEXTRACT-010](../../01-requirements/business-rules.md#br-aiextract-010-대표-음식-카테고리-자동-선정) | 장소·카테고리 자동 판정 | `ai_registration_unit`에 등록 단위별 상태·장소·카테고리 근거·등록 결과 보존 | 단위 순번 unique, 확정 상태와 등록 결과 조합 CHECK | 필요 |
| [BR-AIEXTRACT-011](../../01-requirements/business-rules.md#br-aiextract-011-등록-단위-일괄-등록과-예외-전환) | 등록 단위 일괄 등록 | 맛집·유튜버·영상·방문 관계 4종을 하나의 트랜잭션으로 저장, 실행 주체와 등록 결과 보존 | `executed_by` CHECK, 맛집·방문 등록 결과 FK, 부분 저장 0건 | 필요 |

## 5. API 요청 → 데이터 변경 매핑

| API ID | 요청 목적 | 생성·변경 데이터 | 필수 참조 | 원자성 범위 | 담당 Workstream |
|---|---|---|---|---|---|
| [API-MEMBER-AUTH-006](../api/account/member-authentication-api.md#api-member-auth-006-통합-로그인) | 회원·관리자 통합 로그인 | Redis `auth:session:` 생성·역할별 활성 세션 정리 | `member_account` | MEMBER 최대 3개·ADMIN 최대 1개 | [WS-05](../../02-analysis/first-expansion-workstreams.md#4-ws-05-사용자-계정인증)·[WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |
| [API-MEMBER-AUTH-007](../api/account/member-authentication-api.md#api-member-auth-007-access-token-재발급) | 토큰 재발급 | 기존 session 폐기·통합 session 회전 | `member_account`, `auth:session:` | 검증·회전 원자성 | [WS-05](../../02-analysis/first-expansion-workstreams.md#4-ws-05-사용자-계정인증) |
| [API-MEMBER-AUTH-008](../api/account/member-authentication-api.md#api-member-auth-008-로그아웃) | 로그아웃 | 통합 session 폐기 | `auth:session:` | 현재 session 하나 | [WS-05](../../02-analysis/first-expansion-workstreams.md#4-ws-05-사용자-계정인증) |
| [API-ADMIN-RESTAURANT-PREVIEW-001](../api/admin/reference-data-api.md#api-admin-restaurant-preview-001-맛집-등록-검증-미리보기) | 외부 장소·입력 검증 | 핵심 Entity 변경 없음, `READY`이면 ConfirmationToken 기술 행 생성 | Region, FoodCategory 기준 | Token 발급 행 하나 | [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |
| [API-ADMIN-RESTAURANT-001](../api/admin/reference-data-api.md#api-admin-restaurant-001-맛집-등록-확정) | 맛집 생성 | Restaurant와 필수 참조 연결 | Region, FoodCategory | Restaurant 한 건 전체 | [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) / Restaurant |
| [API-ADMIN-CREATOR-PREVIEW-001](../api/admin/reference-data-api.md#api-admin-creator-preview-001-유튜버-등록-검증-미리보기) | 외부 채널 검증 | 핵심 Entity 변경 없음, `READY`이면 ConfirmationToken 기술 행 생성 | 없음 | Token 발급 행 하나 | [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |
| [API-ADMIN-CREATOR-001](../api/admin/reference-data-api.md#api-admin-creator-001-유튜버-등록-확정) | Creator 생성 | Creator | 없음 | Creator 한 건 전체 | [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) / Creator |
| [API-ADMIN-VIDEO-PREVIEW-001](../api/admin/reference-data-api.md#api-admin-video-preview-001-영상-등록-검증-미리보기) | 외부 영상·게시 채널 검증 | 핵심 Entity 변경 없음, `READY`이면 ConfirmationToken 기술 행 생성 | 게시 채널 후보 | Token 발급 행 하나 | [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |
| [API-ADMIN-VIDEO-001](../api/admin/reference-data-api.md#api-admin-video-001-영상-등록-확정) | Video 생성 | Video와 게시 채널 외부 식별 | 없음(내부 Creator 연결 선택) | Video 한 건 전체 | [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) / Video |
| [API-ADMIN-VISIT-001](../api/admin/visit-registration-api.md#api-admin-visit-001-방문-관계-등록) | 방문 관계 생성 | Visit, 필요 시 Video.Creator 연결 | Restaurant, Creator, Video | 채널 연결 해소·검증·복합 중복·저장 전체 | [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) / Visit |
| API-ADMIN-AIEXTRACT-001 | 관리자 신규 영상 추가·조회·재시도·자동 등록·예외 보정 | `ai_extraction_job`, `ai_candidate_snapshot`, `ai_extraction_attempt` | 자동 검증 통과 뒤 기존 Creator·Restaurant·Video·Visit와 VisitTag로 연결 | Job·Snapshot·정식 Entity 경계를 분리하고 통과 시 원자 생성 | [WS-15](../../02-analysis/third-expansion-workstreams.md#6-ws-15-ai-영상-정보-추출) |
| API-ADMIN-AIEXTRACT-WEBHOOK-001~002 | YouTube 구독 확인·신규 영상 알림 | `youtube_channel_watch`, 필요 시 `ai_extraction_job` | 등록된 Creator·channel watch | 검증·중복 Job 접수만 원자 처리, AI·정식 등록 호출 없음 | [WS-15](../../02-analysis/third-expansion-workstreams.md#6-ws-15-ai-영상-정보-추출) |
| [API-DISCOVERY-NL-001](../api/discovery/natural-language-restaurant-discovery-api.md) | 자연어 해석·기존 목록 조회 | 신규 영속 데이터 없음 | Restaurant·Region·FoodCategory·Creator·Visit 공개 조회 | 해석 조건·충돌·상태는 요청 응답 파생값 | [WS-14](../../02-analysis/third-expansion-workstreams.md#5-ws-14-자연어-맛집-탐색) |
| [API-DISCOVERY-COURSE-001](../api/discovery/restaurant-course-recommendation-api.md) | 선택 맛집 경로 계산 | 신규 영속 데이터 없음 | 공개 Restaurant 좌표·Kakao Mobility 응답 | 순서·구간·거리·시간·만료는 요청 응답 파생값 | [WS-16](../../02-analysis/third-expansion-workstreams.md#7-ws-16-맛집-코스-추천) |

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
| [API-MEMBER-AUTH-006](../api/account/member-authentication-api.md#api-member-auth-006-통합-로그인)·[API-MEMBER-AUTH-007](../api/account/member-authentication-api.md#api-member-auth-007-access-token-재발급)·[API-MEMBER-AUTH-009](../api/account/member-authentication-api.md#api-member-auth-009-현재-사용자-정보) | 인증·재발급·현재 계정 | `auth:session:` | `member_account` 활성 여부·현재 role | JWT Access Token, 만료 시간, role | [WS-05](../../02-analysis/first-expansion-workstreams.md#4-ws-05-사용자-계정인증)·[WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |
| [API-DISCOVERY-NL-001](../api/discovery/natural-language-restaurant-discovery-api.md) | 자연어 해석 결과·목록 | 기존 Restaurant 조회 조합 | Region, FoodCategory, Creator, Visit | `interpretation.status`, 적용·무시·충돌 조건, page | [WS-14](../../02-analysis/third-expansion-workstreams.md#5-ws-14-자연어-맛집-탐색) |
| [API-DISCOVERY-COURSE-001](../api/discovery/restaurant-course-recommendation-api.md) | 코스 경로 결과 | 기존 Restaurant 조회 | 좌표·Route Provider 결과 | 순서·segments·total distance/time·generatedAt·expiresAt | [WS-16](../../02-analysis/third-expansion-workstreams.md#7-ws-16-맛집-코스-추천) |
| API-ADMIN-AIEXTRACT-001 | AI 작업 목록·상세·자동 등록·예외 보정 결과 | `ai_extraction_job`, `ai_candidate_snapshot`, `ai_extraction_attempt` | 후보 필드·근거·오류·자동 등록 상태 | `resultCompleteness`, `reviewStatus`, `attemptCount`, 상태별 목록 | [WS-15](../../02-analysis/third-expansion-workstreams.md#6-ws-15-ai-영상-정보-추출) |
| API-ADMIN-AIEXTRACT-WEBHOOK-001~002 | Webhook 접수 결과 | `youtube_channel_watch`, `ai_extraction_job` | channel·video 외부 식별자 | `204 No Content`, traceId는 로그·운영 추적에만 사용 | [WS-15](../../02-analysis/third-expansion-workstreams.md#6-ws-15-ai-영상-정보-추출) |

`contentStatus`, 페이지 메타데이터, 후보 `decision`, `remainingVisitedByCount`는 엔티티에 저장하지 않는다.

## 7. Workstream → 데이터 소유권 매핑

| Workstream | 변경 소유 | 조회·의존 데이터 | 책임 경계 |
|---|---|---|---|
| [WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) 맛집 탐색 | Restaurant 조회 규칙 | Region, FoodCategory, Visit·Creator 판정 결과 | Visit 규칙을 재구현하지 않음 |
| [WS-02](../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) 상세 및 콘텐츠 | 상세 조합 | Restaurant, Visit, Creator, Video | 기본 데이터와 관계를 임의 변경하지 않음 |
| [WS-03](../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) 유튜버 기반 탐색 | Visit 관계 판정 계약 | Creator, Video, Restaurant 상태 | 최종 Restaurant 페이지 조합은 [WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) |
| [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 관리자 등록 | ADMIN 인가·등록 흐름 조율 | `member_account.role`·`auth:session:` 및 네 소유 도메인 | 계정 인증은 WS-05와 공유하고 도메인 고유·정합성 규칙을 우회하지 않음 |
| [WS-15](../../02-analysis/third-expansion-workstreams.md#6-ws-15-ai-영상-정보-추출) AI 영상 정보 추출 | `ai_extraction_job`, `ai_candidate_snapshot`, `ai_extraction_attempt`, `youtube_channel_watch` | 기존 등록 Entity와 외부 Provider 결과를 직접 소유하지 않음 | 후보·정식 저장·Worker lease 경계 소유 |

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
| 통합 계정 기반 | `FR-MEMBER-001`~`005`, `FR-AUTH-001`~`004`, 통합 계정·인증 API | `member_account.role`, `member_action_token`, `member_session_revocation`, `member_action_mail_outbox`, `member_deletion_job`, `member_session_revocation_recovery`, Redis `auth:session:` | 이메일 고유성, `MEMBER|ADMIN` CHECK, 공개 가입 MEMBER 고정, MEMBER 최대 3세션·ADMIN 최대 1세션, 역할·상태·비밀번호 변경 시 전 세션 폐기 | WS-05·WS-04 |
| 찜 | `FR-FAVORITE-001`~`004`, `API-PERSONAL-001`~`004` | `favorite` | `(member_id, restaurant_id)` PK, 회원 삭제 cascade, 맛집 물리 삭제 전 정리 | WS-06 |
| 최근 본 맛집 | `FR-RECENT-001`~`003`, `API-PERSONAL-005`~`006`, 공개 상세 부수효과 | `recent_restaurant_view` | 복합 PK upsert·최신 시각순·50건 상한, 주기 cleanup Command의 30일 물리 삭제, GET은 읽기 전용 | WS-06 |
| 지도 탐색 | `FR-MAP-001`~`002`, `API-MAP-001` | `restaurant.latitude`, `restaurant.longitude` | nullable WGS84 쌍, 범위 CHECK, 좌표 없음은 지도에서만 제외 | WS-07 |
| 유튜버 상세 | `FR-CREATOR-004`~`006`, `API-CREATOR-DETAIL-001`~`003` | `creator.profile_image_url`, `description`, `handle`과 기존 Creator·Visit·Video | 선택값은 null 또는 유효한 값, 사용자 조회 중 외부 API 호출 없음 | WS-08 |
| 검증 참여자 제한 공개 (역사) | `API-VALIDATION-001`~`002`, `ADR-DEPLOY-003`~`006` | M2 당시 Redis `auth:verification:` 세션·실패 제한 | 128-bit 이상 세션 원문의 SHA-256 해시, 7일 고정 만료, 제품 통합 인증과 분리. 정식 공개 전환으로 namespace와 API 제거 | [OPS-VALIDATION](../../02-analysis/first-expansion-workstreams.md#ops-validation-공통-운영배포-트랙) |

각 물리 계약은 [테이블 정의](table-definitions.md#13-v3-회원-인증-하드닝-데이터-계약), [제약조건](constraints.md), [인덱스 전략](index-strategy.md#5-1차-확장-인덱스), [생명주기 규칙](lifecycle-rules.md#101-회원-개인화-관계-정리), [마이그레이션 계획](migration-plan.md#9-1차-확장-마이그레이션-구성-통합-이전-구간별-기록)을 함께 따른다.

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

## 11. 3차 확장 데이터 추적

| 데이터 범위 | 소유 요구사항·API | 계약·보류 | Workstream | 다음 검증 |
|---|---|---|---|---|
| `ai_extraction_job` | FR-AIEXTRACT-001~007, API-ADMIN-AIEXTRACT-001 | [AI 영상 추출 데이터 계약](third-expansion-ai-video-data-contract.md), 현재 Gemini P8/S2·기존 P1·P2·P3·P4·P5·P6·P7 이력·모델·보존 정책 Accepted | WS-15 | 중복 접수·lease 복구·재시도·원자성 |
| `ai_extraction_temporary_input` | BR-AIEXTRACT-007, NFR-PRIVACY-006 | 관리자 보완 텍스트 암호화 임시 저장, 작업 종료 후 24시간 이내 삭제, Webhook 작업 미생성 | WS-15 | 재시작 복구·암호화·자동 삭제·재시도 입력 재사용 금지 |
| `ai_candidate_snapshot` | FR-AIEXTRACT-002~003·007, BR-AIEXTRACT-001~004·008 | 필드·태그 후보 Schema·근거·자동 등록 상태 버전 보존, 정식 Entity와 분리 | WS-15 | 부분 추출·환각·태그 오분류·자동 차단·폐기 |
| `ai_registration_unit` | FR-AIEXTRACT-003, BR-AIEXTRACT-001·009·010·011, API-ADMIN-AIEXTRACT-001 | Snapshot의 장소 단위 등록 단위와 단위별 판정 상태·차단 사유·장소·카테고리 근거·맛집·유튜버·영상·방문 등록 결과·재사용 자원 | WS-15 | 다장소 영상 독립 판정, 부분 차단 시 원자성 경계, 등록 완료·롤백 완료·폐기 완료 `MANUAL_OVERRIDE` 구분, 단위별 롤백 |
| `ai_registration_unit_review` | BR-AIEXTRACT-011, API-ADMIN-AIEXTRACT-001 | `CONFIRM`·`DISCARD`·`ROLLBACK`·`ADJUST_CATEGORY` 등록 단위 사후 조작의 append-only 감사 이력, 사유·제출자·보충값·이전 카테고리·되돌린 등록 식별자 보존 | WS-15 | 반복 보정 이력 재현, 현재 상태 계산과 분리된 감사 전용 저장 |
| `food_category_mapping` | BR-AIEXTRACT-010, API-ADMIN-AIEXTRACT-001 | Kakao 분류·메뉴 표현을 공통 10개 카테고리에 대응시키는 기준정보, 일치 방식·우선순위·활성 상태 | restaurant 도메인 소유·WS-15 사용 | 복수 일치 차단, 별칭·부분 일치, 비활성 제외, seed 고정 데이터 |
| `ai_candidate_tag_review` | BR-AIEXTRACT-008, API-ADMIN-AIEXTRACT-001 | 후보 태그별 자동 판단·사후 보정 append-only 이력, `UNKNOWN` AI 근거 확정 금지 | WS-15 | 자동 판단·사후 보정 이력·VisitTag 연결 |
| `ai_extraction_attempt` | BR-AIEXTRACT-004·007, NFR-EXTERNAL-005 | Provider request 식별·오류 분류·토큰·무료 quota 사용량 집계만 저장, 원문 미저장 | WS-15 | timeout·429·5xx·무료 quota hard stop |
| `youtube_channel_watch` | FR-AIEXTRACT-004·006, API-ADMIN-AIEXTRACT-WEBHOOK-001~002 | Creator·channel unique, 구독·갱신·오류 상태 | WS-15 | 구독 확인·중복 알림·해지·renewal 실패 |
| `tag_definition` | FR-AIEXTRACT-007, BR-AIEXTRACT-008, API-ADMIN-AIEXTRACT-001 | `MENU/TASTE/OCCASION/ATMOSPHERE` 통제 코드·별칭·활성 상태 | WS-15 | 별칭 충돌·폐기·후보 허용값 |
| `visit_tag` | FR-AIEXTRACT-007·FR-NLSEARCH-004, BR-AIEXTRACT-008·BR-NLSEARCH-003, API-ADMIN-AIEXTRACT-001·API-DISCOVERY-NL-001 | 자동 확정 또는 관리자 사후 보정 태그와 Visit 연결, `(visit_id, tag_definition_id)` unique | WS-15 생성·WS-14 조회 | 자동 검증 전 공개 금지·태그 AND·Visit 비공개 전파 |
| 자연어 해석 결과 | FR-NLSEARCH-001~004, API-DISCOVERY-NL-001 | 검색 이력·원문·임베딩 비저장, 기존 조회와 확정 태그만 사용 | WS-14 | 해석 상태·조건 병합·로그 마스킹·기존 목록 격리 |
| 코스 경로 결과 | FR-COURSE-001~003, API-DISCOVERY-COURSE-001 | `Course`·Route 결과·현재 위치·선택 이력 비저장, 요청 시점 응답만 반환 | WS-16 | 좌표·외부 실패·30km·TTL·quota·호출 1회 |

정확한 SQL 컬럼·FK·partial index·Flyway 순서는 [AI 영상 추출 데이터 계약](third-expansion-ai-video-data-contract.md)의 Accepted 논리 계약을 따라 물리 명세와 migration 계획에 반영한다. 자연어 검색 요청·해석 이력은 저장하지 않지만, 검색 대상인 통제 태그와 확정 Visit 연결은 AI 후보 데이터 계약의 영속 범위에 포함한다. 코스 추천은 별도 영속 데이터 계약을 만들지 않고 기존 좌표 데이터와 요청 범위 외부 응답을 조합한다.

### 11.1 3차 확장 데이터 → ADR·평가·테스트 검증

| 데이터 범위 | 근거 문서 | 품질·운영 검증 | 책임 |
|---|---|---|---|
| Job·Snapshot·Attempt | [ADR-AI-001](../../07-adr/integration/ai-001-video-extraction-candidate-boundary.md), [ADR-EXT-003](../../07-adr/integration/ext-003-ai-extraction-async-reliability.md) | `TST-E3-AI-002~004`, `TST-E3-DATA-001`, [3차 확장 평가 전략](../../08-planning/third-expansion-evaluation-strategy.md)의 `EVAL-AI-001~010`, [120건 계약 자산·dry-run·HOLD 기록](../../08-planning/third-expansion-ai-evaluation-result.md) | WS-15, `E3-T03~08`, `E3-T11`, `E3-T13` |
| Channel Watch·Webhook | [AI 영상 추출 API](../api/admin/ai-video-extraction-api.md) | `TST-E3-AI-001`, `TST-E3-AI-004`, Atom 유효성·중복·Token·대형 Payload·AI 호출 격리 | WS-15, `E3-T04~05` |
| 자연어 검색·코스 응답 | [자연어 맛집 탐색 API](../api/discovery/natural-language-restaurant-discovery-api.md), [맛집 코스 추천 API](../api/discovery/restaurant-course-recommendation-api.md) | `TST-E3-NL-*`, `TST-E3-COURSE-*`, 별도 저장 없음·기존 조회/좌표 데이터와 외부 응답 조합·실패 격리, 운영 ACTIVE·공개 맛집 좌표 보강률 읽기 전용 측정 | WS-14·WS-16, `E3-T01~02`, `E3-T09~10`, `E3-T13` |

## 12. 미매핑 항목

- Restaurant 설명·대표 이미지·영업 정보는 확정 요구사항/API가 없어 저장 모델에서 제외했다.
- Creator 구독자 수·조회 수 같은 통계와 Video 게시일의 외부 API 노출, Visit 방문일·검증 상태·검증자는 저장 모델에서 제외하거나 선택 데이터다. V6 상세 표시 필드인 Creator 프로필 이미지·소개·handle은 저장 계약에 포함한다.
- 수정·삭제·승인·보류 목록 API가 없으므로 관련 운영 전환은 API 변경으로 만들지 않았다.
- 로그인 실패 제한 카운터는 저장 방식이 미정이다. 확인 Token은 PostgreSQL 단기 기술 테이블로 확정됐지만 핵심 도메인 ERD에는 포함하지 않는다.

## 13. 변경 영향 추적

- 지역 단계·범위 변경: Region, Restaurant, 탐색/등록 API와 [BR-RESTAURANT-005](../../01-requirements/business-rules.md#br-restaurant-005-맛집의-지역-소속)를 함께 검토한다.
- 다중 카테고리 변경: Restaurant–FoodCategory 카디널리티, 필터 API와 [BR-RESTAURANT-004](../../01-requirements/business-rules.md#br-restaurant-004-대표-음식-카테고리)를 함께 변경한다.
- Creator를 개인 단위로 변경: Creator·Video·Visit 식별과 모든 유튜버 API를 재설계한다.
- 복수 근거·방문일 도입: Visit 모델, 복합 유일성, 관리자 요청과 상세 응답을 재검토한다.
- 공개·삭제 정책 변경: 네 핵심 데이터, 모든 공개 조회와 운영 정정 흐름을 함께 검토한다.
- 외부 동기화 도입: Creator·Video 상태·이력, 외부 호출 NFR과 운영 책임을 추가한다.

## 14. 3차 확장 데이터 → 테스트·Task 완료 추적

| 데이터·경계 | 테스트 묶음 | Task | 완료 판정 |
|---|---|---|---|
| AI Job·Snapshot·Attempt·Tag Review | `TST-E3-AI-002~004`, `TST-E3-DATA-001`, [`EVAL-AI-001~010` 계약 자산·dry-run·HOLD 기록](../../08-planning/third-expansion-ai-evaluation-result.md) | `E3-T03~08`, `E3-T11`, `E3-T13` | lease·버전·보존·정식 저장 0건·원자성·태그 공개 경계 증거 |
| TagDefinition·VisitTag | `TST-E3-NL-001`, `TST-E3-AI-003`, `TST-E3-DATA-001` | `E3-T01`, `E3-T06` | 허용 태그·근거·중복·공개 Visit·태그 AND 증거 |
| 자연어·코스 파생 응답 | `TST-E3-NL-*`, `TST-E3-COURSE-*`, `TST-E3-PERF-001` | `E3-T01~02`, `E3-T09~10`, `E3-T13` | 원문·코스 결과 비저장, 공개 상태·좌표·TTL·외부 실패, 운영 좌표 보강률 측정·조치·재측정 증거 |

물리 migration·테이블 정의·제약·인덱스 문서가 실제 `V4`와 일치하는지 확인한 뒤 데이터 Task를 완료한다. 논리 계약 Accepted와 물리 실행 증거는 별도로 판정한다.

### 14.1 E3-T13 최종 게이트 증거

자연어·코스의 비저장 경계, 공개 좌표 데이터 의존성, AI Worker lease·quota 경계에 대한 자동화 결과와 운영 측정 보류 사유는 [E3-T13 최종 게이트 판정](../../08-planning/third-expansion-final-gate-result.md)에 기록한다. 운영 좌표 보강률과 Worker·Mobility 측정이 없으면 데이터·운영 완료로 판정하지 않는다.

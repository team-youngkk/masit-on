---
status: Review
review_date: 2026-07-29
baseline_commit: f70ed19
related_documents:
  - README.md
  - mvp-2day-implementation-plan.md
  - mvp-local-verification.md
  - ../01-requirements/functional-requirements.md
  - ../01-requirements/non-functional-requirements.md
  - ../04-product/prd/discovery/creator-discovery.md
  - ../05-specs/data/migration-plan.md
  - ../06-architecture/security-boundary.md
  - ../07-adr/security/auth-001-spring-security-jwt.md
  - ../07-adr/data/data-005-redis-refresh-token.md
---

# 1차 확장 구현 기준선 검토

## 1. 목적

1차 확장 범위와 구현 계획을 정하기 전에 현재 저장소의 실제 구현 상태를 기준선으로 고정한다. 과거 계획의 완료 표시는 판정 근거로 사용하지 않고, 현재 코드·테스트·실행 결과를 우선한다.

이 문서는 계약을 변경하지 않는다. 구현과 확정 계약이 다르면 양쪽을 분리해 기록하고, 제품·데이터·인증 정책을 임의로 결정하지 않는다.

## 2. 조사 기준

| 항목 | 값 |
|---|---|
| 조사일 | 2026-07-29 |
| 브랜치 | `develop` |
| 기준 커밋 | `f70ed19` |
| 작업 트리 | 사용자 변경으로 보이는 `.gitignore` 수정 1건이 있어 보존 |
| 판정 우선순위 | 실제 코드 → 현재 테스트 → 계약 문서 → 과거 계획·검증 기록 |
| 상태 | 완료 / 부분 완료 / 미완료 / 미확인 |

`완료`는 사용자 흐름, 백엔드 구현과 관련 자동화 테스트가 현재 계약을 충족한다는 뜻이다. `부분 완료`는 하위 계층이 구현됐지만 최종 사용자 흐름 또는 품질 게이트가 닫히지 않은 상태다.

## 3. 요약

| 조사 대상 | 현재 기준선 |
|---|---|
| 1차 MVP 기능 | 기능 요구사항 20개 중 완료 20개(E1-T02·PR #67로 잔여 3개 종료), 완전 미구현 0개 |
| 사용자 인증 | 일반 사용자 인증 없음. 사전 발급 `ADMIN` 계정용 인증만 존재 |
| JWT·Redis 재사용 | 암호화·검증·회전 기술은 재사용 가능하나 principal, audience, 쿠키, Redis key와 DB 조회가 관리자에 결합 |
| 맛집 좌표 | Kakao 응답 파싱부터 DB·API까지 전 구간 미저장 |
| Creator 표시 정보 | 채널 ID·이름·URL과 공개·생명주기·외부 이용 상태만 보유. 프로필·소개·핸들·구독자 없음 |
| 프론트 | 공개 목록·상세, 관리자 로그인·등록 Route, 공개 유튜버 선택 UI(E1-T02, PR #67) 구현 |
| Flyway | 단일 `V1__create_initial_schema.sql` baseline. 다음 변경은 `V2` 이상 새 파일 |
| 테스트 | 백엔드 테스트 클래스 로딩 실패 4건을 E1-T01(PR #63)이 고쳐 CI에서 통과한다. 프론트 타입 검사·빌드는 계속 성공 |
| CI | E1-T01(PR #63)이 `.github/workflows/ci.yml`(백엔드 빌드·테스트, 프론트엔드 빌드·타입 검사)을 추가해 `NFR-TEST-003` 품질 게이트를 충족한다 |

## 4. 1차 MVP 완료·미완료 기능

### 4.1 요구사항 판정

| 범위 | 요구사항 | 상태 | 실제 구현 근거 |
|---|---|---|---|
| 맛집 목록·검색·필터·페이지 | `FR-RESTAURANT-001~004`, `FR-RESTAURANT-006~007` | 완료 | [RestaurantSearchController](../../src/main/java/com/masiton/restaurant/presentation/rest/RestaurantSearchController.java), [목록 화면](../../frontend/app/restaurants/page.tsx), `RestaurantSearchApiTest`, `RestaurantSearchQueryAdapterIntegrationTest` |
| 검색·필터 조건 조합 | `FR-RESTAURANT-005` | 완료 | 백엔드 AND 조합에 더해 [목록 화면](../../frontend/app/restaurants/page.tsx)이 유튜버 select를 다른 필터와 같은 폼에서 조합한다(E1-T02, PR #67) |
| 유튜버 기준 맛집 조회 | `FR-CREATOR-001` | 완료 | `GET /api/restaurants?creatorId=...`와 인수 테스트에 더해 [목록 화면](../../frontend/app/restaurants/page.tsx)이 유튜버 선택 UI로 `creatorId`를 구성한다(E1-T02, PR #67) |
| 유튜버 선택 목록 | `FR-CREATOR-003` | 완료 | [CreatorController](../../src/main/java/com/masiton/creator/presentation/rest/CreatorController.java)와 `CreatorApiTest`에 더해 [restaurants-api.ts](../../frontend/lib/restaurants-api.ts)의 `fetchCreators`가 `/restaurants`에서 호출·표시한다(E1-T02, PR #67) |
| 맛집 상세·방문 유튜버·영상 | `FR-RESTAURANT-008~011`, `FR-CREATOR-002`, `FR-VIDEO-001` | 완료 | [RestaurantDetailController](../../src/main/java/com/masiton/orchestration/presentation/detail/RestaurantDetailController.java), [상세 화면](../../frontend/app/restaurants/[id]/page.tsx), `RestaurantDetailApiTest`, `VisitContentQueryIntegrationTest` |
| 관리자 인증 | `FR-ADMIN-001` | 완료 | [AdminAuthenticationController](../../src/main/java/com/masiton/security/presentation/AdminAuthenticationController.java), [로그인 화면](../../frontend/app/admin/login/page.tsx), `SecurityConfigurationApiTest`, `AdminAuthenticationServiceTest` |
| 맛집·유튜버·영상 등록 | `FR-ADMIN-002~004` | 완료 | 세 등록 Controller·서비스·관리자 화면, 각 서비스와 API 테스트 |
| 방문 관계 등록·조회 반영 | `FR-VISIT-001` | 완료 | [VisitRelationshipRegistrationController](../../src/main/java/com/masiton/orchestration/presentation/VisitRelationshipRegistrationController.java), [방문 등록 화면](../../frontend/app/admin/visits/new/page.tsx), `VisitRelationshipRegistrationIntegrationTest`, `AdminRegistrationJourneyAcceptanceTest` |

유튜버 탐색 계약은 별도 `/creators` 화면이 아니라 `/restaurants`의 단일 선택 필터를 요구한다. [유튜버 기반 탐색 PRD](../04-product/prd/discovery/creator-discovery.md)는 공개 유튜버 최소 선택 목록과 탐색 화면에서의 단일 선택을 Must로 두며, E1-T02(PR #67)가 이 선택 UI를 완성해 API URL 직접 구성 없이도 세 요구사항의 사용자 흐름을 닫았다.

### 4.2 구현은 있으나 운영·확장 전에 정리할 제약

- 방문 관계 화면은 맛집·유튜버는 조회해 선택하지만 영상 선택 API가 없어 영상 UUID를 수기로 입력한다. 핵심 등록 기능은 동작하지만 운영 UX 제약이 크다.
- 관리자 인증 게이트는 서버 middleware나 `admin` layout 경계가 아니라 각 등록 화면의 `AdminPage`가 실행하는 client gate다.
- 관리자 화면도 공개 Root Layout의 Header·Footer를 공유한다.

## 5. 현재 인증 구조

### 5.1 관리자 인증

1. `POST /api/admin/auth/tokens`가 `loginId`·비밀번호를 검증한다.
2. Access Token은 RS256 JWT로 발급해 JSON으로 반환한다.
3. Refresh Token은 무작위 opaque token으로 발급하고 `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/admin/auth` 쿠키에 둔다.
4. 브라우저는 Access Token을 메모리에만 보관한다. 새로고침 시 Refresh 쿠키로 Access Token을 복구한다.
5. `POST /api/admin/auth/tokens/refresh`가 Redis 상태를 원자적으로 회전한다.
6. `DELETE /api/admin/auth/tokens`가 Redis Refresh 상태와 쿠키를 폐기한다.
7. `/api/admin/**`는 JWT의 `ADMIN` authority를 요구하며, 공개 GET 3종 이외의 `/api/**`는 기본 거부한다.

주요 근거는 [SecurityConfiguration](../../src/main/java/com/masiton/security/infrastructure/configuration/SecurityConfiguration.java), [AdminAuthenticationService](../../src/main/java/com/masiton/security/application/AdminAuthenticationService.java), [AdminAuthenticationController](../../src/main/java/com/masiton/security/presentation/AdminAuthenticationController.java), [프론트 인증 모듈](../../frontend/lib/admin/auth.ts)이다.

JWT claim은 `iss`, 관리자 UUID인 `sub`, `aud=masit-on-admin-api`, `roles=[ADMIN]`, `iat`, `exp`로 구성되고 JOSE header에 `kid`를 둔다. 관리자 계정은 PostgreSQL `admin_account`에 저장하며 역할 CHECK도 `ADMIN` 하나만 허용한다.

### 5.2 일반 사용자 인증

일반 사용자 계정·로그인·OAuth·세션·보호 Route는 구현되어 있지 않다. 현재 공개 기능은 무인증 조회이며 DB에도 사용자 계정 테이블이 없다.

## 6. 관리자 JWT·Redis 재사용 가능성

### 6.1 재사용 가능한 기술 요소

| 요소 | 현재 구현 |
|---|---|
| JWT 서명·검증 | RS256, PEM parsing, `kid`, 복수 검증키, issuer·audience 검증 |
| Resource Server | stateless Bearer 인증, roles → authority 변환, 공통 401·403 응답 |
| Refresh Token 생성 | 48-byte CSPRNG opaque token |
| Redis 저장 | 원문 대신 SHA-256 hash, TTL 적용 |
| Refresh 회전 | token family와 Lua script를 이용한 원자 회전·재사용 탐지 |
| 로그인 실패 제한 | login ID와 source를 hash한 시도 횟수·TTL 제한 |
| 비밀번호 | BCrypt encoder |

주요 구현은 [JwtConfiguration](../../src/main/java/com/masiton/security/infrastructure/configuration/JwtConfiguration.java), [JwtTokenIssuer](../../src/main/java/com/masiton/security/infrastructure/token/JwtTokenIssuer.java), [RedisRefreshTokenStore](../../src/main/java/com/masiton/security/infrastructure/redis/RedisRefreshTokenStore.java), [RedisLoginFailureStore](../../src/main/java/com/masiton/security/infrastructure/redis/RedisLoginFailureStore.java)다.

### 6.2 그대로 재사용할 수 없는 결합

- principal, role, UseCase와 Port가 `Admin*` 타입과 `adminId` 의미에 결합돼 있다.
- JWT audience가 관리자 API 하나로 고정돼 있다.
- Redis key가 `auth:refresh:{adminId}`라 principal 종류 namespace가 없고 한 subject당 활성 token family가 하나다.
- 쿠키 이름과 Path가 관리자 인증 경계에 고정돼 있다.
- 자격 증명 SQL은 `admin_account`와 `ADMIN` 단일 역할에 고정돼 있다.
- 단일 SecurityFilterChain은 일반 사용자 보호 API를 정의하지 않고 나머지 `/api/**`를 거부한다.
- CSRF가 전역 비활성화돼 있어 일반 사용자 인증 전달 방식을 정한 뒤 다시 검토해야 한다.

따라서 일반 사용자 인증은 기존 구현을 복사하는 작업이 아니라, 공통 암호 기술을 유지하면서 identity·session 경계를 먼저 결정하는 작업이다.

## 7. 맛집 좌표 저장 여부

현재 좌표는 저장하거나 노출하지 않는다.

| 계층 | 현재 상태 |
|---|---|
| Kakao Adapter | `id`, `place_name`, `place_url`, `road_address_name`, `phone`만 파싱하고 `x`, `y`는 읽지 않음 |
| 확인 후보·Token snapshot | 좌표 필드 없음 |
| Domain·JPA | 위도·경도 필드 없음 |
| Flyway | `restaurant` 테이블에 좌표 컬럼 없음 |
| 등록·조회 API | 좌표 요청·응답 필드 없음 |

근거는 [KakaoPlaceVerificationAdapter](../../src/main/java/com/masiton/restaurant/infrastructure/external/KakaoPlaceVerificationAdapter.java), [VerifiedPlace](../../src/main/java/com/masiton/restaurant/application/port/out/VerifiedPlace.java), [Restaurant](../../src/main/java/com/masiton/restaurant/domain/model/Restaurant.java), [RestaurantJpaEntity](../../src/main/java/com/masiton/restaurant/infrastructure/persistence/RestaurantJpaEntity.java), [V1 baseline](../../src/main/resources/db/migration/V1__create_initial_schema.sql)이다.

지도·거리 기능을 넣으려면 Kakao 응답 파싱, 확인 후보 snapshot 버전, Domain/JPA, 새 Flyway migration, 등록·조회 API를 함께 변경해야 한다.

## 8. Creator 상세 표시 정보

### 8.1 현재 보유 정보

- 내부 UUID
- YouTube external channel ID
- 현재 channel name
- canonical channel URL
- publication status
- lifecycle status
- external availability status와 확인 시각
- 생성·수정·삭제 시각

YouTube Adapter는 Channels API의 `snippet.title`만 표시 정보로 읽고, URL은 channel ID로 생성한다. 프로필 이미지, 소개, handle/custom URL과 구독자 수는 저장하지 않는다.

### 8.2 현재 공개 정보

| 소비 위치 | 노출 정보 |
|---|---|
| `GET /api/creators` | ID, channel name |
| 맛집 목록 `visitedBy` | ID, channel name |
| 맛집 상세 `visitedBy` | ID, channel name, channel URL |
| 관리자 미리보기·등록 | channel name, channel URL, 생성 후 ID |

Creator 단일 상세 API와 프론트 Route는 없다. 근거는 [Creator](../../src/main/java/com/masiton/creator/domain/model/Creator.java), [CreatorJpaEntity](../../src/main/java/com/masiton/creator/infrastructure/persistence/CreatorJpaEntity.java), [YouTubeChannelVerificationAdapter](../../src/main/java/com/masiton/creator/infrastructure/external/YouTubeChannelVerificationAdapter.java), [CreatorController](../../src/main/java/com/masiton/creator/presentation/rest/CreatorController.java)다.

## 9. 프론트 Route와 공통 Layout

### 9.1 실제 Route

| Route | 상태·역할 |
|---|---|
| `/` | `/restaurants`로 redirect |
| `/restaurants` | 공개 맛집 목록·검색·자치구·카테고리·페이지 |
| `/restaurants/[id]` | 공개 맛집 상세·방문 유튜버·영상 |
| `/admin` | `/admin/restaurants/new`로 client redirect |
| `/admin/login` | 관리자 로그인 |
| `/admin/restaurants/new` | 맛집 미리보기·확정 등록 |
| `/admin/creators/new` | Creator 미리보기·확정 등록 |
| `/admin/videos/new` | Video 미리보기·확정 등록 |
| `/admin/visits/new` | 방문 관계 등록 |

공개 `/creators` 또는 `/creators/[id]`, 지도·찜·컬렉션·일반 사용자 인증 Route는 없다.

### 9.2 Layout 계층

- [RootLayout](../../frontend/app/layout.tsx): 전체 Route에 `SiteHeader → main → SiteFooter` 적용
- [SiteHeader](../../frontend/components/layout/SiteHeader.tsx): 브랜드와 `맛집 탐색` 메뉴만 노출
- [AdminLayout](../../frontend/app/admin/layout.tsx): React Query Provider만 적용
- [AdminPage](../../frontend/components/admin/AdminPage.tsx): 보호 페이지에 client session gate와 관리자 navigation 적용
- 공통 UI: `Button`, `Card`, `Field`

## 10. Flyway 마이그레이션 순서

실제 migration 파일은 [V1__create_initial_schema.sql](../../src/main/resources/db/migration/V1__create_initial_schema.sql) 하나다. 운영 배포 전 기존 `V1~V5`를 단일 baseline으로 통합했으며 다음 변경은 `V2` 이상의 새 파일로만 추가한다.

V1 내부 적용 순서는 다음과 같다.

1. `region`
2. `food_category`
3. `admin_account`
4. `restaurant`
5. `creator`
6. `video`
7. `visit`
8. `confirmation_token`
9. 조회 인덱스
10. 자치구 25건과 음식 카테고리 10건 기준 데이터

여러 확장 기능이 동시에 스키마를 바꾸면 Flyway 소유자가 버전 순서와 선행 관계를 먼저 고정해야 한다. 기존 V1은 수정하지 않는다.

## 11. 테스트와 CI 상태

### 11.1 정적 테스트 구조

| 구분 | 현재 상태 |
|---|---|
| 백엔드 테스트 | 구체 테스트 클래스 45개 |
| 테스트 케이스 | `@Test` 220개 + `@ArchTest` 9개 = 229개 |
| 비활성 테스트 | `@Disabled` 없음 |
| 계층 | 단위, MockMvc API, PostgreSQL·Redis Testcontainers 통합, WireMock 외부 계약, ArchUnit, 전체 인수 |
| 프론트 테스트 | test/spec 파일과 `test` script 없음 |
| 프론트 정적 검사 | `tsc --noEmit` |
| lint·정적 분석 | 프론트 lint, Checkstyle, SpotBugs, JaCoCo gate 없음 |

### 11.2 2026-07-29 현재 실행 결과

| 명령 | 결과 |
|---|---|
| `.\gradlew.bat clean build` | 실패. compile·assemble까지 성공했으나 `test`가 모든 테스트 클래스를 `ClassNotFoundException`으로 로드하지 못함 |
| `.\gradlew.bat test --no-daemon` | 같은 class loading 실패 재현 |
| `npm --prefix frontend run typecheck` | 성공 |
| `npm --prefix frontend run build` | 성공. App Route 10개 생성 |

백엔드 실패는 assertion 실패가 아니라 Gradle test worker의 classpath·실행 환경 단계 실패다. 컴파일 결과물에는 대상 `.class` 파일이 존재했으므로 저장소 결함인지 현재 로컬 실행 환경 문제인지는 조사 당시 미확인 상태였다.

이 표는 2026-07-29 기준선 조사 당시의 실행 로그다. E1-T01(PR #63)이 CI에서 실패하던 백엔드 테스트 4건을 고쳤고, `.github/workflows/ci.yml`의 `백엔드 빌드·테스트` job이 이후 PR부터 통과한다.

프론트 빌드 중 npm audit가 high severity 취약점 3건을 보고했지만 빌드 종료 코드는 성공이었다. 버전 변경은 ADR과 별도 검토 없이 수행하지 않는다.

### 11.3 CI

조사 당시 `.github/workflows` 디렉터리가 없어 GitHub Actions 또는 다른 저장소 CI 품질 게이트가 없었다. E1-T01(PR #63)이 `.github/workflows/ci.yml`에 `백엔드 빌드·테스트`, `프론트엔드 빌드·타입 검사` 두 job을 추가해 [NFR-TEST-003](../01-requirements/non-functional-requirements.md#nfr-test-003-배포-품질-게이트)을 충족한다. `develop`·`main` ruleset의 필수 상태 검사도 이 두 job 이름과 연결됐다.

[MVP 로컬 검증 결과](mvp-local-verification.md)는 2026-07-28 기준 229개 테스트 성공을 기록했었다. 조사 당시(2026-07-29 오전)에는 이 성공 기록과 별개로 로컬 재실행이 실패하고 CI도 없었으나, 같은 날 E1-T01(PR #63)이 두 문제를 함께 해결했다.

## 12. 확장 계획 전에 해결할 기준선 결함

| 우선순위 | 항목 | 이유 |
|---|---|---|
| Medium | 방문 관계 영상 UUID 수기 입력 | 관리자가 등록된 영상을 안정적으로 선택하기 어려움 |

`CI 품질 게이트 부재`(Critical)와 `백엔드 테스트 재실행 실패 원인 확인`(High)은 E1-T01(PR #63)로, `유튜버 선택 UI 부재`(High)와 `목록 API의 localhost 고정`(High)은 E1-T02(PR #67)로 해결해 표에서 제외했다.

## 13. 당시 결정 필요 사항의 해소와 후속 Task

이 절은 2026-07-29 기준선 조사 당시의 질문을 보존한다. 이후 1차 확장 계약·ADR 확정으로 아래 항목은 더 이상 사용자 결정을 기다리는 blocker가 아니다.

| 당시 질문 | 현재 확정 | 후속 Task |
|---|---|---|
| 유튜버 선택 UI와 CI 품질 게이트의 처리 순서 | `FE-00`(E1-T01, PR #63)이 CI 품질 게이트를 복구했고 `FE-01`(E1-T02, PR #67)이 유튜버 선택 UI와 목록 API 환경변수 설정을 반영해 잔여 탐색 흐름을 닫았다. | [1차 확장 구현 계획](expansion-1-implementation-plan.md#8-전체-task-표) `FE-00`, `FE-01` |
| 일반 사용자 principal·audience·쿠키·Redis namespace, 세션 수와 Access Token 전달 | 관리자와 분리, Bearer+메모리 Access Token, Refresh 쿠키, 최대 3세션으로 확정했다. | [ADR-AUTH-002](../07-adr/security/auth-002-member-jwt-refresh-token.md), `FE-02`, `FE-03` |
| 좌표 타입·PostGIS·nullable·backfill | WGS84 nullable `numeric(9,6)`, PostGIS·현재 위치·반경 검색 제외, 관리자 검증 기반 단계적 backfill로 확정했다. | [ADR-MAP-001](../07-adr/integration/map-001-map-bounds-search.md), `FE-06`, `FE-07` |
| Creator 상세 필드·갱신·외부 장애 | `profile_image_url`, `description`, `handle` nullable 저장, 사용자 조회 중 외부 호출 금지와 기존 공개 상태 정책으로 확정했다. | [유튜버 상세 API](../05-specs/api/detail/creator-detail-api.md), `FE-08` |
| 인증·개인화·좌표·Creator migration 분할 | V2~V5 전진 migration 순서와 최종 Flyway 병합 책임으로 확정했다. | [마이그레이션 계획](../05-specs/data/migration-plan.md#9-1차-확장-전진-마이그레이션-순서), `FE-02`, `FE-04`, `FE-06`, `FE-08` |

현재 Task에 없는 Conditional·Post-MVP 기술은 구현으로 취급하지 않는다. 활성화 근거가 생기면 [ADR Backlog](../07-adr/adr-backlog.md)의 절차를 따라 새 ADR과 새 Task를 만든다.

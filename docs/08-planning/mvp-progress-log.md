---
status: In Progress
log_date: 2026-07-28
baseline_commit: 1389a39
owners:
  - 이우람
  - 양성훈
  - 박진영
  - 김인안
related_documents:
  - mvp-2day-implementation-plan.md
  - ../02-analysis/mvp-workstreams.md
  - ../03-team/ownership.md
  - ../06-architecture/README.md
  - ../07-adr/quality/test-001-automation-strategy.md
---

# 맛잇온 1차 MVP 진행 로그

[1차 MVP 구현 계획](mvp-2day-implementation-plan.md)의 Task가 실제로 어떤 순서로 병합됐는지 기록한다. 계획 문서는 무엇을 할지 정의하고, 이 문서는 무엇이 끝났는지 정리한다. 완료 판정은 `develop`에 병합된 PR 기준이며 이 문서에서 빌드·테스트를 재실행해 검증하지는 않았다.

기준 시점: `develop` `1389a39` (2026-07-28). 근거는 `git log`(89 커밋, 2026-07-22 → 2026-07-28)다.

## 1. 요약

| 항목 | 값 |
|---|---|
| 커밋 | 89 |
| 병합 PR | 32 |
| 완료 Task | 13 / 14 (`T-14` 남음) |
| 백엔드 main 소스 | 178 파일 |
| 테스트 클래스 | 43 |
| Flyway 마이그레이션 | `V1` ~ `V5` |

## 2. 단계별 흐름

문서 계약 확정 → 실행 기반 4갈래 병렬 → 도메인 기능 구현 → 관계·화면 통합 → 통합 검증 순으로 진행했다.

### 2.1 문서 계약 확정 (2026-07-22 ~ 07-27)

| 범위 | 내용 | PR |
|---|---|---|
| 문서 체계 | 요구사항·PRD·API·데이터 명세와 ADR 인덱스, 물리 데이터 모델, 1차 MVP 구현 계획 확정 | #16 |
| 협업 규칙 | PR 템플릿, CODEOWNERS, 브랜치 접두사 규칙, 구현-리뷰 워크플로 스킬 | #19, #20, #21 |

### 2.2 실행 기반 (2026-07-27)

| Task | 담당 | 결과 | PR |
|---|---|---|---|
| `T-01` | 이우람 | Gradle·Spring Boot 실행 기반, PostgreSQL·Redis·WireMock Compose, `/internal/health/*` | #17 |
| `T-02` | 양성훈 | Next.js App Router, 디자인 Token, 헤더·푸터·폼·카드 공통 컴포넌트 | #18 |
| `T-03` | 박진영 | Flyway `V1` 기준정보·관리자, `V2` 핵심 도메인, `V3` 확인 Token, `V4` 조회 인덱스, `V5` 시드와 JPA Adapter | #23 |
| `T-04` | 김인안 | 관리자 JWT(RS256)·Redis Refresh Token, 출처별 로그인 실패 제한, WireMock 정상·오류 Fixture | #22 |

### 2.3 도메인 기능 구현 (2026-07-27 ~ 07-28)

| Task | 담당 | 결과 | PR |
|---|---|---|---|
| `T-05` | 양성훈 | 맛집 검색 API와 목록 화면. 이름·구·카테고리 조건, 1-base 페이지, URL 상태와 빈 결과 | #26 |
| `T-06` | 박진영 | 맛집 상세 API와 상세 화면. 기본 정보·Kakao 링크, 읽기 전용 트랜잭션 경계에서 콘텐츠 조회만 격리 | #27 |
| `T-07` | 이우람 | 공개 유튜버 최소 선택 목록과 Visit 조회 계약. 유효 관계 판정과 중복 제거 | #25 |
| `T-08` | 김인안 | 기본 데이터 등록 API. 미리보기·확인 Token, 맛집·유튜버·영상 등록과 중복 응답 | #24 |

### 2.4 관계·화면 통합 (2026-07-28)

| Task | 담당 | 결과 | PR |
|---|---|---|---|
| `T-09` | 이우람·김인안 | 방문 관계 등록 트랜잭션. 참조·공개·채널 일치·복합 중복 검증, 실패 시 부분 저장 0건 | #28 |
| `T-10` | 양성훈·이우람 | 유튜버 조건을 맛집 목록에 통합. Visit 후보와 나머지 AND 조건·페이지 결합 | #29 |
| `T-11` | 박진영·이우람 | 상세에 방문 유튜버·영상 통합. 공개 관계 Projection, `contentStatus`, 삭제 Visit 상태 쌍 검증 | #31 |
| `T-12` | 양성훈·김인안 | 관리자 로그인·등록 화면. 미리보기·확정 Form, 미리보기 응답 경쟁 상태 방지 | #30 |

### 2.5 통합 검증 (2026-07-28 ~ 진행 중)

| Task | 담당 | 상태 | PR |
|---|---|---|---|
| `T-13` | 전원 | 완료. `AdminRegistrationJourneyAcceptanceTest`로 관리자 등록부터 공개 조회까지 하나의 여정 검증, 인수 테스트 JPA 운영 설정 적용 | #32 |
| `T-14` | 전원 | **남음.** CI, 반응형, 비밀·로그 검사, README 실행 절차와 완료 정의 체크리스트 | — |

### 2.6 핵심 경로

계획의 핵심 경로를 실제 병합 순서로 따라갔다.

`T-01` 실행 기반 → `T-03` 스키마 → `T-08` 등록 API → `T-09` Visit → `T-12` 관리자 UI → `T-13` 인수 → `T-14` 회귀

## 3. 현재 코드 상태

### 3.1 백엔드 도메인 패키지

| 패키지 | 구현 범위 |
|---|---|
| `restaurant` | 검색·상세 기본 정보, 맛집 등록 |
| `creator` | 공개 유튜버 선택 목록, 채널 등록 |
| `video` | 영상 등록 |
| `visit` | 유효 관계 판정, 상세 콘텐츠 Query |
| `orchestration` | 맛집 상세 조합, Visit 등록 Command |
| `security` | 관리자 인증·인가 경계 |
| `common` | 오류 계약, 관측, 영속성 공통 |

### 3.2 구현된 화면

| 경로 | 화면 |
|---|---|
| `/restaurants` | 맛집 목록·검색·유튜버 필터 |
| `/restaurants/[id]` | 맛집 상세와 방문 콘텐츠 |
| `/admin/login` | 관리자 로그인 |
| `/admin/restaurants/new` | 맛집 등록 |
| `/admin/creators/new` | 유튜버 등록 |
| `/admin/videos/new` | 영상 등록 |
| `/admin/visits/new` | 방문 관계 등록 |

### 3.3 검증 층

| 층 | 수단 | 대표 테스트 |
|---|---|---|
| 아키텍처 규칙 | ArchUnit | `ArchitectureTest` |
| 단위 Application | JUnit 5·Mockito | `RestaurantSearchQueryServiceTest`, `RegisterVisitServiceTest` |
| Controller 계약 | MockMvc | `RestaurantDetailApiTest`, `SecurityBoundaryApiTest`, `ErrorContractApiTest` |
| 제약·트랜잭션 | PostgreSQL Testcontainers | `ConstraintViolationIntegrationTest`, `VisitRelationshipRegistrationIntegrationTest` |
| 외부 연동 | WireMock | `ExternalVerificationWireMockFixtureIntegrationTest` |
| 사용자 여정 | 실제 PostgreSQL·Redis·WireMock | `AdminRegistrationJourneyAcceptanceTest` |

## 4. 흐름에서 드러난 작업 방식

- **계약 우선.** 기능 커밋마다 계약 문서 동기화 커밋이 붙었다. `docs: 도메인 간 Query Port 협력 규칙을 명시한다`, `docs: 상세 방문 콘텐츠 계약을 명확히 한다`처럼 구현에서 드러난 규칙을 원문 문서로 되돌렸다.
- **리뷰가 만든 수정.** `fix:` 커밋 다수가 PR 리뷰 반영이다. 인증 토큰 검증 취약점, 예외 로그 민감정보 노출, 비공개·삭제 영상이 유효한 유튜버까지 제외한 문제, 미리보기 응답 경쟁 상태를 병합 전에 잡았다.
- **Fake로 병렬 해소.** WS-01은 유효 맛집 ID Query Port Fake, WS-02는 상세 콘텐츠 Query Port Fake로 선행 Task를 기다리지 않고 진행하고 `T-07`·`T-09` 완료 후 실제 Adapter로 교체했다.
- **원자성 우선.** 외부 HTTP 호출 구간과 DB 트랜잭션을 분리하고, 상세 조회는 읽기 전용 경계에서 콘텐츠 조회만 격리해 콘텐츠 실패가 전체 응답을 rollback시키지 않게 했다.

## 5. 갱신 규칙

이 문서는 `develop` 병합을 기준으로 갱신한다. 갱신할 때 frontmatter의 `log_date`와 `baseline_commit`을 함께 바꾼다. Task 정의·완료 조건 자체를 바꾸는 변경은 [1차 MVP 구현 계획](mvp-2day-implementation-plan.md)에서 하고 이 문서에는 결과만 남긴다.

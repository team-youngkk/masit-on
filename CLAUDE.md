---
related_documents:
  - README.md
  - docs/00-overview/README.md
  - docs/01-requirements/README.md
  - docs/02-analysis/README.md
  - docs/03-team/README.md
  - docs/04-product/README.md
  - docs/05-specs/api/README.md
  - docs/06-architecture/README.md
  - docs/06-architecture/implementation-conventions.md
  - docs/06-architecture/package-structure.md
  - docs/06-architecture/dependency-rules.md
  - docs/07-adr/adr-index.md
  - docs/08-planning/mvp-2day-implementation-plan.md
---

# CLAUDE.md

맛잇온(masit-on) 저장소에서 작업할 때 적용하는 지침이다. 상세 규칙은 이 문서에 복제하지 않고 권위 있는 원문을 연결한다.

## 1. 프로젝트

유튜버가 방문한 맛집을 지역·음식 종류·유튜버별로 탐색하는 서비스. 4인 팀의 1차 MVP를 구현 중이다.

**현재 상태: 백엔드 실행 기반만 있다.** `T-01`로 Gradle·Spring Boot 스캐폴딩, Docker Compose 의존 서비스와 헬스체크가 생겼다. 도메인 패키지(`restaurant`, `creator`, `video`, `visit`, `orchestration`, `security`)와 Flyway 마이그레이션, 프론트엔드(`package.json`)는 아직 없다. 실행 방법은 5절을 따른다.

## 2. 문서가 계약이다

이 프로젝트는 요구사항·PRD·API·데이터·아키텍처·ADR이 모두 확정 문서로 존재한다. 문서는 참고 자료가 아니라 구현 계약이다.

**구현 전에 반드시** 관련 요구사항 ID, PRD, API 계약, ADR, 테이블 정의를 먼저 읽는다. 구현 결과에서 근거 문서를 역추적할 수 있어야 한다. 요구사항 ↔ 산출물 대응은 추적표를 사용한다.

- [제품 추적표](docs/04-product/traceability.md) — 요구사항 ↔ PRD
- [API 추적표](docs/05-specs/api-traceability.md) — 요구사항 ↔ API
- [데이터 추적표](docs/05-specs/data/data-traceability.md) — 요구사항 ↔ 테이블
- [ADR 추적표](docs/07-adr/adr-traceability.md) — 결정 ↔ 영향 범위

규칙이 충돌하면 다음 순서로 적용한다.

1. 확정된 요구사항과 1차 MVP 범위
2. Accepted ADR
3. API·데이터 계약
4. 아키텍처 상세 설계
5. [구현 컨벤션](docs/06-architecture/implementation-conventions.md)
6. 외부 코딩 컨벤션(NAVER Java 컨벤션)

문서 간 충돌을 발견하면 임의 해석하거나 범위를 넓히지 않는다. 충돌 지점을 명시하고 팀 결정을 요청한다.

## 3. AI 작업 규칙

`implementation-conventions.md` 8절이 원문이며, 특히 다음을 지킨다.

- 요구사항, MVP 범위, API 계약, DB 구조를 임의로 변경하지 않는다.
- 요청 범위 밖의 리팩터링과 파일 변경을 하지 않는다.
- 새로운 라이브러리·플러그인·외부 서비스를 임의로 추가하지 않는다. 버전은 ADR에 고정돼 있다.
- 비밀키, 실제 인증정보, 개인정보를 코드·테스트·로그·문서에 남기지 않는다.
- 생성한 코드는 컴파일하고 관련 테스트를 실행한다. **검증하지 못한 항목을 완료로 보고하지 않는다.**
- 실패한 테스트와 알려진 제약은 그대로 보고한다.
- 코드와 문서가 달라지면 관련 문서를 같은 PR에서 동기화한다.
- 주석은 코드로 드러나지 않는 의도와 제약을 설명할 때만 쓴다. 담당자와 제거 조건 없는 `TODO`를 남기지 않는다.

MVP 범위 밖 기능(지도, 찜, 테마 큐레이션, 일반 사용자 로그인, 평점·리뷰 등)은 Route와 메뉴 자체를 만들지 않는다. 와이어프레임에는 확장 기능이 그려져 있으므로 그대로 옮기지 않는다.

## 4. 기술 스택 (ADR 확정, 임의 변경 금지)

| 영역 | 확정 |
|---|---|
| 백엔드 | Java 21, Spring Boot 4.1.0, Gradle 8.14.3 Groovy DSL, 단일 모듈 |
| 프론트엔드 | Node.js 24.18.0, Next.js 16.2.11 App Router, TypeScript 7.0.2 |
| 데이터 | PostgreSQL 17.10, Spring Data JPA, Flyway 12.4.0, Redis 8.8 |
| 인증 | Spring Security 7.1.0, JWT(RS256) + Redis Refresh Token, `ADMIN` 단일 역할 |
| 외부 연동 | Kakao Local REST API V2, YouTube Data API v3 (Port/Adapter) |
| 테스트 | JUnit 5, AssertJ, Mockito, Testcontainers 2.0.5, WireMock, ArchUnit |
| 실행 | Docker / Docker Compose. 1차 MVP는 로컬 통합까지이며 AWS 배포는 하지 않는다 |

전체 목록과 근거는 [ADR 인덱스](docs/07-adr/adr-index.md), 버전 고정·업그레이드 정책은 [기술 정책](docs/06-architecture/technology-policy.md)을 따른다. 아직 결정되지 않은 항목은 [ADR 백로그](docs/07-adr/adr-backlog.md)에 있다.

## 5. 실행 명령

Docker Desktop과 JDK 21이 필요하다. Gradle은 Wrapper를 쓰므로 따로 설치하지 않는다. **시스템 Gradle로 Wrapper를 우회하지 않는다.**

의존 서비스만 띄우고 애플리케이션은 IDE·Gradle로 실행하는 방식이 기본 개발 루프다.

```bash
cp .env.example .env
docker compose up -d postgres redis wiremock
./gradlew bootRun
```

애플리케이션까지 컨테이너로 통합 실행한다.

```bash
docker compose up -d --build
```

빌드와 테스트를 실행한다. 통합 테스트는 Testcontainers를 쓰므로 Docker가 떠 있어야 한다.

```bash
./gradlew clean build
```

컨테이너와 데이터 볼륨까지 초기화한다.

```bash
docker compose down -v
```

| 항목 | 값 |
|---|---|
| 애플리케이션 | `http://localhost:8080` |
| 상태 확인 | `/internal/health/live`, `/internal/health/ready`, `/internal/health/dependencies` |
| PostgreSQL | `localhost:5432` (DB·계정 `masiton`) |
| Redis | `localhost:6379` |
| WireMock | `http://localhost:8081` (관리 `/__admin`) |

`.env`는 로컬 전용 값이며 커밋하지 않는다. **`.env`는 Docker Compose만 읽는다.** `./gradlew bootRun`은 `.env`를 로드하지 않으므로 컨테이너 포트를 바꿨다면 애플리케이션에도 같은 값을 환경 변수로 넘겨야 한다.

```bash
DB_URL=jdbc:postgresql://localhost:15432/masiton REDIS_PORT=16379 ./gradlew bootRun
```

`/internal/**`은 로컬 컨테이너 네트워크 전용이며 최종 배포에서 인터넷 진입점에 노출하지 않는다([ADR-WEB-003](docs/07-adr/platform/web-003-routing-boundary.md)).

## 6. 아키텍처 필수 규칙

단일 모듈 **도메인 중심 계층형 모놀리스**. 루트 패키지 `com.masiton`, 진입점 `com.masiton.MasitOnApplication`.

- 최상위는 도메인(`restaurant`, `creator`, `video`, `visit`, `orchestration`, `security`, `common`)이고 그 안에서 `presentation` / `application` / `domain` / `infrastructure`를 반복한다. 계층을 전역 최상위로 두지 않는다.
- Domain은 Spring, JPA, 제공자 SDK에 의존하지 않는다.
- Application은 자신이 소유한 `port.out`만 호출하고, Infrastructure Adapter가 구현한다. Application에서 Spring Data Repository·`EntityManager`·HTTP Client를 직접 import하지 않는다.
- 다른 도메인의 Entity·Repository를 직접 참조하지 않는다. 공개 Port 또는 `orchestration`을 사용한다.
- 교차 도메인 Command·Query만 `orchestration`에 둔다. `orchestration`은 `common`이 아니고 Entity를 소유하지 않는다.
- ArchUnit 규칙을 첫 구현 PR부터 적용한다.

원문: [아키텍처 개요](docs/06-architecture/architecture-overview.md), [패키지 구조](docs/06-architecture/package-structure.md), [의존성 규칙](docs/06-architecture/dependency-rules.md), [모듈 경계](docs/06-architecture/module-boundaries.md), [트랜잭션 경계](docs/06-architecture/transaction-boundaries.md), [조회 조합](docs/06-architecture/query-composition.md), [애플리케이션 흐름](docs/06-architecture/application-flow.md)

보안 경계(인증 필터·matcher 순서·Principal 전달)는 [보안 경계](docs/06-architecture/security-boundary.md)와 [ADR-WEB-003 라우팅 경계](docs/07-adr/platform/web-003-routing-boundary.md), 외부 연동(Kakao·YouTube Port/Adapter·timeout·실패 처리)은 [외부 연동](docs/06-architecture/external-integration.md)을 따른다.

## 7. API·데이터

- 백엔드 경로는 버전 없는 `/api`, 관리자는 `/api/admin` 경계로 분리한다. `/v1` 같은 경로 버전을 도입하지 않는다.
- 공개 GET 3종(`/api/restaurants`, `/api/creators`, `/api/restaurants/{id}`)은 무인증이다. 로그인(`POST /api/admin/auth/tokens`)과 재발급(`POST /api/admin/auth/tokens/refresh`)은 JWT를 요구하지 않고 각각 자격 증명과 Refresh 쿠키만 검증한다. 그 외 `/api/admin/**`은 JWT + `ADMIN`이고, 정의되지 않은 경로는 기본 거부한다.
- 목록 응답은 `{ "items": [...], "page": {...} }` 형태이고 페이지가 필요 없는 최소 선택 목록은 `{ "items": [...] }`다. 빈 결과도 `200`에 빈 `items`이며, 없는 단일 자원만 `404`다.
- 페이지는 1-base, 크기 10·20·50, 기본 20.
- 외부 API 식별자는 **불투명 문자열**이다. 클라이언트는 UUID 여부나 생성 규칙을 검증하지 않는다. 내부 식별자가 UUID v4인 것([ADR-DATA-007](docs/07-adr/data/data-007-uuid-v4-identifiers.md))은 외부 계약이 아니므로, 응답 필드 타입과 문서에 UUID를 전제하지 않는다.
- 모든 오류 응답에 서버 생성 `traceId`를 포함한다.
- Entity를 API 요청·응답에 노출하지 않는다.
- 트랜잭션은 Application Service의 public 메서드에서 시작한다. 조회는 `@Transactional(readOnly = true)`. OSIV 비활성화, `ddl-auto=validate`.
- 스키마 변경은 전부 Flyway. **이미 적용된 마이그레이션 파일은 수정하지 않고 새 파일을 추가한다.**
- 외부 HTTP 호출 중에는 DB 트랜잭션을 열지 않는다. 외부 호출 실패 시 핵심 Entity 저장 0건이어야 한다.
- API 계약이나 테이블을 바꾸려면 소유자와 먼저 합의하고 코드·문서를 같은 PR에서 변경한다.

원문: [API 계약](docs/05-specs/api/README.md), [데이터 명세](docs/05-specs/data/README.md), [테이블 정의](docs/05-specs/data/table-definitions.md), [제약](docs/05-specs/data/constraints.md), [인덱스 전략](docs/05-specs/data/index-strategy.md), [공개·삭제 생명주기](docs/05-specs/data/lifecycle-rules.md), [Flyway 마이그레이션 계획](docs/05-specs/data/migration-plan.md)

공통 계약 6종: [식별자](docs/05-specs/api/common/identifier-contract.md) · [응답](docs/05-specs/api/common/response-contract.md) · [오류](docs/05-specs/api/common/error-contract.md) · [페이지네이션](docs/05-specs/api/common/pagination-contract.md) · [검색·필터](docs/05-specs/api/common/filtering-contract.md) · [날짜·시간](docs/05-specs/api/common/date-time-contract.md)

## 8. 테스트

- 클래스명 `XxxTest` / `XxxIntegrationTest` / `XxxApiTest`, 메서드명 `행위_조건_기대결과`, `@DisplayName`은 자연스러운 한글 문장, 본문은 Given-When-Then.
- 단위는 외부 저장소 없이, Repository·제약·트랜잭션은 PostgreSQL Testcontainers, Controller는 MockMvc, 외부 Adapter는 WireMock으로 검증한다.
- **로컬·자동화 테스트에서 실제 Kakao·YouTube API를 호출하지 않는다.**
- 기능마다 정상·예외·경계를 최소 한 건씩 검증한다. 중복 등록과 원자성은 동시 요청과 부분 저장 0건을 검증한다.
- `Thread.sleep()`이나 임의 실행 대기를 쓰지 않는다. 테스트 간 실행 순서 의존을 금지한다.
- 커버리지 수치를 병합 기준으로 쓰지 않는다. 요구사항별 필수 시나리오 통과가 완료 기준이다.
- Fake·Stub은 테스트 소스 또는 명시적 로컬 테스트 프로파일에만 둔다. 기본 실행 프로파일에 Fake Bean을 등록하지 않는다.

원문: [ADR-TEST-001](docs/07-adr/quality/test-001-automation-strategy.md)

## 9. Git 협업

- `main`(배포 기준) / `develop`(통합). 둘 다 직접 push 금지.
- 브랜치는 최신 `develop`에서 분기. `feature/ws-{번호}-{기능명}`, `fix/{기능명}`.
- `feature`→`develop`은 일반 Merge, `develop`→`main`만 Squash Merge.
- Conventional Commits (`feat`, `fix`, `test`, `refactor`, `docs`, `build`, `ci`, `chore`). 예: `feat: 맛집 목록 조회 구현`
- 모든 변경은 PR로 병합하고 작성자를 제외한 **최소 2명 승인**이 필요하다. AI가 작성한 코드도 동일하다.
- 서로 독립적인 변경은 커밋·PR을 분리한다. 포매팅·정적 분석만 고치는 변경은 로직 변경과 분리한다.

PR 완료 점검 목록은 [구현 컨벤션 9절](docs/06-architecture/implementation-conventions.md#9-pr-완료-점검)을 사용한다.

## 10. Workstream과 소유권

| WS | 범위 | 담당 | PRD |
|---|---|---|---|
| [WS-01](docs/02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | 맛집 목록·검색·필터 | 양성훈 | [맛집 탐색](docs/04-product/prd/discovery/restaurant-discovery.md) |
| [WS-02](docs/02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | 맛집 상세·콘텐츠 조회 | 박진영 | [맛집 상세](docs/04-product/prd/detail/restaurant-detail.md) |
| [WS-03](docs/02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | 유튜버 기반 탐색·Visit 판정 | 이우람 | [유튜버 탐색](docs/04-product/prd/discovery/creator-discovery.md) |
| [WS-04](docs/02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 관리자 인증·데이터 등록 | 김인안 | [관리자 데이터 등록](docs/04-product/prd/admin/admin-data-management.md) |

이 표는 참조용 요약이다. 배정이 충돌하면 [소유권](docs/03-team/ownership.md)이 최종 기준이고, 사람별 책임·결정 권한은 [역할](docs/03-team/roles.md), 도메인 소유권은 [도메인 경계](docs/02-analysis/domain-boundaries.md)를 따른다.

공통 파일은 동시에 수정하지 않는다. Spring Boot·Docker는 이우람, 프론트 공통 Layout은 양성훈, Flyway 순서는 박진영, 인증 공통은 김인안이 최종 병합한다.

Task 분해·선행 관계·완료 정의는 [1차 MVP 구현 계획](docs/08-planning/mvp-2day-implementation-plan.md)을 따른다.

## 11. 문서 맵

각 디렉터리의 README를 진입점으로 쓴다. README에 읽기 순서와 문서별 역할이 있다.

| 디렉터리 | 내용 | 자주 여는 문서 |
|---|---|---|
| [00-overview](docs/00-overview/README.md) | 서비스 정의와 MVP 범위 | [범위](docs/00-overview/scope.md) · [용어집](docs/00-overview/glossary.md) |
| [01-requirements](docs/01-requirements/README.md) | 기능·비기능 요구사항과 업무 규칙 | [기능 요구사항](docs/01-requirements/functional-requirements.md) · [비즈니스 규칙](docs/01-requirements/business-rules.md) |
| [02-analysis](docs/02-analysis/README.md) | 도메인 경계와 Workstream 분해 | [MVP Workstream](docs/02-analysis/mvp-workstreams.md) · [도메인 경계](docs/02-analysis/domain-boundaries.md) |
| [03-team](docs/03-team/README.md) | 역할과 항목별 최종 책임자 | [소유권](docs/03-team/ownership.md) |
| [04-product](docs/04-product/README.md) | PRD, 와이어프레임, 추적표 | [제품 개요](docs/04-product/prd/00-product-overview.md) |
| [05-specs](docs/05-specs/README.md) | API·데이터 계약 | [API 계약](docs/05-specs/api/README.md) · [데이터 명세](docs/05-specs/data/README.md) · [ERD](docs/05-specs/diagrams/erd-spec.md) |
| [06-architecture](docs/06-architecture/README.md) | 아키텍처, 패키지, 의존성, 컨벤션 | [구현 컨벤션](docs/06-architecture/implementation-conventions.md) |
| [07-adr](docs/07-adr/README.md) | 기술 결정 기록 | [ADR 인덱스](docs/07-adr/adr-index.md) |
| [08-planning](docs/08-planning/README.md) | 구현 계획 | [1차 MVP 구현 계획](docs/08-planning/mvp-2day-implementation-plan.md) |

문서 작성 시 기존 스타일을 따른다. 상단 `related_documents` frontmatter, 한국어 서술, 문서 간 상대 경로 링크.

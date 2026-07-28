---
related_documents:
  - README.md
  - CLAUDE.md
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

# AGENTS.md

맛잇온(masit-on) 저장소에서 Codex와 Codex 하위 에이전트가 작업할 때 적용하는 지침이다. 이 파일은 저장소 루트와 모든 하위 경로에 적용된다. 더 구체적인 하위 `AGENTS.md`가 생기면 해당 디렉터리에서는 하위 문서가 우선한다.

이 문서는 `CLAUDE.md`의 프로젝트 규칙을 Codex 작업 방식에 맞게 옮긴 것이다. 두 파일의 프로젝트 규칙이 다르면 임의로 한쪽을 선택하지 말고, 아래 2절의 권위 순서와 원문 문서를 확인한다.

## 1. 프로젝트

유튜버가 방문한 맛집을 지역·음식 종류·유튜버별로 탐색하는 서비스다. 4인 팀의 MVP를 구현 중이다.

현재 구현 상태를 문서의 과거 설명만으로 단정하지 않는다. 작업을 시작할 때 `git status`, 저장소 구조, 관련 소스와 테스트를 직접 확인한다. 구현 현황이 문서와 다르면 그 차이를 보고하고, 요청 없이 문서 또는 코드를 한쪽에 맞춰 대규모로 변경하지 않는다.

## 2. 문서가 계약이다

요구사항·PRD·API·데이터·아키텍처·ADR은 참고 자료가 아니라 구현 계약이다.

구현 전에 관련 요구사항 ID, PRD, API 계약, ADR, 테이블 정의를 먼저 읽는다. 필요한 문서만 선별해서 읽되, 변경 결과에서 근거 문서를 역추적할 수 있어야 한다.

- [제품 추적표](docs/04-product/traceability.md) — 요구사항 ↔ PRD
- [API 추적표](docs/05-specs/api-traceability.md) — 요구사항 ↔ API
- [데이터 추적표](docs/05-specs/data/data-traceability.md) — 요구사항 ↔ 테이블
- [ADR 추적표](docs/07-adr/adr-traceability.md) — 결정 ↔ 영향 범위

규칙이 충돌하면 다음 순서로 적용한다.

1. 확정된 요구사항과 MVP 범위
2. Accepted ADR
3. API·데이터 계약
4. 아키텍처 상세 설계
5. [구현 컨벤션](docs/06-architecture/implementation-conventions.md)
6. 외부 코딩 컨벤션(NAVER Java 컨벤션)

문서 간 충돌을 발견하면 임의로 해석하거나 범위를 넓히지 않는다. 충돌 지점, 영향 범위, 선택이 필요한 사항을 명시하고 사용자 또는 팀의 결정을 요청한다.

## 3. Codex 작업 규칙

### 작업 시작

- 먼저 `git status --short`와 관련 파일을 확인한다. 사용자가 만들었거나 다른 작업이 남긴 변경을 되돌리거나 덮어쓰지 않는다.
- 파일 검색은 `rg --files`, 내용 검색은 `rg`를 우선 사용한다.
- 한글 문서는 UTF-8로 읽고 쓴다. PowerShell 출력이 깨지면 UTF-8 출력 인코딩을 명시한다.
- 요청 범위와 관련 문서를 확인한 뒤 최소 변경으로 해결한다. 요청 밖 리팩터링, 포매팅, 파일 이동을 섞지 않는다.
- 새 라이브러리·플러그인·외부 서비스와 버전 변경은 사용자의 명시적 요청 및 관련 ADR 없이 추가하지 않는다.

### 구현

- 요구사항, MVP 범위, API 계약, DB 구조를 임의로 변경하지 않는다.
- 기존 코드 스타일과 패키지 구조를 따른다. 새 추상화는 현재 요구사항에 필요한 경우에만 도입한다.
- 비밀키, 실제 인증정보, 개인정보를 코드·테스트·로그·문서에 남기지 않는다.
- 주석은 코드로 드러나지 않는 의도와 제약을 설명할 때만 쓴다. 담당자와 제거 조건 없는 `TODO`를 남기지 않는다.
- 코드와 계약 문서가 함께 달라져야 하는 변경이면 같은 작업 범위에서 동기화한다. 다만 계약 자체의 변경에는 소유자 합의가 먼저 필요하다.
- 기존에 적용된 Flyway 마이그레이션은 수정하지 않고 새 마이그레이션 파일을 추가한다.

### 도구와 하위 에이전트

- 단순하고 원자적인 변경은 현재 에이전트가 직접 처리한다.
- 사용자가 병렬 작업, 하위 에이전트, 역할 분담을 요청했거나 적용 중인 워크플로가 요구할 때만 하위 에이전트를 사용한다.
- 하위 에이전트에게는 담당 파일과 책임 범위를 명확히 지정하고, 다른 작업자의 변경을 되돌리지 않도록 알린다.
- 여러 에이전트가 같은 파일을 동시에 수정하지 않게 한다. 결과는 주 에이전트가 통합하고 직접 검증한다.
- Git commit, push, 브랜치 생성, PR 생성은 사용자가 요청한 경우에만 수행한다. 읽기 전용 Git 명령은 진단과 검증에 사용할 수 있다.

### 검증과 완료 보고

- 변경한 코드는 컴파일하고 관련 테스트를 실행한다. 위험과 변경 범위에 비례해 더 넓은 검증을 추가한다.
- 검증하지 못한 항목을 완료로 보고하지 않는다. 실패한 테스트, 환경 제약, 남은 위험을 그대로 설명한다.
- 테스트 실패가 기존 변경 때문인지 이번 변경 때문인지 가능한 범위에서 구분한다.
- 최종 응답은 변경 결과, 핵심 파일, 실행한 검증, 남은 제약을 간결하게 전달한다.

MVP 범위 밖 기능(지도, 찜, 테마 큐레이션, 일반 사용자 로그인, 평점·리뷰 등)은 Route와 메뉴 자체를 만들지 않는다. 와이어프레임에 확장 기능이 있더라도 그대로 옮기지 않는다.

## 4. 기술 스택

ADR로 확정된 버전을 임의로 변경하지 않는다.

| 영역 | 확정 |
|---|---|
| 백엔드 | Java 21, Spring Boot 4.1.0, Gradle 8.14.3 Groovy DSL, 단일 모듈 |
| 프론트엔드 | Node.js 24.18.0, Next.js 16.2.11 App Router, TypeScript 7.0.2 |
| 데이터 | PostgreSQL 17.10, Spring Data JPA, Flyway 12.4.0, Redis 8.8 |
| 인증 | Spring Security 7.1.0, JWT(RS256) + Redis Refresh Token, `ADMIN` 단일 역할 |
| 외부 연동 | Kakao Local REST API V2, YouTube Data API v3 (Port/Adapter) |
| 테스트 | JUnit 5, AssertJ, Mockito, Testcontainers 2.0.5, WireMock, ArchUnit |
| 실행 | Docker / Docker Compose. MVP는 로컬 통합까지이며 AWS 배포는 하지 않는다 |

전체 목록과 근거는 [ADR 인덱스](docs/07-adr/adr-index.md), 버전 정책은 [기술 정책](docs/06-architecture/technology-policy.md), 미결정 항목은 [ADR 백로그](docs/07-adr/adr-backlog.md)를 따른다.

## 5. 실행과 검증 명령

Docker Desktop과 JDK 21이 필요하다. 시스템 Gradle이 아니라 저장소의 Gradle Wrapper를 사용한다.

PowerShell에서는 다음 명령을 사용한다.

```powershell
Copy-Item .env.example .env
docker compose up -d postgres redis wiremock
.\gradlew.bat bootRun
```

애플리케이션까지 컨테이너로 통합 실행한다.

```powershell
docker compose up -d --build
```

빌드와 테스트를 실행한다. 통합 테스트는 Testcontainers를 사용하므로 Docker가 필요하다.

```powershell
.\gradlew.bat clean build
```

컨테이너와 데이터 볼륨 초기화는 파괴적 작업이다. 사용자가 명시적으로 요청했거나 작업 범위상 명백히 허용된 경우에만 실행한다.

```powershell
docker compose down -v
```

| 항목 | 값 |
|---|---|
| 애플리케이션 | `http://localhost:8080` |
| 상태 확인 | `/internal/health/live`, `/internal/health/ready`, `/internal/health/dependencies` |
| PostgreSQL | `localhost:5432` (DB·계정 `masiton`) |
| Redis | `localhost:6379` |
| WireMock | `http://localhost:8081` (관리 `/__admin`) |

`.env`는 로컬 전용이며 커밋하지 않는다. `.env`는 Docker Compose만 읽는다. `bootRun`에 다른 연결 값을 전달해야 하면 현재 셸의 환경 변수를 사용한다.

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:15432/masiton'
$env:REDIS_PORT = '16379'
.\gradlew.bat bootRun
```

`/internal/**`은 로컬 컨테이너 네트워크 전용이며 최종 배포의 인터넷 진입점에 노출하지 않는다([ADR-WEB-003](docs/07-adr/platform/web-003-routing-boundary.md)).

## 6. 아키텍처 필수 규칙

단일 모듈 도메인 중심 계층형 모놀리스다. 루트 패키지는 `com.masiton`, 진입점은 `com.masiton.MasitOnApplication`이다.

- 최상위는 도메인(`restaurant`, `creator`, `video`, `visit`, `orchestration`, `security`, `common`)이고, 각 도메인 안에서 `presentation` / `application` / `domain` / `infrastructure`를 반복한다. 계층을 전역 최상위로 두지 않는다.
- Domain은 Spring, JPA, 제공자 SDK에 의존하지 않는다.
- Application은 자신이 소유한 `port.out`만 호출하고 Infrastructure Adapter가 구현한다. Application에서 Spring Data Repository, `EntityManager`, HTTP Client를 직접 import하지 않는다.
- 다른 도메인의 Entity·Repository를 직접 참조하지 않는다. 공개 Port 또는 `orchestration`을 사용한다.
- 교차 도메인 Command·Query만 `orchestration`에 둔다. `orchestration`은 `common`이 아니며 Entity를 소유하지 않는다.
- ArchUnit 규칙을 첫 구현부터 적용한다.

원문: [아키텍처 개요](docs/06-architecture/architecture-overview.md), [패키지 구조](docs/06-architecture/package-structure.md), [의존성 규칙](docs/06-architecture/dependency-rules.md), [모듈 경계](docs/06-architecture/module-boundaries.md), [트랜잭션 경계](docs/06-architecture/transaction-boundaries.md), [조회 조합](docs/06-architecture/query-composition.md), [애플리케이션 흐름](docs/06-architecture/application-flow.md)

보안은 [보안 경계](docs/06-architecture/security-boundary.md)와 [ADR-WEB-003](docs/07-adr/platform/web-003-routing-boundary.md), Kakao·YouTube 연동은 [외부 연동](docs/06-architecture/external-integration.md)을 따른다.

## 7. API·데이터 규칙

- 백엔드 경로는 버전 없는 `/api`, 관리자 경계는 `/api/admin`이다. `/v1` 같은 경로 버전을 도입하지 않는다.
- 공개 GET 3종(`/api/restaurants`, `/api/creators`, `/api/restaurants/{id}`)은 무인증이다.
- 로그인 `POST /api/admin/auth/tokens`와 재발급 `POST /api/admin/auth/tokens/refresh`은 JWT를 요구하지 않고 각각 자격 증명과 Refresh 쿠키를 검증한다.
- 그 외 `/api/admin/**`은 JWT + `ADMIN`이며 정의되지 않은 경로는 기본 거부한다.
- 일반 목록 응답은 `{ "items": [...], "page": {...} }`, 페이지가 필요 없는 최소 선택 목록은 `{ "items": [...] }`다.
- 빈 목록은 `200`과 빈 `items`, 없는 단일 자원만 `404`다.
- 페이지는 1-base, 크기는 10·20·50, 기본값은 20이다.
- 외부 API 식별자는 불투명 문자열이다. UUID 여부나 생성 규칙을 외부 계약에서 전제하지 않는다.
- 모든 오류 응답에 서버 생성 `traceId`를 포함한다.
- Entity를 API 요청·응답에 노출하지 않는다.
- 트랜잭션은 Application Service의 public 메서드에서 시작한다. 조회는 `@Transactional(readOnly = true)`다. OSIV는 비활성화하고 `ddl-auto=validate`를 사용한다.
- 외부 HTTP 호출 중 DB 트랜잭션을 열지 않는다. 외부 호출 실패 시 핵심 Entity 저장은 0건이어야 한다.
- API 계약이나 테이블 변경은 소유자 합의 후 코드와 문서를 같은 PR에서 변경한다.

원문: [API 계약](docs/05-specs/api/README.md), [데이터 명세](docs/05-specs/data/README.md), [테이블 정의](docs/05-specs/data/table-definitions.md), [제약](docs/05-specs/data/constraints.md), [인덱스 전략](docs/05-specs/data/index-strategy.md), [생명주기](docs/05-specs/data/lifecycle-rules.md), [Flyway 계획](docs/05-specs/data/migration-plan.md)

공통 계약: [식별자](docs/05-specs/api/common/identifier-contract.md), [응답](docs/05-specs/api/common/response-contract.md), [오류](docs/05-specs/api/common/error-contract.md), [페이지네이션](docs/05-specs/api/common/pagination-contract.md), [검색·필터](docs/05-specs/api/common/filtering-contract.md), [날짜·시간](docs/05-specs/api/common/date-time-contract.md)

## 8. 테스트 규칙

- 클래스명은 `XxxTest` / `XxxIntegrationTest` / `XxxApiTest`, 메서드명은 `행위_조건_기대결과`, `@DisplayName`은 자연스러운 한글 문장을 사용한다. 본문은 Given-When-Then 구조로 작성한다.
- 단위 테스트는 외부 저장소 없이 실행한다.
- Repository·제약·트랜잭션은 PostgreSQL Testcontainers, Controller는 MockMvc, 외부 Adapter는 WireMock으로 검증한다.
- 로컬·자동화 테스트에서 실제 Kakao·YouTube API를 호출하지 않는다.
- 기능마다 정상·예외·경계를 최소 한 건씩 검증한다. 중복 등록과 원자성은 동시 요청과 부분 저장 0건을 검증한다.
- `Thread.sleep()`이나 임의 실행 대기를 사용하지 않고, 테스트 실행 순서에 의존하지 않는다.
- 커버리지 수치가 아니라 요구사항별 필수 시나리오 통과를 완료 기준으로 삼는다.
- Fake·Stub은 테스트 소스 또는 명시적 로컬 테스트 프로파일에만 둔다. 기본 프로파일에 Fake Bean을 등록하지 않는다.

원문: [ADR-TEST-001](docs/07-adr/quality/test-001-automation-strategy.md)

## 9. Git 협업

- `main`은 배포 기준, `develop`은 통합 브랜치이며 둘 다 직접 push하지 않는다.
- 브랜치는 최신 `develop`에서 분기한다.
- 브랜치 이름은 `feature/ws-{번호}-{기능명}`, `feature/t-{번호}-{작업명}`, `fix/{기능명}` 또는 변경 유형과 같은 접두사(`docs/`, `chore/`, `build/`, `ci/`, `test/`, `refactor/`)를 사용한다.
- `feature` → `develop`은 일반 Merge, `develop` → `main`만 Squash Merge한다.
- Conventional Commits의 `feat`, `fix`, `test`, `refactor`, `docs`, `build`, `ci`, `chore`를 사용한다.
- 모든 변경은 PR로 병합하며 작성자를 제외한 최소 2명 승인이 필요하다. AI가 작성한 코드도 동일하다.
- 서로 독립적인 변경은 커밋과 PR을 분리한다. 포매팅·정적 분석만 고치는 변경은 로직 변경과 분리한다.
- PR 본문 첫 줄에 `Closes #{이슈번호}`를 둔다.
- PR 본문과 커밋 메시지에 AI 도구 생성 표기, 도구 서명, 배지를 넣지 않는다.

PR 완료 점검은 [구현 컨벤션 9절](docs/06-architecture/implementation-conventions.md#9-pr-완료-점검)을 따른다.

## 10. Workstream과 소유권

| WS | 범위 | 담당 | PRD |
|---|---|---|---|
| [WS-01](docs/02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | 맛집 목록·검색·필터 | 양성훈 | [맛집 탐색](docs/04-product/prd/discovery/restaurant-discovery.md) |
| [WS-02](docs/02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | 맛집 상세·콘텐츠 조회 | 박진영 | [맛집 상세](docs/04-product/prd/detail/restaurant-detail.md) |
| [WS-03](docs/02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | 유튜버 기반 탐색·Visit 판정 | 이우람 | [유튜버 탐색](docs/04-product/prd/discovery/creator-discovery.md) |
| [WS-04](docs/02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 관리자 인증·데이터 등록 | 김인안 | [관리자 데이터 등록](docs/04-product/prd/admin/admin-data-management.md) |

배정이 충돌하면 [소유권](docs/03-team/ownership.md)이 최종 기준이다. 사람별 책임은 [역할](docs/03-team/roles.md), 도메인 소유권은 [도메인 경계](docs/02-analysis/domain-boundaries.md)를 따른다.

공통 파일은 동시에 수정하지 않는다. Spring Boot·Docker는 이우람, 프론트 공통 Layout은 양성훈, Flyway 순서는 박진영, 인증 공통은 김인안이 최종 병합한다.

Task 분해·선행 관계·완료 정의는 [MVP 구현 계획](docs/08-planning/mvp-2day-implementation-plan.md)을 따른다.

## 11. 문서 작성

각 `docs` 하위 디렉터리의 `README.md`를 진입점으로 사용한다. 문서를 만들거나 수정할 때 기존 스타일을 따른다.

- 상단에 `related_documents` frontmatter를 둔다.
- 한국어로 서술한다.
- 저장소 내부 문서는 상대 경로 링크로 연결한다.
- 문서의 주장과 링크가 실제 파일 및 현재 계약과 일치하는지 확인한다.

문서 탐색은 [프로젝트 개요](docs/00-overview/README.md)와 각 `docs` 하위 디렉터리의 `README.md`에서 시작한다.

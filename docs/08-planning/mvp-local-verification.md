---
status: Verified
verification_date: 2026-07-28
owners:
  - 이우람
  - 양성훈
  - 박진영
  - 김인안
related_documents:
  - mvp-2day-implementation-plan.md
  - README.md
  - ../01-requirements/non-functional-requirements.md
  - ../07-adr/quality/test-001-automation-strategy.md
  - ../06-architecture/implementation-conventions.md
---

# 1차 MVP 로컬 실행·회귀 검증 결과

`T-14`의 검증 결과를 기록한다. 실행한 명령과 관찰한 결과만 적고, 실행하지 못한 항목은 10절에 분리해 남긴다.

## 1. 검증 환경

| 항목 | 값 |
|---|---|
| 검증일 | 2026-07-28 |
| OS | Windows 11 Education 10.0.26200 |
| JDK | Temurin 21.0.11+10 |
| Gradle | Wrapper 8.14.3 |
| Docker | Engine 29.5.3 |
| Node.js / npm | 24.14.0 / 11.9.0 (고정값 24.18.0과 다름, 9절 참고) |
| 기준 커밋 | `1389a39` (develop) |

## 2. 필수 명령과 실행 결과

| 명령 | 목적 | 결과 |
|---|---|---|
| `.\gradlew.bat clean build` | 단위·통합·계약·인수 테스트 전체 | 성공. 45개 테스트 클래스, 229개 테스트, 실패 0, 오류 0, 건너뜀 0 |
| `npm --prefix frontend ci` | 프론트엔드 의존성 설치 | 성공 |
| `npm --prefix frontend run typecheck` | TypeScript 검사 | 성공 |
| `npm --prefix frontend run build` | 프로덕션 빌드 | 성공. Route 10개만 생성되고 MVP 제외 기능 Route는 없다 |
| `docker compose up -d --build` | 애플리케이션까지 컨테이너 통합 실행 | 성공. `postgres`·`redis`·`wiremock`·`app` 네 컨테이너가 healthy |
| `curl /internal/health/live` `ready` `dependencies` | 상태 확인 3종 | 각각 200. `dependencies`가 `db`·`redis`를 각각 `UP`으로 구분 보고 |

설정 계층화 이전 기준선은 43개 클래스·221개 테스트였고, 증가분은 이번에 추가한 `ConfigurationLayeringTest`(6건)와 `EnvironmentInvariantIntegrationTest`(2건)다.

프로파일을 지정하지 않은 실행이 실제로 fail closed인지도 확인했다. `java -jar build/libs/masit-on-0.0.1-SNAPSHOT.jar`는 `Failed to configure a DataSource: 'url' attribute is not specified`로 기동에 실패하고 종료 코드 1을 반환한다.

## 3. 환경 설정 계층과 운영 불변값

### 3.1 계층 구조

| 계층 | 파일 | 정의하는 값 |
|---|---|---|
| 공통 | `src/main/resources/application.yml` | 애플리케이션 이름, 운영 불변값(`spring.jpa.open-in-view=false`, `spring.jpa.hibernate.ddl-auto=validate`, Refresh 쿠키 속성), Hikari·Redis timeout, `server`, `management`, `logging`, `masiton.security` |
| 로컬 | `src/main/resources/application-local.yml` | PostgreSQL·Redis 접속값, Kakao·YouTube를 WireMock으로 돌리는 `masiton.integration.*` |
| 테스트 | `src/test/resources/application-test.yml` | 테스트 접속값, 테스트 전용 JWT 픽스처, 외부 호출 fail-closed 설정 |

`bootRun`은 `build.gradle`이 `local`을, 컨테이너 실행은 Compose가 `SPRING_PROFILES_ACTIVE=local`을 지정한다. Spring 컨텍스트 테스트는 공통 `@TestProfile`이 우선순위 높은 `@ActiveProfiles("test")`를 적용하므로 셸 환경·Gradle·IDE 단건 실행 결과가 같다. 프로파일을 지정하지 않은 애플리케이션 실행은 접속값이 없어 기동에 실패한다(fail closed). 프로파일 계층은 공통 계층에서 상속하는 값을 다시 선언하지 않으며, 규칙 원문은 [구현 컨벤션 4.5절](../06-architecture/implementation-conventions.md#45-설정-계층)에 있다.

### 3.2 수정한 결함

검증 전에는 `src/test/resources/application.yml`이 classpath에서 `src/main/resources/application.yml`을 완전히 가렸다. Spring Boot는 `classpath:/application.yml`을 단일 리소스로 해석하고 테스트 리소스가 앞에 오므로, 테스트 환경에서 두 운영 불변값이 **아예 정의되지 않았다**. 실행 전 확인값은 `open-in-view=null`, `ddl-auto=null`, 활성 프로파일 없음이었다. `AdminRegistrationJourneyAcceptanceTest`가 두 값을 클래스 단위로 다시 선언해 우회하고 있었고, 그 우회는 제거했다.

같은 이유로 로컬 실행에서는 `masiton.integration.*`를 정의하는 파일이 없어 Adapter의 `@Value` 기본값인 실제 Kakao·YouTube 호스트가 쓰였다. 구현 계획 5.5절과 CLAUDE.md 8절은 로컬을 WireMock으로 검증하도록 정하고 있어 로컬 계층에서 WireMock으로 덮어쓴다.

### 3.3 검증 방법

| 테스트 | 검증 내용 |
|---|---|
| `ConfigurationLayeringTest` | 공통 계층이 JPA 불변값과 Refresh 쿠키 불변값(`secure=true`, `same-site=Strict`, `path=/api/admin/auth`)을 선언하고, 모든 `application-*.yml`이 이를 재정의하지 않으며 `management.*`·`spring.jpa.*`를 재선언하지 않는다. 운영 리소스의 프로파일 파일이 JWT 키 재료를 담지 않는지도 확인한다. 프로파일 파일을 리소스 루트에서 탐색하므로 새 프로파일 파일도 자동으로 검사 대상이 된다 |
| `EnvironmentInvariantIntegrationTest` | `test` 프로파일로 기동한 실제 컨텍스트에서 JPA 불변값과 Refresh 쿠키 불변값이 유지된다. Testcontainers PostgreSQL을 써서 공유 DB 상태에 의존하지 않는다 |
| 컨테이너 실행 로그 | `masiton-app`이 `The following 1 profile is active: "local"`로 기동해 로컬 계층이 적용됨을 확인했다 |

검증 도중 프로파일 활성화를 Gradle `test` 태스크나 Config Data에만 두면 IDE 단건 실행 또는 셸의 `SPRING_PROFILES_ACTIVE`에 따라 `local` 프로파일이 적용돼 JWT 키가 비어 컨텍스트 기동이 실패하는 것을 재현했다. 모든 Spring 컨텍스트 테스트에 공통 `@TestProfile`을 적용해 실행기와 셸 상태에 무관하게 `test` 프로파일을 사용하도록 고쳤다.

## 4. 로컬 실행 절차 재현

`.env`를 `.env.example`에서 새로 복사한 상태에서 README 절차를 순서대로 재현했다.

1. `Copy-Item .env.example .env` → `. .\scripts\Initialize-LocalJwt.ps1`: `.env`에 `JWT_KEY_ID`·`JWT_PRIVATE_KEY_PEM`·`JWT_PUBLIC_KEY_PEM`이 채워졌다.
2. `docker compose up -d postgres redis wiremock` → `.\gradlew.bat bootRun`: 상태 확인 3종 200, `db`·`redis` `UP`.
3. `.\scripts\New-LocalAdmin.ps1 -LoginId local-admin`: 계정을 생성하고, 같은 `LoginId`로 재실행하면 중복 생성 없이 종료한다.
4. 관리자 로그인 → 맛집·유튜버·영상 미리보기와 확정 → 방문 관계 등록: 네 등록 모두 `201`. 미리보기 후보 데이터는 WireMock 픽스처에서 왔다.
5. 공개 조회: `GET /api/restaurants`, `creatorId` 필터, `GET /api/restaurants/{id}`가 모두 등록 결과를 반영했고 상세의 `contentStatus`는 `AVAILABLE`, 방문 유튜버 1건·영상 1건이었다.
6. `docker compose up -d --build`로 애플리케이션까지 컨테이너에서 실행한 뒤 같은 관리자 로그인과 Kakao 미리보기를 반복해 컨테이너 네트워크(`http://wiremock:8080`)로도 외부 검증이 WireMock을 향하는 것을 확인했다.

관리자 계정은 사전 발급 운영 대상이라 Flyway 기준 데이터에 넣지 않으므로 3번 절차를 README에 명시했다. 절차가 없던 상태에서는 관리자 흐름을 재현할 수 없었다.

## 5. 반응형 검증

`frontend`를 `npm run dev`로 띄우고 대표 화면 폭 5종에서 공개·관리자 핵심 화면 4개를 확인했다. 각 폭에서 문서 `scrollWidth`가 viewport 폭과 같은지, viewport 밖으로 넘치는 요소가 있는지 측정했다. 측정은 지정 폭으로 고정한 동일 출처 iframe에서 수행했고, 미디어 쿼리는 해당 폭 기준으로 평가됐다. 세로 스크롤바가 있는 화면은 실제 콘텐츠 폭이 지정 폭보다 15px 작아(360px → 345px) 기준보다 좁은 조건에서 확인한 셈이다.

| 화면 | 360px | 390px | 768px | 1280px | 1440px |
|---|---|---|---|---|---|
| 맛집 목록 `/restaurants` | 가로 넘침 없음 | 없음 | 없음 | 없음 | 없음 |
| 맛집 상세 `/restaurants/{id}` | 없음 | 없음 | 없음 | 없음 | 없음 |
| 관리자 로그인 `/admin/login` | 없음 | 없음 | 없음 | 없음 | 없음 |
| 관리자 맛집 등록 `/admin/restaurants/new` | 없음 | 없음 | 없음 | 없음 | 없음 |

- 다섯 폭 전부에서 viewport를 넘는 요소가 0건이었다.
- 목록은 이름·자치구·카테고리·유튜버 필터와 페이지 정보를, 상세는 주소·전화번호·Kakao 장소 링크·방문 유튜버·영상 카드를 모든 폭에서 표시했다. 한글 표시도 정상이었다.
- 브라우저 콘솔 오류 0건.

## 6. 비밀·민감 로그 검사

| 점검 항목 | 결과 |
|---|---|
| 커밋된 비밀정보 | `.env`는 `.gitignore` 대상이고 `.env.example`에 실제 비밀값이 없다. 운영 JWT 키는 전부 환경 변수 주입이며 기본값이 비어 있다. 테스트 전용 RSA 픽스처는 9절에 위험으로 기록했다 |
| 로그 | `src/main/java`의 로깅 지점은 3개 클래스·5개 호출뿐이고 오류 코드, 예외 클래스 단순명, 자원 식별자만 남긴다. 요청 본문을 덤프하는 필터가 없고 `System.out`·`printStackTrace` 사용이 0건이다 |
| 오류 응답 | `ErrorResponse`가 `code`·`message`·`errors`·`resource`·`traceId` 다섯 필드로 고정돼 스택 트레이스·SQL·예외 메시지가 도달할 경로가 없다. 검증 실패 응답에 `rejectedValue`를 넣지 않아 입력 비밀번호가 반사되지 않는다 |
| 저장소 | 비밀번호는 BCrypt 해시, 확인 Token과 Refresh Token은 SHA-256 해시로만 저장된다. 관리자 계정·키를 seed하는 마이그레이션이 없다 |
| 프론트엔드 | Access Token을 모듈 스코프 메모리에만 두고 `localStorage`·`sessionStorage`·`document.cookie`를 쓰지 않는다. Refresh Token은 `HttpOnly` 쿠키로만 오간다 |
| 실행 로그 | 로그인과 미리보기(비밀번호 전달·확인 Token 발급)를 포함한 컨테이너 실행 로그 62줄에서 비밀번호 원문·JWT·PEM·API Key·BCrypt 해시 패턴 일치 0건. `WARN`·`ERROR` 0건 |
| 로컬 계정 생성 스크립트 | 비밀번호 원문을 명령줄 인자로 넘기지 않는다. 초기 구현은 `java` 인자로 전달해 프로세스 커맨드라인과 프로세스 생성 감사 로그(4688)에 원문이 남을 수 있었고, 검증 중 자식 프로세스 전용 환경 변수 전달로 고쳤다. BCrypt 해시도 `psql` 인자 대신 stdin으로 전달한다 |

애플리케이션 로그만 검사한 초기 판정에는 스크립트 실행 경로가 빠져 있었다. 위 마지막 항목은 그 지적을 반영해 수정한 결과다.

## 7. 영향받는 Workstream 계약 테스트

`.\gradlew.bat clean build`가 네 Workstream의 계약·인수 테스트를 함께 실행하며 전부 통과했다. 대표 테스트는 다음과 같다.

- WS-01: `RestaurantSearchApiTest`, `RestaurantSearchQueryAdapterIntegrationTest`
- WS-02: `RestaurantDetailApiTest`, `VisitContentQueryIntegrationTest`, `RestaurantDetailContentFailureIntegrationTest`
- WS-03: `CreatorApiTest`, `VisitQueryIntegrationTest`
- WS-04: `SecurityBoundaryApiTest`, `AdminAuthenticationRefreshPostgreSqlIntegrationTest`, `ConfirmationTokenPostgreSqlIntegrationTest`, `VisitRelationshipRegistrationIntegrationTest`
- 전체 흐름: `AdminRegistrationJourneyAcceptanceTest`
- 구조 규칙: `ArchitectureTest`, `FlywayMigrationIntegrationTest`, `ConstraintViolationIntegrationTest`

## 8. 완료 정의 체크리스트

구현 계획 14절 항목별 판정이다.

| 완료 정의 | 판정 | 근거 |
|---|---|---|
| 문서화된 명령으로 로컬 전체 환경 실행 | 통과 | 4절. `.env` 초기화·의존 서비스·bootRun·컨테이너 통합 실행 전부 재현 |
| 목록·이름 검색·세 필터 AND 조합 | 통과 | `RestaurantSearchApiTest`, 로컬에서 `creatorId` 필터 반영 확인 |
| 페이지 크기·정렬·빈 결과·잘못된 입력 | 통과 | `RestaurantSearchApiTest`, 빈 목록이 200과 빈 `items`로 응답 |
| 상세 기본 정보와 방문 유튜버·영상 | 통과 | 4절 5번, `RestaurantDetailApiTest` |
| 영상 없는 맛집도 정상 조회 | 통과 | `VisitContentQueryIntegrationTest`, `RestaurantDetailApiTest` |
| 관리자 로그인·재발급·로그아웃과 접근 통제 | 통과 | `SecurityBoundaryApiTest`, `AdminAuthenticationRefreshPostgreSqlIntegrationTest`. 로컬 로그인은 직접 확인 |
| WireMock 미리보기와 확인 Token으로 세 자원 등록 | 통과 | 4절 4번, `ConfirmationTokenPostgreSqlIntegrationTest` |
| Visit 참조·채널 일치·중복·rollback | 통과 | `VisitRelationshipRegistrationIntegrationTest`, `RegisterVisitServiceTest` |
| 등록 결과가 목록·필터·상세에 반영 | 통과 | 4절 5번, `AdminRegistrationJourneyAcceptanceTest` |
| 확장 기능 미노출 | 통과 | `next build` Route 10개에 지도·찜·테마·일반 로그인 Route가 없다 |
| 비밀번호·Token·API Key 원문 부재 | 통과 | 6절 |
| 핵심 단위·통합·계약·인수 테스트 통과 | 통과 | 2절. 229개 테스트 실패 0 |
| 지정 화면 폭에서 핵심 흐름 완료 | 통과 | 5절 |
| AWS 리소스 미생성 | 통과 | 로컬 Docker만 사용했고 AWS 관련 설정·자원이 없다 |

## 9. 알려진 위험

| 위험 | 영향 | 대응 |
|---|---|---|
| CI 자동화가 없다. 필수 명령은 각 담당자가 로컬에서 실행한다 | 병합 전 회귀 검증이 사람 손에 달려 있다 | 2절 명령을 PR 점검 목록으로 사용하고, CI 파이프라인 구성은 후속 작업으로 분리한다 |
| 테스트 전용 RSA 키가 저장소에 평문으로 있다(`src/test/resources/application-test.yml`) | 커밋된 키는 회수할 수 없다. 실환경에 재사용하면 `ADMIN` Token 위조가 가능하다 | 영구히 테스트 전용으로 고정하고 `key-id`를 `test-1`로 구분한다. 실환경 키는 `scripts/Initialize-LocalJwt.ps1`로 환경별 생성한다. 이 키는 `T-04`(커밋 `8809c9c`)에서 이미 저장소에 들어왔고 `T-14`는 파일 위치만 옮겼다. `SecurityConfigurationApiTest`처럼 실행마다 키를 생성하는 방식으로 픽스처를 없애는 것을 후속 과제로 남긴다 |
| 외부 Adapter의 `base-url` 기본값이 실제 Kakao·YouTube 호스트다 | `masiton.integration.*`를 정의하지 않는 새 프로파일을 추가하면 기동은 성공하고 관리자 미리보기 시점에 실제 호스트로 요청이 나간다. Key 기본값이 비어 있어 자격 증명 유출은 없고 검색어만 나간다 | 기본값을 제거해 미설정 시 기동 실패로 바꾸는 방안은 외부 연동 소유자(김인안)·공통 설정 소유자(이우람) 합의가 필요하므로 `T-14`에서 변경하지 않았다 |
| WireMock Admin API(`/__admin`)가 무인증으로 8081에 열려 있다 | 로컬 프로파일에 실제 제공자 Key를 넣으면 요청 저널에 헤더·query string이 남아 같은 네트워크에서 회수될 수 있다 | 로컬 프로파일의 Kakao·YouTube Key를 고정 문자열로 바꿔 환경 변수 override를 막았다. 로컬 프로파일에 실제 Key를 넣지 않는다 |
| 로컬 계정 생성 스크립트가 대상 Docker 엔드포인트를 신뢰한다 | 원격 Docker context나 원격 `DOCKER_HOST`에서 실행하면 로컬이 아닌 데이터베이스에 `ADMIN` 계정이 생성된다 | 엔드포인트가 `npipe://`·`unix://`가 아니면 중단하는 가드를 넣었다 |
| Node.js 로컬 버전(24.14.0)이 고정값 24.18.0과 다르다 | 고정 버전에서만 나타나는 빌드 차이를 놓칠 수 있다 | 검증 환경을 기록하고 고정 버전으로 재확인한다 |
| 로그 수준을 DEBUG로 올리면 프레임워크가 검증 실패한 비밀번호를 남길 수 있다 | 비밀번호 원문 기록 위험 | 실제 자격 증명을 다루는 환경에서 root 수준을 DEBUG로 올리지 않는다 |
| YouTube API Key가 query string으로 전달된다 | 요청 URI를 남기는 로깅·프록시가 추가되면 Key가 기록된다 | YouTube Adapter 경로에 URI를 남기는 로깅을 추가하지 않는다 |
| Actuator 노출 범위를 넓히면 `configprops`·`env`로 JWT 개인키에 도달할 수 있다 | 비밀키 노출 | `management.endpoints.web.exposure.include`를 `health`에서 넓히지 않는다 |
| `.env`를 예전 파일로 재사용하면 초기화 스크립트가 JWT 값을 기록하지 못한다 | 관리자 인증이 조용히 실패한다 | README에 `.env.example`에서 새로 복사하고 `JWT_*` 세 줄을 확인하도록 명시했다 |
| 정식 성능 p95 측정을 하지 않았다 | 성능 목표 미확인 | 구현 계획 10절대로 후속 안정화 과제로 남긴다 |

## 10. 검증하지 않은 항목

- 실제 단말·브라우저 매트릭스(PC Chrome·Edge, Android Chrome, iPhone Safari)에서의 확인. 이번 검증은 단일 브라우저 엔진에서 대표 화면 폭 5종만 확인했다.
- 화면 캡처 증적. 검증 환경에서 스크린샷을 얻을 수 없어 DOM 구조·텍스트·가로 넘침 측정으로 대체했다.
- 정식 성능 부하 시험과 p95 측정.
- 실제 Kakao·YouTube Sandbox 계약 검증. 구현 계획 5.5절대로 최종 배포 전 과제로 남는다.

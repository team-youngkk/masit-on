---
related_documents:
  - docs/00-overview/service-overview.md
  - docs/00-overview/scope.md
  - docs/04-product/prd/00-product-overview.md
  - docs/08-planning/mvp-2day-implementation-plan.md
  - docs/08-planning/mvp-local-verification.md
  - docs/08-planning/first-expansion-baseline-review.md
  - docs/02-analysis/first-expansion-workstreams.md
  - docs/06-architecture/README.md
---

# masit-on
유튜버가 방문한 맛집을 지역, 음식 종류, 유튜버별로 탐색할 수 있는 맛집 정보 서비스

MVP 범위(공개 탐색·상세, 관리자 데이터 등록)에 1차 확장(회원 계정·인증, 찜·최근 본 맛집, 지도 탐색, 유튜버 상세)이 더해진 상태다. 마이그레이션은 `V1`(초기 스키마) · `V2`(1차 확장 통합, [마이그레이션 계획](docs/05-specs/data/migration-plan.md) 2.3절) 두 파일로 구성된다.

## 로컬 실행

### 사전 조건

- JDK 21 (`java -version`이 21인지 확인)
- Docker Desktop 실행 중
- Gradle은 설치하지 않는다. 저장소의 Wrapper(`./gradlew`, 8.14.3)를 사용한다.

### 최초 1회

```powershell
Copy-Item .env.example .env
. .\scripts\Initialize-LocalJwt.ps1
```

초기화 스크립트는 로컬 JWT RSA 키와 회원 Action 메일 Token용 AES-256 키를 `.env`와 현재 PowerShell 세션에 생성한다. 운영에서는 `MEMBER_ACTION_MAIL_ACTIVE_KEY_ID`와 `MEMBER_ACTION_MAIL_ACTIVE_KEY`를 저장소가 아닌 배포 환경의 비밀 관리 수단으로 주입한다.

`.env`는 로컬 전용 값이며 커밋하지 않는다. 운영 자격 증명을 넣지 않는다. 새 PowerShell 세션에서 `bootRun`을 실행할 때는 스크립트를 다시 dot-source한다.

`.env`를 `.env.example`에서 새로 복사하지 않고 예전 파일을 그대로 쓰면 `JWT_*` 또는 `MEMBER_ACTION_MAIL_*` 항목이 없어 초기화 스크립트가 값을 기록하지 못한다. `.env`에 `JWT_KEY_ID`, `JWT_PRIVATE_KEY_PEM`, `JWT_PUBLIC_KEY_PEM`, `MEMBER_ACTION_MAIL_ACTIVE_KEY_ID`, `MEMBER_ACTION_MAIL_ACTIVE_KEY` 항목이 있는지 확인한다.

### 개발 루프 (의존 서비스는 컨테이너, 애플리케이션은 로컬)

```powershell
docker compose up -d postgres redis wiremock
.\gradlew.bat bootRun
```

### 프론트엔드

```powershell
npm --prefix frontend ci
npm --prefix frontend run dev
```

`http://localhost:3000`에서 공개 화면과 관리자 화면을 사용한다. 프론트엔드는 `/api`를 `API_BASE_URL`(기본값 `http://localhost:8080`)로 전달하므로 백엔드를 먼저 띄운다.

지도 화면(`/map`)은 `NEXT_PUBLIC_KAKAO_MAPS_JS_KEY`가 있어야 Kakao 지도를 표시한다. 이 값은 Kakao 콘솔에서 허용 도메인으로 제한하는 브라우저 노출 식별자이며 비밀키가 아니다([ADR-MAP-001 6.5](docs/07-adr/integration/map-001-map-bounds-search.md#65-키-비용과-외부-서비스-경계)). 값이 없으면 지도는 SDK 오류 상태로 대체되고 그 밖의 화면은 영향을 받지 않는다.

### 로컬 관리자 계정

관리자 계정은 사전 발급 대상이라 Flyway 기준 데이터에 넣지 않는다. 관리자 화면·API를 확인하려면 로컬에서만 계정을 만든다.

```powershell
.\scripts\New-LocalAdmin.ps1 -LoginId local-admin
```

비밀번호는 실행 중 프롬프트로 입력받고 BCrypt 해시만 컨테이너로 전달한다. 원문은 명령줄 인자나 파일에 남기지 않는다. **`-Password`를 명령줄로 넘기지 않는다.** 넘기면 PowerShell 히스토리 파일(`ConsoleHost_history.txt`)에 평문으로 남는다. 같은 `LoginId`로 다시 실행해도 계정을 중복 생성하지 않고, 원격 Docker context에서 실행하면 중단한다. 로컬 전용 값이며 운영 비밀번호를 쓰지 않는다.

### 환경별 설정 계층

| 계층 | 파일 | 담는 값 |
|---|---|---|
| 공통 | `src/main/resources/application.yml` | 모든 환경 공통값과 운영 불변값(`spring.jpa.open-in-view=false`, `spring.jpa.hibernate.ddl-auto=validate`), `management`·`logging`·`masiton.security` |
| 로컬 | `src/main/resources/application-local.yml` | PostgreSQL·Redis 접속값, Kakao·YouTube를 WireMock으로 돌리는 `masiton.integration.*` |
| 테스트 | `src/test/resources/application-test.yml` | 테스트 접속값, 테스트 전용 JWT 픽스처, 외부 호출 fail-closed 설정 |

`bootRun`은 `local` 프로파일로 실행되고(`build.gradle`), 컨테이너 실행은 `SPRING_PROFILES_ACTIVE=local`을 받는다. Spring 컨텍스트 테스트는 공통 `@TestProfile`이 `test` 프로파일을 활성화하므로 셸 환경·Gradle·IDE 단건 실행에서 동일하게 동작한다. 프로파일을 지정하지 않은 애플리케이션 실행은 접속값이 없어 기동에 실패한다.

각 프로파일 계층은 공통 계층에서 상속하는 값을 다시 선언하지 않는다. 규칙 원문은 [구현 컨벤션 4.5절](docs/06-architecture/implementation-conventions.md#45-설정-계층)이며 `ConfigurationLayeringTest`와 `EnvironmentInvariantIntegrationTest`가 검증한다.

### 통합 실행 (애플리케이션까지 컨테이너)

```powershell
docker compose up -d --build
```

### 상태 확인

```bash
curl http://localhost:8080/internal/health/live
curl http://localhost:8080/internal/health/ready
curl http://localhost:8080/internal/health/dependencies
```

`live`는 프로세스, `ready`는 PostgreSQL 준비 상태, `dependencies`는 PostgreSQL과 Redis를 각각 구분해 보고한다. 이 경로는 내부 운영용이며 최종 배포에서 인터넷에 노출하지 않는다.

### 빌드와 테스트

```powershell
.\gradlew.bat clean build
```

통합 테스트가 Testcontainers로 PostgreSQL·Redis·WireMock 컨테이너를 테스트 실행 시점에 띄우므로 Docker만 실행 중이면 된다. 테스트는 Compose 서비스나 실제 Kakao·YouTube API에 연결하지 않는다.

프론트엔드까지 포함한 병합 전 필수 명령은 다음 네 개다.

```powershell
.\gradlew.bat clean build
npm --prefix frontend ci
npm --prefix frontend run typecheck
npm --prefix frontend run build
```

가장 최근 실행 결과와 완료 정의 판정, 알려진 위험은 [로컬 실행·회귀 검증 결과](docs/08-planning/mvp-local-verification.md)에 있다.

### 종료와 초기화

```bash
# 컨테이너 종료 (데이터 유지)
docker compose down

# 컨테이너와 데이터 볼륨까지 삭제
docker compose down -v
```

### 기본 포트

| 대상 | 주소 |
|---|---|
| 애플리케이션 | `http://localhost:8080` |
| PostgreSQL | `localhost:5432` |
| Redis | `localhost:6379` |
| WireMock | `http://localhost:8081` (관리 `/__admin`) |

포트가 겹치면 `.env`의 `APP_PORT`, `POSTGRES_PORT`, `REDIS_PORT`, `WIREMOCK_PORT`를 바꾼다.

`.env`는 Docker Compose만 읽는다. `bootRun`은 `.env`를 로드하지 않으므로, 컨테이너 포트를 바꿨다면 애플리케이션에도 같은 값을 환경 변수로 넘겨야 한다. `WIREMOCK_PORT`를 바꿨다면 `KAKAO_BASE_URL`, `YOUTUBE_BASE_URL`도 같이 넘긴다. JWT 설정은 위 초기화 스크립트를 dot-source해 현재 셸에 주입한다.

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:15432/masiton'
$env:REDIS_PORT = '16379'
.\gradlew.bat bootRun
```

## 문서

구현 규칙과 설계 문서는 [CLAUDE.md](CLAUDE.md)와 [docs/](docs/)를 따른다. 진입점은 각 디렉터리의 `README.md`다.

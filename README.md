---
related_documents:
  - docs/00-overview/service-overview.md
  - docs/00-overview/scope.md
  - docs/04-product/prd/00-product-overview.md
  - docs/08-planning/mvp-2day-implementation-plan.md
  - docs/06-architecture/README.md
---

# masit-on
유튜버가 방문한 맛집을 지역, 음식 종류, 유튜버별로 탐색할 수 있는 맛집 정보 서비스

## 로컬 실행

### 사전 조건

- JDK 21 (`java -version`이 21인지 확인)
- Docker Desktop 실행 중
- Gradle은 설치하지 않는다. 저장소의 Wrapper(`./gradlew`, 8.14.3)를 사용한다.

### 최초 1회

```bash
cp .env.example .env
```

`.env`는 로컬 전용 값이며 커밋하지 않는다. 운영 자격 증명을 넣지 않는다.

### 개발 루프 (의존 서비스는 컨테이너, 애플리케이션은 로컬)

```bash
docker compose up -d postgres redis wiremock
./gradlew bootRun
```

### 통합 실행 (애플리케이션까지 컨테이너)

```bash
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

```bash
./gradlew clean build
```

통합 테스트가 Testcontainers로 PostgreSQL·Redis·WireMock 컨테이너를 띄우므로 Docker가 실행 중이어야 한다. 테스트는 실제 Kakao·YouTube API를 호출하지 않는다.

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

## 문서

구현 규칙과 설계 문서는 [CLAUDE.md](CLAUDE.md)와 [docs/](docs/)를 따른다. 진입점은 각 디렉터리의 `README.md`다.

# 맛잇온 ADR 인덱스

| ADR ID | 제목 | 상태 | 우선순위 | 관련 기술 | 관련 범위 | 문서 경로 |
|---|---|---|---|---|---|---|
| ADR-LANG-001 | Java 21 런타임 기준 | Accepted | Critical | JDK 21.0.12 LTS | 전체 백엔드 | [문서](platform/lang-001-java-21-runtime.md) |
| ADR-BUILD-001 | Gradle과 Groovy DSL 빌드 체계 | Accepted | Critical | Gradle 8.14.3, Groovy DSL | 전체 백엔드·CI | [문서](platform/build-001-gradle-groovy.md) |
| ADR-FRAME-001 | Spring Boot 애플리케이션 기준 | Accepted | Critical | Spring Boot 4.1.0, Spring Security 7.1.0 BOM | 전체 백엔드 | [문서](platform/frame-001-spring-boot.md) |
| ADR-WEB-001 | 프론트엔드 런타임과 프레임워크 기준 | Accepted | High | Node.js 24.18.0, Next.js 16.2.11, TypeScript 7.0.2 | 전체 웹 UI | [문서](platform/web-001-frontend-platform.md) |
| ADR-WEB-002 | 프론트엔드 데이터와 상태 책임 분리 | Accepted | Medium | Server Components `fetch`, TanStack Query, URL Query Parameter, `useState` | WS-01~04 웹 UI | [문서](platform/web-002-data-state.md) |
| ADR-ARCH-001 | 단일 모듈 도메인 중심 모놀리스 | Accepted | Critical | 단일 모듈, 계층형 모놀리스 | 전체 Workstream | [문서](architecture/arch-001-domain-monolith.md) |
| ADR-ARCH-002 | 외부 연동 Port/Adapter 경계 | Accepted | High | Port/Adapter | WS-02~04, 외부 연동 | [문서](architecture/arch-002-external-ports-adapters.md) |
| ADR-DATA-001 | PostgreSQL 17.10 주 데이터베이스 | Accepted | Critical | PostgreSQL 17.10 | 전체 영속 데이터 | [문서](data/data-001-postgresql.md) |
| ADR-DATA-002 | 개발 Docker와 운영 RDS 데이터베이스 분리 | Accepted | High | Docker PostgreSQL, Amazon RDS | 개발·테스트·운영 | [문서](data/data-002-database-placement.md) |
| ADR-DATA-003 | Spring Data JPA 기본 데이터 접근 | Accepted | High | Spring Data JPA | 전체 Repository | [문서](data/data-003-spring-data-jpa.md) |
| ADR-DATA-004 | Flyway 스키마 마이그레이션 | Accepted | Critical | Flyway 12.4.0 | 전체 스키마 변경 | [문서](data/data-004-flyway.md) |
| ADR-AUTH-001 | 관리자 Spring Security JWT 인증·인가 | Accepted | Critical | Spring Security 7.1.0, JWT, Redis 8.8 Refresh Token | WS-04 관리자 | [문서](security/auth-001-spring-security-jwt.md) |
| ADR-DATA-005 | Redis 8.8 관리자 Refresh Token 저장소 | Accepted | Critical | Redis Open Source 8.8 | WS-04 인증·운영 | [문서](data/data-005-redis-refresh-token.md) |
| ADR-EXT-001 | 관리자 외부 기준정보 확인 서비스 | Accepted | High | Kakao Local REST API V2, YouTube Data API v3 | WS-04 등록 | [문서](integration/ext-001-reference-verification.md) |
| ADR-TEST-001 | 계층별 자동화 테스트 전략 | Accepted | Critical | JUnit 5, Mockito, Spring Boot Test, Testcontainers 2.0.5, WireMock | 전체 Workstream | [문서](quality/test-001-automation-strategy.md) |
| ADR-OBS-001 | 애플리케이션 로그와 운영 관측 기준 | Accepted | High | SLF4J, Logback, Actuator, CloudWatch | 전체 운영 | [문서](quality/obs-001-logging-observability.md) |
| ADR-SEC-001 | 비밀정보와 AWS 워크로드 인증 | Accepted | Critical | Parameter Store, KMS, IAM Role, GitHub OIDC | 운영·CI·외부 연동 | [문서](security/sec-001-secrets-workload-identity.md) |
| ADR-RUNTIME-001 | Docker 기반 실행 환경 | Accepted | High | Docker | 개발·테스트·배포 산출물 | [문서](platform/runtime-001-docker.md) |
| ADR-CI-001 | GitHub Actions 빌드·테스트 품질 게이트 | Accepted | Critical | GitHub Actions | 전체 배포 후보 | [문서](platform/ci-001-github-actions-quality-gate.md) |

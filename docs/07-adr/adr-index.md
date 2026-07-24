---
related_documents:
  1: README.md
  2: ../06-architecture/technology-policy.md
  3: adr-backlog.md
  4: adr-traceability.md
  5: platform/lang-001-java-21-runtime.md
  6: platform/build-001-gradle-groovy.md
  7: platform/frame-001-spring-boot.md
  8: platform/web-001-frontend-platform.md
  9: platform/web-002-data-state.md
  10: ../02-analysis/mvp-workstreams.md
  11: architecture/arch-001-domain-monolith.md
  12: architecture/arch-002-external-ports-adapters.md
  13: data/data-001-postgresql.md
  14: data/data-002-database-placement.md
  15: data/data-003-spring-data-jpa.md
  16: data/data-004-flyway.md
  17: security/auth-001-spring-security-jwt.md
  18: data/data-005-redis-refresh-token.md
  19: integration/ext-001-reference-verification.md
  20: quality/test-001-automation-strategy.md
  21: quality/obs-001-logging-observability.md
  22: security/sec-001-secrets-workload-identity.md
  23: platform/runtime-001-docker.md
  24: platform/ci-001-github-actions-quality-gate.md
---

# 맛잇온 ADR 인덱스

| ADR ID | 제목 | 상태 | 우선순위 | 관련 기술 | 관련 범위 | 문서 경로 |
|---|---|---|---|---|---|---|
| [#5 ADR-LANG-001](platform/lang-001-java-21-runtime.md) | Java 21 런타임 기준 | Accepted | Critical | JDK 21.0.12 LTS | 전체 백엔드 | [#5 문서](platform/lang-001-java-21-runtime.md) |
| [#6 ADR-BUILD-001](platform/build-001-gradle-groovy.md) | Gradle과 Groovy DSL 빌드 체계 | Accepted | Critical | Gradle 8.14.3, Groovy DSL | 전체 백엔드·CI | [#6 문서](platform/build-001-gradle-groovy.md) |
| [#7 ADR-FRAME-001](platform/frame-001-spring-boot.md) | Spring Boot 애플리케이션 기준 | Accepted | Critical | Spring Boot 4.1.0, Spring Security 7.1.0 BOM | 전체 백엔드 | [#7 문서](platform/frame-001-spring-boot.md) |
| [#8 ADR-WEB-001](platform/web-001-frontend-platform.md) | 프론트엔드 런타임과 프레임워크 기준 | Accepted | High | Node.js 24.18.0, Next.js 16.2.11, TypeScript 7.0.2 | 전체 웹 UI | [#8 문서](platform/web-001-frontend-platform.md) |
| [#9 ADR-WEB-002](platform/web-002-data-state.md) | 프론트엔드 데이터와 상태 책임 분리 | Accepted | Medium | Server Components `fetch`, TanStack Query, URL Query Parameter, `useState` | [#10 WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)~[#10 WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 웹 UI | [#9 문서](platform/web-002-data-state.md) |
| [#11 ADR-ARCH-001](architecture/arch-001-domain-monolith.md) | 단일 모듈 도메인 중심 모놀리스 | Accepted | Critical | 단일 모듈, 계층형 모놀리스 | 전체 Workstream | [#11 문서](architecture/arch-001-domain-monolith.md) |
| [#12 ADR-ARCH-002](architecture/arch-002-external-ports-adapters.md) | 외부 연동 Port/Adapter 경계 | Accepted | High | Port/Adapter | [#10 WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)~[#10 WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록), 외부 연동 | [#12 문서](architecture/arch-002-external-ports-adapters.md) |
| [#13 ADR-DATA-001](data/data-001-postgresql.md) | PostgreSQL 17.10 주 데이터베이스 | Accepted | Critical | PostgreSQL 17.10 | 전체 영속 데이터 | [#13 문서](data/data-001-postgresql.md) |
| [#14 ADR-DATA-002](data/data-002-database-placement.md) | 개발 Docker와 운영 RDS 데이터베이스 분리 | Accepted | High | Docker PostgreSQL, Amazon RDS | 개발·테스트·운영 | [#14 문서](data/data-002-database-placement.md) |
| [#15 ADR-DATA-003](data/data-003-spring-data-jpa.md) | Spring Data JPA 기본 데이터 접근 | Accepted | High | Spring Data JPA | 전체 Repository | [#15 문서](data/data-003-spring-data-jpa.md) |
| [#16 ADR-DATA-004](data/data-004-flyway.md) | Flyway 스키마 마이그레이션 | Accepted | Critical | Flyway 12.4.0 | 전체 스키마 변경 | [#16 문서](data/data-004-flyway.md) |
| [#17 ADR-AUTH-001](security/auth-001-spring-security-jwt.md) | 관리자 Spring Security JWT 인증·인가 | Accepted | Critical | Spring Security 7.1.0, JWT, Redis 8.8 Refresh Token | [#10 WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 관리자 | [#17 문서](security/auth-001-spring-security-jwt.md) |
| [#18 ADR-DATA-005](data/data-005-redis-refresh-token.md) | Redis 8.8 관리자 Refresh Token 저장소 | Accepted | Critical | Redis Open Source 8.8 | [#10 WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 인증·운영 | [#18 문서](data/data-005-redis-refresh-token.md) |
| [#19 ADR-EXT-001](integration/ext-001-reference-verification.md) | 관리자 외부 기준정보 확인 서비스 | Accepted | High | Kakao Local REST API V2, YouTube Data API v3 | [#10 WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 등록 | [#19 문서](integration/ext-001-reference-verification.md) |
| [#20 ADR-TEST-001](quality/test-001-automation-strategy.md) | 계층별 자동화 테스트 전략 | Accepted | Critical | JUnit 5, Mockito, Spring Boot Test, Testcontainers 2.0.5, WireMock | 전체 Workstream | [#20 문서](quality/test-001-automation-strategy.md) |
| [#21 ADR-OBS-001](quality/obs-001-logging-observability.md) | 애플리케이션 로그와 운영 관측 기준 | Accepted | High | SLF4J, Logback, Actuator, CloudWatch | 전체 운영 | [#21 문서](quality/obs-001-logging-observability.md) |
| [#22 ADR-SEC-001](security/sec-001-secrets-workload-identity.md) | 비밀정보와 AWS 워크로드 인증 | Accepted | Critical | Parameter Store, KMS, IAM Role, GitHub OIDC | 운영·CI·외부 연동 | [#22 문서](security/sec-001-secrets-workload-identity.md) |
| [#23 ADR-RUNTIME-001](platform/runtime-001-docker.md) | Docker 기반 실행 환경 | Accepted | High | Docker | 개발·테스트·배포 산출물 | [#23 문서](platform/runtime-001-docker.md) |
| [#24 ADR-CI-001](platform/ci-001-github-actions-quality-gate.md) | GitHub Actions 빌드·테스트 품질 게이트 | Accepted | Critical | GitHub Actions | 전체 배포 후보 | [#24 문서](platform/ci-001-github-actions-quality-gate.md) |

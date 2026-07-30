---
related_documents:
  - README.md
  - ../06-architecture/technology-policy.md
  - adr-backlog.md
  - adr-traceability.md
  - platform/lang-001-java-21-runtime.md
  - platform/build-001-gradle-groovy.md
  - platform/frame-001-spring-boot.md
  - platform/web-001-frontend-platform.md
  - platform/web-002-data-state.md
  - platform/web-003-routing-boundary.md
  - ../02-analysis/mvp-workstreams.md
  - architecture/arch-001-domain-monolith.md
  - architecture/arch-002-external-ports-adapters.md
  - data/data-001-postgresql.md
  - data/data-002-database-placement.md
  - data/data-003-spring-data-jpa.md
  - data/data-004-flyway.md
  - security/auth-001-spring-security-jwt.md
  - security/auth-002-member-jwt-refresh-token.md
  - security/auth-003-confirmation-token.md
  - data/data-005-redis-refresh-token.md
  - data/data-007-uuid-v4-identifiers.md
  - data/data-008-publication-lifecycle-soft-delete.md
  - data/data-010-recent-view-retention-cleanup.md
  - integration/ext-001-reference-verification.md
  - integration/map-001-map-bounds-search.md
  - quality/test-001-automation-strategy.md
  - quality/obs-001-logging-observability.md
  - security/sec-001-secrets-workload-identity.md
  - platform/runtime-001-docker.md
  - platform/ci-001-github-actions-quality-gate.md
  - platform/deploy-001-release-sequencing.md
---

# 맛잇온 ADR 인덱스

| ADR ID | 제목 | 상태 | 우선순위 | 관련 기술 | 관련 범위 | 문서 경로 |
|---|---|---|---|---|---|---|
| [ADR-LANG-001](platform/lang-001-java-21-runtime.md) | Java 21 런타임 기준 | Accepted | Critical | JDK 21.0.12 LTS | 전체 백엔드 | [문서](platform/lang-001-java-21-runtime.md) |
| [ADR-BUILD-001](platform/build-001-gradle-groovy.md) | Gradle과 Groovy DSL 빌드 체계 | Accepted | Critical | Gradle 8.14.3, Groovy DSL | 전체 백엔드·CI | [문서](platform/build-001-gradle-groovy.md) |
| [ADR-FRAME-001](platform/frame-001-spring-boot.md) | Spring Boot 애플리케이션 기준 | Accepted | Critical | Spring Boot 4.1.0, Spring Security 7.1.0 BOM | 전체 백엔드 | [문서](platform/frame-001-spring-boot.md) |
| [ADR-WEB-001](platform/web-001-frontend-platform.md) | 프론트엔드 런타임과 프레임워크 기준 | Accepted | High | Node.js 24.18.0, Next.js 16.2.11, TypeScript 7.0.2 | 전체 웹 UI | [문서](platform/web-001-frontend-platform.md) |
| [ADR-WEB-002](platform/web-002-data-state.md) | 프론트엔드 데이터와 상태 책임 분리 | Accepted | Medium | Server Components `fetch`, TanStack Query, URL Query Parameter, `useState` | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)~[WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 웹 UI | [문서](platform/web-002-data-state.md) |
| [ADR-WEB-003](platform/web-003-routing-boundary.md) | 웹 화면·API·운영 경로 경계 | Accepted | Critical | Next.js App Router, Nginx, Spring Security, `/api`, `/internal` | 전체 웹·API·운영 진입점 | [문서](platform/web-003-routing-boundary.md) |
| [ADR-ARCH-001](architecture/arch-001-domain-monolith.md) | 단일 모듈 도메인 중심 모놀리스 | Accepted | Critical | 단일 모듈, 계층형 모놀리스 | 전체 Workstream | [문서](architecture/arch-001-domain-monolith.md) |
| [ADR-ARCH-002](architecture/arch-002-external-ports-adapters.md) | 외부 연동 Port/Adapter 경계 | Accepted | High | Port/Adapter | [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)~[WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록), 외부 연동 | [문서](architecture/arch-002-external-ports-adapters.md) |
| [ADR-DATA-001](data/data-001-postgresql.md) | PostgreSQL 17.10 주 데이터베이스 | Accepted | Critical | PostgreSQL 17.10 | 전체 영속 데이터 | [문서](data/data-001-postgresql.md) |
| [ADR-DATA-002](data/data-002-database-placement.md) | 개발 Docker와 운영 RDS 데이터베이스 분리 | Accepted | High | Docker PostgreSQL, Amazon RDS | 개발·테스트·운영 | [문서](data/data-002-database-placement.md) |
| [ADR-DATA-003](data/data-003-spring-data-jpa.md) | Spring Data JPA 기본 데이터 접근 | Accepted | High | Spring Data JPA | 전체 Repository | [문서](data/data-003-spring-data-jpa.md) |
| [ADR-DATA-004](data/data-004-flyway.md) | Flyway 스키마 마이그레이션 | Accepted | Critical | Flyway 12.4.0 | 전체 스키마 변경 | [문서](data/data-004-flyway.md) |
| [ADR-AUTH-001](security/auth-001-spring-security-jwt.md) | 관리자 Spring Security JWT 인증·인가 | Accepted | Critical | Spring Security 7.1.0, JWT, Redis 8.8 Refresh Token | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 관리자 | [문서](security/auth-001-spring-security-jwt.md) |
| [ADR-AUTH-002](security/auth-002-member-jwt-refresh-token.md) | 회원 JWT와 Refresh Token | Accepted | Critical | Spring Security 7.1.0, JWT, Redis 8.8 Refresh Token | 회원 계정·인증 | [문서](security/auth-002-member-jwt-refresh-token.md) |
| [ADR-AUTH-003](security/auth-003-confirmation-token.md) | 관리자 등록 확인 Token의 저장·소비·재시도 | Accepted | Critical | PostgreSQL, SHA-256, 불투명 Token, JSONB Snapshot | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 기준정보 등록 | [문서](security/auth-003-confirmation-token.md) |
| [ADR-DATA-005](data/data-005-redis-refresh-token.md) | Redis 8.8 관리자 Refresh Token 저장소 | Accepted | Critical | Redis Open Source 8.8 | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 인증·운영 | [문서](data/data-005-redis-refresh-token.md) |
| [ADR-DATA-007](data/data-007-uuid-v4-identifiers.md) | 애플리케이션 생성 UUID v4 내부 식별자 | Accepted | High | Java UUID, PostgreSQL uuid | 전체 영속 데이터 | [문서](data/data-007-uuid-v4-identifiers.md) |
| [ADR-DATA-008](data/data-008-publication-lifecycle-soft-delete.md) | 공개 상태와 논리 삭제 생명주기 분리 | Accepted | Critical | PostgreSQL CHECK, partial index | 핵심 공개 데이터 | [문서](data/data-008-publication-lifecycle-soft-delete.md) |
| [ADR-EXT-001](integration/ext-001-reference-verification.md) | 관리자 외부 기준정보 확인 서비스 | Accepted | High | Kakao Local REST API V2, YouTube Data API v3 | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 등록 | [문서](integration/ext-001-reference-verification.md) |
| [ADR-MAP-001](integration/map-001-map-bounds-search.md) | Kakao 지도 표시와 WGS84 영역 조회 | Accepted | High | Kakao Maps JavaScript API V3, WGS84 bounds, PostgreSQL 좌표 컬럼 | [WS-07](../02-analysis/first-expansion-workstreams.md#6-ws-07-지도-탐색) 지도 탐색 | [문서](integration/map-001-map-bounds-search.md) |
| [ADR-TEST-001](quality/test-001-automation-strategy.md) | 계층별 자동화 테스트 전략 | Accepted | Critical | JUnit 5, Mockito, Spring Boot Test, Testcontainers 2.0.5, WireMock | 전체 Workstream | [문서](quality/test-001-automation-strategy.md) |
| [ADR-OBS-001](quality/obs-001-logging-observability.md) | 애플리케이션 로그와 운영 관측 기준 | Accepted | High | SLF4J, Logback, Actuator, CloudWatch | 전체 운영 | [문서](quality/obs-001-logging-observability.md) |
| [ADR-SEC-001](security/sec-001-secrets-workload-identity.md) | 비밀정보와 AWS 워크로드 인증 | Accepted | Critical | Parameter Store, KMS, IAM Role, GitHub OIDC | 운영·CI·외부 연동 | [문서](security/sec-001-secrets-workload-identity.md) |
| [ADR-DATA-009](data/data-009-pre-release-migration-consolidation.md) | 운영 배포 전 마이그레이션 통합 범위 | Accepted | High | Flyway | 마이그레이션 파일과 모든 환경 스키마 | [문서](data/data-009-pre-release-migration-consolidation.md) |
| [ADR-DATA-010](data/data-010-recent-view-retention-cleanup.md) | 최근 본 맛집 보존 기간 정리 실행 | Accepted | High | Spring Scheduler, PostgreSQL | [WS-06](../02-analysis/first-expansion-workstreams.md#5-ws-06-개인-맛집-관리) 최근 기록 생명주기 | [문서](data/data-010-recent-view-retention-cleanup.md) |
| [ADR-RUNTIME-001](platform/runtime-001-docker.md) | Docker 기반 실행 환경 | Accepted | High | Docker | 개발·테스트·배포 산출물 | [문서](platform/runtime-001-docker.md) |
| [ADR-CI-001](platform/ci-001-github-actions-quality-gate.md) | GitHub Actions 빌드·테스트 품질 게이트 | Accepted | Critical | GitHub Actions | 전체 배포 후보 | [문서](platform/ci-001-github-actions-quality-gate.md) |
| [ADR-DEPLOY-001](platform/deploy-001-release-sequencing.md) | 단계별 로컬 검증과 최종 AWS 배포 순서 | Superseded | Critical | Docker, AWS | 전체 단계 및 최종 배포 | [문서](platform/deploy-001-release-sequencing.md) |
| [ADR-DEPLOY-002](platform/deploy-002-validation-deployment-before-expansion.md) | 초기 운영 배포 선행과 확장 단계별 인프라 반영 | Accepted | Critical | Docker, AWS | M2 초기 운영 배포 및 이후 확장 | [문서](platform/deploy-002-validation-deployment-before-expansion.md) |
